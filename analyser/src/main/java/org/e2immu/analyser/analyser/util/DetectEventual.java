/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public record DetectEventual(MethodInfo methodInfo,
                             MethodAnalysis methodAnalysis,
                             TypeAnalysis typeAnalysis,
                             AnalyserContext analyserContext) {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectEventual.class);

    public MethodAnalysis.Eventual detect(EvaluationResult context) {
        CausesOfDelay delaysE1 = typeAnalysis.approvedPreconditionsStatus(false);
        if (delaysE1.isDelayed()) {
            LOGGER.debug("No decision on approved E1 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.delayedEventual(delaysE1);
        }
        CausesOfDelay delaysE2 = typeAnalysis.approvedPreconditionsStatus(true);
        if (delaysE2.isDelayed()) {
            LOGGER.debug("No decision on approved E2 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.delayedEventual(delaysE2);
        }

        DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
        if (modified.isDelayed()) {
            LOGGER.debug("Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return MethodAnalysis.delayedEventual(modified.causesOfDelay());
        }
        CausesOfDelay delaysPc = methodAnalysis.getPreconditionForEventual().causesOfDelay();
        if (delaysPc.isDelayed()) {
            LOGGER.debug("Waiting for preconditions to be resolved in {}", methodInfo.distinguishingName());
            return MethodAnalysis.delayedEventual(delaysPc);
        }
        Precondition precondition = methodAnalysis.getPreconditionForEventual();
        boolean e2 = typeAnalysis.approvedPreconditionsIsNotEmpty(true);

        if (modified.valueIsFalse() && methodInfo.returnType().isBoolean()) {
            MethodAnalysis.Eventual eventual = testMark(context, precondition, e2);
            if (eventual != null) return eventual;
        }

        /*
        @Mark("label")
        @Only(before="label"), @Only(after="label")
         */
        if (precondition == null || precondition.isEmpty()) {
            return eventualFromEventuallyImmutableFields(context, modified);
        }

        MethodAnalysis.Eventual fromCompanion = eventualFromCompanion(precondition);
        if (fromCompanion != null) {
            return fromCompanion;
        }

        FieldsAndBefore fieldsAndBefore = analyseExpression(context, e2, precondition.expression(), false);
        if (fieldsAndBefore == NO_FIELDS) return MethodAnalysis.NOT_EVENTUAL;

        // fieldsAndBefore.before == true -> @Mark or @Only(before); otherwise @OnlyAfter
        // the easy one is @OnlyAfter
        if (!fieldsAndBefore.before) {
            return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, true, null);
        }

        /* now we need to decide between @Only(before) and @Mark
         either all are @Mark, or all are @Only(before)

        the cause of the precondition will help in case of non-assignment-based @Mark detection
         */
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalyser(methodInfo);
        DV isMark = AssignmentIncompatibleWithPrecondition.isMark(analyserContext, precondition, methodAnalyser);
        if (isMark.isDelayed()) {
            return MethodAnalysis.delayedEventual(isMark.causesOfDelay());
        }

        if (isMark.valueIsTrue()) {
            return new MethodAnalysis.Eventual(fieldsAndBefore.fields, true, null, null);
        }
        DV isMarkViaModifyingMethod = MethodCallIncompatibleWithPrecondition.isMark(context,
                fieldsAndBefore.fields, methodAnalyser);
        if (isMarkViaModifyingMethod.isDelayed()) {
            return MethodAnalysis.delayedEventual(isMarkViaModifyingMethod.causesOfDelay());
        }
        if (isMarkViaModifyingMethod.valueIsTrue()) {
            return new MethodAnalysis.Eventual(fieldsAndBefore.fields, true, null, null);
        }
        return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, false, null);
    }

    /*
    See e.g. EventuallyImmutableUtil_2, _12, _13.
    When a method accesses one or more eventually immutable fields in a consistent way, this can be propagated,
    e.g.

    @Modified
    @Only(before = "eventuallyFinal")   // modified + eventuallyFinal remains in BEFORE state
    public void set(String s) {
        eventuallyFinal.setVariable(s);
    }

    @Mark("eventuallyFinal")            // modified + in AFTER state
    public void done(String last) {
        eventuallyFinal.setFinal(last);
    }
     */
    private MethodAnalysis.Eventual eventualFromEventuallyImmutableFields(EvaluationResult context, DV modified) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        Map<FieldInfo, MultiLevel.Effective> map = new HashMap<>();
        for (FieldInfo fieldInfo : methodInfo.typeInfo.typeInspection.get().fields()) {
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();
            if (bestType == null) continue; // unbound type parameter, is never eventual
            TypeAnalysis bestTypeAnalysis = context.getAnalyserContext().getTypeAnalysis(bestType);
            DV immutableType = bestTypeAnalysis.getProperty(Property.IMMUTABLE);
            if (immutableType.isDelayed()) {
                causes = causes.merge(immutableType.causesOfDelay());
            } else if (bestTypeAnalysis.approvedPreconditionsStatus(false).isDelayed()) {
                causes = causes.merge(bestTypeAnalysis.approvedPreconditionsStatus(false));
            } else if (bestTypeAnalysis.approvedPreconditionsStatus(true).isDelayed()) {
                causes = causes.merge(bestTypeAnalysis.approvedPreconditionsStatus(true));
            } else if (bestTypeAnalysis.isEventual()) {
                for (VariableInfo vi : methodAnalysis().getFieldAsVariable(fieldInfo)) {
                    // currently, only looking at EXT_IMM: no assignment on this field!!
                    DV immutableField = vi.getProperty(Property.CONTEXT_IMMUTABLE);
                    if (immutableField.isDelayed()) {
                        causes = causes.merge(immutableField.causesOfDelay());
                    } else {
                        MultiLevel.Effective effective = MultiLevel.effective(immutableField);
                        if (effective == MultiLevel.Effective.EVENTUAL_BEFORE && modified.valueIsTrue()) {
                            LOGGER.debug("Detect @Only before={} in @Modified method {}", fieldInfo.name, methodInfo.fullyQualifiedName);
                            map.put(fieldInfo, effective);
                        } else if (effective == MultiLevel.Effective.EVENTUAL_AFTER && modified.valueIsFalse()) {
                            LOGGER.debug("Detect @Only after={} in @NotModified method {}", fieldInfo.name, methodInfo.fullyQualifiedName);
                            map.put(fieldInfo, effective);
                        } else if (effective == MultiLevel.Effective.EVENTUAL_AFTER && modified.valueIsTrue()) {
                            LOGGER.debug("Detect @Mark({}) in @Modified method {}", fieldInfo.name, methodInfo.fullyQualifiedName);
                            map.put(fieldInfo, MultiLevel.Effective.EVENTUAL);
                        }
                    }
                }
            }
        }
        if (causes.isDelayed()) {
            return MethodAnalysis.delayedEventual(causes);
        }
        if (!map.isEmpty()) {
            MultiLevel.Effective effective = map.values().stream().reduce(MultiLevel.Effective.DELAY,
                    (e1, e2) -> e1 == MultiLevel.Effective.DELAY ? e2 : e2 == MultiLevel.Effective.DELAY ? e1 :
                            e1 != e2 ? MultiLevel.Effective.FALSE : e1);
            if (effective != MultiLevel.Effective.FALSE) {
                Set<FieldInfo> fields = map.keySet();
                return switch (effective) {
                    case EVENTUAL -> new MethodAnalysis.Eventual(fields, true, null, null);
                    case EVENTUAL_BEFORE -> new MethodAnalysis.Eventual(fields, false, false, null);
                    case EVENTUAL_AFTER -> new MethodAnalysis.Eventual(fields, false, true, null);
                    default -> MethodAnalysis.NOT_EVENTUAL;
                };
            }
        }
        return MethodAnalysis.NOT_EVENTUAL;
    }

    private MethodAnalysis.Eventual testMark(EvaluationResult context, Precondition precondition, boolean e2) {
        /*
        @TestMark first situation: non-modifying method, no preconditions, simply detecting method calls that are @TestMark
        themselves.
        */
        Expression srv = methodAnalysis.getSingleReturnValue();
        if (precondition != null && precondition.isEmpty()) {
            if (srv.isDelayed()) {
                LOGGER.debug("Waiting for @TestMark, need single return value of {}", methodInfo.distinguishingName());
                return MethodAnalysis.delayedEventual(srv.causesOfDelay());
            }
            if (srv instanceof InlinedMethod inlinedMethod) {
                MethodAnalysis.Eventual eventual = detectTestMark(inlinedMethod.expression());
                if (eventual.causesOfDelay().isDelayed()) {
                    return MethodAnalysis.delayedEventual(eventual.causesOfDelay());
                }
                if (eventual != MethodAnalysis.NOT_EVENTUAL) {
                    return eventual;
                }
            }
            MethodAnalysis.Eventual eventual = detectTestMark(srv);
            if (eventual.causesOfDelay().isDelayed()) {
                return MethodAnalysis.delayedEventual(eventual.causesOfDelay());
            }
            if (eventual != MethodAnalysis.NOT_EVENTUAL) {
                return eventual;
            }
        }

        /*
        @TestMark second situation: we require approvedPreconditions; still non-modifying

        From the preconditions, we extract the approved fields, which have their associated precondition==after state.
        We join up those, either all in after state, or all in before state.

        The outcome is either @TestMark("fields") (all conditions true: field1 && field2 && ...
        or @TestMark("fields", before="true") which means all conditions false: !field1 && !field2 && ...

        */
        if (typeAnalysis.approvedPreconditionsIsNotEmpty(e2)) {
            FieldsAndBefore fieldsAndBefore = analyseExpression(context, e2, srv, true);
            if (fieldsAndBefore == NO_FIELDS) return MethodAnalysis.NOT_EVENTUAL;
            return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, null, !fieldsAndBefore.before);
        }
        return null;
    }

    public AnnotationExpression makeAnnotation(MethodAnalysis.Eventual eventual) {
        E2ImmuAnnotationExpressions e2ae = analyserContext.getE2ImmuAnnotationExpressions();
        if (eventual.mark()) {
            return new AnnotationExpressionImpl(e2ae.mark.typeInfo(),
                    List.of(new MemberValuePair("value",
                            new StringConstant(analyserContext.getPrimitives(), eventual.markLabel()))));
        }
        if (eventual.test() != null) {
            return new AnnotationExpressionImpl(e2ae.testMark.typeInfo(),
                    eventual.test() ?
                            List.of(new MemberValuePair("value",
                                    new StringConstant(analyserContext.getPrimitives(), eventual.markLabel()))) :
                            List.of(new MemberValuePair("value",
                                            new StringConstant(analyserContext.getPrimitives(), eventual.markLabel())),
                                    new MemberValuePair("before",
                                            new BooleanConstant(analyserContext.getPrimitives(), true)))
            );
        }
        return new AnnotationExpressionImpl(e2ae.only.typeInfo(),
                List.of(new MemberValuePair(eventual.after() ? "after" : "before",
                        new StringConstant(analyserContext.getPrimitives(), eventual.markLabel()))));
    }

    /**
     * @param expression the expression to analyse, like "flipSwitch.isSet()"
     * @return null when the expression does not represent a @TestMark; the mark value otherwise
     */
    private MethodAnalysis.Eventual detectTestMark(Expression expression) {
        Negation neg = null;
        if (expression.isInstanceOf(And.class) || ((neg = expression.asInstanceOf(Negation.class)) != null)
                && neg.expression.isInstanceOf(And.class)) {
            And and;
            boolean negated;
            if (neg != null) {
                and = (And) neg.expression;
                negated = true;
            } else {
                and = (And) expression;
                negated = false;
            }
            Set<FieldInfo> fields = new HashSet<>();
            for (Expression part : and.getExpressions()) {
                MethodAnalysis.Eventual eventual = singleTestMark(part);
                if (eventual.causesOfDelay().isDelayed()) {
                    return eventual;
                }
                if (eventual == MethodAnalysis.NOT_EVENTUAL || eventual.test() == Boolean.FALSE) {
                    return MethodAnalysis.NOT_EVENTUAL;
                }
                fields.addAll(eventual.fields());
            }
            return new MethodAnalysis.Eventual(fields, false, null, !negated);
        }
        return singleTestMark(expression);
    }

    private MethodAnalysis.Eventual singleTestMark(Expression expression) {
        Expression expressionAfterNegation;
        boolean negated;
        if (expression instanceof Negation negation) {
            expressionAfterNegation = negation.expression;
            negated = true;
        } else {
            expressionAfterNegation = expression;
            negated = false;
        }
        // @TestMark method on This, FieldReference
        VariableExpression ve;
        if (expressionAfterNegation instanceof MethodCall methodCall
                && !methodInfo.equals(methodCall.methodInfo) // block recursive results (Basics_30)
                && ((ve = methodCall.object.asInstanceOf(VariableExpression.class)) != null)) {
            MethodAnalysis methodCallAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
            MethodAnalysis.Eventual mao = methodCallAnalysis.getEventual();
            if (mao.causesOfDelay().isDelayed()) {
                return MethodAnalysis.delayedEventual(mao.causesOfDelay());
            }
            Boolean testMark = mao.test();
            if (testMark == null) return MethodAnalysis.NOT_EVENTUAL;
            Set<FieldInfo> fields;
            if (ve.variable() instanceof This) fields = mao.fields();
            else if (ve.variable() instanceof FieldReference fr) {
                FieldInfo selected = typeAnalysis().translateToVisibleField(fr);
                if (selected == null) {
                    return MethodAnalysis.NOT_EVENTUAL;
                }
                fields = Set.of(selected);
            } else return MethodAnalysis.NOT_EVENTUAL;
            // test == true corresponds to isSet, after==true, no negation; remains true if there is no negation
            boolean test = testMark ^ negated; // 1^0==0^1==1; 0^0==1^1==0
            return new MethodAnalysis.Eventual(fields, false, null, test);
        }
        return MethodAnalysis.NOT_EVENTUAL;
    }

    private static final FieldsAndBefore NO_FIELDS = new FieldsAndBefore(Set.of(), true);

    private record FieldsAndBefore(Set<FieldInfo> fields, boolean before) {
    }

    private MethodAnalysis.Eventual eventualFromCompanion(Precondition precondition) {
        Precondition.CompanionCause cc = precondition.singleCompanionCauseOrNull();
        if (cc != null) {
            Precondition.MethodCallAndNegation mc = precondition.expressionIsPossiblyNegatedMethodCall();
            if (mc != null) {
                MethodAnalysis analysis = analyserContext.getMethodAnalysis(mc.methodCall().methodInfo);
                MethodAnalysis.Eventual eventual = analysis.getEventual();
                if (eventual != MethodAnalysis.NOT_EVENTUAL) {
                    LOGGER.debug("Precondition for eventual copied from precondition: companion cause: {}", methodInfo.fullyQualifiedName);
                    // isFrozen == @TestMark, but then in a precondition... so we return a before
                    if (eventual.mark() == Boolean.FALSE && eventual.test() != null) {
                        boolean after = eventual.test() != mc.negation();
                        return new MethodAnalysis.Eventual(eventual.fields(), false, after, null);
                    }
                }
            }
        }
        return null;
    }

    private FieldsAndBefore analyseExpression(EvaluationResult context,
                                              boolean e2,
                                              Expression expression,
                                              boolean allowLocalCopies) {

        Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(expression,
                filter.individualFieldClause(context.getAnalyserContext(), allowLocalCopies));
        boolean allAfter = true;
        boolean allBefore = true;
        Set<FieldInfo> fields = new HashSet<>();
        for (Map.Entry<FieldReference, Expression> e : filterResult.accepted().entrySet()) {
            if (typeAnalysis.approvedPreconditionsStatus(e2, e.getKey()).isDone()) {
                Expression approvedPreconditionBefore = typeAnalysis.getApprovedPreconditions(e2, e.getKey());
                FieldInfo field = typeAnalysis.translateToVisibleField(e.getKey());
                if (field != null) {
                    Expression value = e.getValue();
                    if (approvedPreconditionBefore.equals(value)) {
                        allAfter = false;
                        fields.add(field);
                    } else {
                        Expression negated = Negation.negate(context, approvedPreconditionBefore);
                        if (negated.equals(value)) {
                            allBefore = false;
                            fields.add(field);
                        }
                    }
                }
            }
        }
        if (allAfter && !allBefore) {
            return new FieldsAndBefore(fields, false);
        }
        if (allBefore && !allAfter) {
            return new FieldsAndBefore(fields, true);
        }
        return NO_FIELDS;
    }
}

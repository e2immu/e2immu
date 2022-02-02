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
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DetectEventual(MethodInfo methodInfo,
                             MethodAnalysis methodAnalysis,
                             TypeAnalysis typeAnalysis,
                             AnalyserContext analyserContext) {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectEventual.class);

    public MethodAnalysis.Eventual detect(EvaluationContext evaluationContext) {
        CausesOfDelay delaysE1 = typeAnalysis.approvedPreconditionsStatus(false);
        if (delaysE1.isDelayed()) {
            LOGGER.debug("No decision on approved E1 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.delayedEventual(delaysE1);
        }
        CausesOfDelay delaysE2 = typeAnalysis.approvedPreconditionsStatus(false);
        if (delaysE2.isDelayed()) {
            LOGGER.debug("No decision on approved E2 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.delayedEventual(delaysE2);
        }

        DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD);
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

            /*
            @TestMark first situation: non-modifying method, no preconditions, simply detecting method calls that are @TestMark
            themselves.
            */

            if (precondition != null && precondition.isEmpty()) {
                Expression srv = methodAnalysis.getSingleReturnValue();
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
            }

            /*
            @TestMark second situation: we require approvedPreconditions; still non-modifying

            From the preconditions, we extract the approved fields, which have their associated precondition==after state.
            We join up those, either all in after state, or all in before state.

            The outcome is either @TestMark("fields") (all conditions true: field1 && field2 && ...
            or @TestMark("fields", before="true") which means all conditions false: !field1 && !field2 && ...

            */
            Expression srv = methodAnalysis.getSingleReturnValue();
            if (srv != null && typeAnalysis.approvedPreconditionsIsNotEmpty(e2) && srv instanceof InlinedMethod im) {
                FieldsAndBefore fieldsAndBefore = analyseExpression(evaluationContext, e2, im.expression(), true);
                if (fieldsAndBefore == NO_FIELDS) return MethodAnalysis.NOT_EVENTUAL;
                return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, null, !fieldsAndBefore.before);
            }
        }

        /*
        @Mark("label")
        @Only(before="label"), @Only(after="label")
         */
        if (precondition == null || precondition.isEmpty()) return MethodAnalysis.NOT_EVENTUAL;

        FieldsAndBefore fieldsAndBefore = analyseExpression(evaluationContext, e2, precondition.expression(), false);
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
        DV isMarkViaModifyingMethod = MethodCallIncompatibleWithPrecondition.isMark(evaluationContext,
                fieldsAndBefore.fields, methodAnalyser);
        if (isMarkViaModifyingMethod.isDelayed()) {
            return MethodAnalysis.delayedEventual(isMarkViaModifyingMethod.causesOfDelay());
        }
        if (isMarkViaModifyingMethod.valueIsTrue()) {
            return new MethodAnalysis.Eventual(fieldsAndBefore.fields, true, null, null);
        }
        return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, false, null);
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
        if (expression instanceof And || expression instanceof Negation neg && neg.expression instanceof And) {
            And and;
            boolean negated;
            if (expression instanceof Negation negation) {
                and = (And) negation.expression;
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
        if (expressionAfterNegation instanceof MethodCall methodCall &&
                ((ve = methodCall.object.asInstanceOf(VariableExpression.class)) != null)) {
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

    private FieldsAndBefore analyseExpression(EvaluationContext evaluationContext,
                                              boolean e2,
                                              Expression expression,
                                              boolean allowLocalCopies) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(expression,
                filter.individualFieldClause(evaluationContext.getAnalyserContext(), allowLocalCopies));
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
                        Expression negated = Negation.negate(evaluationContext, approvedPreconditionBefore);
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

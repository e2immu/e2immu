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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public record DetectEventual(MethodInfo methodInfo,
                             MethodAnalysisImpl.Builder methodAnalysis,
                             TypeAnalysisImpl.Builder typeAnalysis,
                             Set<FieldInfo> visibleFields,
                             AnalyserContext analyserContext) {

    public MethodAnalysis.Eventual detect(EvaluationContext evaluationContext) {
        if (!typeAnalysis.approvedPreconditionsIsFrozen(false)) {
            log(DELAYED, "No decision on approved E1 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_EVENTUAL;
        }
        if (!typeAnalysis.approvedPreconditionsIsFrozen(true)) {
            log(DELAYED, "No decision on approved E2 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_EVENTUAL;
        }

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_EVENTUAL;
        }
        if (!methodAnalysis.preconditionForEventual.isSet()) {
            log(DELAYED, "Waiting for preconditions to be resolved in {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_EVENTUAL;
        }
        Optional<Precondition> precondition = methodAnalysis.preconditionForEventual.get();
        boolean e2 = !typeAnalysis.approvedPreconditionsIsEmpty(true);

        if (modified == Level.FALSE && Primitives.isBoolean(methodInfo.returnType())) {

            /*
            @TestMark first situation: non-modifying method, no preconditions, simply detecting method calls that are @TestMark
            themselves.
            */

            if (precondition.isEmpty()) {
                if (methodAnalysis.getSingleReturnValue() == null) {
                    log(DELAYED, "Waiting for @TestMark, need single return value of {}", methodInfo.distinguishingName());
                    return MethodAnalysis.DELAYED_EVENTUAL;
                }
                Expression srv = methodAnalysis.getSingleReturnValue();
                if (srv instanceof InlinedMethod inlinedMethod) {
                    MethodAnalysis.Eventual eventual = detectTestMark(inlinedMethod.expression());
                    if (eventual == MethodAnalysis.DELAYED_EVENTUAL) {
                        return MethodAnalysis.DELAYED_EVENTUAL;
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
            if (srv != null && !typeAnalysis.approvedPreconditionsIsEmpty(e2) && srv instanceof InlinedMethod im) {
                FieldsAndBefore fieldsAndBefore = analyseExpression(evaluationContext, e2, im.expression());
                if (fieldsAndBefore == NO_FIELDS) return MethodAnalysis.NOT_EVENTUAL;
                return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, null, !fieldsAndBefore.before);
            }
        }

        /*
        @Mark("label")
        @Only(before="label"), @Only(after="label")
         */
        if (precondition.isEmpty()) return MethodAnalysis.NOT_EVENTUAL;

        FieldsAndBefore fieldsAndBefore = analyseExpression(evaluationContext, e2, precondition.get().expression());
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
        Boolean isMark = AssignmentIncompatibleWithPrecondition.isMark(analyserContext, precondition.get(), methodAnalyser);
        if (isMark == null) {
            return MethodAnalysis.DELAYED_EVENTUAL;
        }

        if (isMark) {
            return new MethodAnalysis.Eventual(fieldsAndBefore.fields, true, null, null);
        }
        return new MethodAnalysis.Eventual(fieldsAndBefore.fields, false, false, null);
    }

    /*
    Given a field reference in a precondition, select the field that fits to our type.
    e.g., the field could be this.x.y.t, with x being the field in this type;
    alternatively, x.y.t could result in y where y is a field of this type, and x is another
    local instance of the type itself.
    Important is that t may be an eventually final variable in a utility class such as SetOnce,
    not relevant to the type's instance of SetOnce
     */
    private FieldInfo selectField(FieldReference fieldReference) {
        if (visibleFields.contains(fieldReference.fieldInfo)) return fieldReference.fieldInfo;
        if (fieldReference.scope instanceof FieldReference fr) return selectField(fr);
        return null; // not one of ours, ignore
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
            for (Expression part : and.expressions()) {
                MethodAnalysis.Eventual eventual = singleTestMark(part);
                if (eventual == MethodAnalysis.DELAYED_EVENTUAL) return eventual;
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
        if (expressionAfterNegation instanceof MethodCall methodCall &&
                methodCall.object instanceof VariableExpression ve) {
            MethodAnalysis methodCallAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
            MethodAnalysis.Eventual mao = methodCallAnalysis.getEventual();
            if (mao == MethodAnalysis.DELAYED_EVENTUAL) {
                return MethodAnalysis.DELAYED_EVENTUAL;
            }
            Boolean testMark = mao.test();
            if (testMark == null) return MethodAnalysis.NOT_EVENTUAL;
            Set<FieldInfo> fields;
            if (ve.variable() instanceof This) fields = mao.fields();
            else if (ve.variable() instanceof FieldReference fr) {
                FieldInfo selected = selectField(fr);
                if (selected == null) {
                    return MethodAnalysis.NOT_EVENTUAL;
                }
                fields = Set.of(selected);
            } else return MethodAnalysis.NOT_EVENTUAL;
            return new MethodAnalysis.Eventual(fields, false, null, testMark ^ negated);
        }
        return MethodAnalysis.NOT_EVENTUAL;
    }

    private static final FieldsAndBefore NO_FIELDS = new FieldsAndBefore(Set.of(), true);

    private record FieldsAndBefore(Set<FieldInfo> fields, boolean before) {
    }

    private FieldsAndBefore analyseExpression(EvaluationContext evaluationContext, boolean e2, Expression expression) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(expression, filter.individualFieldClause());
        boolean allAfter = true;
        boolean allBefore = true;
        Set<FieldInfo> fields = new HashSet<>();
        for (Map.Entry<FieldReference, Expression> e : filterResult.accepted().entrySet()) {
            if (typeAnalysis.approvedPreconditionsIsSet(e2, e.getKey().fieldInfo)) {
                Expression approvedPreconditionBefore = typeAnalysis.getApprovedPreconditions(e2, e.getKey().fieldInfo);
                FieldInfo field = selectField(e.getKey());
                if (field != null) {
                    if (approvedPreconditionBefore.equals(e.getValue())) {
                        allAfter = false;
                        fields.add(field);
                    } else {
                        Expression negated = Negation.negate(evaluationContext, approvedPreconditionBefore);
                        if (negated.equals(e.getValue())) {
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

/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.MARK;
import static org.e2immu.analyser.util.Logger.log;

public record DetectMarkAndOnly(MethodInfo methodInfo,
                                MethodAnalysisImpl.Builder methodAnalysis,
                                TypeAnalysis typeAnalysis,
                                AnalyserContext analyserContext) {

    public MethodAnalysis.MarkAndOnly detect(EvaluationContext evaluationContext) {

        SetOnceMap<String, Expression> e1 = ((TypeAnalysisImpl.Builder) typeAnalysis).approvedPreconditionsE1;
        if (!e1.isFrozen()) {
            log(DELAYED, "No decision on approved E1 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_MARK_AND_ONLY;
        }
        SetOnceMap<String, Expression> e2 = ((TypeAnalysisImpl.Builder) typeAnalysis).approvedPreconditionsE2;
        if (!e2.isFrozen()) {
            log(DELAYED, "No decision on approved E2 preconditions yet for {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_MARK_AND_ONLY;
        }

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_MARK_AND_ONLY;
        }
        if (!methodAnalysis.preconditionForMarkAndOnly.isSet()) {
            log(DELAYED, "Waiting for preconditions to be resolved in {}", methodInfo.distinguishingName());
            return MethodAnalysis.DELAYED_MARK_AND_ONLY;
        }
        List<Expression> preconditions = methodAnalysis.preconditionForMarkAndOnly.get();
        SetOnceMap<String, Expression> approvedPreconditions = !e2.isEmpty() ? e2 : e1;

        if (modified == Level.FALSE && Primitives.isBoolean(methodInfo.returnType())) {

            /*
            @TestMark first situation: non-modifying method, no preconditions, simply detecting method calls that are @TestMark
            themselves.
            */

            if (preconditions.isEmpty()) {
                if (methodAnalysis.getSingleReturnValue() == null) {
                    log(DELAYED, "Waiting for @TestMark, need single return value of {}", methodInfo.distinguishingName());
                    return MethodAnalysis.DELAYED_MARK_AND_ONLY;
                }
                Expression srv = methodAnalysis.getSingleReturnValue();
                if (srv != null) {
                    MethodAnalysis.MarkAndOnly markAndOnly = detectTestMark(srv);
                    if (markAndOnly == MethodAnalysis.DELAYED_MARK_AND_ONLY) {
                        return MethodAnalysis.DELAYED_MARK_AND_ONLY;
                    }
                    if (markAndOnly != MethodAnalysis.NO_MARK_AND_ONLY) {
                        return markAndOnly;
                    }
                }
            }

            /*
            @TestMark second situation: we require approvedPreconditions; still non-modifying
            */
            Expression srv = methodAnalysis.getSingleReturnValue();
            if (srv != null && !approvedPreconditions.isEmpty() && srv instanceof InlinedMethod im) {
                // TODO this is not the joint label, we'll need more code for that
                String markLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(im.expression());
                if (approvedPreconditions.isSet(markLabel)) {
                    Expression before = approvedPreconditions.get(markLabel);
                    if (before.equals(im.expression())) {
                        return new MethodAnalysis.MarkAndOnly(List.of(), markLabel, false, null, false);
                    }
                    Expression negated = Negation.negate(evaluationContext, before);
                    if (negated.equals(im.expression())) {
                        return new MethodAnalysis.MarkAndOnly(List.of(), markLabel, false, null, true);
                    }
                }
            }
        }

        /*
        @Mark("label")
        @Only(before="label"), @Only(after="label")
         */

        boolean mark = false;
        Boolean after = null;
        String jointMarkLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(preconditions);
        for (Expression precondition : preconditions) {
            String markLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(precondition);
            if (!approvedPreconditions.isSet(markLabel)) {
                // not going to work...
                continue;
            }
            Expression before = approvedPreconditions.get(markLabel);
            // TODO parameters have different owners, so a precondition containing them cannot be the same in a different method
            // we need a better solution
            if (before.toString().equals(precondition.toString())) {
                after = false;
            } else {
                Expression negated = Negation.negate(evaluationContext, precondition);
                if (before.toString().equals(negated.toString())) {
                    if (after == null) after = true;
                } else {
                    E2ImmuAnnotationExpressions e2ae = analyserContext.getE2ImmuAnnotationExpressions();
                    log(MARK, "No approved preconditions for {} in {}", precondition, methodInfo.distinguishingName());
                    if (!methodAnalysis.annotations.isSet(e2ae.mark)) {
                        methodAnalysis.annotations.put(e2ae.mark, false);
                    }
                    if (!methodAnalysis.annotations.isSet(e2ae.only)) {
                        methodAnalysis.annotations.put(e2ae.only, false);
                    }
                    return MethodAnalysis.NO_MARK_AND_ONLY;
                }
            }
            if (!mark && !after) {
                MethodAnalyser methodAnalyser = analyserContext.getMethodAnalyser(methodInfo);
                Boolean isMark = AssignmentIncompatibleWithPrecondition.isMark(analyserContext,
                        precondition, methodAnalyser, true);
                if (isMark == null) {
                    return MethodAnalysis.DELAYED_MARK_AND_ONLY;
                }
                mark = isMark;
            }
        }
        if (after == null) {
            return MethodAnalysis.NO_MARK_AND_ONLY;
        }

        return new MethodAnalysis.MarkAndOnly(preconditions, jointMarkLabel, mark, after, null);
    }

    public AnnotationExpression makeAnnotation(MethodAnalysis.MarkAndOnly markAndOnly) {
        E2ImmuAnnotationExpressions e2ae = analyserContext.getE2ImmuAnnotationExpressions();
        if (markAndOnly.mark()) {
            return new AnnotationExpressionImpl(e2ae.mark.typeInfo(),
                    List.of(new MemberValuePair("value",
                            new StringConstant(analyserContext.getPrimitives(), markAndOnly.markLabel()))));
        }
        if (markAndOnly.test() != null) {
            return new AnnotationExpressionImpl(e2ae.testMark.typeInfo(),
                    markAndOnly.test() ?
                            List.of(new MemberValuePair("value",
                                    new StringConstant(analyserContext.getPrimitives(), markAndOnly.markLabel()))) :
                            List.of(new MemberValuePair("value",
                                            new StringConstant(analyserContext.getPrimitives(), markAndOnly.markLabel())),
                                    new MemberValuePair("isMark",
                                            new BooleanConstant(analyserContext.getPrimitives(), false)))
            );
        }
        return new AnnotationExpressionImpl(e2ae.only.typeInfo(),
                List.of(new MemberValuePair(markAndOnly.after() ? "after" : "before",
                        new StringConstant(analyserContext.getPrimitives(), markAndOnly.markLabel()))));
    }

    /**
     * IMPROVE this is a trivial implementation; more code needed
     *
     * @param expression the expression to analyse, like "flipSwitch.isSet()"
     * @return null when the expression does not represent a @TestMark; the mark value otherwise
     */
    private MethodAnalysis.MarkAndOnly detectTestMark(Expression expression) {
        Expression expressionAfterNegation;
        boolean negated;
        if (expression instanceof Negation negation) {
            expressionAfterNegation = negation.expression;
            negated = true;
        } else {
            expressionAfterNegation = expression;
            negated = false;
        }
        if (expressionAfterNegation instanceof MethodCall methodCall &&
                methodCall.object instanceof VariableExpression ve &&
                ve.variable() instanceof FieldReference fr &&
                fr.scope instanceof This) {
            MethodAnalysis methodCallAnalysis = analyserContext.getMethodAnalysis(methodCall.methodInfo);
            MethodAnalysis.MarkAndOnly mao = methodCallAnalysis.getMarkAndOnly();
            if (mao == null) {
                return MethodAnalysis.DELAYED_MARK_AND_ONLY;
            }
            Boolean testMark = mao.test();
            if (testMark == null) return null;
            return new MethodAnalysis.MarkAndOnly(List.of(), fr.fieldInfo.name, false, null, testMark ^ negated);
        }
        return MethodAnalysis.NO_MARK_AND_ONLY;
    }
}

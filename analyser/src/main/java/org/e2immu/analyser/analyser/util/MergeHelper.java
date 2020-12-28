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

// assignment in if and else block

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.objectflow.ObjectFlow;

public record MergeHelper(EvaluationContext evaluationContext, VariableInfo vi) {


    public Expression oneOverwritten(Expression currentValue, VariableInfo variableInfo) {
        if (variableInfo.getStateOnAssignment().isBoolValueTrue()) return variableInfo.getValue();
        return inlineConditional(variableInfo.getStateOnAssignment(), variableInfo.getValue(), currentValue);
    }

    public Expression oneNotOverwritten(Expression a, VariableInfo vi) {
        Expression b = vi.getValue();
        Expression x = vi.getStateOnAssignment();

        // int c = a; if(x) c = b;  --> c = x?b:a
        if (!x.isBoolValueTrue()) {
            return inlineConditional(x, b, a);
        }

        return noConclusion();
    }

    // see ConditionalChecks, 5 and 6 for the more elaborate examples

    // FIXME the first if statement gets in the way of the 2nd in the 2nd part
    // s1 = p>2; s2 = p <=2; while the values already have the state baked in
    // clash between 1 state across different blocks, or a state per level

    public Expression twoOverwritten(VariableInfo vi1, VariableInfo vi2) {
        Expression s1 = vi1.getStateOnAssignment();
        Expression s2 = vi2.getStateOnAssignment();

        if (!s1.isBoolValueTrue() && !s2.isBoolValueTrue()) {
            // int c; if(s1) c = a; else c = b;
            And.CommonComponentResult ccr;
            if (s1 instanceof And s1And) {
                ccr = s1And.findCommon(evaluationContext, s2);
            } else if (s2 instanceof And s2And) {
                ccr = s2And.findCommon(evaluationContext, s1);
            } else {
                ccr = new And.CommonComponentResult(TRUE(), s1, s2);
            }
            // common component example: if(a) { if(b) { } else { } }
            // results in a?b?x:empty and a?b?empty:y==a?!b?y:empty
            // in the middle layers, we need to avoid the situation where the state and the value have an inline with the same
            // parts
            if (not(ccr.rest1()).equals(ccr.rest2()) && isNotPartOf(ccr.rest1(), vi1.getValue()) && isNotPartOf(ccr.rest2(), vi2.getValue())) {
                Expression inner = inlineConditional(ccr.rest1(), vi1.getValue(), vi2.getValue());
                if (ccr.common().isBoolValueTrue()) return inner;
                return inlineConditional(ccr.common(), inner, EmptyExpression.EMPTY_EXPRESSION);
            }
        }

        Expression v1 = vi1.getValue();
        Expression v2 = vi2.getValue();
        // pretty concrete situation, arising from nested if- statements
        // v1 = a?b:<empty> and v2 = !a?d:<empty> --> a?b:d
        if (v1 instanceof InlineConditional i1 && v2 instanceof InlineConditional i2) {
            Expression join = joinInlineWithEmpty(i1, i2);
            if (join != null) return join;

            // given p>=3&&q<=-1?"tuv":null and p<=2?q>=5?"abc":"xyz":<empty>
            // replace p>=3&&q<=-1?"tuv":null with p<=2?<empty>:q<=1?"tuv":null
            And.CommonComponentResult ccr;
            if (i1.condition instanceof And i1And) {
                ccr = i1And.findCommon(evaluationContext, not(i2.condition));
            } else if (i2.condition instanceof And i2And) {
                ccr = i2And.findCommon(evaluationContext, not(i1.condition));
            } else {
                ccr = new And.CommonComponentResult(TRUE(), i1.condition, not(i2.condition));
            }

            // TODO needs more tests, and more code; this is merely proof of concept

            if (!ccr.common().isBoolValueTrue()) {
                Expression newI2 = inlineConditional(ccr.common(), inlineConditional(ccr.rest1(), i2.ifTrue, i2.ifFalse),
                        EmptyExpression.EMPTY_EXPRESSION);
                if (newI2 instanceof InlineConditional newI2Inline) {

                    // newI2 = p>=3 ? q<=-1?"tuv":null : <empty>
                    // i1    = p<=2 ? q>=5 ? "abc":"xyz" : <empty>
                    Expression join2 = joinInlineWithEmpty(i1, newI2Inline);
                    if (join2 != null) return join2;
                }
            }
        }
        return noConclusion();
    }

    private boolean isNotPartOf(Expression condition, Expression value) {
        if (value instanceof InlineConditional inline) {
            And.CommonComponentResult ccr;
            if (condition instanceof And i1And) {
                ccr = i1And.findCommon(evaluationContext, inline.condition);
            } else if (inline.condition instanceof And i2And) {
                ccr = i2And.findCommon(evaluationContext, condition);
            } else {
                return !condition.equals(inline.condition);
            }
            return ccr.common().isBoolValueTrue();
        }
        return true;
    }

    private Expression joinInlineWithEmpty(InlineConditional i1, InlineConditional i2) {
        if (not(i1.condition).equals(i2.condition)) {
            if (i1.ifFalse == EmptyExpression.EMPTY_EXPRESSION && i2.ifFalse == EmptyExpression.EMPTY_EXPRESSION) {
                return inlineConditional(i1.condition, i1.ifTrue, i2.ifTrue);
            }
        }
        // there's symmetrical situations
        if (i1.condition.equals(i2.condition)) {
            if (i1.ifFalse == EmptyExpression.EMPTY_EXPRESSION && i2.ifTrue == EmptyExpression.EMPTY_EXPRESSION) {
                return inlineConditional(i1.condition, i1.ifTrue, i2.ifFalse);
            }
            if (i2.ifFalse == EmptyExpression.EMPTY_EXPRESSION && i1.ifTrue == EmptyExpression.EMPTY_EXPRESSION) {
                return inlineConditional(i1.condition, i1.ifFalse, i2.ifTrue);
            }
        }
        return null;
    }

    public Expression two(Expression x, VariableInfo vi1, VariableInfo vi2) {
        Expression s1 = vi1.getStateOnAssignment();
        Expression s2 = vi2.getStateOnAssignment();

        // silly situation, twice the same condition
        // int c = ex; if(s1) c = a; if(s1) c =b;
        if (s1.equals(s2) && !s1.isBoolValueTrue()) {
            return inlineConditional(s1, vi2.getValue(), x);
        }
        // int c = x; if(s1) c = a; if(s2) c = b; --> s1?a:(s2?b:x)
        if (!s1.isBoolValueTrue() && !s2.isBoolValueTrue()) {
            Expression s2bx = inlineConditional(s2, vi2.getValue(), x);
            return inlineConditional(s1, vi1.getValue(), s2bx);
        }
        return noConclusion();
    }

    private BooleanConstant TRUE() {
        return new BooleanConstant(evaluationContext.getPrimitives(), true);
    }

    private Expression not(Expression expression) {
        return Negation.negate(evaluationContext, expression);
    }

    private Expression inlineConditional(Expression conditon, Expression ifTrue, Expression ifFalse) {
        return safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext,
                conditon, ifTrue, ifFalse, ObjectFlow.NO_FLOW));
    }

    private Expression safe(EvaluationResult result) {
        if (result.getModificationStream().anyMatch(m -> m instanceof StatementAnalyser.RaiseErrorMessage)) {
            // something gone wrong, retreat
            return noConclusion();
        }
        return result.value();
    }

    public Expression noConclusion() {
        return new NewObject(evaluationContext.getPrimitives(), vi.variable().parameterizedType(), vi.getObjectFlow());
    }
}

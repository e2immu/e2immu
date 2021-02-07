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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public record GreaterThanZero(ParameterizedType booleanParameterizedType,
                              Expression expression,
                              boolean allowEquals,
                              ObjectFlow objectFlow) implements Expression {


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GreaterThanZero that = (GreaterThanZero) o;
        if (allowEquals != that.allowEquals) return false;
        return expression.equals(that.expression);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reValue = expression.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setExpression(GreaterThanZero.greater(evaluationContext,
                reValue.getExpression(),
                new IntConstant(evaluationContext.getPrimitives(), 0, ObjectFlow.NO_FLOW),
                allowEquals, getObjectFlow())).build();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, allowEquals);
    }

    // NOT (x >= 0) == x < 0  == (not x) > 0
    // NOT (x > 0)  == x <= 0 == (not x) >= 0
    public Expression negate(EvaluationContext evaluationContext) {
        IntConstant zero = new IntConstant(evaluationContext.getPrimitives(), 0);
        if (expression instanceof Sum sum) {
            if (sum.lhs instanceof Numeric ln && sum.lhs.isDiscreteType()) {
                // NOT (-3 + x >= 0) == NOT (x >= 3) == x < 3 == x <= 2 == 2 + -x >= 0
                // NOT (3 + x >= 0) == NOT (x >= -3) == x < -3 == x <= -4 == -4 + -x >= 0
                Expression minusSumPlusOne = IntConstant.intOrDouble(evaluationContext.getPrimitives(),
                        -(ln.doubleValue() + 1.0), sum.lhs.getObjectFlow());
                return GreaterThanZero.greater(evaluationContext,
                        Sum.sum(evaluationContext, minusSumPlusOne,
                                Negation.negate(evaluationContext, sum.rhs),
                                expression.getObjectFlow()), zero, true, getObjectFlow());
            }
        }
        return GreaterThanZero.greater(evaluationContext,
                Negation.negate(evaluationContext, expression), zero,
                !allowEquals, getObjectFlow());
    }

    /**
     * if xNegated is false: -b + x >= 0 or x >= b
     * if xNegated is true: b - x >= 0 or x <= b
     */
    public record XB(Expression x, double b, boolean lessThan) {
    }

    public XB extract(EvaluationContext evaluationContext) {
        if (expression instanceof Sum sumValue) {
            if (sumValue.lhs instanceof Numeric ln) {
                Expression v = sumValue.rhs;
                Expression x;
                boolean lessThan;
                double b;
                if (v instanceof Negation ne) {
                    x = ne.expression;
                    lessThan = true;
                    b = ln.doubleValue();
                } else {
                    x = v;
                    lessThan = false;
                    b = ((Numeric) Negation.negate(evaluationContext, sumValue.lhs)).doubleValue();
                }
                return new XB(x, b, lessThan);
            }
        }
        Expression x;
        boolean lessThan;
        if (expression instanceof Negation ne) {
            x = ne.expression;
            lessThan = true;
        } else {
            x = expression;
            lessThan = false;
        }
        return new XB(x, 0.0d, lessThan);
    }

    // testing only
    public static Expression greater(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals) {
        return greater(evaluationContext, l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Expression greater(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return new BooleanConstant(primitives, false);
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (l instanceof Numeric ln && r instanceof Numeric rn) {
            if (allowEquals)
                return new BooleanConstant(primitives, ln.doubleValue() >= rn.doubleValue(), objectFlow);
            return new BooleanConstant(primitives, ln.doubleValue() > rn.doubleValue(), objectFlow);
        }

        ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
        ParameterizedType booleanParameterizedType = evaluationContext.getPrimitives().booleanParameterizedType;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof Numeric ln && !allowEquals && l.isDiscreteType()) {
            // 3 > x == 3 + (-x) > 0 transform to 2 >= x
            Expression lMinusOne = IntConstant.intOrDouble(primitives, ln.doubleValue() - 1.0, l.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, lMinusOne,
                            Negation.negate(evaluationContext, r),
                            objectFlowSum), true, objectFlow);
        }
        if (r instanceof Numeric rn && !allowEquals && r.isDiscreteType()) {
            // x > 3 == -3 + x > 0 transform to x >= 4
            Expression minusRPlusOne = IntConstant.intOrDouble(primitives, -(rn.doubleValue() + 1.0), r.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, l, minusRPlusOne, objectFlowSum), true, objectFlow);
        }

        return new GreaterThanZero(booleanParameterizedType,
                Sum.sum(evaluationContext, l, Negation.negate(evaluationContext, r), objectFlowSum),
                allowEquals, objectFlow);
    }

    // testing only
    public static Expression less(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals) {
        return less(evaluationContext, l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Expression less(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return new BooleanConstant(primitives, false);
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (l instanceof Numeric ln && r instanceof Numeric rn) {
            if (allowEquals)
                return new BooleanConstant(primitives, ln.doubleValue() <= rn.doubleValue(), objectFlow);
            return new BooleanConstant(primitives, ln.doubleValue() < rn.doubleValue(), objectFlow);
        }

        ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
        ParameterizedType booleanParameterizedType = evaluationContext.getPrimitives().booleanParameterizedType;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof Numeric ln && !allowEquals && l.isDiscreteType()) {
            // 3 < x == x > 3 == -3 + x > 0 transform to x >= 4
            Expression minusLPlusOne = IntConstant.intOrDouble(primitives, -(ln.doubleValue() + 1.0), l.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, minusLPlusOne, r, objectFlowSum), true, objectFlow);
        }
        if (r instanceof Numeric rn && !allowEquals && r.isDiscreteType()) {
            // x < 3 == 3 + -x > 0 transform to x <= 2 == 2 + -x >= 0
            Expression rMinusOne = IntConstant.intOrDouble(primitives, rn.doubleValue() - 1.0, r.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, Negation.negate(evaluationContext, l), rMinusOne, objectFlowSum), true, objectFlow);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (l instanceof Numeric ln) {
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, ln.negate(), r, objectFlowSum), allowEquals, objectFlow);
        }

        // TODO add tautology call

        return new GreaterThanZero(primitives.booleanParameterizedType, Sum.sum(evaluationContext,
                Negation.negate(evaluationContext, l), r, objectFlowSum), allowEquals, objectFlow);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return UnknownExpression.primitiveGetProperty(variableProperty);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        Symbol gt = Symbol.binaryOperator(allowEquals ? ">=" : ">");
        Symbol lt = Symbol.binaryOperator(allowEquals ? "<=" : "<");
        if (expression instanceof Sum sum) {
            if (sum.lhs instanceof Numeric ln) {
                if (ln.doubleValue() < 0) {
                    // -1 -a >= 0 will be written as a <= -1
                    if (sum.rhs instanceof Negation neg) {
                        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), neg.expression))
                                .add(lt).add(sum.lhs.output(qualification));
                    }
                    // -1 + a >= 0 will be written as a >= 1
                    Text negNumber = new Text(Text.formatNumber(-ln.doubleValue(), ln.getClass()));
                    return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), sum.rhs))
                            .add(gt).add(negNumber);
                } else if (sum.rhs instanceof Negation neg) {
                    // 1 + -a >= 0 will be written as a <= 1
                    return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), neg.expression))
                            .add(lt).add(sum.lhs.output(qualification));
                }
            }
            // according to sorting, the rhs cannot be numeric

            // -x + a >= 0 will be written as a >= x
            if (sum.lhs instanceof Negation neg && !(sum.rhs instanceof Negation)) {
                return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), sum.rhs))
                        .add(gt).add(outputInParenthesis(qualification, precedence(), neg.expression));
            }
            // a + -x >= 0 will be written as a >= x
            if (sum.rhs instanceof Negation neg && !(sum.lhs instanceof Negation)) {
                return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), sum.lhs))
                        .add(gt).add(outputInParenthesis(qualification, precedence(), neg.expression));
            }
        } else if (expression instanceof Negation neg) {
            return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), neg.expression))
                    .add(lt).add(new Text("0"));
        }
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), expression))
                .add(gt).add(new Text("0"));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public ParameterizedType returnType() {
        return booleanParameterizedType;
    }

    @Override
    public Precedence precedence() {
        return Precedence.RELATIONAL;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_GEQ0;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return expression.compareTo(((GreaterThanZero) v).expression);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public List<Variable> variables() {
        return expression.variables();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }
}

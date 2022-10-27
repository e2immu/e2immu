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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class GreaterThanZero extends BaseExpression implements Expression {

    private final Primitives primitives;
    private final Expression expression;
    private final boolean allowEquals;

    public GreaterThanZero(Identifier identifier,
                           Primitives primitives,
                           Expression expression,
                           boolean allowEquals) {
        super(identifier, expression.getComplexity());
        this.primitives = Objects.requireNonNull(primitives);
        this.expression = Objects.requireNonNull(expression);
        assert !expression.returnType().equals(primitives.intParameterizedType()) || allowEquals :
                "integers must have allowEquals==true";
        this.allowEquals = allowEquals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GreaterThanZero that = (GreaterThanZero) o;
        if (allowEquals != that.allowEquals) return false;
        return expression.equals(that.expression);
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
    public Expression negate(EvaluationResult evaluationContext) {
        IntConstant zero = new IntConstant(evaluationContext.getPrimitives(), 0);
        if (expression instanceof Sum sum) {
            if (sum.lhs instanceof Numeric ln && sum.lhs.isDiscreteType()) {
                // NOT (-3 + x >= 0) == NOT (x >= 3) == x < 3 == x <= 2 == 2 + -x >= 0
                // NOT (3 + x >= 0) == NOT (x >= -3) == x < -3 == x <= -4 == -4 + -x >= 0
                Expression minusSumPlusOne = IntConstant.intOrDouble(evaluationContext.getPrimitives(),
                        -(ln.doubleValue() + 1.0));
                return GreaterThanZero.greater(evaluationContext,
                        Sum.sum(evaluationContext, minusSumPlusOne,
                                Negation.negate(evaluationContext, sum.rhs)), zero, true);
            }
        }
        return GreaterThanZero.greater(evaluationContext, Negation.negate(evaluationContext, expression), zero,
                !allowEquals);
    }

    /**
     * if xNegated is false: -b + x >= 0 or x >= b
     * if xNegated is true: b - x >= 0 or x <= b
     */
    public record XB(Expression x, double b, boolean lessThan) {
    }

    public XB extract(EvaluationResult evaluationContext) {
        Sum sumValue = expression.asInstanceOf(Sum.class);
        if (sumValue != null) {
            Double d = sumValue.numericPartOfLhs();
            if (d != null) {
                Expression v = sumValue.nonNumericPartOfLhs(evaluationContext);
                Expression x;
                boolean lessThan;
                double b;
                if (v instanceof Negation ne) {
                    x = ne.expression;
                    lessThan = true;
                    b = d;
                } else {
                    x = v;
                    lessThan = false;
                    b = -d;
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

    public static Expression greater(EvaluationResult evaluationContext, Expression l, Expression r, boolean allowEquals) {
        return greater(Identifier.joined("gt0", List.of(l.getIdentifier(), r.getIdentifier())), evaluationContext, l, r, allowEquals);
    }

    public static Expression greater(Identifier identifier,
                                     EvaluationResult evaluationContext, Expression l, Expression r, boolean allowEquals) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return new BooleanConstant(primitives, false);
        if (l.isEmpty() || r.isEmpty()) throw new UnsupportedOperationException();

        Numeric ln = l.asInstanceOf(Numeric.class);
        Numeric rn = r.asInstanceOf(Numeric.class);
        if (ln != null && rn != null) {
            if (allowEquals)
                return new BooleanConstant(primitives, ln.doubleValue() >= rn.doubleValue());
            return new BooleanConstant(primitives, ln.doubleValue() > rn.doubleValue());
        }

        if (ln != null && !allowEquals && l.isDiscreteType()) {
            // 3 > x == 3 + (-x) > 0 transform to 2 >= x
            Expression lMinusOne = IntConstant.intOrDouble(primitives, ln.doubleValue() - 1.0);
            return new GreaterThanZero(identifier, primitives,
                    Sum.sum(evaluationContext, lMinusOne,
                            Negation.negate(evaluationContext, r)), true);
        }
        if (rn != null && !allowEquals && r.isDiscreteType()) {
            // x > 3 == -3 + x > 0 transform to x >= 4
            Expression minusRPlusOne = IntConstant.intOrDouble(primitives, -(rn.doubleValue() + 1.0));
            return new GreaterThanZero(identifier, primitives,
                    Sum.sum(evaluationContext, l, minusRPlusOne), true);
        }

        Expression sum = Sum.sum(evaluationContext, l, Negation.negate(evaluationContext, r));
        if (!allowEquals && sum.returnType().equals(primitives.intParameterizedType())) {
            // GTZ cannot take allowEquals==false for integers
            Expression minusOne = Sum.sum(evaluationContext, sum, new IntConstant(primitives, -1));
            return new GreaterThanZero(identifier, primitives, minusOne, true);
        }
        return new GreaterThanZero(identifier, primitives, sum, allowEquals);
    }

    // mainly for testing
    public static Expression less(EvaluationResult evaluationContext, Expression l, Expression r, boolean allowEquals) {
        return less(Identifier.joined("gt0", List.of(l.getIdentifier(), r.getIdentifier())), evaluationContext, l, r, allowEquals);
    }

    public static Expression less(Identifier identifier,
                                  EvaluationResult evaluationContext, Expression l, Expression r, boolean allowEquals) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return new BooleanConstant(primitives, false);
        if (l.isEmpty() || r.isEmpty()) throw new UnsupportedOperationException();

        Numeric ln = l.asInstanceOf(Numeric.class);
        Numeric rn = r.asInstanceOf(Numeric.class);
        if (ln != null && rn != null) {
            if (allowEquals)
                return new BooleanConstant(primitives, ln.doubleValue() <= rn.doubleValue());
            return new BooleanConstant(primitives, ln.doubleValue() < rn.doubleValue());
        }

        if (ln != null && !allowEquals && l.isDiscreteType()) {
            // 3 < x == x > 3 == -3 + x > 0 transform to x >= 4
            Expression minusLPlusOne = IntConstant.intOrDouble(primitives, -(ln.doubleValue() + 1.0));
            return new GreaterThanZero(identifier, primitives,
                    Sum.sum(evaluationContext, minusLPlusOne, r), true);
        }
        if (rn != null && !allowEquals && r.isDiscreteType()) {
            // x < 3 == 3 + -x > 0 transform to x <= 2 == 2 + -x >= 0
            Expression rMinusOne = IntConstant.intOrDouble(primitives, rn.doubleValue() - 1.0);
            return new GreaterThanZero(identifier, primitives,
                    Sum.sum(evaluationContext, Negation.negate(evaluationContext, l), rMinusOne), true);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (ln != null) {
            return new GreaterThanZero(identifier, primitives, Sum.sum(evaluationContext, ln.negate(), r), allowEquals);
        }

        // TODO add tautology call

        Expression sum = Sum.sum(evaluationContext, Negation.negate(evaluationContext, l), r);
        if (!allowEquals && sum.returnType().equals(primitives.intParameterizedType())) {
            // GTZ cannot take allowEquals==false for integers
            Expression minusOne = Sum.sum(evaluationContext, sum, new IntConstant(primitives, -1));
            return new GreaterThanZero(identifier, primitives, minusOne, true);
        }
        return new GreaterThanZero(identifier, primitives, sum, allowEquals);
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return getPropertyForPrimitiveResults(property);
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
        return primitives.booleanParameterizedType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.RELATIONAL;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult er = expression.evaluate(context, forwardEvaluationInfo);
        Expression expression;
        Numeric n = er.value().asInstanceOf(Numeric.class);
        if (n != null) {
            expression = new BooleanConstant(context.getPrimitives(),
                    allowEquals ? n.doubleValue() >= 0 : n.doubleValue() > 0);
        } else {
            expression = new GreaterThanZero(identifier, primitives, er.getExpression(), allowEquals);
        }
        return new EvaluationResult.Builder(context).compose(er).setExpression(expression).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_GEQ0;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if (v instanceof InlineConditional inline) {
            return expression.compareTo(inline.condition);
        }
        if (v instanceof BinaryOperator binary) {
            return -BinaryOperator.compareBinaryToGt0(binary, this);
        }
        if (!(v instanceof GreaterThanZero)) throw new UnsupportedOperationException();

        int c = BinaryOperator.compareVariables(this, v);
        if (c != 0) return c;
        return expression.compareTo(((GreaterThanZero) v).expression);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translate = expression.translate(inspectionProvider, translationMap);
        if (translate == expression) return this;
        return new GreaterThanZero(identifier, primitives, translate, allowEquals);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (expression.isDelayed()) {
            return new GreaterThanZero(identifier, primitives,
                    expression.mergeDelays(causesOfDelay), allowEquals);
        }
        return this;
    }

    public Expression expression() {
        return expression;
    }

    public boolean allowEquals() {
        return allowEquals;
    }

    public ParameterizedType booleanParameterizedType() {
        return primitives.booleanParameterizedType();
    }

    @Override
    public Expression removeAllReturnValueParts(Primitives primitives) {
        Expression removed = expression.removeAllReturnValueParts(primitives);
        if (removed == null) {
            return new BooleanConstant(primitives, true);
        }
        return new GreaterThanZero(identifier, primitives, removed, allowEquals);
    }
}

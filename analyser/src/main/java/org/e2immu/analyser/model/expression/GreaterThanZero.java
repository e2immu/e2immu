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
import org.e2immu.analyser.util.IntUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/*
convention, if the type is discrete:
(1) x >= 3 rather than x > 2  (so we prefer -3+x>=0 over -2+x>0)
(2) x < 3 rather than x <= 2  (so we prefer 3-x>0 over 2-x>=0)
 */
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

    public Expression negate(EvaluationResult context) {
        Expression negated = Negation.negate(context, expression);
        return GreaterThanZero.greater(identifier, context, negated, new IntConstant(primitives, 0), !allowEquals);
    }

    /**
     * IMPORTANT:
     * XB in the integer case ALWAYS works with x <= b, rather than x < b.
     * This simplifies combining multiple GT0 clauses in And, Or.
     * <p>
     * This is not what we do by convention for GreaterThanZero!!!
     * We keep x < b, x >= b because the negation can keep the same constant (see io.codelaser.jfocus)
     */
    public record XB(Expression x, double b, boolean lessThan) {
    }

    public XB extract(EvaluationResult context) {
        Sum sumValue = expression.asInstanceOf(Sum.class);
        if (sumValue != null) {
            Double d = sumValue.numericPartOfLhs();
            if (d != null) {
                Expression v = sumValue.nonNumericPartOfLhs(context);
                Expression x;
                boolean lessThan;
                double b;
                if (v instanceof Negation ne) {
                    x = ne.expression;
                    lessThan = true;
                    if (IntUtil.isMathematicalInteger(d)) {
                        assert !allowEquals : "By convention, we store x < 4 rather than x <= 3";
                        b = d - 1;
                    } else {
                        b = d;
                    }
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
        double d;
        if (expression instanceof Negation ne) {
            x = ne.expression;
            lessThan = true;
            if (!allowEquals && x.returnType().isMathematicallyInteger()) {
                d = -1;
            } else {
                d = 0;
            }
        } else {
            x = expression;
            lessThan = false;
            d = 0;
        }
        return new XB(x, d, lessThan);
    }

    // mainly for testing
    public static Expression less(EvaluationResult context, Expression l, Expression r, boolean allowEquals) {
        Identifier id = Identifier.joined("gt0", List.of(l.getIdentifier(), r.getIdentifier()));
        return greater(id, context, r, l, allowEquals);
    }

    public static Expression less(Identifier identifier, EvaluationResult context, Expression l, Expression r,
                                  boolean allowEquals) {
        return greater(identifier, context, r, l, allowEquals);
    }

    public static Expression greater(EvaluationResult context, Expression l, Expression r, boolean allowEquals) {
        return greater(Identifier.joined("gt0", List.of(l.getIdentifier(), r.getIdentifier())),
                context, l, r, allowEquals);
    }

    public static Expression greater(Identifier identifier, EvaluationResult context, Expression l, Expression r,
                                     boolean allowEquals) {
        Expression sum = Sum.sum(context, l, Negation.negate(context, r));
        return compute(identifier, context, sum, allowEquals);
    }

    private static Expression compute(Identifier identifier, EvaluationResult context, Expression expression,
                                      boolean allowEquals) {
        Primitives primitives = context.getPrimitives();
        Expression[] terms = Sum.expandTerms(context, expression, false).toArray(Expression[]::new);
        Arrays.sort(terms);

        Numeric n0 = terms[0].asInstanceOf(Numeric.class);
        if (terms.length == 1) {
            return oneTerm(identifier, context, allowEquals, primitives, terms, n0);
        }
        if (terms.length == 2 && n0 != null
                && IntUtil.isMathematicalInteger(n0.doubleValue())
                && terms[1].returnType().isMathematicallyInteger()) {
            return twoTerms(identifier, context, allowEquals, primitives, terms, n0);
        }
        if (terms.length == 2 && terms[0].returnType().isMathematicallyInteger() && terms[1].returnType().isMathematicallyInteger()) {
            if (!allowEquals) {
                // +-i +-j > 0
                expression = Sum.sum(context, new IntConstant(primitives, -1),
                        Sum.sum(context, terms[0], terms[1]));
                allowEquals = true;
            }
        }
        if (terms.length == 3 && n0 != null
                && IntUtil.isMathematicalInteger(n0.doubleValue())
                && terms[1].returnType().isMathematicallyInteger()
                && terms[2].returnType().isMathematicallyInteger()) {

            if (n0.doubleValue() >= 0 && !allowEquals) {
                // special cases, i>=j == i-j >=0, 1+i-j>0
                IntConstant minusOne = new IntConstant(primitives, -1 + (int) n0.doubleValue());
                expression = Sum.sum(context, minusOne, Sum.sum(context, terms[1], terms[2]));
                allowEquals = true;
            }
        }
        // fallback
        return new GreaterThanZero(identifier, primitives, expression, allowEquals);
    }

    private static GreaterThanZero twoTerms(Identifier identifier, EvaluationResult context, boolean allowEquals, Primitives primitives, Expression[] terms, Numeric n0) {
        // basic int comparisons, take care that we use >= and <
        boolean n0Negated = n0.doubleValue() < 0;
        boolean n1Negated = terms[1] instanceof Negation;

        Expression sum;
        boolean newAllowEquals;
        if (n0Negated) {
            if (!n1Negated) {
                newAllowEquals = true;
                if (!allowEquals) {
                    // -3 + x > 0 == x>3 == x>=4 == -4 + x >= 0
                    IntConstant minusOne = new IntConstant(primitives, -1 + (int) n0.doubleValue());
                    sum = Sum.sum(context, minusOne, terms[1]);
                } else {
                    // -3 + x >= 0 == x >= 3 OK
                    sum = Sum.sum(context, terms[0], terms[1]);
                }
            } else {
                newAllowEquals = false;
                if (!allowEquals) {
                    // -3 - x > 0 == x<-3 OK
                    sum = Sum.sum(context, terms[0], terms[1]);
                } else {
                    // -3 - x >= 0 == x<=-3 == x<-2 == -2 - x > 0
                    IntConstant plusOne = new IntConstant(primitives, 1 + (int) n0.doubleValue());
                    sum = Sum.sum(context, plusOne, terms[1]);
                }
            }
        } else {
            if (!n1Negated) {
                newAllowEquals = true;
                if (!allowEquals) {
                    // 3 + x > 0 == x>-3 == x>=-2 == 2 + x >= 0
                    IntConstant minusOne = new IntConstant(primitives, -1 + (int) n0.doubleValue());
                    sum = Sum.sum(context, minusOne, terms[1]);
                } else {
                    // 3 + x >= 0 == x>=-3 OK
                    sum = Sum.sum(context, terms[0], terms[1]);
                }
            } else {
                newAllowEquals = false;
                if (!allowEquals) {
                    // 3 - x > 0 == x<3 OK
                    sum = Sum.sum(context, terms[0], terms[1]);
                } else {
                    // 3 - x >= 0 == x<=3 == x<4 == 4 - x > 0
                    IntConstant plusOne = new IntConstant(primitives, 1 + (int) n0.doubleValue());
                    sum = Sum.sum(context, plusOne, terms[1]);
                }
            }
        }
        return new GreaterThanZero(identifier, primitives, sum, newAllowEquals);
    }

    private static BaseExpression oneTerm(Identifier identifier, EvaluationResult context, boolean allowEquals, Primitives primitives, Expression[] terms, Numeric n0) {
        if (n0 != null) {
            boolean accept = n0.doubleValue() > 0.0 || allowEquals && n0.doubleValue() == 0.0;
            return new BooleanConstant(primitives, accept);
        }
        Expression term;
        boolean newAllowEquals;
        if (terms[0].returnType().isMathematicallyInteger()) {
            // some int expression >= 0
            if (terms[0] instanceof Negation) {
                newAllowEquals = false;
                if (allowEquals) {
                    // -x >= 0 == x <= 0 == x < 1 == 1 - x > 0
                    term = Sum.sum(context, new IntConstant(primitives, 1), terms[0]);
                } else {
                    // -x > 0 == x < 0 OK
                    term = terms[0];
                }
            } else {
                newAllowEquals = true;
                if (allowEquals) {
                    // x >= 0 OK
                    term = terms[0];
                } else {
                    // x > 0 == x >= 1 == -1+x >= 0
                    term = Sum.sum(context, new IntConstant(primitives, -1), terms[0]);
                }
            }
        } else {
            term = terms[0];
            newAllowEquals = allowEquals;
        }
        // expr >= 0, expr > 0
        return new GreaterThanZero(identifier, primitives, term, newAllowEquals);
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
        Expression e = GreaterThanZero.compute(identifier, context, er.getExpression(), allowEquals);
        return new EvaluationResult.Builder(context).compose(er).setExpression(e).build();
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
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
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
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedExpression = expression.translate(inspectionProvider, translationMap);
        if (translatedExpression == expression) return this;
        return new GreaterThanZero(identifier, primitives, translatedExpression, allowEquals);
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

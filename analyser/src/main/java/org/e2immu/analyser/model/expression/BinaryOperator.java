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

import com.github.javaparser.ast.expr.BinaryExpr;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.Precedence.*;

/**
 * From https://introcs.cs.princeton.edu/java/11precedence/
 * All associativity is from left to right for binary operators: a+b+c = (a+b)+c
 * <p>
 * precedence 12: * / % multiplicative
 * precedence 11: + - additive, + string concat
 * precedence 10: >>>, <<< shift
 * precedence 9: <, <=, >, >= comparison
 * precedence 8: ==, != equality
 * precedence 7: & AND
 * precedence 6: ^ XOR
 * precedence 5: | OR
 * precedence 4: && logical AND
 * precedence 3: || logical OR
 */
public class BinaryOperator extends BaseExpression implements Expression {
    protected final Primitives primitives;
    public final Expression lhs;
    public final Expression rhs;
    public final Precedence precedence;
    public final MethodInfo operator;

    public BinaryOperator(Identifier identifier,
                          Primitives primitives,
                          Expression lhs,
                          MethodInfo operator,
                          Expression rhs,
                          Precedence precedence) {
        super(identifier, precedence.getComplexity() + lhs.getComplexity() + rhs.getComplexity());
        this.lhs = Objects.requireNonNull(lhs);
        this.rhs = Objects.requireNonNull(rhs);
        this.precedence = precedence;
        this.operator = Objects.requireNonNull(operator);
        this.primitives = primitives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryOperator that = (BinaryOperator) o;
        return lhs.equals(that.lhs) &&
                rhs.equals(that.rhs) &&
                operator.equals(that.operator);
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return getPropertyForPrimitiveResults(property);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs, operator);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedLhs = lhs.translate(inspectionProvider, translationMap);
        Expression translatedRhs = rhs.translate(inspectionProvider, translationMap);
        if (translatedRhs == this.rhs && translatedLhs == this.lhs) return this;
        return new BinaryOperator(identifier, primitives, translatedLhs,
                operator, translatedRhs, precedence);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_BINARY_OPERATOR; // not yet evaluated
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        return ListUtil.concatImmutable(lhs.variables(descendIntoFieldReferences),
                rhs.variables(descendIntoFieldReferences));
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            lhs.visit(predicate);
            rhs.visit(predicate);
        }
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        // we need to handle the short-circuit operators differently
        Primitives primitives = context.getPrimitives();
        if (operator == primitives.orOperatorBool()) {
            return shortCircuit(context, forwardEvaluationInfo, false);
        }
        if (operator == primitives.andOperatorBool()) {
            return shortCircuit(context, forwardEvaluationInfo, true);
        }

        ForwardEvaluationInfo.Builder forwardBuilder = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo);
        if (allowsForNullOperands(primitives)) {
            forwardBuilder.setCnnNullable();
        } else {
            forwardBuilder.notNullNotAssignment();
        }
        ForwardEvaluationInfo forward = forwardBuilder.removeContextContainer().build();

        EvaluationResult leftResult = lhs.evaluate(context, forward);
        /*
        IMPORTANT: we want the changeData of "context" to be available to the rhs evaluation (See InstanceOf_13)
        Therefore we actively compose "context" into the context for rhs
         */
        EvaluationResult leftResultContext = new EvaluationResult.Builder(context)
                .compose(context)
                .compose(leftResult).build();
        EvaluationResult rightResult = rhs.evaluate(leftResultContext, forward);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(leftResult, rightResult);
        builder.setExpression(determineValueProtect(primitives, builder, leftResult, rightResult, context, forward));
        return builder.build();
    }

    private Expression determineValueProtect(Primitives primitives,
                                             EvaluationResult.Builder builder,
                                             EvaluationResult left,
                                             EvaluationResult right,
                                             EvaluationResult context,
                                             ForwardEvaluationInfo forwardEvaluationInfo) {
        CausesOfDelay causes = left.causesOfDelay().merge(right.causesOfDelay());
        Expression expression = determineValue(primitives, builder, left, right, context, forwardEvaluationInfo);
        return causes.isDelayed() && expression.isDone()
                ? DelayedExpression.forSimplification(identifier, expression.returnType(), expression, causes)
                : expression;
    }

    private Expression determineValue(Primitives primitives,
                                      EvaluationResult.Builder builder,
                                      EvaluationResult left,
                                      EvaluationResult right,
                                      EvaluationResult context,
                                      ForwardEvaluationInfo forwardEvaluationInfo) {
        Expression l = left.value();
        Expression r = right.value();
        assert l != null;
        assert r != null;

        if (operator == primitives.equalsOperatorObject()) {
            if (l.equals(r)) {
                return new BooleanConstant(primitives, true);
            }

            // HERE are the ==null checks
            if (l.isNullConstant()) {
                DV dv = right.isNotNull0(false, forwardEvaluationInfo);
                if (dv.valueIsTrue()) return new BooleanConstant(primitives, false);
                if (dv.isDelayed())
                    return DelayedExpression.forNullCheck(identifier, primitives,
                            newEquals(l, r), dv.causesOfDelay().merge(r.causesOfDelay()));
            }
            if (r.isNullConstant()) {
                DV dv = left.isNotNull0(false, forwardEvaluationInfo);
                if (dv.valueIsTrue()) return new BooleanConstant(primitives, false);
                if (dv.isDelayed())
                    return DelayedExpression.forNullCheck(identifier, primitives,
                            newEquals(r, l), dv.causesOfDelay().merge(l.causesOfDelay()));
            }
            // the following line ensures that a warning is sent when the ENN of a field/parameter is not NULLABLE
            // but the CNN is. The ENN trumps the annotation, but is not used in the computation of the constructor
            // see example in ExternalNotNull_0
            if (l.isNullConstant() && right.isNotNull0(true,
                    forwardEvaluationInfo).valueIsTrue() && r instanceof IsVariableExpression ve) {
                builder.setProperty(ve.variable(), Property.CANDIDATE_FOR_NULL_PTR_WARNING, DV.TRUE_DV);
            } else if (r.isNullConstant() && left.isNotNull0(true,
                    forwardEvaluationInfo).valueIsTrue() && l instanceof IsVariableExpression ve) {
                builder.setProperty(ve.variable(), Property.CANDIDATE_FOR_NULL_PTR_WARNING, DV.TRUE_DV);
            }
            return Equals.equals(identifier, context, l, r, forwardEvaluationInfo);
        }
        if (operator == primitives.equalsOperatorInt()) {
            if (l.equals(r)) return new BooleanConstant(primitives, true);
            if (l.isNullConstant() || r.isNullConstant()) {
                // TODO need more resolution here to distinguish int vs Integer comparison throw new UnsupportedOperationException();
            }
            return Equals.equals(identifier, context, l, r, forwardEvaluationInfo);
        }
        if (operator == primitives.notEqualsOperatorObject()) {
            if (l.equals(r)) new BooleanConstant(primitives, false);

            // HERE are the !=null checks
            if (l.isNullConstant()) {
                DV dv = right.isNotNull0(false, forwardEvaluationInfo);
                if (dv.valueIsTrue()) return new BooleanConstant(primitives, true);
                if (dv.isDelayed())
                    // note that the negation is necessary because we need to distinguish between ==null, !=null,
                    // see e.g. statementAnalysis.stateData().equalityAccordingToStatePut
                    return Negation.negate(context,
                            DelayedExpression.forNullCheck(identifier, primitives,
                                    newEquals(l, r), dv.causesOfDelay().merge(r.causesOfDelay())));
            }
            if (r.isNullConstant()) {
                DV dv = left.isNotNull0(false, forwardEvaluationInfo);
                if (dv.valueIsTrue()) return new BooleanConstant(primitives, true);
                if (dv.isDelayed())
                    // note that the negation is necessary because we need to distinguish between ==null, !=null
                    // furthermore, we expect null to be on the left in LhsRhs.extractEqualities
                    return Negation.negate(context, DelayedExpression.forNullCheck(identifier, primitives,
                            newEquals(r, l), dv.causesOfDelay().merge(l.causesOfDelay())));
            }
            return Negation.negate(context, Equals.equals(identifier, context, l, r, forwardEvaluationInfo));
        }
        if (operator == primitives.notEqualsOperatorInt()) {
            if (l.equals(r)) return new BooleanConstant(primitives, false);
            if (l.isNullConstant() || r.isNullConstant()) {
                // TODO need more resolution throw new UnsupportedOperationException();
            }
            return Negation.negate(context, Equals.equals(identifier, context, l, r, forwardEvaluationInfo));
        }

        // from here on, straightforward operations
        if (operator == primitives.plusOperatorInt()) {
            return Sum.sum(identifier, context, l, r);
        }
        if (operator == primitives.minusOperatorInt()) {
            return Sum.sum(identifier, context, l, Negation.negate(context, r));
        }
        if (operator == primitives.multiplyOperatorInt()) {
            return Product.product(identifier, context, l, r);
        }
        if (operator == primitives.divideOperatorInt()) {
            EvaluationResult er = Divide.divide(identifier, context, l, r);
            builder.compose(er);
            return er.value();
        }
        if (operator == primitives.remainderOperatorInt()) {
            EvaluationResult er = Remainder.remainder(identifier, context, l, r);
            builder.compose(er);
            return er.value();
        }
        if (operator == primitives.lessEqualsOperatorInt()) {
            return GreaterThanZero.less(identifier, context, l, r, true);
        }
        if (operator == primitives.lessOperatorInt()) {
            return GreaterThanZero.less(identifier, context, l, r, false);
        }
        if (operator == primitives.greaterEqualsOperatorInt()) {
            return GreaterThanZero.greater(identifier, context, l, r, true);
        }
        if (operator == primitives.greaterOperatorInt()) {
            return GreaterThanZero.greater(identifier, context, l, r, false);
        }
        if (operator == primitives.plusOperatorString()) {
            return StringConcat.stringConcat(identifier, context, l, r);
        }

        if (operator == primitives.andOperatorBool()) {
            return And.and(identifier, context, l, r);
        }
        if (operator == primitives.orOperatorBool()) {
            return Or.or(identifier, context, l, r);
        }

        // more obscure operators

        if (operator == primitives.xorOperatorBool()) {
            return BooleanXor.booleanXor(identifier, context, l, r);
        }
        if (operator == primitives.bitwiseAndOperatorInt()) {
            return BitwiseAnd.bitwiseAnd(identifier, context, l, r);
        }
        if (operator == primitives.bitwiseOrOperatorInt()) {
            return BitwiseOr.bitwiseOr(identifier, context, l, r);
        }
        if (operator == primitives.bitwiseXorOperatorInt()) {
            return BitwiseXor.bitwiseXor(identifier, context, l, r);
        }
        if (operator == primitives.leftShiftOperatorInt()) {
            return ShiftLeft.shiftLeft(identifier, context, l, r);
        }
        if (operator == primitives.signedRightShiftOperatorInt()) {
            return SignedShiftRight.shiftRight(identifier, context, l, r);
        }
        if (operator == primitives.unsignedRightShiftOperatorInt()) {
            return UnsignedShiftRight.unsignedShiftRight(identifier, context, l, r);
        }
        throw new UnsupportedOperationException("Operator " + operator.fullyQualifiedName());
    }

    private  Expression newEquals(Expression l, Expression r) {
        return new Equals(identifier, primitives, l, r);
    }

    private EvaluationResult shortCircuit(EvaluationResult context,
                                          ForwardEvaluationInfo forwardEvaluationInfo,
                                          boolean and) {
        ForwardEvaluationInfo forward = forwardEvaluationInfo.copy().notNullNotAssignment().build();
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        Primitives primitives = context.getPrimitives();

        EvaluationResult l = lhs.evaluate(context, forward);
        Expression constant = new BooleanConstant(primitives, !and);
        if (l.value().equals(constant)) {
            builder.raiseError(lhs.getIdentifier(), Message.Label.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            return builder.compose(l).build();
        }

        Expression state = and ? l.value() : Negation.negate(context, l.value());
        if (!lhs.equals(l.value()) && !forwardEvaluationInfo.isInCompanionExpression()) {
            Expression literalNotNull = lhs.keepLiteralNotNull(context, and);
            if (literalNotNull != null) {
                /*
                sometimes, the expanded state cannot be related to a variable anymore, which causes
                null-pointer problems in the RHS. See e.g. DGSimplified_0, _1, SubTypes_12, NotNull_AAPI_3_1...
                We take care NOT to evaluate the LHS, but to put it in a form so that the null-check
                is recognized. (Evaluation would have to be without expansion of variables, but that
                causes problems when working with companion methods.)
                 */
                state = And.and(context, state, literalNotNull);
            }
        }
        Set<Variable> stateVariables = Stream.concat(state.variableStream(), lhs.variableStream())
                .collect(Collectors.toUnmodifiableSet());
        EvaluationResult child = context.childState(state, stateVariables);
        EvaluationResult r = rhs.evaluate(child, forward);
        builder.compose(l, r);
        if (r.value() instanceof BooleanConstant booleanConstant) {
            builder.raiseError(rhs.getIdentifier(), Message.Label.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            if (and && booleanConstant.getValue() || !and && !booleanConstant.getValue()) {
                // x && true, x || false
                builder.setExpression(l.value());
            } else {
                // x && false, x || true
                builder.setExpression(r.value());
            }
            return builder.build();
        }
        if (and) {
            builder.setExpression(And.and(context, l.value(), r.value()));
        } else {
            builder.setExpression(Or.or(context, l.value(), r.value()));
        }
        return builder.build();
    }

    private boolean allowsForNullOperands(Primitives primitives) {
        return operator == primitives.equalsOperatorInt() ||
                operator == primitives.equalsOperatorObject() ||
                operator == primitives.notEqualsOperatorObject() ||
                operator == primitives.notEqualsOperatorInt() ||
                operator == primitives.plusOperatorString() ||
                operator == primitives.plusOperatorString();
    }

    @NotNull
    public static MethodInfo getOperator(@NotNull Primitives primitives,
                                         @NotNull @NotModified BinaryExpr.Operator operator,
                                         @NotModified TypeInfo widestType) {
        if (widestType == null || !widestType.isPrimitiveExcludingVoid()) {
            if (operator == BinaryExpr.Operator.EQUALS) {
                return primitives.equalsOperatorObject();
            }
            if (operator == BinaryExpr.Operator.NOT_EQUALS) {
                return primitives.notEqualsOperatorObject();
            }
            if (widestType == primitives.stringTypeInfo() && operator == BinaryExpr.Operator.PLUS) {
                return primitives.plusOperatorString();
            }
            if (widestType == null || !widestType.isBoxedExcludingVoid()) {
                throw new UnsupportedOperationException("? what else can you have on " + widestType + ", operator " + operator);
            }
        }
        if (widestType == primitives.booleanTypeInfo() || widestType == primitives.boxedBooleanTypeInfo()) {
            switch (operator) {
                case XOR:
                    return primitives.xorOperatorBool();
                case BINARY_OR:
                case OR:
                    return primitives.orOperatorBool();
                case BINARY_AND:
                case AND:
                    return primitives.andOperatorBool();
                case EQUALS:
                    return primitives.equalsOperatorInt(); // TODO should clean up
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt();
            }
            throw new UnsupportedOperationException("Operator " + operator + " on boolean");
        }
        if (widestType == primitives.charTypeInfo() || widestType == primitives.characterTypeInfo()) {
            switch (operator) {
                case PLUS:
                    return primitives.plusOperatorInt();
                case MINUS:
                    return primitives.minusOperatorInt();
                case EQUALS:
                    return primitives.equalsOperatorInt(); // TODO should clean up
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt();
            }
            throw new UnsupportedOperationException("Operator " + operator + " on char");
        }
        if (widestType.isNumeric()) {
            switch (operator) {
                case MULTIPLY:
                    return primitives.multiplyOperatorInt();
                case REMAINDER:
                    return primitives.remainderOperatorInt();
                case DIVIDE:
                    return primitives.divideOperatorInt();
                case PLUS:
                    return primitives.plusOperatorInt();
                case MINUS:
                    return primitives.minusOperatorInt();
                case BINARY_OR:
                    return primitives.bitwiseOrOperatorInt();
                case BINARY_AND:
                    return primitives.bitwiseAndOperatorInt();
                case XOR:
                    return primitives.bitwiseXorOperatorInt();
                case UNSIGNED_RIGHT_SHIFT:
                    return primitives.unsignedRightShiftOperatorInt();
                case SIGNED_RIGHT_SHIFT:
                    return primitives.signedRightShiftOperatorInt();
                case LEFT_SHIFT:
                    return primitives.leftShiftOperatorInt();
                case GREATER:
                    return primitives.greaterOperatorInt();
                case GREATER_EQUALS:
                    return primitives.greaterEqualsOperatorInt();
                case LESS:
                    return primitives.lessOperatorInt();
                case LESS_EQUALS:
                    return primitives.lessEqualsOperatorInt();
                case EQUALS:
                    return primitives.equalsOperatorInt();
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt();
            }
        }

        throw new UnsupportedOperationException("Unknown operator " + operator + " on widest type " +
                widestType.fullyQualifiedName);
    }

    public static MethodInfo fromAssignmentOperatorToNormalOperator(Primitives primitives, MethodInfo methodInfo) {
        if (primitives.assignOperatorInt() == methodInfo) return null;
        if (primitives.assignPlusOperatorInt() == methodInfo) return primitives.plusOperatorInt();
        if (primitives.assignMinusOperatorInt() == methodInfo) return primitives.minusOperatorInt();
        if (primitives.assignMultiplyOperatorInt() == methodInfo) return primitives.multiplyOperatorInt();
        if (primitives.assignDivideOperatorInt() == methodInfo) return primitives.divideOperatorInt();
        if (primitives.assignOrOperatorInt() == methodInfo) return primitives.bitwiseOrOperatorInt();
        if (primitives.assignAndOperatorInt() == methodInfo) return primitives.bitwiseAndOperatorInt();

        throw new UnsupportedOperationException("TODO! " + methodInfo.distinguishingName());
    }

    public static Precedence precedence(@NotNull Primitives primitives, @NotNull @NotModified MethodInfo methodInfo) {
        if (primitives.divideOperatorInt() == methodInfo || primitives.remainderOperatorInt() == methodInfo || primitives.multiplyOperatorInt() == methodInfo) {
            return MULTIPLICATIVE;
        }
        if (primitives.plusOperatorString() == methodInfo) {
            return STRING_CONCAT;
        }
        if (primitives.minusOperatorInt() == methodInfo || primitives.plusOperatorInt() == methodInfo) {
            return ADDITIVE;
        }
        if (primitives.signedRightShiftOperatorInt() == methodInfo || primitives.unsignedRightShiftOperatorInt() == methodInfo || primitives.leftShiftOperatorInt() == methodInfo) {
            return SHIFT;
        }
        if (primitives.greaterEqualsOperatorInt() == methodInfo || primitives.greaterOperatorInt() == methodInfo || primitives.lessEqualsOperatorInt() == methodInfo || primitives.lessOperatorInt() == methodInfo) {
            return RELATIONAL;
        }
        if (primitives.equalsOperatorInt() == methodInfo || primitives.equalsOperatorObject() == methodInfo || primitives.notEqualsOperatorInt() == methodInfo || primitives.notEqualsOperatorObject() == methodInfo) {
            return EQUALITY;
        }
        if (primitives.bitwiseAndOperatorInt() == methodInfo) {
            return AND;
        }
        if (primitives.bitwiseXorOperatorInt() == methodInfo) {
            return XOR;
        }
        if (primitives.bitwiseOrOperatorInt() == methodInfo) {
            return OR;
        }
        if (primitives.andOperatorBool() == methodInfo) {
            return LOGICAL_AND;
        }
        if (primitives.orOperatorBool() == methodInfo) {
            return LOGICAL_OR;
        }
        if (primitives.xorOperatorBool() == methodInfo) {
            return XOR;
        }
        throw new UnsupportedOperationException("? unknown operator " + methodInfo.distinguishingName());
    }

    // TODO needs cleanup
    @Override
    public ParameterizedType returnType() {
        Precedence precedence = precedence();
        return switch (precedence) {
            case RELATIONAL, LOGICAL_AND, LOGICAL_OR, EQUALITY, INSTANCE_OF -> primitives.booleanParameterizedType();
            case STRING_CONCAT -> primitives.stringParameterizedType();
            default -> primitives.widestType(lhs.returnType(), rhs.returnType());
        };
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), lhs))
                .add(Symbol.binaryOperator(operator.name))
                .add(outputInParenthesis(qualification, precedence(), rhs));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return precedence;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(lhs, rhs);
    }

    @Override
    public int internalCompareTo(Expression v) {
        BinaryOperator b;
        if (v instanceof InlineConditional inlineConditional &&
                inlineConditional.condition instanceof BinaryOperator binaryOperator) {
            b = binaryOperator;
        } else if (v instanceof BinaryOperator binaryOperator) {
            b = binaryOperator;
        } else if (v instanceof GreaterThanZero gt0) {
            return compareBinaryToGt0(this, gt0);
        } else {
            int c = rhs.compareTo(v);
            if (c == 0) c = lhs.compareTo(v);
            return c;
        }
        int c0 = compareVariables(this, b);
        if (c0 != 0) return c0;

        // if there's a variable, it'll be in rhs
        // so priority is on the right hand side!!!
        int c = rhs.compareTo(b.rhs);
        if (c == 0) {
            c = lhs.compareTo(b.lhs);
        }
        return c;
    }

    public static int compareBinaryToGt0(BinaryOperator e1, GreaterThanZero e2) {
        int c = compareVariables(e1, e2);
        if (c != 0) return c;
        return -1;// binary operator (equals, e.g.) left of comparison
    }

    public static int compareVariables(Expression e1, Expression e2) {
        List<Variable> variables1 = e1.variables();
        List<Variable> variables2 = e2.variables();
        int s1 = variables1.size();
        int s2 = variables2.size();
        if (s1 == 0 && s2 == 0) return 0;
        if (s1 == 1 && s2 == 0 || s1 == 0) return s1 - s2;
        if (s1 == 1 && s2 == 1)
            return variables1.get(0).fullyQualifiedName().compareTo(variables2.get(0).fullyQualifiedName());

        // now the more complex situation
        Set<Variable> myVariables = new HashSet<>(variables1);
        Set<Variable> otherVariables = new HashSet<>(variables2);
        int varDiff = myVariables.size() - otherVariables.size();
        if (varDiff != 0) return varDiff;
        String myVarStr = myVariables.stream().map(vv -> vv.fullyQualifiedName())
                .sorted().collect(Collectors.joining(","));
        String otherVarStr = otherVariables.stream().map(vv -> vv.fullyQualifiedName())
                .sorted().collect(Collectors.joining(","));
        return myVarStr.compareTo(otherVarStr);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return lhs.causesOfDelay().merge(rhs.causesOfDelay());
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        Expression l = lhs.isDelayed() ? lhs.mergeDelays(causesOfDelay) : lhs;
        Expression r = rhs.isDelayed() ? rhs.mergeDelays(causesOfDelay) : rhs;
        if (l != lhs || r != rhs) return new BinaryOperator(identifier, primitives, l, operator, r, precedence);
        return this;
    }
}

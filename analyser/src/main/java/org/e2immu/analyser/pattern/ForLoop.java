package org.e2immu.analyser.pattern;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ForStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ForLoop {

    private static final String VARIABLE_STUB = "$";
    private static final int SOME_VALUE = 2_010_989_767;

    /*
    is the statement a classic index loop?

    for(int i=0; i<n; i++) {
       ... use i but do not assign to it
       ... do not break out
    }

    Alternatives provided for: i<n, i<=n, n>i, n>=i; i++, ++i, i+= 1; arbitrary variable name
     */

    public static Statement classicIndexLoop() {
        LocalVariableCreation i = new LocalVariableCreation(localVariableStub(0), someIntConstant(0));
        Expression someIntValue = someIntValue(0);
        Expression conditionWithGreaterThan = new BinaryOperatorTemplate(
                someIntValue,
                Set.of(Primitives.PRIMITIVES.greaterEqualsOperatorInt, Primitives.PRIMITIVES.greaterOperatorInt),
                new VariableExpression(i.localVariableReference),
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression conditionWithLessThan = new BinaryOperatorTemplate(
                new VariableExpression(i.localVariableReference),
                Set.of(Primitives.PRIMITIVES.lessEqualsOperatorInt, Primitives.PRIMITIVES.lessOperatorInt),
                someIntValue,
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression condition = new ExpressionAlternatives(conditionWithGreaterThan, conditionWithLessThan);
        Expression iPlusEquals1 = new UnaryOperator(Primitives.PRIMITIVES.unaryPlusOperatorInt, new IntConstant(1),
                UnaryOperator.DEFAULT_PRECEDENCE);
        Block block = new Block.BlockBuilder()
                .addStatement(new NoAssignmentRestriction(i.localVariableReference))
                .addStatement(new NoBreakoutRestriction())
                .build();
        return new ForStatement(null, List.of(i), condition, List.of(iPlusEquals1), block);
    }

    /*
    decreasing loop

    for(int i=n; i>=0; i--) {
        ... use i but do not assign to it
        ... do not break out
    }

    Alternatives provided: i>=0; 0<=i; i--, --i, i-= 1, arbitrary variable name
     */

    public static Statement decreasingIndexLoop() {
        LocalVariableCreation i = new LocalVariableCreation(localVariableStub(0), someIntValue(0));
        Expression conditionWithGreaterThan = new BinaryOperatorTemplate(
                new VariableExpression(i.localVariableReference),
                Set.of(Primitives.PRIMITIVES.greaterEqualsOperatorInt),
                new IntConstant(0),
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression conditionWithLessThan = new BinaryOperatorTemplate(
                new IntConstant(0),
                Set.of(Primitives.PRIMITIVES.lessEqualsOperatorInt),
                new VariableExpression(i.localVariableReference),
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression condition = new ExpressionAlternatives(conditionWithGreaterThan, conditionWithLessThan);
        Expression iMinusEquals = new UnaryOperator(Primitives.PRIMITIVES.unaryMinusOperatorInt, new IntConstant(1),
                UnaryOperator.DEFAULT_PRECEDENCE);
        Block block = new Block.BlockBuilder()
                .addStatement(new NoAssignmentRestriction(i.localVariableReference))
                .addStatement(new NoBreakoutRestriction())
                .build();
        return new ForStatement(null, List.of(i), condition, List.of(iMinusEquals), block);
    }

    static abstract class GenericRestrictionStatement implements Statement {
        @Override
        public String statementString(int indent) {
            return null;
        }

        @Override
        public Set<String> imports() {
            return null;
        }

        @Override
        public SideEffect sideEffect(SideEffectContext sideEffectContext) {
            return null;
        }
    }

    static class NoAssignmentRestriction extends GenericRestrictionStatement {

        public Set<Variable> variables;

        public NoAssignmentRestriction(Variable... variables) {
            this.variables = ImmutableSet.copyOf(variables);
        }

    }

    static class NoBreakoutRestriction extends GenericRestrictionStatement {

    }

    static class ExpressionAlternatives implements Expression {

        public List<Expression> expressions;

        public ExpressionAlternatives(Expression... expressions) {
            if (expressions == null || expressions.length == 0) throw new UnsupportedOperationException();
            this.expressions = Arrays.asList(expressions);
        }

        @Override
        public ParameterizedType returnType() {
            return expressions.get(0).returnType();
        }

        @Override
        public String expressionString(int indent) {
            return expressions.toString();
        }

        @Override
        public int precedence() {
            return expressions.stream().mapToInt(Expression::precedence).min().orElse(0);
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    static class BinaryOperatorTemplate implements Expression {

        public final ParameterizedType returnType;
        public final Expression e1;
        public final Expression e2;
        public final Set<MethodInfo> operators;
        public final boolean orderImportant; // true-> e1=lhs, e2=rhs; false -> doesn't matter

        public BinaryOperatorTemplate(Expression e1,
                                      Set<MethodInfo> operators,
                                      Expression e2,
                                      boolean orderImportant,
                                      ParameterizedType returnType) {
            this.e1 = e1;
            this.operators = operators;
            this.e2 = e2;
            this.returnType = returnType;
            this.orderImportant = orderImportant;
        }

        @Override
        public ParameterizedType returnType() {
            return returnType;
        }

        @Override
        public String expressionString(int indent) {
            return e1 + " " + operators + " " + e2;
        }

        @Override
        public int precedence() {
            return operators.stream().mapToInt(BinaryOperator::precedence).min().orElse(0);
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    public static Expression someIntConstant(int index) {
        return new IntConstant(SOME_VALUE + index) {
            @Override
            public String toString() {
                return "Some int constant " + index;
            }
        };
    }

    public static Expression someIntValue(int index) {
        return new GenericExpression(index, Primitives.PRIMITIVES.intParameterizedType);
    }

    static class GenericExpression implements Expression {
        public final int index;
        public final ParameterizedType returnType;

        public GenericExpression(int index, ParameterizedType returnType) {
            this.index = index;
            this.returnType = returnType;
        }

        @Override
        public ParameterizedType returnType() {
            return returnType;
        }

        @Override
        public String expressionString(int indent) {
            return "Some expression " + index;
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    public static LocalVariable localVariableStub(int index) {
        return new LocalVariable(List.of(), VARIABLE_STUB + index, Primitives.PRIMITIVES.intParameterizedType, List.of());
    }
}


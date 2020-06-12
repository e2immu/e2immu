package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestAssignment {

    @Test
    public void testNormal() {
        LocalVariable lvi = new LocalVariable(List.of(), "i", Primitives.PRIMITIVES.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(lvi, new IntConstant(0));
        Expression iPlusEquals1 = new Assignment(new VariableExpression(i.localVariableReference), new IntConstant(1));
        Assert.assertEquals("i = 1", iPlusEquals1.expressionString(0));
    }

    @Test
    public void testPlusEqualsOne() {
        LocalVariable lvi = new LocalVariable(List.of(), "i", Primitives.PRIMITIVES.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(lvi, new IntConstant(0));
        Expression iPlusEquals1 = new Assignment(new VariableExpression(i.localVariableReference),
                new IntConstant(1), Primitives.PRIMITIVES.assignPlusOperatorInt, null);
        Assert.assertEquals("i += 1", iPlusEquals1.expressionString(0));

        Expression iPlusEquals1AsPlusPlusI = new Assignment(new VariableExpression(i.localVariableReference),
                new IntConstant(1), Primitives.PRIMITIVES.assignPlusOperatorInt, true);
        Assert.assertEquals("++i", iPlusEquals1AsPlusPlusI.expressionString(0));

        Expression iPlusEquals1AsIPlusPlus = new Assignment(new VariableExpression(i.localVariableReference),
                new IntConstant(1), Primitives.PRIMITIVES.assignPlusOperatorInt, false);
        Assert.assertEquals("i++", iPlusEquals1AsIPlusPlus.expressionString(0));
    }

    @Test
    public void testPlusPlus() {
        LocalVariable lvi = new LocalVariable(List.of(), "i", Primitives.PRIMITIVES.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(lvi, new IntConstant(0));

        Expression iPlusPlus = new UnaryOperator(Primitives.PRIMITIVES.postfixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), UnaryOperator.PRECEDENCE_POST_INCREMENT);
        Assert.assertEquals("i++", iPlusPlus.expressionString(0));

        Expression plusPlusI = new UnaryOperator(Primitives.PRIMITIVES.prefixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), UnaryOperator.DEFAULT_PRECEDENCE);
        Assert.assertEquals("++i", plusPlusI.expressionString(0));
    }
}

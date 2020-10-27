package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestAssignment {

    private final Primitives primitives = new Primitives();

    @Test
    public void testNormal() {
        LocalVariable lvi = new LocalVariable(List.of(), "i", primitives.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(primitives, lvi, new IntConstant(primitives, 0));
        Expression iPlusEquals1 = new Assignment(primitives,
                new VariableExpression(i.localVariableReference), new IntConstant(primitives, 1));
        Assert.assertEquals("i = 1", iPlusEquals1.expressionString(0));
    }

    @Test
    public void testPlusEqualsOne() {
        LocalVariable lvi = new LocalVariable(List.of(), "i", primitives.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(primitives, lvi, new IntConstant(primitives, 0));
        Expression iPlusEquals1 = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, null);
        Assert.assertEquals("i += 1", iPlusEquals1.expressionString(0));

        Expression iPlusEquals1AsPlusPlusI = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, true);
        Assert.assertEquals("++i", iPlusEquals1AsPlusPlusI.expressionString(0));

        Expression iPlusEquals1AsIPlusPlus = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, false);
        Assert.assertEquals("i++", iPlusEquals1AsIPlusPlus.expressionString(0));
    }

    @Test
    public void testPlusPlus() {
        LocalVariable lvi = new LocalVariable(List.of(), "i", primitives.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(primitives, lvi, new IntConstant(primitives, 0));

        Expression iPlusPlus = new UnaryOperator(primitives.postfixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), UnaryOperator.PRECEDENCE_POST_INCREMENT);
        Assert.assertEquals("i++", iPlusPlus.expressionString(0));

        Expression plusPlusI = new UnaryOperator(primitives.prefixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), UnaryOperator.DEFAULT_PRECEDENCE);
        Assert.assertEquals("++i", plusPlusI.expressionString(0));
    }
}

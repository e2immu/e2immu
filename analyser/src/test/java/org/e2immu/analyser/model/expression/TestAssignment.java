package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class TestAssignment {

    private final Primitives primitives = new Primitives();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    @Test
    public void testNormal() {
        LocalVariable lvi = new LocalVariable(Set.of(), "i", primitives.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(inspectionProvider, lvi, new IntConstant(primitives, 0));
        Expression iPlusEquals1 = new Assignment(primitives,
                new VariableExpression(i.localVariableReference), new IntConstant(primitives, 1));
        Assert.assertEquals("i = 1", iPlusEquals1.minimalOutput());
    }

    @Test
    public void testPlusEqualsOne() {
        LocalVariable lvi = new LocalVariable(Set.of(), "i", primitives.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(inspectionProvider, lvi, new IntConstant(primitives, 0));
        Expression iPlusEquals1 = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, null);
        Assert.assertEquals("i += 1", iPlusEquals1.minimalOutput());

        Expression iPlusEquals1AsPlusPlusI = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, true);
        Assert.assertEquals("++i", iPlusEquals1AsPlusPlusI.minimalOutput());

        Expression iPlusEquals1AsIPlusPlus = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, false);
        Assert.assertEquals("i++", iPlusEquals1AsIPlusPlus.minimalOutput());
    }

    @Test
    public void testPlusPlus() {
        LocalVariable lvi = new LocalVariable(Set.of(), "i", primitives.intParameterizedType, List.of());
        LocalVariableCreation i = new LocalVariableCreation(inspectionProvider, lvi, new IntConstant(primitives, 0));

        Expression iPlusPlus = new UnaryOperator(primitives.postfixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), Precedence.PLUSPLUS);
        Assert.assertEquals("i++", iPlusPlus.minimalOutput());

        Expression plusPlusI = new UnaryOperator(primitives.prefixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), Precedence.UNARY);
        Assert.assertEquals("++i", plusPlusI.minimalOutput());
    }
}

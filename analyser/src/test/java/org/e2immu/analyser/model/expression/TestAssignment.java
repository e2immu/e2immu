package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAssignment {

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    private final Primitives primitives = new Primitives();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    private LocalVariable makeLocalVariableInt() {
        return new LocalVariable.Builder()
                .setName("i")
                .setParameterizedType(primitives.intParameterizedType)
                .setOwningType(primitives.stringTypeInfo)
                .build();
    }

    @Test
    public void testNormal() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(inspectionProvider, lvi, new IntConstant(primitives, 0));
        Expression iPlusEquals1 = new Assignment(primitives,
                new VariableExpression(i.localVariableReference), new IntConstant(primitives, 1));
        assertEquals("i=1", iPlusEquals1.minimalOutput());
    }

    @Test
    public void testPlusEqualsOne() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(inspectionProvider, lvi, new IntConstant(primitives, 0));
        Expression iPlusEquals1 = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, null, true);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());

        Expression iPlusEquals1AsPlusPlusI = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, true, true);
        assertEquals("++i", iPlusEquals1AsPlusPlusI.minimalOutput());

        Expression iPlusEquals1AsIPlusPlus = new Assignment(primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, false, true);
        assertEquals("i++", iPlusEquals1AsIPlusPlus.minimalOutput());
    }

    @Test
    public void testPlusPlus() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(inspectionProvider, lvi, new IntConstant(primitives, 0));

        Expression iPlusPlus = new UnaryOperator(primitives.postfixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), Precedence.PLUSPLUS);
        assertEquals("i++", iPlusPlus.minimalOutput());

        Expression plusPlusI = new UnaryOperator(primitives.prefixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), Precedence.UNARY);
        assertEquals("++i", plusPlusI.minimalOutput());
    }
}

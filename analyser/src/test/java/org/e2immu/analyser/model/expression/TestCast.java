package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestCast extends CommonTest {

    @Test
    public void test() throws ExpressionComparator.InternalError {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        VariableExpression vb = makeLVAsExpression("b", zero);

        ExpressionMock vx = simpleMock(primitives.stringParameterizedType(), LinkedVariables.of(
                va.variable(), LV.LINK_DEPENDENT,
                vb.variable(), LV.LINK_ASSIGNED
        ));
        Cast cast = new Cast(newId(), vx, primitives.stringParameterizedType());
        assertEquals("(String)(mock)", cast.minimalOutput());
        EvaluationResult er = cast.evaluate(context(evaluationContext(Map.of())), ForwardEvaluationInfo.DEFAULT);
        assertEquals("a:2,b:1", er.linkedVariablesOfExpression().toString());
        assertSame(primitives.stringParameterizedType(), er.getExpression().returnType());

        Cast cast2 = new Cast(newId(), vx, primitives.intParameterizedType());
        assertEquals("(int)(mock)", cast2.minimalOutput());
        EvaluationResult er2 = cast2.evaluate(context(evaluationContext(Map.of())), ForwardEvaluationInfo.DEFAULT);
        assertEquals("a:2,b:1", er2.linkedVariablesOfExpression().toString());
        assertSame(primitives.intParameterizedType(), er2.getExpression().returnType());
        assertTrue(er2.getExpression() instanceof PropertyWrapper);

        EvaluationResult er2b = cast2.evaluate(context(evaluationContext(Map.of())), onlySort);
        assertEquals("a:2,b:1", er2b.linkedVariablesOfExpression().toString());
        assertSame(primitives.intParameterizedType(), er2b.getExpression().returnType());
        assertTrue(er2b.getExpression() instanceof Cast);
        assertSame(primitives.intParameterizedType(), cast2.getParameterizedType());
        assertSame(vx, cast2.getExpression());
        assertEquals(1, cast2.subElements().size());
        assertSame(vx, cast2.subElements().get(0));
        
        assertNotEquals(cast, cast2);
        assertNotEquals(cast.hashCode(), cast2.hashCode());
        assertFalse(cast.internalCompareTo(cast2) > 0);
        assertThrows(ExpressionComparator.InternalError.class, () -> cast.internalCompareTo(vx));
    }
}

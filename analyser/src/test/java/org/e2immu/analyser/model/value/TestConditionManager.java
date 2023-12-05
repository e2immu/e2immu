package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.PostCondition;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestConditionManager extends CommonAbstractValue {

    @Test
    public void test1() {
        ConditionManager cm = ConditionManagerImpl.initialConditionManager(PRIMITIVES);
        Expression sNotNull = negate(equals(NullConstant.NULL_CONSTANT, s));
        ConditionManager cm1 = cm.newCondition(context, sNotNull, Set.of(vs));
        assertEquals("CM{condition=null!=s;parent=CM{}}", cm1.toString());
        ConditionManager cm2 = cm1.newCondition(context, a, Set.of(va));
        assertEquals("CM{condition=a&&null!=s;parent=CM{condition=null!=s;parent=CM{}}}", cm2.toString());
        assertEquals("a&&null!=s", cm2.absoluteState(context).toString());

        ConditionManager cm3 = cm2.removeVariables(Set.of(va));
        assertEquals("CM{condition=a&&null!=s;ignore=a;parent=CM{condition=null!=s;parent=CM{}}}", cm3.toString());
        assertEquals("null!=s", cm3.absoluteState(context).toString());

        ConditionManager cm4 = cm2.removeVariables(Set.of(vs));
        assertEquals("CM{condition=a&&null!=s;ignore=s;parent=CM{condition=null!=s;parent=CM{}}}", cm4.toString());
        assertEquals("a", cm4.expressionWithoutVariables(context, cm4.condition(), Set.of(vs)).toString());
        assertEquals("a", cm4.absoluteState(context).toString());
    }

    @Test
    public void test2() {
        ConditionManager cm = ConditionManagerImpl.initialConditionManager(PRIMITIVES);
        Expression sNotNull = negate(equals(NullConstant.NULL_CONSTANT, s));
        Expression de = DelayedExpression.forNullCheck(Identifier.CONSTANT, PRIMITIVES, sNotNull,
                PostCondition.NO_INFO_YET.causesOfDelay());
        ConditionManager cm1 = cm.newCondition(context, de, Set.of(vs));
        assertEquals("CM{condition=<null-check>;parent=CM{}}", cm1.toString());
        ConditionManager cm2 = cm1.newCondition(context, a, Set.of(va));
        assertEquals("CM{condition=a&&<null-check>;parent=CM{condition=<null-check>;parent=CM{}}}", cm2.toString());
        assertEquals("a&&<null-check>", cm2.absoluteState(context).toString());

        ConditionManager cm3 = cm2.removeVariables(Set.of(va));
        assertEquals("CM{condition=a&&<null-check>;ignore=a;parent=CM{condition=<null-check>;parent=CM{}}}", cm3.toString());
        assertEquals("<null-check>", cm3.absoluteState(context).toString());

        ConditionManager cm4 = cm2.removeVariables(Set.of(vs));
        assertEquals("CM{condition=a&&<null-check>;ignore=s;parent=CM{condition=<null-check>;parent=CM{}}}", cm4.toString());
        assertEquals("a", cm4.expressionWithoutVariables(context, cm4.condition(), Set.of(vs)).toString());
        assertEquals("a", cm4.absoluteState(context).toString());
    }
}

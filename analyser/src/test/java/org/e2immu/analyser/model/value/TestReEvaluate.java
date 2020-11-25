package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestReEvaluate extends CommonAbstractValue {

    @Test
    public void test1() {
        Value square = ProductValue.product(minimalEvaluationContext, i, i, ObjectFlow.NO_FLOW);
        Assert.assertEquals("i * i", square.toString());
        Map<Value, Value> translate = Map.of(i, newInt(3));
        Value re = square.reEvaluate(minimalEvaluationContext, translate).value;
        Assert.assertEquals("9", re.toString());
    }

    @Test
    public void test2() {
        Value value = SumValue.sum(minimalEvaluationContext,
                newInt(10), negate(ProductValue.product(minimalEvaluationContext, i, j, ObjectFlow.NO_FLOW)),
                ObjectFlow.NO_FLOW);
        Assert.assertEquals("(10 + (-(i * j)))", value.toString());
        Map<Value, Value> translate = Map.of(i, newInt(3));
        Value re = value.reEvaluate(minimalEvaluationContext, translate).value;
        Assert.assertEquals("(10 + (-(3 * j)))", re.toString());
        Map<Value, Value> translate2 = Map.of(j, newInt(2));
        Value re2 = re.reEvaluate(minimalEvaluationContext, translate2).value;
        Assert.assertEquals("4", re2.toString());
    }
}

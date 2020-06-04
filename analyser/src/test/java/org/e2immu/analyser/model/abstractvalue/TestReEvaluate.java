package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.IntValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestReEvaluate extends CommonAbstractValue {

    @Test
    public void test1() {
        Value square = ProductValue.product(i, i);
        Assert.assertEquals("i * i", square.toString());
        Map<Value, Value> translate = Map.of(i, new IntValue(3));
        Value re = square.reEvaluate(translate);
        Assert.assertEquals("9", re.toString());
    }

    @Test
    public void test2() {
        Value value = SumValue.sum(new IntValue(10), NegatedValue.negate(ProductValue.product(i, j)));
        Assert.assertEquals("(10 + not (i * j))", value.toString());
        Map<Value, Value> translate = Map.of(i, new IntValue(3));
        Value re = value.reEvaluate(translate);
        Assert.assertEquals("(10 + not (3 * j))", re.toString());
        Map<Value, Value> translate2 = Map.of(j, new IntValue(2));
        Value re2 = re.reEvaluate(translate2);
        Assert.assertEquals("4", re2.toString());
    }
}

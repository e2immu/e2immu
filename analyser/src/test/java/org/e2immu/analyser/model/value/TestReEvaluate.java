package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.Product;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestReEvaluate extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression square = Product.product(minimalEvaluationContext, i, i, ObjectFlow.NO_FLOW);
        Assert.assertEquals("i*i", square.toString());
        Map<Expression, Expression> translate = Map.of(i, newInt(3));
        Expression re = square.reEvaluate(minimalEvaluationContext, translate).value();
        Assert.assertEquals("9", re.toString());
    }

    @Test
    public void test2() {
        Expression value = Sum.sum(minimalEvaluationContext,
                newInt(10), negate(Product.product(minimalEvaluationContext, i, j, ObjectFlow.NO_FLOW)),
                ObjectFlow.NO_FLOW);
        Assert.assertEquals("10+-(i*j)", value.toString());
        Map<Expression, Expression> translate = Map.of(i, newInt(3));
        Expression re = value.reEvaluate(minimalEvaluationContext, translate).value();
        Assert.assertEquals("10+-(3*j)", re.toString());
        Map<Expression, Expression> translate2 = Map.of(j, newInt(2));
        Expression re2 = re.reEvaluate(minimalEvaluationContext, translate2).value();
        Assert.assertEquals("4", re2.toString());
    }
}

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.Product;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProduct extends CommonAbstractValue {
    VariableExpression k = newVariableExpression(createVariable("k"));
    VariableExpression l = newVariableExpression(createVariable("l"));
    VariableExpression m = newVariableExpression(createVariable("m"));
    VariableExpression n = newVariableExpression(createVariable("m"));

    // TODO build some protection

    @Test
    public void test1() {
        Expression all = sum(sum(sum(Product.product(context, k, l), i), sum(k, newInt(2))),
                Product.product(context, n, m));
        assertEquals("2+k*l+m*m+i+k", all.toString());
        Expression product = Product.product(context, all, all);
        assertEquals(140, product.getComplexity());
        assertEquals("4+4*i+i*i+4*k+k*k+2*i*k+2*k*l*k+4*k*l+k*l*k*l+4*m*m+m*m*m*m+2*k*l*i+2*m*m*i+2*m*m*k+2*k*l*m*m",
                product.toString());
    }

    @Test
    public void test2() {
        Expression all = sum(sum(sum(Product.product(context, k, l), i), sum(k, newInt(2))), m);
        assertEquals("2+k*l+i+k+m", all.toString());
        Expression product = Product.product(context, all, all);
        assertEquals(122, product.getComplexity());
        assertEquals("4+4*i+i*i+4*k+k*k+4*m+m*m+2*i*k+2*i*m+2*k*l*k+4*k*l+k*l*k*l+2*k*m+2*k*l*i+2*k*l*m",
                product.toString());
    }
}

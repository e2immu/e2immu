package org.e2immu.analyser.resolver.testexample;

public class MethodCall_39 {

    private final double FIELD = 3.14;
    private final Integer c = 300_000;

    public static double multiply(double x, double y) {
        return x*y;
    }
    public static double subtract(Number n, Number m){
        return n.doubleValue() - m.doubleValue();
    }

    public double method(double d1, double d2) {
        double d = multiply(FIELD, subtract(d1, d2));
        return d;
    }

    public double method2(double d1, double d2) {
        return multiply(c, subtract(d1, d2));
    }
}

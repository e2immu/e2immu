package org.e2immu.analyser.resolver.testexample;

public class Overload_7 {

    public static final class Statement {
    }

    public static void replace(Statement statement){
    }

    public static <S> S replace(S t){
        return t;
    }

    public static <T> T someExpression() {
        return (T) new Object();
    }

    public static <U> void test1() {
        U expression = someExpression();
        replace(expression);
    }
}

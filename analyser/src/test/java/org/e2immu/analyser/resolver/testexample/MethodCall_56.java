package org.e2immu.analyser.resolver.testexample;

public class MethodCall_56 {

    interface I<T> {

    }
    static class AI<A>  implements I<A> {

    }
    static class BI<B> implements I<B> {

    }

    String method(I<? extends Number> i) {
        return "hello "+i;
    }
    public String method(boolean b) {
        return method(b ? new AI<Long>(): new BI<Integer>());
    }
}

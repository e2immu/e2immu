package org.e2immu.analyser.resolver.testexample;

public class MethodCall_73 {

    interface I {
    }

    interface P {
    }

    interface R {
        void call(I i, P p);
    }

    static <T> T verify(T t) {
        return t;
    }

    static <T> T any(Class<T> clazz) {
        return null;
    }

    // need to achieve that 2nd argument's erasure becomes ? extends Object
    void method(R r, P p) {
        verify(r).call(any(I.class), any(p.getClass()));
    }

    static Class<? extends Object> clazz(Object p) {
        return p.getClass();
    }

    // need to achieve that 2nd argument's erasure becomes ? extends Object
    void method2(R r, P p) {
        // DOES NOT COMPILE verify(r).call(any(I.class), any(clazz(p)));
    }
}

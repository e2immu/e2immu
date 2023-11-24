package org.e2immu.analyser.resolver.testexample;

public class MethodCall_68 {

    interface A {
    }

    static class B implements A {
        void set() {
            System.out.println(5);
        }
    }

    interface Run<T extends A> {
        T run(T t);
    }

    B get() {
        return method(new B(), a -> {
            a.set();
            return a;
        });
    }

    <T extends A> T method(T t, Run<T> run) {
        run.run(t);
        return t;
    }
}

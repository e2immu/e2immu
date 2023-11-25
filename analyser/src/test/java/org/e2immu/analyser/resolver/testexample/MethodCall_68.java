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

    // change the order of the parameters; now parameter 1 takes from param 2

    B get2() {
        return method2(a -> {
            a.set();
            return a;
        }, new B());
    }

    <T extends A> T method2(Run<T> run, T t) {
        run.run(t);
        return t;
    }

    // without new B(), extends A: info about T must come from return type

    B get3() {
        return method3(a -> {
            a.set();
            return a;
        });
    }

    <T extends A> T method3(Run<T> run) {
        run.run(null);
        return null;
    }

    // without new B(), extends B: no transfer necessary, because base is B

    B get4() {
        return method4(a -> {
            a.set();
            return a;
        });
    }

    <T extends B> T method4(Run<T> run) {
        run.run(null);
        return null;
    }
}

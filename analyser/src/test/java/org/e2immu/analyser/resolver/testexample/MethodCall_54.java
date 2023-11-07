package org.e2immu.analyser.resolver.testexample;

public class MethodCall_54 {

    interface I {

    }

    record M(I[] is) {

    }

    static class Vector {
        Vector(long[] longs) {
        }

        Vector(I[] objects) {
        }
    }

    interface KI {
        void set(I[] is);
    }

    static class K implements KI {
        I i;

        @Override
        public void set(I[] is) {
            this.i = is[0];
        }
    }

    public Object method(long[] longs) {
        return new Vector(longs.clone());
    }

    public long[] method2(long[] longs) {
        return longs.clone();
    }

    public Object method3(long[] longs) {
        return new Vector(longs == null ? null : longs.clone());
    }

    Object method4(M m) {
        return new Vector(m.is() == null ? null : m.is().clone());
    }

    Object method5(M m) {
        return new Vector(m.is == null ? null : m.is.clone());
    }

    void method6(M m) {
        K k = new K();
        k.set(m.is() == null ? null : m.is().clone());
    }
}

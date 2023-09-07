package org.e2immu.analyser.resolver.testexample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubType_1 {

    static class Clazz<T> {
        private final T t;

        public Clazz(T t) {
            this.t = t;
        }

        class Sub<S> {
            private final S s;

            public Sub(S s) {
                this.s = s;
            }

            public S getS() {
                return s;
            }

            @Override
            public String toString() {
                return s + "=" + t;
            }
        }

        public T getT() {
            return t;
        }
    }

    @Test
    public void test() {
        Clazz<Integer> clazz = new Clazz<>(3);
        Clazz<Integer>.Sub<Character> sub = clazz.new Sub<Character>('a');
        assertEquals("a=3", sub.toString());
    }
}

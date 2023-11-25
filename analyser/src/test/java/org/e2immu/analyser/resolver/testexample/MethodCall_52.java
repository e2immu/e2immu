package org.e2immu.analyser.resolver.testexample;

public class MethodCall_52 {

    static class MyException extends RuntimeException implements java.io.Serializable {

        public MyException(long anError, String... args) {
            this(anError, args, true);
        }

        public MyException(long anError, String[] args, boolean logTrace) {
        }
    }

    <V> void method(long id, V value) {
        throw new MyException(3L, String.valueOf(id), String.valueOf(value));
    }

    <V> String method2(V value) {
        return String.valueOf(value);
    }
}

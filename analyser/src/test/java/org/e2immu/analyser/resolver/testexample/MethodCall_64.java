package org.e2immu.analyser.resolver.testexample;

public class MethodCall_64 {

    static class B {
        void set(Double d) {
        }
    }

    B b;

    Double getDoubleValueFromString(String val) {
        return Double.valueOf(val);
    }

    void method(String s) {
        b.set(getDoubleValueFromString(s) * 100);
    }
}

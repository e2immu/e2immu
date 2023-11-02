package org.e2immu.analyser.resolver.testexample;

public class MethodCall_34 {

    public String test1(boolean b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b ? "x" : 3);
        return sb.toString();
    }

    public String test2(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        sb.append(cs);
        return sb.toString();
    }
}

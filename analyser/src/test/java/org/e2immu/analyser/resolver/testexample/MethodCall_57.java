package org.e2immu.analyser.resolver.testexample;

import java.util.Properties;

public class MethodCall_57 {

    Properties properties;
    private static final String X = "x";


    static class III  {
    }

    static class II extends III {
    }

    static class I extends II {
    }

    interface K {
        R makeR();
    }

    record R(I[] is) {
    }

    void method(K k) {
        properties.put(X, k.makeR().is());
    }
}

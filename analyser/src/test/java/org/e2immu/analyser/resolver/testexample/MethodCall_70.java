package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.b.B;

import static org.e2immu.analyser.resolver.testexample.b.C.doNothing;

public class MethodCall_70 {

    B method1(B b) {
        return b.doNothing();
    }

    B method2() {
        return doNothing();
    }
}

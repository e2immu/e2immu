package org.e2immu.analyser.resolver.testexample;

import java.io.Serializable;

public class MethodCall_35 {

    static class A {

    }

    static class B extends A implements Serializable {

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        // this is bad practice, but it occurs in the wild :-(
        public boolean equals(A a) {
            return equals((Object) a);
        }
    }

    boolean method(B a, B b) {
        return b.equals(a);
    }
}

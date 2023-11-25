package org.e2immu.analyser.resolver.testexample;

import java.util.List;

public class MethodCall_65 {

    interface I {

    }

    static class S {
        List<I> getList() {
            return null;
        }
    }

    static class B {
        D d;

        public void setD(D value) {
            this.d = value;
        }

        static class D {

        }
    }

    private B.D create(List<I> list) {
        return null;
    }

    B method(S s) {
        B b = new B();
        b.setD(create(s.getList()));
        return b;
    }
}

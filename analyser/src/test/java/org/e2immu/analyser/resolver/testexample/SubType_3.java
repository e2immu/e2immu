package org.e2immu.analyser.resolver.testexample;

public class SubType_3 {

    public static class O {
        public interface PP {
            void theFirstMethod();
        }

        void someMethod(PP pp) {

        }
    }

    private interface PP extends O.PP {
        void oneMoreMethod();
    }

    private final PP pp = makePP();

    void method() {
        new O().someMethod(pp);
    }

    private PP makePP() {
        return new PP() {
            @Override
            public void theFirstMethod() {

            }

            @Override
            public void oneMoreMethod() {

            }
        };
    }
}

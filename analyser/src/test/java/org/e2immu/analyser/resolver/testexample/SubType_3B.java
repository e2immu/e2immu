package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.importhelper.SubType_3BHelper;

public class SubType_3B {

    private interface PP extends org.e2immu.analyser.resolver.testexample.importhelper.SubType_3BHelper.PP {
        void oneMoreMethod();
    }

    private final PP pp = makePP();

    void method() {
        new SubType_3BHelper().someMethod(pp);
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

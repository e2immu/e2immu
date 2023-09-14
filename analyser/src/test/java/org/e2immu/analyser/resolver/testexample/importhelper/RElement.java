package org.e2immu.analyser.resolver.testexample.importhelper;

public interface RElement {

    enum DescendMode {
        NO,
        YES,
        YES_INCLUDE_THIS
    }

    void doSomething(DescendMode descendMode);
}

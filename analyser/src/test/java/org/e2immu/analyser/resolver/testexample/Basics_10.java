package org.e2immu.analyser.resolver.testexample;

public abstract class Basics_10 {

    interface X {
    }

    abstract X getX();

    void method() {
        X X;
        X = getX();
    }
}

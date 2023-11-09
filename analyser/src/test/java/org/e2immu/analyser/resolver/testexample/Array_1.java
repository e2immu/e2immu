package org.e2immu.analyser.resolver.testexample;

public class Array_1 {

    public Array_1(String s) {

    }

    private Array_1[] copiesOfMyself;

    private void make() {
        copiesOfMyself = new Array_1[3];
    }

    Array_1 get(int i) {
        return copiesOfMyself[i];
    }
}

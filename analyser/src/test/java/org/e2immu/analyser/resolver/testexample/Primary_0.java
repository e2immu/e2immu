package org.e2immu.analyser.resolver.testexample;

// only one class can be public!!
class Y {
    private int i = 3;
}

public class Primary_0 {
    X x = new X();
//    Y y = x.yy; ILLEGAL! visibility is within Primary_0
}

class X {
    private Y yy = new Y();
    // int j = yy.i; ILLEGAL! visibility is within X
}

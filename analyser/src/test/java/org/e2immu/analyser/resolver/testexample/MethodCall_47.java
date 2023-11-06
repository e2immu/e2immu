package org.e2immu.analyser.resolver.testexample;

import java.util.Arrays;

public class MethodCall_47 {

    // ensure that there are sufficient methods so that Element and Expression cannot be @FunctionalInterface
    interface Element {
        int method1();
        int method2();
    }

    interface Expression extends Element, Comparable<Expression> {
        int method3();
        int method4();
    }

    record MultiExpression(Expression... expressions) {
    }

    MultiExpression multiExpression;

    int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((MethodCall_47) v).multiExpression.expressions());
    }
}

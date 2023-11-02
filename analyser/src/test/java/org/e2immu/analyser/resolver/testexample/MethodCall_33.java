package org.e2immu.analyser.resolver.testexample;


import java.util.Arrays;

public class MethodCall_33 {

    interface Expression extends Comparable<Expression> { }
    static class ArrayInitializer implements Expression {
        private Expression[] expressions;

        int internalCompareTo(Expression v) {
            return Arrays.compare(expressions, ((ArrayInitializer) v).expressions);
        }

        @Override
        public int compareTo(Expression o) {
            return 0;
        }
    }
}

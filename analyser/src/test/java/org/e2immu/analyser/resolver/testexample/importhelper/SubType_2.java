package org.e2immu.analyser.resolver.testexample.importhelper;

public class SubType_2 extends RBaseExpression implements RExpression {
    @Override
    public void doSomething(DescendMode descendMode) {
        System.out.println(descendMode);
    }

    @Override
    public int compareTo(RExpression o) {
        return 0;
    }
}

package org.e2immu.analyser.pattern;

import org.e2immu.analyser.model.Statement;
import org.junit.Test;

public class TestForLoop {

    @Test
    public void test() {
        Statement forLoop = ForLoop.classicIndexLoop();
        System.out.println(forLoop.statementString(0));
    }
}

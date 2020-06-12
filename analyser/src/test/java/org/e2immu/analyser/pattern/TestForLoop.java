package org.e2immu.analyser.pattern;

import org.e2immu.analyser.model.Statement;
import org.junit.Test;

public class TestForLoop {

    @Test
    public void testClassic() {
        Statement forLoop = ForLoop.classicIndexLoop();
        System.out.println(forLoop.statementString(0));
    }

    @Test
    public void testDecreasing() {
        Statement forLoop = ForLoop.decreasingIndexLoop();
        System.out.println(forLoop.statementString(0));
    }
}

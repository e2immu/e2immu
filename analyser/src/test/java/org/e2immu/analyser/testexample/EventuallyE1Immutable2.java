package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;

/**
 * the goal is to show that there is no Eventual Level 2 container, because there are no preconditions on j
 */
@Container
public class EventuallyE1Immutable2 {

    private int i;
    private int j;

    public void setI(int i) {
        if (i <= 0 || this.i != 0) throw new UnsupportedOperationException();
        this.i = i;
    }

    public int getI() {
        return i;
    }

    public void setJ(int j) {
        this.j = j;
    }

    public int getJ() {
        return j;
    }
}

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@Container
public class Basics_4 {

    @Nullable
    @Variable
    private int i;

    @Modified
    public void increment() {
        i = i + 1;
    }

    @NotModified
    public int getI() {
        return i;
    }
}


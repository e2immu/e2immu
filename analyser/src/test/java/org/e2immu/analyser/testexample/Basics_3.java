package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@Container
public class Basics_3 {

    @Nullable
    @Variable
    private String s;

    @Modified
    public void setS1(@NotNull String input1) {
        if (input1.contains("a")) {
            // APIs are not loaded, so should produce a potential null pointer warning
            System.out.println("With a " + s);
            s = "xyz";
        } else {
            s = "abc";
        }
        assert s != null; // should produce a warning
    }

    @Modified
    public void setS2(@Nullable String input2) {
        s = input2;
        // nullable
    }

    @NotModified
    @Nullable
    public String getS() {
        return s;
    }
}


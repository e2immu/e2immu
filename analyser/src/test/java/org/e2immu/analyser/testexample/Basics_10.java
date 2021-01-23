package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

/*
tests the contract=true on the parameter annotation (also in Enum_ tests)
 */
@E2Container
public class Basics_10 {

    @Final
    @NotNull
    private String string;

    public Basics_10(@NotNull(contract = true) String in) {
        this.string = in;
    }

    @NotNull
    @NotModified
    public String getString() {
        return string;
    }

}

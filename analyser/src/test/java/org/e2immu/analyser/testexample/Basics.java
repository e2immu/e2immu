package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
public class Basics {

    @Final
    private final String explicitlyFinal = "abc";

    @NotNull
    @Independent
    @Constant(stringValue = "abc")
    public String getExplicitlyFinal() {
        return explicitlyFinal;
    }
}

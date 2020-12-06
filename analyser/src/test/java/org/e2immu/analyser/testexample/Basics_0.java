package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
public class Basics_0 {

    // we don't want @Final here, because it is explicitly so...
    @Final(absent = true)
    // again, String is @E2Container by definition, we don't want that plastered all around
    @E2Container(absent = true)

    @Constant("abc")
    private final String explicitlyFinal = "abc";

    // again, String is @E2Container by definition, we don't want that plastered all around
    @E2Container(absent = true)
    // a method returning an @E2Immutable type is always @Independent
    @Independent(absent = true)

    @NotNull
    @Constant("abc")
    public String getExplicitlyFinal() {
        return explicitlyFinal;
    }
}

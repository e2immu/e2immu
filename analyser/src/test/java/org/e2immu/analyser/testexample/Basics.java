package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

@E2Container
public class Basics {

    // we don't want @Final here, because it is explicitly so...
    @Final(type = AnnotationType.VERIFY_ABSENT)
    // again, String is @E2Container by definition, we don't want that plastered all around
    @E2Container(type = AnnotationType.VERIFY_ABSENT)

    @Constant(stringValue = "abc")
    private final String explicitlyFinal = "abc";

    // again, String is @E2Container by definition, we don't want that plastered all around
    @E2Container(type = AnnotationType.VERIFY_ABSENT)
    // a method returning an @E2Immutable type is always @Independent
    @Independent(type = AnnotationType.VERIFY_ABSENT)

    @NotNull
    @Constant(stringValue = "abc")
    public String getExplicitlyFinal() {
        return explicitlyFinal;
    }
}

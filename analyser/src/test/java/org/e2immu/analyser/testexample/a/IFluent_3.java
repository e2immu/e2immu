package org.e2immu.analyser.testexample.a;

import org.e2immu.analyser.testexample.Fluent_3;
import org.e2immu.annotation.NotModified;

/*
Variant to IFluent_0, but this one has no @E2Container on the interface.
This causes/caused a crash.
*/
public interface IFluent_3 {
    @NotModified
    int value();

    class Builder extends Fluent_3.Builder {}
}

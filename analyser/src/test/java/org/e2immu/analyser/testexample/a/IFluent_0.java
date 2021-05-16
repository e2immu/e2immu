package org.e2immu.analyser.testexample.a;

import org.e2immu.analyser.testexample.Fluent_0;
import org.e2immu.annotation.NotModified;

/*
IFluent_1 does not have the explicit @NotModified
 */
public interface IFluent_0 {
    @NotModified
    int value();

    class Builder extends Fluent_0.Builder {}
}

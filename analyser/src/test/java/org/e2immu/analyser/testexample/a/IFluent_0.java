package org.e2immu.analyser.testexample.a;

import org.e2immu.analyser.testexample.Fluent_0;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;

/*
IFluent_1 does not have the explicit @NotModified: IFluent_1 and IFluent_2 are computed
IFluent_3 does not have the @E2Container
 */
@E2Container
public interface IFluent_0 {
    @NotModified
    int value();

    @Container
    class Builder extends Fluent_0.Builder {}
}

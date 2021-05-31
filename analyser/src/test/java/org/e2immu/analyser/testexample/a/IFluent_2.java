package org.e2immu.analyser.testexample.a;

import org.e2immu.analyser.testexample.Fluent_2;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
/*
variant on IFluent_1, going through the normal analysers rather
than the shallow analysers, because there is code.
 */
@E2Container // will be computed, and verified!!
public interface IFluent_2 {
    @NotModified //will be computed, and verified!!
    int value();

    class Builder extends Fluent_2.Builder {}

    default void withCode() {
        System.out.println("Hello!");
    }
}

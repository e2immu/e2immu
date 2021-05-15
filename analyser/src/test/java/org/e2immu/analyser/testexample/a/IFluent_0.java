package org.e2immu.analyser.testexample.a;

import org.e2immu.analyser.testexample.Fluent_0;
import org.e2immu.annotation.NotModified;

public interface IFluent_0 {
    @NotModified // IMPROVE remove when sealed has been implemented; or make a new test
    int value();

    class Builder extends Fluent_0.Builder {}
}

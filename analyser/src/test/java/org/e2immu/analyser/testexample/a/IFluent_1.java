package org.e2immu.analyser.testexample.a;

import org.e2immu.analyser.testexample.Fluent_1;
import org.e2immu.annotation.NotModified;

public interface IFluent_1 {
    @NotModified //will be computed, and verified!!
    int value();

    class Builder extends Fluent_1.Builder {}
}

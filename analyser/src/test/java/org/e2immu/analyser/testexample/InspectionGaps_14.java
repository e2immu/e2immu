package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.PropagateModification;

import java.util.function.Function;
import java.util.stream.Stream;


// looks like issue with flatMap definition

public class InspectionGaps_14<T> {

    <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return null;
    }

}

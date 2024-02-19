package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodReference_3C {

    record R2(long l) {}
    record R1(R2 r2) {
        long l() {
            return r2.l();
        }
    }

    // result of .map(R1::new) is not of correct type
    private Map<Long, R1> method(List<R2> r2) {
        Map<Long, R1> result = new HashMap<>();
        if(r2 != null) {
            result = r2
                    .stream()
                    .filter(Objects::nonNull)
                    .map(R1::new)
                    .filter(r -> Integer.MIN_VALUE != r.l())
                    .collect(Collectors.toMap(R1::l, x -> x));
        }
        return result;
    }

    // this one works fine
    private Map<Long, R1> method2(List<R2> r2) {
        Map<Long, R1> result = new HashMap<>();
        if(r2 != null) {
            Stream<R1> r1Stream = r2
                    .stream()
                    .filter(Objects::nonNull)
                    .map(R1::new);
            result = r1Stream
                    .filter(r -> Integer.MIN_VALUE != r.l())
                    .collect(Collectors.toMap(R1::l, x -> x));
        }
        return result;
    }
}

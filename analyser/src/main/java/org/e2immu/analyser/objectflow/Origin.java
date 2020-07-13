package org.e2immu.analyser.objectflow;

import java.util.Set;
import java.util.stream.Stream;

public interface Origin {
    Stream<ObjectFlow> sources();

    String safeToString(Set<ObjectFlow> visited, boolean detailed);
}

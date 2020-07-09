package org.e2immu.analyser.objectflow;

import java.util.stream.Stream;

public interface Origin {
    Stream<ObjectFlow> sources();
}

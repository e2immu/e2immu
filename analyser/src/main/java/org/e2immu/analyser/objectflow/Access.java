package org.e2immu.analyser.objectflow;

import java.util.Set;

public interface Access {
    String safeToString(Set<ObjectFlow> visited, boolean detailed);
}

package org.e2immu.analyser.util;

import org.e2immu.analyser.model.MethodInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/*
This is an extremely limited implementation. See JFocus.
 */
public record MethodParSeq(Map<MethodInfo, CommutableData> immutableMap) implements ParSeq<MethodInfo> {
    @Override
    public boolean containsParallels() {
        return false;
    }

    @Override
    public <X> List<X> sortParallels(List<X> items, Comparator<X> comparator) {
        return null;
    }
}

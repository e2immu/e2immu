package org.e2immu.analyser.util;

import org.e2immu.analyser.model.ParameterInfo;

import java.util.Comparator;
import java.util.List;

/*
This is an extremely limited implementation. See JFocus.
 */
public class ParameterParallelGroup implements ParSeq<ParameterInfo> {

    @Override
    public boolean containsParallels() {
        return true;
    }

    @Override
    public <X> List<X> sortParallels(List<X> items, Comparator<X> comparator) {
        return items.stream().sorted(comparator).toList();
    }

}

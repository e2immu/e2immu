package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Value;
import org.e2immu.annotation.Singleton;

import java.util.Comparator;

@Singleton
public class ValueComparator implements Comparator<Value> {
    public static final ValueComparator SINGLETON = new ValueComparator();

    private ValueComparator() {
        // nothing here
    }

    private static class Unwrapped {
        final Value value;

        public Unwrapped(Value v) {
            Value unwrapped = v;
            while (unwrapped instanceof ValueWrapper) {
                unwrapped = ((ValueWrapper) unwrapped).getValue();
            }
            value = unwrapped;
        }
    }

    @Override
    public int compare(Value v1, Value v2) {
        boolean v1Wrapped = v1 instanceof ValueWrapper;
        boolean v2Wrapped = v2 instanceof ValueWrapper;

        // short-cut
        if (!v1Wrapped && !v2Wrapped) {
            return compareWithoutWrappers(v1, v2);
        }

        Unwrapped u1 = new Unwrapped(v1);
        Unwrapped u2 = new Unwrapped(v2);

        int withoutWrappers = compareWithoutWrappers(u1.value, u2.value);
        if (withoutWrappers != 0) return withoutWrappers;

        if (!v1Wrapped) return -1; // unwrapped always before wrapped
        if (!v2Wrapped) return 1;

        // now both are wrapped...
        int w = ((ValueWrapper) v1).wrapperOrder() - ((ValueWrapper) v2).wrapperOrder();

        // different wrappers
        if (w != 0) return w;

        // same wrappers, go deeper
        return compare(((ValueWrapper) v1).getValue(), ((ValueWrapper) v2).getValue());
    }

    private int compareWithoutWrappers(Value v1, Value v2) {
        int orderDiff = v1.order() - v2.order();
        if (orderDiff != 0) return orderDiff;
        return v1.internalCompareTo(v2);
    }
}

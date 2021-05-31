package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Modified;

import java.util.Set;

/**
 * A type cannot have a lower container or immutable value than any of the interfaces it implements.
 * This has to follow after computation, and will only be enforced during the 'check' phase.
 */
public class Warnings_7 {

    @Container
    interface MustBeContainer {
        void addToSet(Set<Integer> set);
    }

    @E2Immutable // still, will cause an error because we had expected @E2Container
    static class IsNotAContainer implements MustBeContainer {

        public final int i;

        IsNotAContainer(int i) {
            this.i = i;
        }

        // must cause an error, because addToSet is explicitly not modifying set (inside @Container)
        public void addToSet(@Modified Set<Integer> set) {
            set.add(i);
        }
    }
}

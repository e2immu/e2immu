/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.support.SetOnce;

import java.util.Set;

/*
allowsInterrupts == increases statement time
 */
public record MethodResolution(Set<MethodInfo> overrides,
                               Set<MethodInfo> methodsOfOwnClassReached,
                               CallStatus partOfConstruction,
                               boolean staticMethodCallsOnly,
                               boolean allowsInterrupts,
                               boolean ignoreMeBecauseOfPartOfCallCycle) {

    public enum CallStatus {
        PART_OF_CONSTRUCTION,
        NOT_CALLED_AT_ALL,
        CALLED_FROM_NON_PRIVATE_METHOD,
        NON_PRIVATE,
        NOT_RESOLVED; // this one means that we did not do any effort to find out

        public boolean accessibleFromTheOutside() {
            return this == NON_PRIVATE || this == CALLED_FROM_NON_PRIVATE_METHOD;
        }
    }

    public static class Builder {

        public MethodResolution build() {
            return new MethodResolution(
                    getOverrides(),
                    getMethodsOfOwnClassReached(),
                    getPartOfConstruction(),
                    isStaticMethodCallsOnly(),
                    isAllowsInterrupts(),
                    isIgnoreMeBecauseOfPartOfCallCycle.getOrDefault(false));
        }

        private final SetOnce<Set<MethodInfo>> overrides = new SetOnce<>();

        public Set<MethodInfo> getOverrides() {
            return overrides.isSet() ? Set.copyOf(overrides.get()) : Set.of();
        }

        /**
         * this one contains all own methods called from this method, and the transitive closure.
         * we use this to compute effective finality: some methods are only called from constructors,
         * they form part of the construction aspect of the class
         */
        private final SetOnce<Set<MethodInfo>> methodsOfOwnClassReached = new SetOnce<>();

        public void setMethodsOfOwnClassReached(Set<MethodInfo> set) {
            methodsOfOwnClassReached.set(set);
        }

        public Set<MethodInfo> getMethodsOfOwnClassReached() {
            return methodsOfOwnClassReached.isSet() ? Set.copyOf(methodsOfOwnClassReached.get()) : Set.of();
        }

        public final SetOnce<CallStatus> partOfConstruction = new SetOnce<>();

        public CallStatus getPartOfConstruction() {
            return partOfConstruction.getOrDefaultNull();
        }

        // ************** VARIOUS ODDS AND ENDS
        // used to check that in a utility class, no objects of the class itself are created

        public final SetOnce<Boolean> staticMethodCallsOnly = new SetOnce<>();

        public boolean isStaticMethodCallsOnly() {
            return staticMethodCallsOnly.getOrDefault(false);
        }

        public final SetOnce<Boolean> allowsInterrupts = new SetOnce<>();

        public boolean isAllowsInterrupts() {
            return allowsInterrupts.getOrDefault(true);
        }

        private final  SetOnce<Boolean> isIgnoreMeBecauseOfPartOfCallCycle = new SetOnce<>();

        public void setIgnoreMeBecauseOfPartOfCallCycle(boolean value) {
            isIgnoreMeBecauseOfPartOfCallCycle.set(value);
        }

        public void setOverrides(MethodInfo methodInfo, Set<MethodInfo> overrides) {
            assert !overrides.contains(methodInfo);
            this.overrides.set(overrides);
            overrides.forEach(override -> override.addImplementation(methodInfo));
        }

        // ***************

    }

}

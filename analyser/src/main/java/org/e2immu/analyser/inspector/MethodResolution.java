/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                               boolean createObjectOfSelf,
                               boolean staticMethodCallsOnly,
                               boolean allowsInterrupts) {

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
                    isCreateObjectOfSelf(),
                    isStaticMethodCallsOnly(),
                    isAllowsInterrupts());
        }

        public final SetOnce<Set<MethodInfo>> overrides = new SetOnce<>();

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
            return partOfConstruction.getOrElse(null);
        }

        // ************** VARIOUS ODDS AND ENDS
        // used to check that in a utility class, no objects of the class itself are created

        public final SetOnce<Boolean> createObjectOfSelf = new SetOnce<>();
        // if true, the method has no (non-static) method calls on the "this" scope

        public boolean isCreateObjectOfSelf() {
            return createObjectOfSelf.getOrElse(false);
        }

        public final SetOnce<Boolean> staticMethodCallsOnly = new SetOnce<>();

        public boolean isStaticMethodCallsOnly() {
            return staticMethodCallsOnly.getOrElse(false);
        }

        public final SetOnce<Boolean> allowsInterrupts = new SetOnce<>();

        public boolean isAllowsInterrupts() {
            return allowsInterrupts.getOrElse(true);
        }

        // ***************

    }

}

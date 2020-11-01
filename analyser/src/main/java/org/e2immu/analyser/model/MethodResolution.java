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

package org.e2immu.analyser.model;

import org.e2immu.analyser.util.SetOnce;

import java.util.Set;

public class MethodResolution {

    public enum CallStatus {
        PART_OF_CONSTRUCTION,
        NOT_CALLED_AT_ALL,
        CALLED_FROM_NON_PRIVATE_METHOD,
        NON_PRIVATE;

        public boolean accessibleFromTheOutside() {
            return this == NON_PRIVATE || this == CALLED_FROM_NON_PRIVATE_METHOD;
        }
    }

    /**
     * this one contains all own methods called from this method, and the transitive closure.
     * we use this to compute effective finality: some methods are only called from constructors,
     * they form part of the construction aspect of the class
     */
    public final SetOnce<Set<MethodInfo>> methodsOfOwnClassReached = new SetOnce<>();

    public final SetOnce<CallStatus> partOfConstruction = new SetOnce<>();
    // ************** VARIOUS ODDS AND ENDS
    // used to check that in a utility class, no objects of the class itself are created

    public final SetOnce<Boolean> createObjectOfSelf = new SetOnce<>();
    // if true, the method has no (non-static) method calls on the "this" scope

    public final SetOnce<Boolean> staticMethodCallsOnly = new SetOnce<>();

    // ***************

    public void setCallStatus(MethodInfo methodInfo) {
        MethodResolution.CallStatus callStatus;
        if (methodInfo.isConstructor) {
            callStatus = MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        } else if (!methodInfo.isPrivate()) {
            callStatus = MethodResolution.CallStatus.NON_PRIVATE;
        } else if (methodInfo.isCalledFromNonPrivateMethod()) {
            callStatus = MethodResolution.CallStatus.CALLED_FROM_NON_PRIVATE_METHOD;
        } else if (methodInfo.isCalledFromConstructors()) {
            callStatus = MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        } else {
            callStatus = MethodResolution.CallStatus.NOT_CALLED_AT_ALL;
        }
        partOfConstruction.set(callStatus);
    }


}

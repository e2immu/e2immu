/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.Set;

public class FieldAnalysis extends Analysis {

    // if the field turns out to be effectively final, it can have a value
    public final SetOnce<Value> effectivelyFinalValue = new SetOnce<>();

    // once we know the dynamic type annotations, we can convert this to annotations on the fields
    public final SetOnce<Boolean> dynamicTypeAnnotationsAdded = new SetOnce<>();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // here, the key of the map are fields; the local variables and parameters are stored in method analysis
    // the values are either other fields (in which case these other fields are not linked to parameters)
    // or parameters
    public final SetOnce<Set<Variable>> variablesLinkedToMe = new SetOnce<>();

    public final SetOnceMap<MethodInfo, Boolean> errorsForAssignmentsOutsidePrimaryType = new SetOnceMap<>();
}

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


// ...
public class TypeAnalysis extends Analysis {

    // to avoid repetitions
    public final SetOnce<Boolean> startedPostAnalysisIntoNestedTypes = new SetOnce<>();

    // the keys here are all the nested types of an enclosing type
    public final SetOnceMap<TypeInfo, Boolean> accessFromEnclosingToNestedTypesVerified = new SetOnceMap<>();

    // the keys here are all the enclosing types of a nested type
    public final SetOnceMap<TypeInfo, Boolean> accessFromNestedToEnclosingTypesVerified = new SetOnceMap<>();

}

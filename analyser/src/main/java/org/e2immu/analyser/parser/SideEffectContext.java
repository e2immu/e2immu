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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.TypeInfo;

public class SideEffectContext {

    public final TypeContext typeContext;
    public final TypeInfo enclosingType;
    public final MethodInfo enclosingMethod;
    public final SideEffect exposureToOutsideWorld;

    // this would be the default, given that Object.equals, Object.hashCode and Object.toString() *should* be ContextFunctions
    // or suppliers.
    public SideEffectContext(TypeContext typeContext, MethodInfo enclosingMethod) {
        this.typeContext = typeContext;
        exposureToOutsideWorld = SideEffect.NONE_CONTEXT;
        this.enclosingType = enclosingMethod.typeInfo;
        this.enclosingMethod = enclosingMethod;
    }
}

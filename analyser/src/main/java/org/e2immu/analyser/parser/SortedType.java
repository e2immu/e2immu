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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;

/**
 * Main usage: defined types, where the order of methods, fields and sub-types is important for the analysis phase.
 * <p>
 * This type is also used for sub-resolvers. In that case, the TypeInfo object is not a primary type, but either
 * a locally declared type, an anonymous type, a lambda, ...
 */
public class SortedType {
    public final TypeInfo primaryType;

    public final List<WithInspectionAndAnalysis> methodsFieldsSubTypes;

    // these two lists are there to avoid running over the full list 3 times in the PrimaryTypeAnalyser
    public final List<TypeInfo> types;
    public final List<MethodInfo> methods;

    public SortedType(TypeInfo primaryType, List<TypeInfo> types, List<MethodInfo> methods, List<WithInspectionAndAnalysis> methodsFieldsSubTypes) {
        this.primaryType = Objects.requireNonNull(primaryType);
        this.methodsFieldsSubTypes = Objects.requireNonNull(methodsFieldsSubTypes);
        this.types = types;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return primaryType.fullyQualifiedName + ": " + methodsFieldsSubTypes;
    }
}

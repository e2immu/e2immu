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

import org.e2immu.analyser.resolver.SortedType;

import java.util.Set;

/*
The resolver is used recursively at the level of sub-types defined in statements, not at the level
of "normal" subtypes -- only the former require a new EvaluationContext closure.

This recursion results in a SortedType object which will be used to create a PrimaryTypeAnalyser
in the statement analyser.
SortedType is only not-null for sub-types defined in statements; it is kept null for primary types.
 */
public record TypeResolution(SortedType sortedType,
                             Set<TypeInfo> circularDependencies,
                             Set<TypeInfo> superTypesExcludingJavaLangObject) {
}

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

import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.TypeName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class QualificationImpl implements Qualification {
    private final Set<FieldInfo> fieldsShadowedByLocalName = new HashSet<>();
    private final Set<MethodInfo> unqualifiedMethods = new HashSet<>();
    private final Map<TypeInfo, TypeName.Required> typesNotImported = new HashMap<>();

    public QualificationImpl(Qualification parent) {
        parent.fieldStream().forEach(fieldsShadowedByLocalName::add);
        parent.methodStream().forEach(unqualifiedMethods::add);
        parent.typeStream().forEach(e -> typesNotImported.put(e.getKey(), e.getValue()));
    }

    @Override
    public boolean qualifierRequired(Variable variable) {
        if (variable instanceof FieldReference fieldReference) {
            return fieldsShadowedByLocalName.contains(fieldReference.fieldInfo);
        }
        return false;
    }

    @Override
    public Stream<FieldInfo> fieldStream() {
        return fieldsShadowedByLocalName.stream();
    }

    public void addField(FieldInfo fieldInfo) {
        fieldsShadowedByLocalName.add(fieldInfo);
    }

    @Override
    public boolean qualifierRequired(MethodInfo methodInfo) {
        return !unqualifiedMethods.contains(methodInfo);
    }

    @Override
    public Stream<MethodInfo> methodStream() {
        return unqualifiedMethods.stream();
    }

    public void addMethodUnlessOverride(MethodInfo methodInfo) {
        // TODO check override
        unqualifiedMethods.add(methodInfo);
    }


    @Override
    public TypeName.Required qualifierRequired(TypeInfo typeInfo) {
        return typesNotImported.getOrDefault(typeInfo, TypeName.Required.SIMPLE);
    }

    @Override
    public Stream<Map.Entry<TypeInfo, TypeName.Required>> typeStream() {
        return typesNotImported.entrySet().stream();
    }

    public void addType(TypeInfo typeInfo, TypeName.Required required) {
        typesNotImported.put(typeInfo, required);
    }
}

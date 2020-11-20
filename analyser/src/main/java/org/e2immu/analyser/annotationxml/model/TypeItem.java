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

package org.e2immu.analyser.annotationxml.model;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Mark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@E2Immutable(after = "freeze")
public class TypeItem extends HasAnnotations implements Comparable<TypeItem> {
    public final String name;

    //@Mark("freeze")
    public TypeItem(TypeInfo typeInfo) {
        this.name = typeInfo.fullyQualifiedName;
        boolean haveTypeInspection = typeInfo.typeInspection.isSet();
        addAnnotations(haveTypeInspection ? typeInfo.typeInspection.get().getAnnotations() : List.of(),
                typeInfo.typeAnalysis.isSet() ?
                        typeInfo.typeAnalysis.get().getAnnotationStream().filter(e -> e.getValue() == Boolean.TRUE)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        if (haveTypeInspection) {
            for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)) {
                MethodItem methodItem = new MethodItem(methodInfo);
                methodItems.put(methodItem.name, methodItem);
            }
            for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields()) {
                FieldItem fieldItem = new FieldItem(fieldInfo);
                fieldItems.put(fieldItem.name, fieldItem);
            }
        }
        freeze();
    }

    public TypeItem(String name) {
        this.name = name;
    }

    private Map<String, MethodItem> methodItems = new HashMap<>();
    private Map<String, FieldItem> fieldItems = new HashMap<>();

    @Mark("freeze")
    public void freeze() {
        super.freeze();
        methodItems = ImmutableMap.copyOf(methodItems);
        methodItems.values().forEach(MethodItem::freeze);
        fieldItems = ImmutableMap.copyOf(fieldItems);
        fieldItems.values().forEach(FieldItem::freeze);
    }

    public Map<String, MethodItem> getMethodItems() {
        return methodItems;
    }

    public Map<String, FieldItem> getFieldItems() {
        return fieldItems;
    }

    @Override
    public int compareTo(TypeItem o) {
        return name.compareTo(o.name);
    }
}

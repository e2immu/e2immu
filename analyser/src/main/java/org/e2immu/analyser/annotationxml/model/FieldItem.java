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

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.annotation.E2Immutable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@E2Immutable(after = "freeze")
public class FieldItem extends HasAnnotations implements Comparable<FieldItem> {
    public final String name;

    public FieldItem(String name) {
        this.name = name;
    }

    //@Mark("freeze")
    public FieldItem(FieldInfo fieldInfo) {
        this.name = fieldInfo.name;
        addAnnotations(fieldInfo.fieldInspection.isSet() ? fieldInfo.fieldInspection.get().getAnnotations() : List.of(),
                fieldInfo.fieldAnalysis.isSet() ?
                fieldInfo.fieldAnalysis.get().getAnnotationStream().filter(e -> e.getValue() == Boolean.TRUE)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList()): List.of());
        freeze();
    }

    @Override
    public int compareTo(FieldItem o) {
        return name.compareTo(o.name);
    }
}

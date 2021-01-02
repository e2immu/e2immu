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

import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.annotation.E2Immutable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@E2Immutable(after = "freeze")
public class ParameterItem extends HasAnnotations implements Comparable<ParameterItem> {
    public final int index;

    public ParameterItem(int parameterIndex) {
        index = parameterIndex;
    }

    public ParameterItem(ParameterInfo parameterInfo) {
        index = parameterInfo.index;
        addAnnotations(parameterInfo.parameterInspection.isSet() ? parameterInfo.parameterInspection.get().getAnnotations() : List.of(),
                parameterInfo.parameterAnalysis.isSet() ?
                        parameterInfo.parameterAnalysis.get().getAnnotationStream().filter(e -> e.getValue().isPresent())
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        freeze();
    }

    @Override
    public int compareTo(ParameterItem o) {
        return index - o.index;
    }
}

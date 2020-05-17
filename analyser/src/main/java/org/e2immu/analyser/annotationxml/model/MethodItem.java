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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.annotation.E2Immutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@E2Immutable(after = "freeze")
public class MethodItem extends HasAnnotations implements Comparable<MethodItem> {
    public final String name;
    public final String returnType;
    private List<ParameterItem> parameterItems = new ArrayList<>();

    public MethodItem(String name, String returnType) {
        this.name = name;
        this.returnType = returnType;
    }

    //@Mark("freeze")
    public MethodItem(MethodInfo methodInfo) {
        returnType = methodInfo.returnType().stream();
        String parameters;
        if (methodInfo.methodInspection.isSet()) {
            List<String> parameterTypes = new ArrayList<>();
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                ParameterItem parameterItem = new ParameterItem(parameterInfo);
                parameterItems.add(parameterItem);
                parameterTypes.add(parameterInfo.parameterizedType.stream());
            }
            parameters = String.join(", ", parameterTypes);
        } else {
            parameters = "";
        }
        name = methodInfo.name + "(" + parameters + ")";
        addAnnotations(methodInfo.methodInspection.isSet() ? methodInfo.methodInspection.get().annotations : List.of(),
                methodInfo.methodAnalysis.get().annotations.stream().filter(e -> e.getValue() == Boolean.TRUE)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList()));
        freeze();
    }

    public List<ParameterItem> getParameterItems() {
        return parameterItems;
    }

    void freeze() {
        super.freeze();
        parameterItems = ImmutableList.copyOf(parameterItems);
        parameterItems.forEach(ParameterItem::freeze);
    }

    @Override
    public int compareTo(MethodItem o) {
        return name.compareTo(o.name);
    }
}

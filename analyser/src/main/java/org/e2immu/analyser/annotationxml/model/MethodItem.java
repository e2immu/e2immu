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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
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
    private List<MethodItem> companionMethods = new ArrayList<>();
    public final String companionValue;
    public final String paramNamesCsv;

    public MethodItem(String name, String returnType) {
        this.name = name;
        this.returnType = returnType;
        companionValue = "";
        paramNamesCsv = "";
    }

    //@Mark("freeze")
    public MethodItem(MethodInfo methodInfo, Expression companionExpression) {
        returnType = methodInfo.returnType().print();
        String parameters;
        if (methodInfo.methodInspection.isSet()) {
            List<String> parameterTypes = new ArrayList<>();
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
                ParameterItem parameterItem = new ParameterItem(parameterInfo);
                parameterItems.add(parameterItem);
                parameterTypes.add(parameterInfo.parameterizedType.print());
            }
            parameters = String.join(", ", parameterTypes);
            paramNamesCsv = methodInfo.methodInspection.get().getParameters().stream()
                    .map(ParameterInfo::name).collect(Collectors.joining(","));
            if (companionExpression != null) {
                companionValue = companionExpression.minimalOutput();
            } else {
                companionValue = "";
                for (MethodInfo companionMethod : methodInfo.methodInspection.get().getCompanionMethods().values()) {
                    MethodItem companionItem = new MethodItem(companionMethod, extractExpression(companionMethod));
                    companionMethods.add(companionItem);
                }
            }
        } else {
            parameters = "";
            companionValue = "";
            paramNamesCsv = "";
        }
        name = methodInfo.name + "(" + parameters + ")";
        addAnnotations(methodInfo.methodInspection.isSet() ? methodInfo.methodInspection.get().getAnnotations() : List.of(),
                methodInfo.methodAnalysis.isSet() ?
                        methodInfo.methodAnalysis.get().getAnnotationStream().filter(e -> e.getValue() == Boolean.TRUE)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        freeze();
    }

    private static Expression extractExpression(MethodInfo companionMethod) {
        Block block = companionMethod.methodInspection.get().getMethodBody();
        List<Statement> statements = block.structure.statements;
        if (statements == null || statements.isEmpty()) return EmptyExpression.EMPTY_EXPRESSION;
        assert statements.size() == 1;
        if (statements.get(0) instanceof ReturnStatement ret) {
            return ret.expression;
        }
        throw new UnsupportedOperationException();
    }

    public List<ParameterItem> getParameterItems() {
        return parameterItems;
    }

    public List<MethodItem> getCompanionMethods() {
        return companionMethods;
    }

    void freeze() {
        super.freeze();
        parameterItems = ImmutableList.copyOf(parameterItems);
        parameterItems.forEach(ParameterItem::freeze);
        companionMethods = ImmutableList.copyOf(companionMethods);
        companionMethods.forEach(MethodItem::freeze);
    }

    @Override
    public int compareTo(MethodItem o) {
        return name.compareTo(o.name);
    }
}

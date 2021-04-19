/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.annotationxml.model;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.E2Immutable;

import java.util.*;
import java.util.stream.Collectors;

@E2Immutable(after = "freeze")
public class MethodItem extends HasAnnotations implements Comparable<MethodItem> {
    public final String name;
    public final String returnType;
    private List<ParameterItem> parameterItems = new ArrayList<>();
    private Map<String, MethodItem> companionMethods = new HashMap<>();
    public final String companionValue;
    public final String paramNamesCsv;
    public final boolean isStatic;
    public final String typeParametersCsv;

    public MethodItem(String name, String returnType) {
        this(false, "", returnType, name, "", "");
    }

    public MethodItem(boolean isStatic,
                      String typeParametersCsv,
                      String returnType,
                      String name,
                      String paramNamesCsv,
                      String companionValue) {
        this.isStatic = isStatic;
        this.typeParametersCsv = typeParametersCsv;
        this.name = Objects.requireNonNull(name);
        this.returnType = checkReturnType(returnType);
        this.companionValue = companionValue;
        this.paramNamesCsv = paramNamesCsv;
    }

    private static String checkReturnType(String s) {
        assert s == null || s.trim().equals(s) && s.length() > 0;
        return s;
    }

    public MethodItem(MethodInfo methodInfo, Expression companionExpression) {
        returnType = methodInfo.isConstructor ? null :
                methodInfo.hasReturnValue() ? checkReturnType(methodInfo.returnType().print()) : "void";
        String parameters;
        if (methodInfo.methodInspection.isSet()) {
            MethodInspection inspection = methodInfo.methodInspection.get();
            List<String> parameterTypes = new ArrayList<>();
            for (ParameterInfo parameterInfo : inspection.getParameters()) {
                ParameterItem parameterItem = new ParameterItem(parameterInfo);
                parameterItems.add(parameterItem);
                parameterTypes.add(parameterInfo.parameterizedType.print());
            }
            parameters = String.join(", ", parameterTypes);
            paramNamesCsv = inspection.getParameters().stream()
                    .map(ParameterInfo::name).collect(Collectors.joining(","));
            if (companionExpression != null) {
                companionValue = companionExpression.minimalOutput();
            } else {
                companionValue = "";
                for (MethodInfo companionMethod : inspection.getCompanionMethods().values()) {
                    MethodItem companionItem = new MethodItem(companionMethod, extractExpression(companionMethod));
                    companionMethods.put(companionItem.name, companionItem);
                }
            }
            isStatic = inspection.isStatic();
            typeParametersCsv = inspection.getTypeParameters().stream()
                    .map(tp -> tp.output(InspectionProvider.DEFAULT,
                            Qualification.FULLY_QUALIFIED_NAME, new HashSet<>()).toString())
                    .collect(Collectors.joining(","));
        } else {
            parameters = "";
            companionValue = "";
            paramNamesCsv = "";
            isStatic = false; // NOT RELEVANT
            typeParametersCsv = ""; // NOT RELEVANT
        }
        name = checkReturnType(methodInfo.name + "(" + parameters + ")");
        addAnnotations(methodInfo.methodInspection.isSet() ? methodInfo.methodInspection.get().getAnnotations() : List.of(),
                methodInfo.methodAnalysis.isSet() ?
                        methodInfo.methodAnalysis.get().getAnnotationStream().filter(e -> e.getValue().isPresent())
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        freeze();
    }

    private static Expression extractExpression(MethodInfo companionMethod) {
        Block block = companionMethod.methodInspection.get().getMethodBody();
        List<Statement> statements = block.structure.statements();
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

    public void putCompanionMethod(MethodItem methodItem) {
        companionMethods.put(methodItem.name, methodItem);
        assert methodItem.companionMethods.isEmpty();
    }

    public MethodItem getCompanionMethod(String name) {
        return companionMethods.get(name);
    }

    public Collection<MethodItem> getCompanionMethods() {
        return companionMethods.values();
    }

    void freeze() {
        super.freeze();
        parameterItems = List.copyOf(parameterItems);
        parameterItems.forEach(ParameterItem::freeze);
        companionMethods = Map.copyOf(companionMethods);
        companionMethods.values().forEach(MethodItem::freeze);
    }

    @Override
    public String toString() {
        return "MethodItem " + name;
    }

    @Override
    public int compareTo(MethodItem o) {
        return name.compareTo(o.name);
    }
}

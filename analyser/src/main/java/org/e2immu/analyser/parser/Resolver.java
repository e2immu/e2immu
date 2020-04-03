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

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    public List<SortedType> sortTypes(Map<TypeInfo, TypeContext> inspectedTypes) {
        DependencyGraph<TypeInfo> typeGraph = new DependencyGraph<>();
        Map<TypeInfo, SortedType> toSortedType = new HashMap<>();
        Set<TypeInfo> stayWithin = new HashSet<>(inspectedTypes.keySet());

        for (Map.Entry<TypeInfo, TypeContext> entry : inspectedTypes.entrySet()) {
            try {
                recursivelyAddToTypeGraph(typeGraph, toSortedType, stayWithin, entry.getKey(), entry.getValue());
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving type {}", entry.getKey().fullyQualifiedName);
                throw rte;
            }
        }
        return typeGraph.sorted().stream().map(toSortedType::get).collect(Collectors.toList());
    }

    // the type context of the file contains the imports and the top-level types of the compilation unit
    // one level down, it contains the imports, the top-level types, and for one top-level type, all sub-level types

    private void recursivelyAddToTypeGraph(DependencyGraph<TypeInfo> typeGraph, Map<TypeInfo, SortedType> toSortedType,
                                           Set<TypeInfo> stayWithin, TypeInfo typeInfo, TypeContext typeContextOfFile) {
        Set<TypeInfo> typeDependencies = new HashSet<>();

        TypeInspection ti = typeInfo.typeInspection.get();
        ti.interfacesImplemented.forEach(pt -> typeDependencies.addAll(pt.typeInfoSet()));
        if (ti.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT)
            typeDependencies.addAll(ti.parentClass.typeInfoSet());
        if (ti.packageNameOrEnclosingType.isRight())
            typeDependencies.add(ti.packageNameOrEnclosingType.getRight());

        TypeContext typeContextOfType = new TypeContext(typeContextOfFile);
        // add static sub-types to the type graph
        ti.subTypes.forEach(typeContextOfType::addToContext);
        ti.subTypes.stream()
                .filter(subType -> subType.typeInspection.get().modifiers.contains(TypeModifier.STATIC))
                .forEach(subType -> {
                    stayWithin.add(subType);
                    recursivelyAddToTypeGraph(typeGraph, toSortedType, stayWithin, subType, typeContextOfType);
                });
        // TODO add non-static sub-types to the methodGraph
        DependencyGraph<WithInspectionAndAnalysis> methodGraph = doType(typeInfo, typeContextOfType, typeDependencies);
        toSortedType.put(typeInfo, new SortedType(typeInfo, methodGraph.sorted()));

        // remove myself, and stay within the set of inspectedTypes
        typeDependencies.remove(typeInfo);
        typeDependencies.retainAll(stayWithin);

        ImmutableList<WithInspectionAndAnalysis> methodOrder = ImmutableList.copyOf(methodGraph.sorted());
        log(RESOLVE, "Method graph has {} relations", methodGraph.relations());
        log(RESOLVE, "Method and field order in {}: {}", typeInfo.fullyQualifiedName,
                methodOrder.stream().map(WithInspectionAndAnalysis::name).collect(Collectors.joining(", ")));
        typeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));
    }

    private DependencyGraph<WithInspectionAndAnalysis> doType(TypeInfo typeInfo, TypeContext typeContextOfType, Set<TypeInfo> typeDependencies) {
        TypeInspection typeInspection = typeInfo.typeInspection.get();

        log(RESOLVE, "Resolving type {}", typeInfo.fullyQualifiedName);
        ExpressionContext expressionContext = ExpressionContext.forBodyParsing(typeInfo, typeContextOfType, typeDependencies);

        expressionContext.typeContext.addToContext(typeInfo);
        typeInspection.subTypes.forEach(expressionContext.typeContext::addToContext);
        typeInspection.typeParameters.forEach(expressionContext.typeContext::addToContext);

        This thisScope = new This(typeInfo);
        typeInspection.fields.forEach(fieldInfo -> expressionContext.variableContext.add(
                new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisScope)));

        DependencyGraph<WithInspectionAndAnalysis> methodGraph = new DependencyGraph<>();

        typeInspection.fields.forEach(fieldInfo -> {
            if (!fieldInfo.fieldInspection.get().initializer.isSet()) {
                Expression expression = fieldInfo.fieldInspection.get().initializer.getFirst();
                if (expression != FieldInspection.EMPTY) {
                    ExpressionContext subContext = expressionContext.newTypeContext("new field dependencies");
                    Objects.requireNonNull(subContext.dependenciesOnOtherMethodsAndFields); // to keep IntelliJ happy
                    MethodTypeParameterMap singleAbstractMethod = fieldInfo.type.findSingleAbstractMethodOfInterface(subContext.typeContext);
                    if (singleAbstractMethod != null) {
                        log(RESOLVE, "Passing on functional interface method to field initializer of {}", fieldInfo.fullyQualifiedName());
                    }
                    org.e2immu.analyser.model.Expression parsedExpression = subContext.parseExpression(expression, singleAbstractMethod);
                    fieldInfo.fieldInspection.get().initializer.set(parsedExpression);
                    methodGraph.addNode(fieldInfo, ImmutableList.copyOf(subContext.dependenciesOnOtherMethodsAndFields));
                } else {
                    fieldInfo.fieldInspection.get().initializer.set(EmptyExpression.EMPTY_EXPRESSION);
                    methodGraph.addNode(fieldInfo, List.of());
                }
            }
        });

        Stream.concat(typeInspection.constructors.stream(), typeInspection.methods.stream())
                .forEach(methodInfo -> {
                    try {
                        List<TypeParameter> typeParameters = methodInfo.methodInspection.get().typeParameters;
                        ExpressionContext subContext;
                        if (typeParameters.isEmpty()) {
                            subContext = expressionContext.newTypeContext("new method dependencies");
                        } else {
                            subContext = expressionContext.newTypeContext("new method dependencies and type parameters of " +
                                    methodInfo.name);
                            typeParameters.forEach(subContext.typeContext::addToContext);
                        }
                        Objects.requireNonNull(subContext.dependenciesOnOtherMethodsAndFields); // to keep IntelliJ happy
                        // let's start by adding the types of parameters, and the return type
                        methodInfo.methodInspection.get().parameters.stream().map(p -> p.parameterizedType)
                                .forEach(pt -> typeDependencies.addAll(pt.typeInfoSet()));
                        if (!methodInfo.isConstructor) {
                            typeDependencies.addAll(methodInfo.methodInspection.get().returnType.typeInfoSet());
                        }
                        boolean doBlock = !methodInfo.methodInspection.get().methodBody.isSet();
                        if (doBlock) {
                            BlockStmt block = methodInfo.methodInspection.get().methodBody.getFirst();
                            if (!block.getStatements().isEmpty()) {
                                log(RESOLVE, "Parsing block of method {}", methodInfo.name);
                                doBlock(subContext, methodInfo, block);
                                methodGraph.addNode(methodInfo, ImmutableList.copyOf(subContext.dependenciesOnOtherMethodsAndFields));
                            } else {
                                methodInfo.methodInspection.get().methodBody.set(Block.EMPTY_BLOCK);
                            }
                        }
                    } catch (RuntimeException rte) {
                        LOGGER.warn("Caught runtime exception while resolving method {}", methodInfo.fullyQualifiedName());
                        throw rte;
                    }
                });
        log(RESOLVE, "Method graph of {} has {} entries", typeInfo.fullyQualifiedName, methodGraph.size());
        methodGraph.visit((n, list) -> log(RESOLVE, " -- Node {} --> {}", n.name(),
                list == null ? "[]" : StringUtil.join(list, WithInspectionAndAnalysis::name)));
        return methodGraph;
    }

    private void doBlock(ExpressionContext expressionContext, MethodInfo methodInfo, BlockStmt block) {
        try {
            ExpressionContext newContext = expressionContext.newVariableContext("resolving " + methodInfo.fullyQualifiedName());
            methodInfo.methodInspection.get().parameters.forEach(newContext.variableContext::add);
            log(RESOLVE, "Parsing block with variable context {}", newContext.variableContext);
            Block parsedBlock = newContext.parseBlockOrStatement(block);
            methodInfo.methodInspection.get().methodBody.set(parsedBlock);
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while resolving block starting at line {}", block.getBegin().orElse(null));
            throw rte;
        }
    }
}

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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.expr.ParseLambdaExpr;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    public static List<SortedType> sortTypes(Map<TypeInfo, TypeContext> inspectedTypes, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        DependencyGraph<TypeInfo> typeGraph = new DependencyGraph<>();
        Map<TypeInfo, SortedType> toSortedType = new HashMap<>();
        Set<TypeInfo> stayWithin = new HashSet<>(inspectedTypes.keySet());

        for (Map.Entry<TypeInfo, TypeContext> entry : inspectedTypes.entrySet()) {
            try {
                recursivelyAddToTypeGraph(typeGraph, toSortedType, stayWithin, entry.getKey(), entry.getValue(), e2ImmuAnnotationExpressions);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving type {}", entry.getKey().fullyQualifiedName);
                throw rte;
            }
        }
        return typeGraph.sorted().stream().map(toSortedType::get).collect(Collectors.toList());
    }

    // the type context of the file contains the imports and the top-level types of the compilation unit
    // one level down, it contains the imports, the top-level types, and for one top-level type, all sub-level types

    // the typeContextOfFile contains the types imported; we have no other access to import statements here
    private static void recursivelyAddToTypeGraph(DependencyGraph<TypeInfo> typeGraph,
                                                  Map<TypeInfo, SortedType> toSortedType,
                                                  Set<TypeInfo> stayWithin,
                                                  TypeInfo typeInfo,
                                                  TypeContext typeContextOfFile,
                                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        Set<TypeInfo> typeDependencies = new HashSet<>();

        TypeInspection ti = typeInfo.typeInspection.get();
        ti.interfacesImplemented.forEach(pt -> typeDependencies.addAll(pt.typeInfoSet()));
        if (ti.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT)
            typeDependencies.addAll(ti.parentClass.typeInfoSet());

        TypeContext typeContextOfType = new TypeContext(typeContextOfFile);
        typeDependencies.addAll(ti.subTypes); // dependencies for subtypes go from enclosing to sub
        ti.subTypes.forEach(typeContextOfType::addToContext);
        ti.subTypes.forEach(subType -> {
            stayWithin.add(subType);
            recursivelyAddToTypeGraph(typeGraph, toSortedType, stayWithin, subType, typeContextOfType, e2ImmuAnnotationExpressions);
        });
        DependencyGraph<WithInspectionAndAnalysis> methodGraph = doType(typeInfo, typeContextOfType, typeDependencies);

        typeInfo.copyAnnotationsIntoTypeAnalysisProperties(e2ImmuAnnotationExpressions, false, "resolver");
        fillInternalMethodCalls(methodGraph);

        toSortedType.put(typeInfo, new SortedType(typeInfo, methodGraph.sorted()));

        // remove myself and all my enclosing types, and stay within the set of inspectedTypes
        typeDependencies.removeAll(typeInfo.myselfAndMyEnclosingTypes());
        typeDependencies.retainAll(stayWithin);

        ImmutableList<WithInspectionAndAnalysis> methodOrder = ImmutableList.copyOf(methodGraph.sorted());
        log(RESOLVE, "Method graph has {} relations", methodGraph.relations());
        log(RESOLVE, "Method and field order in {}: {}", typeInfo.fullyQualifiedName,
                methodOrder.stream().map(WithInspectionAndAnalysis::name).collect(Collectors.joining(", ")));
        typeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));
    }

    private static DependencyGraph<WithInspectionAndAnalysis> doType(TypeInfo typeInfo, TypeContext typeContextOfType, Set<TypeInfo> typeDependencies) {
        TypeInspection typeInspection = typeInfo.typeInspection.get();

        log(RESOLVE, "Resolving type {}", typeInfo.fullyQualifiedName);
        ExpressionContext expressionContext = ExpressionContext.forBodyParsing(typeInfo,
                typeInfo.primaryType(),
                typeContextOfType, typeDependencies);

        expressionContext.typeContext.addToContext(typeInfo);
        typeInspection.typeParameters.forEach(expressionContext.typeContext::addToContext);

        // add visible types to the type context
        typeInfo.accessibleBySimpleNameTypeInfoStream().forEach(expressionContext.typeContext::addToContext);

        // add visible fields to variable context
        typeInfo.accessibleFieldsStream().forEach(fieldInfo -> expressionContext.variableContext.add(new FieldReference(fieldInfo,
                fieldInfo.isStatic() ? null : new This(fieldInfo.owner))));

        DependencyGraph<WithInspectionAndAnalysis> methodGraph = new DependencyGraph<>();

        // ANNOTATIONS ON THE TYPE

        typeInspection.annotations.forEach(annotationExpression -> {
            if (!annotationExpression.expressions.isSet()) {
                annotationExpression.resolve(expressionContext);
            }
        });

        // FIELDS

        typeInspection.fields.forEach(fieldInfo -> {
            if (!fieldInfo.fieldInspection.get().initialiser.isSet()) {
                FieldInspection fieldInspection = fieldInfo.fieldInspection.get();

                // ANNOTATIONS

                fieldInspection.annotations.forEach(annotationExpression -> {
                    if (!annotationExpression.expressions.isSet()) {
                        annotationExpression.resolve(expressionContext);
                    }
                });

                // INITIALISERS

                Expression expression = fieldInspection.initialiser.getFirst();
                FieldInspection.FieldInitialiser fieldInitialiser;
                List<WithInspectionAndAnalysis> dependencies;

                if (expression != FieldInspection.EMPTY) {
                    ExpressionContext subContext = expressionContext.newTypeContext("new field dependencies");
                    Objects.requireNonNull(subContext.dependenciesOnOtherMethodsAndFields); // to keep IntelliJ happy
                    // fieldInfo.type can have concrete types; but the abstract method will not have them filled in
                    MethodTypeParameterMap singleAbstractMethod = fieldInfo.type.findSingleAbstractMethodOfInterface();
                    if (singleAbstractMethod != null) {
                        singleAbstractMethod = singleAbstractMethod.expand(fieldInfo.type.initialTypeParameterMap());
                        log(RESOLVE, "Passing on functional interface method to field initializer of {}: {}", fieldInfo.fullyQualifiedName(), singleAbstractMethod);
                    }
                    org.e2immu.analyser.model.Expression parsedExpression = subContext.parseExpression(expression, singleAbstractMethod);

                    MethodInfo sam;
                    boolean artificial;
                    if (fieldInfo.type.isFunctionalInterface()) {
                        List<NewObject> newObjects = parsedExpression.find(NewObject.class);
                        artificial = newObjects.stream().filter(no -> no.parameterizedType.isFunctionalInterface()).count() != 1L;

                        if (!artificial) {
                            NewObject newObject = newObjects.stream().filter(no -> no.parameterizedType.isFunctionalInterface()).findFirst().orElseThrow();
                            TypeInfo anonymousType = newObject.anonymousClass;
                            sam = anonymousType.findOverriddenSingleAbstractMethod();
                        } else {
                            // implicit anonymous type
                            // no point in creating something that we cannot (yet) deal with...
                            if (parsedExpression instanceof NullConstant || parsedExpression == EmptyExpression.EMPTY_EXPRESSION) {
                                sam = null;
                            } else if (parsedExpression instanceof Lambda) {
                                sam = ((Lambda) parsedExpression).implementation.typeInfo.findOverriddenSingleAbstractMethod();
                            } else if (parsedExpression instanceof MethodReference) {
                                sam = typeInfo.convertMethodReferenceIntoLambda(fieldInfo.type, typeInfo, (MethodReference) parsedExpression, expressionContext);
                            } else {
                                throw new UnsupportedOperationException("Cannot (yet) deal with " + parsedExpression.getClass());
                            }
                        }
                    } else {
                        sam = null;
                        artificial = false;
                    }
                    fieldInitialiser = new FieldInspection.FieldInitialiser(parsedExpression, sam, artificial);
                    dependencies = ImmutableList.copyOf(subContext.dependenciesOnOtherMethodsAndFields);
                } else {
                    fieldInitialiser = new FieldInspection.FieldInitialiser(EmptyExpression.EMPTY_EXPRESSION, null, false);
                    dependencies = List.of();
                }
                methodGraph.addNode(fieldInfo, dependencies);
                fieldInspection.initialiser.set(fieldInitialiser);
            }
        });

        // METHOD AND CONSTRUCTOR, without the SAMs in FIELDS

        typeInspection.constructorAndMethodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM).forEach(methodInfo -> {
            try {
                MethodInspection methodInspection = methodInfo.methodInspection.get();

                // TYPE PARAMETERS OF METHOD

                List<TypeParameter> typeParameters = methodInspection.typeParameters;
                ExpressionContext subContext;
                if (typeParameters.isEmpty()) {
                    subContext = expressionContext.newTypeContext("new method dependencies");
                } else {
                    subContext = expressionContext.newTypeContext("new method dependencies and type parameters of " +
                            methodInfo.name);
                    typeParameters.forEach(subContext.typeContext::addToContext);
                }

                // ANNOTATIONS

                methodInspection.annotations.forEach(annotationExpression -> {
                    if (!annotationExpression.expressions.isSet()) {
                        annotationExpression.resolve(subContext);
                    }
                });
                methodInspection.parameters.forEach(parameterInfo -> parameterInfo.parameterInspection
                        .get().annotations.forEach(annotationExpression -> {
                            if (!annotationExpression.expressions.isSet()) {
                                annotationExpression.resolve(subContext);
                            }
                        }));

                // BODY

                Objects.requireNonNull(subContext.dependenciesOnOtherMethodsAndFields); // to keep IntelliJ happy
                // let's start by adding the types of parameters, and the return type
                methodInspection.parameters.stream().map(p -> p.parameterizedType)
                        .forEach(pt -> typeDependencies.addAll(pt.typeInfoSet()));
                if (!methodInfo.isConstructor) {
                    typeDependencies.addAll(methodInspection.returnType.typeInfoSet());
                }
                boolean doBlock = !methodInspection.methodBody.isSet();
                if (doBlock) {
                    BlockStmt block = methodInspection.methodBody.getFirst();
                    if (!block.getStatements().isEmpty()) {
                        log(RESOLVE, "Parsing block of method {}", methodInfo.name);
                        doBlock(subContext, methodInfo, block);
                        methodGraph.addNode(methodInfo, ImmutableList.copyOf(subContext.dependenciesOnOtherMethodsAndFields));
                    } else {
                        methodInspection.methodBody.set(Block.EMPTY_BLOCK);
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

    private static void doBlock(ExpressionContext expressionContext, MethodInfo methodInfo, BlockStmt block) {
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


    private static void fillInternalMethodCalls(DependencyGraph<WithInspectionAndAnalysis> methodGraph) {
        methodGraph.visit((from, toList) -> {
            if (from instanceof MethodInfo) {
                MethodInfo methodInfo = ((MethodInfo) from);
                Set<WithInspectionAndAnalysis> dependencies = methodGraph.dependenciesOnlyTerminals(from);
                Set<MethodInfo> methodsReached = dependencies.stream().filter(w -> w instanceof MethodInfo).map(w -> (MethodInfo) w).collect(Collectors.toSet());
                methodInfo.methodAnalysis.get().methodsOfOwnClassReached.set(methodsReached);
            }
        });
        methodGraph.visit((from, toList) -> {
            if (from instanceof MethodInfo) {
                MethodInfo methodInfo = (MethodInfo) from;
                methodInfo.methodAnalysis.get().partOfConstruction.set(methodInfo.isConstructor ||
                        methodInfo.isPrivate() && !methodInfo.isCalledFromNonPrivateMethod());
            }
        });
    }
}

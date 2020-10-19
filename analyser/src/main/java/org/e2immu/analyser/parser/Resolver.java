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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.SMapSet;
import org.e2immu.annotation.NotModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.Primitives.PRIMITIVES;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    private final Messages messages = new Messages();
    private final boolean subResolver;

    public Resolver(boolean subResolver) {
        this.subResolver = subResolver;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    /**
     * Responsible for resolving, circular dependency detection.
     *
     * @param inspectedTypes when a subResolver, the map contains only one type, and it will not be a primary type.
     *                       When not a subResolver, it only contains primary types.
     * @return A list of sorted primary types, each with their sub-elements (sub-types, fields, methods) sorted.
     */

    public List<SortedType> sortTypes(Map<TypeInfo, TypeContext> inspectedTypes) {
        DependencyGraph<TypeInfo> typeGraph = new DependencyGraph<>();
        Map<TypeInfo, SortedType> toSortedType = new HashMap<>();
        Set<TypeInfo> stayWithin = inspectedTypes.keySet().stream()
                .flatMap(typeInfo -> typeInfo.allTypesInPrimaryType().stream()).collect(Collectors.toSet());

        for (Map.Entry<TypeInfo, TypeContext> entry : inspectedTypes.entrySet()) {
            try {
                TypeInfo typeInfo = entry.getKey();
                TypeContext typeContext = entry.getValue();

                assert subResolver || typeInfo.isPrimaryType();
                SortedType sortedType = addToTypeGraph(typeGraph, stayWithin, typeInfo, typeContext);
                toSortedType.put(typeInfo, sortedType);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving type {}", entry.getKey().fullyQualifiedName);
                throw rte;
            }
        }
        if (subResolver) {
            return ImmutableList.copyOf(toSortedType.values());
        }
        List<SortedType> result = sortWarnForCircularDependencies(typeGraph).stream().map(toSortedType::get).collect(Collectors.toList());
        log(RESOLVE, "Result of type sorting: {}", result);
        return result;
    }

    private List<TypeInfo> sortWarnForCircularDependencies(DependencyGraph<TypeInfo> typeGraph) {
        Map<TypeInfo, Set<TypeInfo>> participatesInCycles = new HashMap<>();
        List<TypeInfo> sorted = typeGraph.sorted(typeInfo -> {
            // typeInfo is part of a cycle, dependencies are:
            Set<TypeInfo> typesInCycle = typeGraph.dependencies(typeInfo);
            log(RESOLVE, "Type {} is part of cycle: {}", typeInfo,
                    () -> typesInCycle.stream().map(t -> t.simpleName).collect(Collectors.joining(",")));
            for (TypeInfo other : typesInCycle) {
                SMapSet.add(participatesInCycles, other, typesInCycle);
            }
            messages.add(Message.newMessage(new Location(typeInfo), Message.CIRCULAR_TYPE_DEPENDENCY,
                    typesInCycle.stream().map(t -> t.fullyQualifiedName).collect(Collectors.joining(", "))));
        });
        for (TypeInfo typeInfo : sorted) {
            Set<TypeInfo> circularDependencies = participatesInCycles.get(typeInfo);
            TypeResolution typeResolution = new TypeResolution();
            typeResolution.circularDependencies.set(circularDependencies == null ? Set.of() : ImmutableSet.copyOf(circularDependencies));

            typeInfo.typeResolution.set(typeResolution);
        }
        return sorted;
    }

    private SortedType addToTypeGraph(DependencyGraph<TypeInfo> typeGraph,
                                      Set<TypeInfo> stayWithin,
                                      TypeInfo typeInfo,
                                      TypeContext typeContextOfFile) {

        // main call
        TypeContext typeContextOfType = new TypeContext(typeContextOfFile);
        DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph = new DependencyGraph<>();
        List<MethodInfo> methods = doType(typeInfo, typeContextOfType, methodFieldSubTypeGraph);

        fillInternalMethodCalls(methodFieldSubTypeGraph);

        // remove myself and all my enclosing types, and stay within the set of inspectedTypes
        Set<TypeInfo> typeDependencies = typeInfo.typesReferenced().stream().map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));

        List<TypeInfo> allTypesInPrimaryType = typeInfo.allTypesInPrimaryType();
        typeDependencies.removeAll(allTypesInPrimaryType);
        typeDependencies.retainAll(stayWithin);
        typeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));

        ImmutableList<WithInspectionAndAnalysis> methodFieldSubTypeOrder = ImmutableList.copyOf(methodFieldSubTypeGraph.sorted());

        if (isLogEnabled(RESOLVE)) {
            log(RESOLVE, "Method graph has {} relations", methodFieldSubTypeGraph.relations());
            log(RESOLVE, "Method and field order in {}: {}", typeInfo.fullyQualifiedName,
                    methodFieldSubTypeOrder.stream().map(WithInspectionAndAnalysis::name).collect(Collectors.joining(", ")));
            log(RESOLVE, "Types referred to in {}: {}", typeInfo.fullyQualifiedName, typeDependencies);
        }

        return new SortedType(typeInfo, allTypesInPrimaryType, methods, methodFieldSubTypeOrder);
    }

    private List<MethodInfo> doType(TypeInfo typeInfo, TypeContext typeContextOfType,
                                    DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        TypeInspection typeInspection = typeInfo.typeInspection.getPotentiallyRun();

        typeInspection.subTypes.forEach(typeContextOfType::addToContext);
        List<MethodInfo> methods = new ArrayList<>();

        // recursion, do sub-types first
        typeInspection.subTypes.forEach(subType -> methods.addAll(doType(subType, typeContextOfType, methodFieldSubTypeGraph)));

        log(RESOLVE, "Resolving type {}", typeInfo.fullyQualifiedName);
        ExpressionContext expressionContext = ExpressionContext.forBodyParsing(typeInfo, typeInfo.primaryType(), typeContextOfType);

        expressionContext.typeContext.addToContext(typeInfo);
        typeInspection.typeParameters.forEach(expressionContext.typeContext::addToContext);

        // add visible types to the type context
        typeInfo.accessibleBySimpleNameTypeInfoStream().forEach(expressionContext.typeContext::addToContext);

        // add visible fields to variable context
        typeInfo.accessibleFieldsStream().forEach(fieldInfo -> expressionContext.variableContext.add(new FieldReference(fieldInfo,
                fieldInfo.isStatic() ? null : new This(fieldInfo.owner))));

        // ANNOTATIONS ON THE TYPE

        typeInspection.annotations.forEach(annotationExpression -> {
            if (!annotationExpression.expressions.isSet()) {
                annotationExpression.resolve(expressionContext);
            }
        });

        doFields(typeInspection, expressionContext, methodFieldSubTypeGraph);
        methods.addAll(doMethodsAndConstructors(typeInspection, expressionContext, methodFieldSubTypeGraph));

        // dependencies of the type

        Set<TypeInfo> typeDependencies = typeInfo.typesReferenced().stream().map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));
        List<TypeInfo> allTypesInPrimaryType = typeInfo.allTypesInPrimaryType();
        typeDependencies.retainAll(allTypesInPrimaryType);
        methodFieldSubTypeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));
        return methods;
    }

    private void doFields(TypeInspection typeInspection,
                          ExpressionContext expressionContext,
                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        typeInspection.fields.forEach(fieldInfo -> {

            // ANNOTATIONS

            fieldInfo.fieldInspection.get().annotations.forEach(annotationExpression -> {
                if (!annotationExpression.expressions.isSet()) {
                    annotationExpression.resolve(expressionContext);
                }
            });

            if (!fieldInfo.fieldInspection.get().initialiser.isSet()) {
                doFieldInitialiser(fieldInfo, expressionContext, methodFieldSubTypeGraph);
            }
        });
    }

    private void doFieldInitialiser(FieldInfo fieldInfo,
                                    ExpressionContext expressionContext,
                                    DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        FieldInspection fieldInspection = fieldInfo.fieldInspection.get();

        Expression expression = fieldInspection.initialiser.getFirst();
        FieldInspection.FieldInitialiser fieldInitialiser;
        List<WithInspectionAndAnalysis> dependencies;

        if (expression != FieldInspection.EMPTY) {
            ExpressionContext subContext = expressionContext.newTypeContext("new field dependencies");

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
                List<NewObject> newObjects = parsedExpression.collect(NewObject.class);
                artificial = newObjects.stream().filter(no -> no.parameterizedType.isFunctionalInterface()).count() != 1L;

                if (!artificial) {
                    NewObject newObject = newObjects.stream().filter(no -> no.parameterizedType.isFunctionalInterface()).findFirst().orElseThrow();
                    TypeInfo anonymousType = Objects.requireNonNull(newObject.anonymousClass);
                    sam = anonymousType.findOverriddenSingleAbstractMethod();
                } else {
                    // implicit anonymous type
                    // no point in creating something that we cannot (yet) deal with...
                    if (parsedExpression instanceof NullConstant || parsedExpression == EmptyExpression.EMPTY_EXPRESSION) {
                        sam = null;
                    } else if (parsedExpression instanceof Lambda) {
                        sam = ((Lambda) parsedExpression).implementation.typeInfo.findOverriddenSingleAbstractMethod();
                    } else if (parsedExpression instanceof MethodReference) {
                        Resolver resolver = new Resolver(true);
                        sam = fieldInfo.owner.convertMethodReferenceIntoLambda(fieldInfo.type, fieldInfo.owner,
                                (MethodReference) parsedExpression, expressionContext, resolver);
                        messages.addAll(resolver.getMessageStream());
                    } else {
                        throw new UnsupportedOperationException("Cannot (yet) deal with " + parsedExpression.getClass());
                    }
                }
            } else {
                sam = null;
                artificial = false;
            }
            fieldInitialiser = new FieldInspection.FieldInitialiser(parsedExpression, sam, artificial);
            Element toVisit = sam != null ? sam.methodInspection.get().methodBody.get() : parsedExpression;
            MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited();
            methodsAndFieldsVisited.visit(toVisit);
            dependencies = ImmutableList.copyOf(methodsAndFieldsVisited.methodsAndFields);
        } else {
            fieldInitialiser = new FieldInspection.FieldInitialiser(EmptyExpression.EMPTY_EXPRESSION, null, false);
            dependencies = List.of();
        }
        methodFieldSubTypeGraph.addNode(fieldInfo, dependencies);
        fieldInspection.initialiser.set(fieldInitialiser);
    }

    private List<MethodInfo> doMethodsAndConstructors(TypeInspection typeInspection, ExpressionContext expressionContext,
                                                      DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // METHOD AND CONSTRUCTOR, without the SAMs in FIELDS
        List<MethodInfo> methods = new ArrayList<>();
        typeInspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {
            try {
                doMethodOrConstructor(methodInfo, expressionContext, methodFieldSubTypeGraph);
                methods.add(methodInfo);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving method {}", methodInfo.fullyQualifiedName());
                throw rte;
            }
        });
        return methods;
    }

    private void doMethodOrConstructor(MethodInfo methodInfo,
                                       ExpressionContext expressionContext,
                                       DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
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

        boolean doBlock = !methodInspection.methodBody.isSet();
        if (doBlock) {
            BlockStmt block = methodInspection.methodBody.getFirst();
            if (!block.getStatements().isEmpty()) {
                log(RESOLVE, "Parsing block of method {}", methodInfo.name);
                doBlock(subContext, methodInfo, block);
                MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited();
                methodsAndFieldsVisited.visit(methodInspection.methodBody.get());
                methodFieldSubTypeGraph.addNode(methodInfo, ImmutableList.copyOf(methodsAndFieldsVisited.methodsAndFields));
            } else {
                methodInspection.methodBody.set(Block.EMPTY_BLOCK);
            }
        }
    }

    private static class MethodsAndFieldsVisited {
        final Set<WithInspectionAndAnalysis> methodsAndFields = new HashSet<>();

        void visit(Element element) {
            element.visit(e -> {
                if (e instanceof FieldAccess fieldAccess) {
                    methodsAndFields.add(((FieldReference) fieldAccess.variable).fieldInfo);
                } else if (e instanceof VariableExpression variableExpression) {
                    if (variableExpression.variable instanceof FieldReference) {
                        methodsAndFields.add(((FieldReference) variableExpression.variable).fieldInfo);
                    }
                } else if (e instanceof MethodCall) {
                    methodsAndFields.add(((MethodCall) e).methodInfo);
                } else if (e instanceof MethodReference) {
                    methodsAndFields.add(((MethodReference) e).methodInfo);
                } else if (e instanceof NewObject) {
                    MethodInfo constructor = ((NewObject) e).constructor; // can be null!
                    if (constructor != null) {
                        methodsAndFields.add(constructor);
                    }
                }
            });
        }
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
            if (from instanceof MethodInfo methodInfo) {
                Set<WithInspectionAndAnalysis> dependencies = methodGraph.dependenciesOnlyTerminals(from);
                Set<MethodInfo> methodsReached = dependencies.stream().filter(w -> w instanceof MethodInfo).map(w -> (MethodInfo) w).collect(Collectors.toSet());

                MethodResolution methodResolution = new MethodResolution();
                methodResolution.methodsOfOwnClassReached.set(methodsReached);
                methodInfo.methodResolution.set(methodResolution);

                methodCreatesObjectOfSelf(methodInfo, methodResolution);
                computeStaticMethodCallsOnly(methodInfo, methodResolution);

            }
        });
        methodGraph.visit((from, toList) -> {
            if (from instanceof MethodInfo methodInfo) {
                methodInfo.methodResolution.get().partOfConstruction.set(methodInfo.isConstructor ||
                        methodInfo.isPrivate() && !methodInfo.isCalledFromNonPrivateMethod());
            }
        });
    }


    // part of @UtilityClass computation in the type analyser
    private static void methodCreatesObjectOfSelf(MethodInfo methodInfo, MethodResolution methodResolution) {
        AtomicBoolean createSelf = new AtomicBoolean();
        methodInfo.methodInspection.get().methodBody.get().visit(element -> {
            if (element instanceof NewObject newObject) {
                if (newObject.parameterizedType.typeInfo == methodInfo.typeInfo) {
                    createSelf.set(true);
                }
            }
        });
        methodResolution.createObjectOfSelf.set(createSelf.get());
    }

    private static void computeStaticMethodCallsOnly(@NotModified MethodInfo methodInfo, MethodResolution methodResolution) {
        if (!methodResolution.staticMethodCallsOnly.isSet()) {
            if (methodInfo.isStatic) {
                methodResolution.staticMethodCallsOnly.set(true);
            } else {
                AtomicBoolean atLeastOneCallOnThis = new AtomicBoolean(false);
                Block block = methodInfo.methodInspection.get().methodBody.get();
                block.visit(element -> {
                    if (element instanceof MethodCall methodCall) {
                        boolean callOnThis = !methodCall.methodInfo.isStatic &&
                                methodCall.object == null || ((methodCall.object instanceof This) &&
                                ((This) methodCall.object).typeInfo == methodInfo.typeInfo);
                        if (callOnThis) atLeastOneCallOnThis.set(true);
                    }
                });
                boolean staticMethodCallsOnly = !atLeastOneCallOnThis.get();
                log(STATIC_METHOD_CALLS, "Method {} is not static, does it have no calls on <this> scope? {}", methodInfo.fullyQualifiedName(), staticMethodCallsOnly);
                methodResolution.staticMethodCallsOnly.set(staticMethodCallsOnly);
            }
        }
    }


}

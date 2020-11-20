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
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.SMapSet;
import org.e2immu.annotation.NotModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.LogTarget.STATIC_METHOD_CALLS;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    private final Messages messages = new Messages();

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
                .flatMap(typeInfo -> typeInfo.allTypesInPrimaryType().stream())
                .collect(Collectors.toSet());

        for (Map.Entry<TypeInfo, TypeContext> entry : inspectedTypes.entrySet()) {
            try {
                TypeInfo typeInfo = entry.getKey();
                TypeContext typeContext = entry.getValue();

                assert typeInfo.isPrimaryType() : "Not a primary type: " + typeInfo.fullyQualifiedName;
                SortedType sortedType = addToTypeGraph(typeGraph, stayWithin, typeInfo, typeContext);
                toSortedType.put(typeInfo, sortedType);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving type {}", entry.getKey().fullyQualifiedName);
                throw rte;
            }
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
            TypeResolution typeResolution = new TypeResolution(circularDependencies == null ? Set.of() : circularDependencies);
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
        doType(typeInfo, typeContextOfType, methodFieldSubTypeGraph);


        fillInternalMethodCalls(methodFieldSubTypeGraph);

        // remove myself and all my enclosing types, and stay within the set of inspectedTypes
        Set<TypeInfo> typeDependencies = typeInfo.typesReferenced().stream().map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));

        if (Primitives.isJavaLangObject(typeInfo)) {
            typeDependencies.clear(); // removes a gigantic circular dependency on Object -> String
        } else {
            List<TypeInfo> allTypesInPrimaryType = typeInfo.allTypesInPrimaryType();
            typeDependencies.removeAll(allTypesInPrimaryType);
            typeDependencies.remove(typeInfo);
            typeDependencies.retainAll(stayWithin);
        }

        typeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));
        ImmutableList<WithInspectionAndAnalysis> methodFieldSubTypeOrder = ImmutableList.copyOf(methodFieldSubTypeGraph.sorted());

        if (isLogEnabled(RESOLVE)) {
            log(RESOLVE, "Method graph has {} relations", methodFieldSubTypeGraph.relations());
            log(RESOLVE, "Method and field order in {}: {}", typeInfo.fullyQualifiedName,
                    methodFieldSubTypeOrder.stream().map(WithInspectionAndAnalysis::name).collect(Collectors.joining(", ")));
            log(RESOLVE, "Types referred to in {}: {}", typeInfo.fullyQualifiedName, typeDependencies);
        }

        return new SortedType(typeInfo, methodFieldSubTypeOrder);
    }

    private void doType(TypeInfo typeInfo, TypeContext typeContextOfType,
                        DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        try {
            TypeInspection typeInspection = typeContextOfType.getTypeInspection(typeInfo);

            typeInspection.subTypes.forEach(typeContextOfType::addToContext);

            // recursion, do sub-types first
            typeInspection.subTypes.forEach(subType -> doType(subType, typeContextOfType, methodFieldSubTypeGraph));

            log(RESOLVE, "Resolving type {}", typeInfo.fullyQualifiedName);
            ExpressionContext expressionContext = ExpressionContext.forBodyParsing(typeInfo, typeInfo.primaryType(), typeContextOfType);

            expressionContext.typeContext.addToContext(typeInfo);
            typeInspection.typeParameters.forEach(expressionContext.typeContext::addToContext);

            // add visible types to the type context
            typeInfo.accessibleBySimpleNameTypeInfoStream().forEach(expressionContext.typeContext::addToContext);

            // add visible fields to variable context
            typeInfo.accessibleFieldsStream().forEach(fieldInfo -> expressionContext.variableContext.add(new FieldReference(fieldInfo,
                    fieldInfo.isStatic() ? null : new This(fieldInfo.owner))));

            doFields(typeInspection, expressionContext, methodFieldSubTypeGraph);
            doMethodsAndConstructors(typeInspection, expressionContext, methodFieldSubTypeGraph);

            // dependencies of the type

            Set<TypeInfo> typeDependencies = typeInfo.typesReferenced().stream().map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));
            List<TypeInfo> allTypesInPrimaryType = typeInfo.allTypesInPrimaryType();
            typeDependencies.retainAll(allTypesInPrimaryType);
            methodFieldSubTypeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));
        } catch (RuntimeException re) {
            LOGGER.warn("Caught exception resolving type {}", typeInfo.fullyQualifiedName);
            throw re;
        }
    }

    private void doFields(TypeInspection typeInspection,
                          ExpressionContext expressionContext,
                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        typeInspection.fields.forEach(fieldInfo -> {

            if (!fieldInfo.fieldInspection.get().initialiserIsSet()) {
                doFieldInitialiser(fieldInfo, expressionContext, methodFieldSubTypeGraph);
            }
        });
    }

    private void doFieldInitialiser(FieldInfo fieldInfo,
                                    ExpressionContext expressionContext,
                                    DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        FieldInspectionImpl.Builder fieldInspection = expressionContext.typeContext.getFieldInspection(fieldInfo);

        Expression expression = fieldInspection.getInitializer();
        FieldInspection.FieldInitialiser fieldInitialiser;
        List<WithInspectionAndAnalysis> dependencies;

        if (expression != FieldInspectionImpl.EMPTY) {
            ExpressionContext subContext = expressionContext.newTypeContext("new field dependencies");

            // fieldInfo.type can have concrete types; but the abstract method will not have them filled in
            MethodTypeParameterMap singleAbstractMethod = fieldInfo.type.findSingleAbstractMethodOfInterface();
            if (singleAbstractMethod != null) {
                singleAbstractMethod = singleAbstractMethod.expand(fieldInfo.type.initialTypeParameterMap());
                log(RESOLVE, "Passing on functional interface method to field initializer of {}: {}", fieldInfo.fullyQualifiedName(), singleAbstractMethod);
            }
            org.e2immu.analyser.model.Expression parsedExpression = subContext.parseExpression(expression, singleAbstractMethod);
            subContext.streamNewlyCreatedTypes().forEach(anonymousType -> doType(anonymousType, subContext.typeContext, methodFieldSubTypeGraph));

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
                        sam = fieldInfo.owner.convertMethodReferenceIntoLambda(fieldInfo.type, fieldInfo.owner,
                                (MethodReference) parsedExpression, expressionContext);
                    } else {
                        throw new UnsupportedOperationException("Cannot (yet) deal with " + parsedExpression.getClass());
                    }
                }
            } else {
                sam = null;
                artificial = false;
            }
            fieldInitialiser = new FieldInspection.FieldInitialiser(parsedExpression, sam, artificial);
            Element toVisit = sam != null ? sam.methodInspection.get().getMethodBody().get() : parsedExpression;
            MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited();
            methodsAndFieldsVisited.visit(toVisit);
            dependencies = ImmutableList.copyOf(methodsAndFieldsVisited.methodsAndFields);
        } else {
            fieldInitialiser = new FieldInspection.FieldInitialiser(EmptyExpression.EMPTY_EXPRESSION, null, false);
            dependencies = List.of();
        }
        methodFieldSubTypeGraph.addNode(fieldInfo, dependencies);
        fieldInspection.setFieldInitializer(fieldInitialiser);
    }

    private void doMethodsAndConstructors(TypeInspection typeInspection, ExpressionContext expressionContext,
                                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // METHOD AND CONSTRUCTOR, without the SAMs in FIELDS
        typeInspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {
            try {
                MethodInspectionImpl.Builder methodInspection = expressionContext.typeContext.getMethodInspection(methodInfo);
                doMethodOrConstructor(methodInfo, methodInspection, expressionContext, methodFieldSubTypeGraph);
                methodInspection.getCompanionMethods().values().forEach(companionMethod -> {
                    MethodInspectionImpl.Builder companionMethodInspection = expressionContext.typeContext.getMethodInspection(companionMethod);
                    doMethodOrConstructor(companionMethod, companionMethodInspection, expressionContext, methodFieldSubTypeGraph);
                });
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving method {}", methodInfo.fullyQualifiedName());
                throw rte;
            }
        });
    }

    private void doMethodOrConstructor(MethodInfo methodInfo,
                                       MethodInspectionImpl.Builder methodInspection,
                                       ExpressionContext expressionContext,
                                       DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // TYPE PARAMETERS OF METHOD

        List<TypeParameter> typeParameters = methodInspection.getTypeParameters();
        ExpressionContext subContext;
        if (typeParameters.isEmpty()) {
            subContext = expressionContext.newTypeContext("new method dependencies");
        } else {
            subContext = expressionContext.newTypeContext("new method dependencies and type parameters of " +
                    methodInfo.name);
            typeParameters.forEach(subContext.typeContext::addToContext);
        }

        // BODY

        boolean doBlock = !methodInspection.methodBody.isSet();
        if (doBlock) {
            BlockStmt block = methodInspection.methodBody.getFirst();
            if (!block.getStatements().isEmpty()) {
                log(RESOLVE, "Parsing block of method {}", methodInfo.name);
                doBlock(subContext, methodInfo, block, methodFieldSubTypeGraph);
            } else {
                methodInspection.methodBody.set(Block.EMPTY_BLOCK);
            }
        }
        MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited();
        methodsAndFieldsVisited.visit(methodInspection.methodBody.get());
        methodFieldSubTypeGraph.addNode(methodInfo, ImmutableList.copyOf(methodsAndFieldsVisited.methodsAndFields));
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

    private void doBlock(ExpressionContext expressionContext, MethodInfo methodInfo,
                         MethodInspectionImpl.Builder methodInspection,
                         BlockStmt block,
                         DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        try {
            ExpressionContext newContext = expressionContext.newVariableContext("resolving " + methodInfo.fullyQualifiedName());
            methodInspection.getParameters().forEach(newContext.variableContext::add);
            log(RESOLVE, "Parsing block with variable context {}", newContext.variableContext);
            Block parsedBlock = newContext.parseBlockOrStatement(block);
            methodInspection.methodBody.set(parsedBlock);

            newContext.streamNewlyCreatedTypes().forEach(anonymousType -> doType(anonymousType, newContext.typeContext, methodFieldSubTypeGraph));

        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while resolving block starting at line {}", block.getBegin().orElse(null));
            throw rte;
        }
    }


    private static void fillInternalMethodCalls(DependencyGraph<WithInspectionAndAnalysis> methodGraph) {
        methodGraph.visit((from, toList) -> {
            try {
                if (from instanceof MethodInfo methodInfo) {
                    Set<WithInspectionAndAnalysis> dependencies = methodGraph.dependenciesOnlyTerminals(from);
                    Set<MethodInfo> methodsReached = dependencies.stream().filter(w -> w instanceof MethodInfo).map(w -> (MethodInfo) w).collect(Collectors.toSet());

                    MethodResolution methodResolution = new MethodResolution();
                    methodResolution.methodsOfOwnClassReached.set(methodsReached);
                    methodInfo.methodResolution.set(methodResolution);

                    methodCreatesObjectOfSelf(methodInfo, methodResolution);
                    computeStaticMethodCallsOnly(methodInfo, methodResolution);

                }
            } catch (RuntimeException e) {
                LOGGER.error("Caught runtime exception while filling {} to {} ", from.fullyQualifiedName(), toList);
                throw e;
            }
        });
        methodGraph.visit((from, toList) -> {
            if (from instanceof MethodInfo methodInfo) {
                methodInfo.methodResolution.get().setCallStatus(methodInfo);
            }
        });
    }


    // part of @UtilityClass computation in the type analyser
    private static void methodCreatesObjectOfSelf(MethodInfo methodInfo, MethodInspection methodInspection, MethodResolution methodResolution) {
        AtomicBoolean createSelf = new AtomicBoolean();
        methodInspection.methodBody.get().visit(element -> {
            if (element instanceof NewObject newObject) {
                if (newObject.parameterizedType.typeInfo == methodInfo.typeInfo) {
                    createSelf.set(true);
                }
            }
        });
        methodResolution.createObjectOfSelf.set(createSelf.get());
    }

    private static void computeStaticMethodCallsOnly(@NotModified MethodInfo methodInfo, MethodInspection methodInspection, MethodResolution methodResolution) {
        if (!methodResolution.staticMethodCallsOnly.isSet()) {
            if (methodInfo.isStatic) {
                methodResolution.staticMethodCallsOnly.set(true);
            } else {
                AtomicBoolean atLeastOneCallOnThis = new AtomicBoolean(false);
                Block block = methodInspection.methodBody.get();
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

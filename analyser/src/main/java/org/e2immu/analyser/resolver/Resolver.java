/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.resolver;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
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
    private final boolean shallowResolver;

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    public Resolver(boolean shallowResolver) {
        this.shallowResolver = shallowResolver;
    }

    /**
     * Responsible for resolving, circular dependency detection.
     *
     * @param inspectedTypes when a subResolver, the map contains only one type, and it will not be a primary type.
     *                       When not a subResolver, it only contains primary types.
     * @return A list of sorted primary types, each with their sub-elements (sub-types, fields, methods) sorted.
     */

    public List<SortedType> sortTypes(InspectionProvider inspectionProvider, Map<TypeInfo, TypeContext> inspectedTypes) {
        DependencyGraph<TypeInfo> typeGraph = new DependencyGraph<>();
        Map<TypeInfo, SortedType> toSortedType = new HashMap<>();
        Set<TypeInfo> stayWithin = inspectedTypes.keySet().stream()
                .flatMap(typeInfo -> typeAndAllSubTypes(inspectionProvider, typeInfo).stream())
                .collect(Collectors.toUnmodifiableSet());

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
        List<SortedType> result = sortWarnForCircularDependencies(inspectionProvider, typeGraph).stream().map(toSortedType::get).collect(Collectors.toList());
        log(RESOLVE, "Result of type sorting: {}", result);
        return result;
    }

    private static List<TypeInfo> typeAndAllSubTypes(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        List<TypeInfo> result = new ArrayList<>();
        recursivelyCollectSubTypes(inspectionProvider, typeInfo, result);
        return ImmutableList.copyOf(result);
    }

    private static void recursivelyCollectSubTypes(InspectionProvider inspectionProvider, TypeInfo typeInfo, List<TypeInfo> result) {
        result.add(typeInfo);
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        for (TypeInfo sub : typeInspection.subTypes()) {
            recursivelyCollectSubTypes(inspectionProvider, sub, result);
        }
    }

    private List<TypeInfo> sortWarnForCircularDependencies(InspectionProvider inspectionProvider, DependencyGraph<TypeInfo> typeGraph) {
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
            TypeResolution typeResolution = new TypeResolution(circularDependencies == null ? Set.of() : circularDependencies,
                    superTypesExcludingJavaLangObject(inspectionProvider, typeInfo));
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
        List<TypeInfo> typeAndAllSubTypes = doType(typeInfo, typeContextOfType, methodFieldSubTypeGraph);

        // FROM HERE ON, ALL INSPECTION HAS BEEN SET!

        methodResolution(typeContextOfFile, methodFieldSubTypeGraph);

        // NOW, ALL METHODS IN THIS PRIMARY TYPE HAVE METHOD RESOLUTION SET

        // remove myself and all my enclosing types, and stay within the set of inspectedTypes
        Set<TypeInfo> typeDependencies = shallowResolver ?
                new HashSet<>(superTypesExcludingJavaLangObject(typeContextOfFile, typeInfo)) :
                typeInfo.typesReferenced().stream().map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));

        typeDependencies.removeAll(typeAndAllSubTypes);
        typeDependencies.remove(typeInfo);
        typeDependencies.retainAll(stayWithin);

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

    private List<TypeInfo> doType(TypeInfo typeInfo, TypeContext typeContextOfType,
                                  DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        try {
            TypeInspection typeInspection = typeContextOfType.getTypeInspection(typeInfo);
            if (typeInspection.getInspectionState().le(TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION)) {
                // no need to inspect this method, we'll never use it
                return List.of(typeInfo);
            }
            typeInspection.subTypes().forEach(typeContextOfType::addToContext);

            // recursion, do sub-types first
            typeInspection.subTypes().forEach(subType -> {
                log(RESOLVE, "From {} into {}", typeInfo.fullyQualifiedName, subType.fullyQualifiedName);
                doType(subType, typeContextOfType, methodFieldSubTypeGraph);
            });

            log(RESOLVE, "Resolving type {}", typeInfo.fullyQualifiedName);
            TypeInfo primaryType = typeInfo.primaryType();
            ExpressionContext expressionContext = ExpressionContext.forBodyParsing(typeInfo,
                    primaryType, typeContextOfType);
            TypeContext typeContext = expressionContext.typeContext;
            typeContext.addToContext(typeInfo);
            typeInspection.typeParameters().forEach(typeContext::addToContext);

            // add visible types to the type context
            accessibleBySimpleNameTypeInfoStream(typeContext, typeInfo, primaryType).forEach(typeContext::addToContext);

            // add visible fields to variable context
            accessibleFieldsStream(typeContext, typeInfo, primaryType)
                    .forEach(fieldInfo -> expressionContext.variableContext.add(new FieldReference(
                            typeContext,
                            fieldInfo,
                            fieldInfo.isStatic() ? null : new This(typeContext, fieldInfo.owner))));

            doFields(typeInspection, expressionContext, methodFieldSubTypeGraph);
            doMethodsAndConstructors(typeInspection, expressionContext, methodFieldSubTypeGraph);

            // dependencies of the type

            Set<TypeInfo> typeDependencies = typeInspection.typesReferenced().stream()
                    .map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));
            List<TypeInfo> typeAndAllSubTypes = typeAndAllSubTypes(typeContextOfType, typeInfo);
            typeDependencies.retainAll(typeAndAllSubTypes);
            methodFieldSubTypeGraph.addNode(typeInfo, ImmutableList.copyOf(typeDependencies));
            return typeAndAllSubTypes;
        } catch (RuntimeException re) {
            LOGGER.warn("Caught exception resolving type {}", typeInfo.fullyQualifiedName);
            throw re;
        }
    }

    private void doFields(TypeInspection typeInspection,
                          ExpressionContext expressionContext,
                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        typeInspection.fields().forEach(fieldInfo -> {
            FieldInspectionImpl.Builder fieldInspection = (FieldInspectionImpl.Builder)
                    expressionContext.typeContext.getFieldInspection(fieldInfo);
            if (!fieldInspection.fieldInitialiserIsSet() && fieldInspection.getInitialiserExpression() != null) {
                doFieldInitialiser(fieldInfo, fieldInspection, expressionContext, methodFieldSubTypeGraph);
            }
            assert !fieldInfo.fieldInspection.isSet() : "Field inspection for " + fieldInfo.fullyQualifiedName() + " has already been set";
            fieldInfo.fieldInspection.set(fieldInspection.build());
            log(RESOLVE, "Set field inspection of " + fieldInfo.fullyQualifiedName());
        });
    }

    private void doFieldInitialiser(FieldInfo fieldInfo,
                                    FieldInspectionImpl.Builder fieldInspectionBuilder,
                                    ExpressionContext expressionContext,
                                    DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // we can cast here: no point in resolving if inspection has been set.

        Expression expression = fieldInspectionBuilder.getInitialiserExpression();
        FieldInspection.FieldInitialiser fieldInitialiser;
        List<WithInspectionAndAnalysis> dependencies;

        if (expression != FieldInspectionImpl.EMPTY) {
            ExpressionContext subContext = expressionContext.newTypeContext("new field dependencies");

            // fieldInfo.type can have concrete types; but the abstract method will not have them filled in
            MethodTypeParameterMap singleAbstractMethod = fieldInfo.type.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            if (singleAbstractMethod != null) {
                singleAbstractMethod = singleAbstractMethod.expand(fieldInfo.type.initialTypeParameterMap(expressionContext.typeContext));
                log(RESOLVE, "Passing on functional interface method to field initializer of {}: {}", fieldInfo.name, singleAbstractMethod);
            }
            org.e2immu.analyser.model.Expression parsedExpression = subContext.parseExpression(expression, singleAbstractMethod);
            subContext.streamNewlyCreatedTypes().forEach(anonymousType -> doType(anonymousType, subContext.typeContext, methodFieldSubTypeGraph));

            MethodInfo sam;
            boolean artificial;
            if (fieldInfo.type.isFunctionalInterface(subContext.typeContext)) {
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
            Element toVisit = sam != null ? sam.methodInspection.get().getMethodBody() : parsedExpression;
            MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited();
            methodsAndFieldsVisited.visit(toVisit);
            dependencies = ImmutableList.copyOf(methodsAndFieldsVisited.methodsAndFields);
        } else {
            fieldInitialiser = new FieldInspection.FieldInitialiser(EmptyExpression.EMPTY_EXPRESSION, null, false);
            dependencies = List.of();
        }
        methodFieldSubTypeGraph.addNode(fieldInfo, dependencies);
        fieldInspectionBuilder.setFieldInitializer(fieldInitialiser);
    }

    private void doMethodsAndConstructors(TypeInspection typeInspection,
                                          ExpressionContext expressionContext,
                                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // METHOD AND CONSTRUCTOR, without the SAMs in FIELDS
        typeInspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {

            MethodInspection methodInspection = expressionContext.typeContext.getMethodInspection(methodInfo);
            assert methodInspection != null :
                    "Method inspection for " + methodInfo.name + " in " + methodInfo.typeInfo.fullyQualifiedName + " not found";
            boolean haveCompanionMethods = !methodInspection.getCompanionMethods().isEmpty();
            if (haveCompanionMethods) {
                log(RESOLVE, "Start resolving companion methods of {}", methodInspection.getDistinguishingName());

                methodInspection.getCompanionMethods().values().forEach(companionMethod -> {
                    MethodInspection companionMethodInspection = expressionContext.typeContext.getMethodInspection(companionMethod);
                    try {
                        doMethodOrConstructor(companionMethod, (MethodInspectionImpl.Builder)
                                companionMethodInspection, expressionContext, methodFieldSubTypeGraph);
                    } catch (RuntimeException rte) {
                        LOGGER.warn("Caught runtime exception while resolving companion method {} in {}", companionMethod.name,
                                methodInfo.typeInfo.fullyQualifiedName);
                        throw rte;
                    }
                });

                log(RESOLVE, "Finished resolving companion methods of {}", methodInspection.getDistinguishingName());
            }
            try {
                doMethodOrConstructor(methodInfo, (MethodInspectionImpl.Builder) methodInspection,
                        expressionContext, methodFieldSubTypeGraph);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving method {} in {}", methodInfo.name,
                        methodInfo.typeInfo.fullyQualifiedName);
                throw rte;
            }
        });
    }

    private void doMethodOrConstructor(MethodInfo methodInfo,
                                       MethodInspectionImpl.Builder methodInspection,
                                       ExpressionContext expressionContext,
                                       DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        log(RESOLVE, "Resolving {}", methodInfo.fullyQualifiedName);

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

        boolean doBlock = !methodInspection.inspectedBlockIsSet();
        if (doBlock) {
            BlockStmt block = methodInspection.getBlock();
            if (block != null && !block.getStatements().isEmpty()) {
                log(RESOLVE, "Parsing block of method {}", methodInfo.name);
                doBlock(subContext, methodInfo, methodInspection, block, methodFieldSubTypeGraph);
            } else {
                methodInspection.setInspectedBlock(Block.EMPTY_BLOCK);
            }
        }
        MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited();
        methodsAndFieldsVisited.visit(methodInspection.getMethodBody());

        // finally, we build the method inspection and set it in the methodInfo object
        methodInspection.build(expressionContext.typeContext);

        // and only then, when the FQN is known, add to the sub-graph
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
            methodInspection.setInspectedBlock(parsedBlock);

            newContext.streamNewlyCreatedTypes().forEach(anonymousType -> doType(anonymousType, newContext.typeContext, methodFieldSubTypeGraph));

        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while resolving block starting at line {}", block.getBegin().orElse(null));
            throw rte;
        }
    }


    private static void methodResolution(InspectionProvider inspectionProvider, DependencyGraph<WithInspectionAndAnalysis> methodGraph) {
        // iterate twice, because we partial results on all MethodInfo objects for the setCallStatus computation
        Map<MethodInfo, MethodResolution.Builder> builders = new HashMap<>();
        methodGraph.visit((from, toList) -> {
            try {
                if (from instanceof MethodInfo methodInfo) {
                    Set<WithInspectionAndAnalysis> dependencies = methodGraph.dependenciesOnlyTerminals(from);
                    Set<MethodInfo> methodsReached = dependencies.stream().filter(w -> w instanceof MethodInfo).map(w -> (MethodInfo) w).collect(Collectors.toSet());

                    MethodResolution.Builder methodResolutionBuilder = new MethodResolution.Builder();
                    methodResolutionBuilder.methodsOfOwnClassReached.set(methodsReached);

                    methodCreatesObjectOfSelf(methodInfo, methodResolutionBuilder);
                    computeStaticMethodCallsOnly(inspectionProvider, methodInfo, methodResolutionBuilder);
                    methodResolutionBuilder.overrides.set(ShallowMethodResolver.overrides(inspectionProvider, methodInfo));

                    builders.put(methodInfo, methodResolutionBuilder);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Caught runtime exception while filling {} to {} ", from.fullyQualifiedName(), toList);
                throw e;
            }
        });
        methodGraph.visit((from, toList) -> {
            if (from instanceof MethodInfo methodInfo) {
                MethodResolution.Builder builder = builders.get(methodInfo);
                builder.partOfConstruction.set(computeCallStatus(inspectionProvider, builders, methodInfo));

                methodInfo.methodResolution.set(builder.build());
            }
        });
    }


    // part of @UtilityClass computation in the type analyser
    private static void methodCreatesObjectOfSelf(MethodInfo methodInfo, MethodResolution.Builder methodResolution) {
        AtomicBoolean createSelf = new AtomicBoolean();
        methodInfo.methodInspection.get().getMethodBody().visit(element -> {
            if (element instanceof NewObject newObject) {
                if (newObject.parameterizedType.typeInfo == methodInfo.typeInfo) {
                    createSelf.set(true);
                }
            }
        });
        methodResolution.createObjectOfSelf.set(createSelf.get());
    }

    private static void computeStaticMethodCallsOnly(InspectionProvider inspectionProvider,
                                                     @NotModified MethodInfo methodInfo,
                                                     MethodResolution.Builder methodResolution) {
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        if (!methodResolution.staticMethodCallsOnly.isSet()) {
            if (methodInspection.isStatic()) {
                methodResolution.staticMethodCallsOnly.set(true);
            } else {
                AtomicBoolean atLeastOneCallOnThis = new AtomicBoolean(false);
                Block block = methodInspection.getMethodBody();
                block.visit(element -> {
                    if (element instanceof MethodCall methodCall) {
                        MethodInspection callInspection = inspectionProvider.getMethodInspection(methodCall.methodInfo);
                        boolean callOnThis = !callInspection.isStatic() &&
                                methodCall.object == null || ((methodCall.object instanceof This) &&
                                ((This) methodCall.object).typeInfo == methodInfo.typeInfo);
                        if (callOnThis) atLeastOneCallOnThis.set(true);
                    }
                });
                boolean staticMethodCallsOnly = !atLeastOneCallOnThis.get();
                log(STATIC_METHOD_CALLS, "Method {} is not static, does it have no calls on <this> scope? {}",
                        methodInfo.fullyQualifiedName(), staticMethodCallsOnly);
                methodResolution.staticMethodCallsOnly.set(staticMethodCallsOnly);
            }
        }
    }

    /**
     * Note that this computation has to contain transitive calls.
     *
     * @return true if there is a non-private method in this class which calls this private method.
     */
    private static boolean isCalledFromNonPrivateMethod(
            InspectionProvider inspectionProvider,
            Map<MethodInfo, MethodResolution.Builder> builders,
            MethodInfo methodInfo) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        for (MethodInfo other : typeInspection.methods()) {
            if (!other.isPrivate() && builders.get(other).methodsOfOwnClassReached.get().contains(methodInfo)) {
                return true;
            }
        }
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            if (!fieldInfo.isPrivate() && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                if (fieldInitialiser.implementationOfSingleAbstractMethod() != null &&
                        builders.get(fieldInitialiser.implementationOfSingleAbstractMethod()).methodsOfOwnClassReached.get().contains(methodInfo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCalledFromConstructors(
            InspectionProvider inspectionProvider,
            Map<MethodInfo, MethodResolution.Builder> builders,
            MethodInfo methodInfo) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        for (MethodInfo other : typeInspection.constructors()) {
            if (builders.get(other).methodsOfOwnClassReached.get().contains(methodInfo)) {
                return true;
            }
        }
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            if (fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                if (fieldInitialiser.implementationOfSingleAbstractMethod() == null) {
                    // return true when the method is part of the expression
                    AtomicBoolean found = new AtomicBoolean();
                    fieldInitialiser.initialiser().visit(elt -> {
                        if (elt instanceof MethodCall methodCall) {
                            if (methodCall.methodInfo == methodInfo ||
                                    builders.get(methodCall.methodInfo).methodsOfOwnClassReached.get().contains(methodInfo)) {
                                found.set(true);
                            }
                        }
                    });
                    return found.get();
                }
            }
        }
        return false;
    }


    private static MethodResolution.CallStatus computeCallStatus(InspectionProvider inspectionProvider,
                                                                 Map<MethodInfo, MethodResolution.Builder> builders,
                                                                 MethodInfo methodInfo) {
        if (methodInfo.isConstructor) {
            return MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        }
        if (!methodInfo.isPrivate()) {
            return MethodResolution.CallStatus.NON_PRIVATE;
        }
        if (isCalledFromNonPrivateMethod(inspectionProvider, builders, methodInfo)) {
            return MethodResolution.CallStatus.CALLED_FROM_NON_PRIVATE_METHOD;
        }
        if (isCalledFromConstructors(inspectionProvider, builders, methodInfo)) {
            return MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        }
        return MethodResolution.CallStatus.NOT_CALLED_AT_ALL;
    }

    public static Set<TypeInfo> superTypesExcludingJavaLangObject(InspectionProvider inspectionProvider, TypeInfo typeInfo) {
        if (Primitives.isJavaLangObject(typeInfo)) return Set.of();
        List<TypeInfo> list = new ArrayList<>();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        if (typeInspection.parentClass() != null && typeInspection.parentClass().typeInfo != null) {
            TypeInfo parent = Objects.requireNonNull(typeInspection.parentClass().typeInfo);
            list.add(parent);
            list.addAll(superTypesExcludingJavaLangObject(inspectionProvider, parent));
        } // else: silently ignore, we may be going out of bounds

        typeInspection.interfacesImplemented().forEach(i -> {
            list.add(i.typeInfo);
            assert i.typeInfo != null;
            list.addAll(superTypesExcludingJavaLangObject(inspectionProvider, i.typeInfo));
        });
        return ImmutableSet.copyOf(list);
    }


    public static Stream<TypeInfo> accessibleBySimpleNameTypeInfoStream(InspectionProvider inspectionProvider,
                                                                        TypeInfo typeInfo,
                                                                        TypeInfo primaryType) {
        return accessibleBySimpleNameTypeInfoStream(inspectionProvider, typeInfo, typeInfo, primaryType.packageName(), new HashSet<>());
    }

    private static Stream<TypeInfo> accessibleBySimpleNameTypeInfoStream(InspectionProvider inspectionProvider,
                                                                         TypeInfo typeInfo,
                                                                         TypeInfo startingPoint,
                                                                         String startingPointPackageName,
                                                                         Set<TypeInfo> visited) {
        if (visited.contains(typeInfo)) return Stream.empty();
        visited.add(typeInfo);
        Stream<TypeInfo> mySelf = Stream.of(typeInfo);

        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        TypeInfo primaryType = typeInfo.primaryType();
        boolean inSameCompilationUnit = typeInfo == startingPoint ||
                primaryType == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit && primaryType.packageNameOrEnclosingType.getLeft().equals(startingPointPackageName);

        Stream<TypeInfo> localStream = typeInspection.subTypes().stream()
                .filter(ti -> acceptSubType(inspectionProvider, ti, inSameCompilationUnit, inSamePackage));
        Stream<TypeInfo> parentStream;
        boolean isJLO = Primitives.isJavaLangObject(typeInfo);
        if (!isJLO) {
            assert typeInspection.parentClass() != null && typeInspection.parentClass().typeInfo != null;
            parentStream = accessibleBySimpleNameTypeInfoStream(inspectionProvider,
                    typeInspection.parentClass().typeInfo, startingPoint, startingPointPackageName, visited);
        } else parentStream = Stream.empty();

        Stream<TypeInfo> joint = Stream.concat(Stream.concat(mySelf, localStream), parentStream);
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented()) {
            assert interfaceType.typeInfo != null;
            Stream<TypeInfo> fromInterface = accessibleBySimpleNameTypeInfoStream(inspectionProvider,
                    interfaceType.typeInfo,
                    startingPoint, startingPointPackageName, visited);
            joint = Stream.concat(joint, fromInterface);
        }
        return joint;
    }

    private static boolean acceptSubType(InspectionProvider inspectionProvider, TypeInfo typeInfo,
                                         boolean inSameCompilationUnit, boolean inSamePackage) {
        if (inSameCompilationUnit) return true;
        TypeInspection inspection = inspectionProvider.getTypeInspection(typeInfo);
        return inspection.access() == TypeModifier.PUBLIC ||
                inSamePackage && inspection.access() == TypeModifier.PACKAGE ||
                !inSamePackage && inspection.access() == TypeModifier.PROTECTED;
    }


    public static Stream<FieldInfo> accessibleFieldsStream(InspectionProvider inspectionProvider, TypeInfo typeInfo, TypeInfo primaryType) {
        return accessibleFieldsStream(inspectionProvider, typeInfo, typeInfo, primaryType.packageName());
    }

    private static Stream<FieldInfo> accessibleFieldsStream(InspectionProvider inspectionProvider,
                                                            TypeInfo typeInfo,
                                                            TypeInfo startingPoint,
                                                            String startingPointPackageName) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        TypeInfo primaryType = typeInfo.primaryType();

        boolean inSameCompilationUnit = typeInfo == startingPoint || primaryType == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit && primaryType.packageName().equals(startingPointPackageName);

        // my own field
        Stream<FieldInfo> localStream = typeInspection.fields().stream()
                .filter(fieldInfo -> acceptField(inspectionProvider, fieldInfo, inSameCompilationUnit, inSamePackage));

        // my enclosing type's fields
        Stream<FieldInfo> enclosingStream;
        if (typeInfo.packageNameOrEnclosingType.isRight()) {
            enclosingStream = accessibleFieldsStream(inspectionProvider,
                    typeInfo.packageNameOrEnclosingType.getRight(), startingPoint, startingPointPackageName);
        } else {
            enclosingStream = Stream.empty();
        }
        Stream<FieldInfo> joint = Stream.concat(localStream, enclosingStream);

        // my parent's fields
        Stream<FieldInfo> parentStream;
        boolean isJLO = Primitives.isJavaLangObject(typeInfo);
        if (!isJLO) {
            assert typeInspection.parentClass() != null && typeInspection.parentClass().typeInfo != null;
            parentStream = accessibleFieldsStream(inspectionProvider, typeInspection.parentClass().typeInfo,
                    startingPoint, startingPointPackageName);
        } else parentStream = Stream.empty();
        joint = Stream.concat(joint, parentStream);

        // my interfaces' fields
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented()) {
            assert interfaceType.typeInfo != null;
            Stream<FieldInfo> fromInterface = accessibleFieldsStream(inspectionProvider, interfaceType.typeInfo,
                    startingPoint, startingPointPackageName);
            joint = Stream.concat(joint, fromInterface);
        }

        return joint;
    }

    private static boolean acceptField(InspectionProvider inspectionProvider, FieldInfo fieldInfo,
                                       boolean inSameCompilationUnit, boolean inSamePackage) {
        if (inSameCompilationUnit) return true;
        FieldInspection inspection = inspectionProvider.getFieldInspection(fieldInfo);
        return inspection.getAccess() == FieldModifier.PUBLIC ||
                inSamePackage && inspection.getAccess() == FieldModifier.PACKAGE ||
                !inSamePackage && inspection.getAccess() == FieldModifier.PROTECTED;
    }
}

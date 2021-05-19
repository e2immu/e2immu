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

package org.e2immu.analyser.resolver;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.util.DependencyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.Generated;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.util.ConvertMethodReference.convertMethodReferenceIntoAnonymous;
import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVER;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

/*
The Resolver is recursive with respect to types defined in statements: anonymous types (new XXX() { }),
lambdas, and classes defined in methods.
These result in a "new" SortedType object that is stored in the local type's TypeResolution object.

Sub-types defined in the primary type go along with methods and fields.
 */
public class Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    private final Messages messages = new Messages();
    private final boolean shallowResolver;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final InspectionProvider inspectionProvider;
    private final Resolver parent;
    private final AnonymousTypeCounters anonymousTypeCounters;

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    public Resolver(Resolver parent,
                    InspectionProvider inspectionProvider,
                    E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                    boolean shallowResolver) {
        this.shallowResolver = shallowResolver;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inspectionProvider = inspectionProvider;
        this.parent = parent;
        this.anonymousTypeCounters = parent.anonymousTypeCounters;
    }

    public Resolver(AnonymousTypeCounters anonymousTypeCounters,
                    InspectionProvider inspectionProvider,
                    E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                    boolean shallowResolver) {
        this.shallowResolver = shallowResolver;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inspectionProvider = inspectionProvider;
        this.parent = null;
        this.anonymousTypeCounters = anonymousTypeCounters;
    }

    /**
     * Responsible for resolving, circular dependency detection.
     *
     * @param inspectedTypes when a subResolver, the map contains only one type, and it will not be a primary type.
     *                       When not a subResolver, it only contains primary types.
     * @return A list of sorted primary types, each with their sub-elements (sub-types, fields, methods) sorted.
     */

    public List<SortedType> resolve(Map<TypeInfo, ExpressionContext> inspectedTypes) {
        DependencyGraph<TypeInfo> typeGraph = new DependencyGraph<>();
        Map<TypeInfo, TypeResolution.Builder> resolutionBuilders = new HashMap<>();
        Set<TypeInfo> stayWithin = inspectedTypes.keySet().stream()
                .flatMap(typeInfo -> typeAndAllSubTypes(typeInfo).stream())
                .collect(Collectors.toUnmodifiableSet());

        for (Map.Entry<TypeInfo, ExpressionContext> entry : inspectedTypes.entrySet()) {
            try {
                TypeInfo typeInfo = entry.getKey();
                ExpressionContext expressionContext = entry.getValue();

                if (parent == null) {
                    assert typeInfo.isPrimaryType() : "Not a primary type: " + typeInfo.fullyQualifiedName;
                } else {
                    assert !typeInfo.isPrimaryType() :
                            "?? in recursive situation we do not expect a primary type" + typeInfo.fullyQualifiedName;
                }
                SortedType sortedType = addToTypeGraph(typeGraph, stayWithin, typeInfo, expressionContext);
                resolutionBuilders.put(typeInfo, new TypeResolution.Builder().setSortedType(sortedType));
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving type {}", entry.getKey().fullyQualifiedName);
                throw rte;
            }
        }
        List<TypeInfo> sorted = sortWarnForCircularDependencies(typeGraph, resolutionBuilders);
        return computeTypeResolution(sorted, resolutionBuilders);
    }

    private List<TypeInfo> typeAndAllSubTypes(TypeInfo typeInfo) {
        List<TypeInfo> result = new ArrayList<>();
        recursivelyCollectSubTypes(typeInfo, result);
        return List.copyOf(result);
    }

    private void recursivelyCollectSubTypes(TypeInfo typeInfo, List<TypeInfo> result) {
        result.add(typeInfo);
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        for (TypeInfo sub : typeInspection.subTypes()) {
            recursivelyCollectSubTypes(sub, result);
        }
    }

    private List<TypeInfo> sortWarnForCircularDependencies(DependencyGraph<TypeInfo> typeGraph,
                                                           Map<TypeInfo, TypeResolution.Builder> resolutionBuilders) {
        List<TypeInfo> sorted = typeGraph.sorted(typeInfo -> {
            // typeInfo is part of a cycle, dependencies are:
            Set<TypeInfo> typesInCycle = typeGraph.dependencies(typeInfo);
            log(RESOLVER, "Type {} is part of cycle: {}", typeInfo,
                    () -> typesInCycle.stream().map(t -> t.simpleName).collect(Collectors.joining(",")));
            for (TypeInfo other : typesInCycle) {
                TypeResolution.Builder otherBuilder = resolutionBuilders.get(other);
                otherBuilder.addCircularDependencies(typesInCycle);
            }
            messages.add(Message.newMessage(new Location(typeInfo), Message.Label.CIRCULAR_TYPE_DEPENDENCY,
                    typesInCycle.stream().map(t -> t.fullyQualifiedName).collect(Collectors.joining(", "))));
        }, Comparator.comparing(typeInfo -> typeInfo.fullyQualifiedName));
        log(RESOLVER, "Sorted types: {}", sorted);
        return sorted;
    }

    private List<SortedType> computeTypeResolution(
            List<TypeInfo> sorted,
            Map<TypeInfo, TypeResolution.Builder> resolutionBuilders) {
        /*
        The code that computes supertypes and counts implementations runs over all known types and sub-types,
        out of the standard sorting order, exactly because of circular dependencies.
         */
        Map<TypeInfo, TypeResolution.Builder> allBuilders = new HashMap<>(resolutionBuilders);
        resolutionBuilders.forEach((typeInfo, builder) ->
                addSubtypeResolutionBuilders(inspectionProvider, typeInfo, builder, allBuilders));

        allBuilders.forEach((typeInfo, builder) -> computeSuperTypes(inspectionProvider, typeInfo, builder, allBuilders));
        allBuilders.forEach((typeInfo, builder) -> typeInfo.typeResolution.set(builder.build()));

        return sorted.stream().map(typeInfo -> typeInfo.typeResolution.get().sortedType).toList();
    }

    private void addSubtypeResolutionBuilders(InspectionProvider inspectionProvider,
                                              TypeInfo typeInfo,
                                              TypeResolution.Builder resolutionBuilder,
                                              Map<TypeInfo, TypeResolution.Builder> allBuilders) {
        Set<TypeInfo> circularDependencies = resolutionBuilder.getCircularDependencies();
        for (TypeInfo subType : inspectionProvider.getTypeInspection(typeInfo).subTypes()) {
            // IMPROVE circularDependencies is at the level of primary types, can be better
            TypeResolution.Builder builder = new TypeResolution.Builder().setCircularDependencies(circularDependencies);
            allBuilders.put(subType, builder);
        }
    }

    private static void computeSuperTypes(InspectionProvider inspectionProvider,
                                          TypeInfo typeInfo,
                                          TypeResolution.Builder builder,
                                          Map<TypeInfo, TypeResolution.Builder> allBuilders) {
        Set<TypeInfo> superTypes = superTypesExcludingJavaLangObject(inspectionProvider, typeInfo, allBuilders);
        builder.setSuperTypesExcludingJavaLangObject(superTypes);
    }

    private SortedType addToTypeGraph(DependencyGraph<TypeInfo> typeGraph,
                                      Set<TypeInfo> stayWithin,
                                      TypeInfo typeInfo,
                                      ExpressionContext expressionContextOfFile) {

        // main call
        ExpressionContext expressionContextOfType = expressionContextOfFile.newTypeContext("Primary type");
        DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph = new DependencyGraph<>();
        List<TypeInfo> typeAndAllSubTypes = doType(typeInfo, expressionContextOfType, methodFieldSubTypeGraph);

        // FROM HERE ON, ALL INSPECTION HAS BEEN SET!

        methodResolution(methodFieldSubTypeGraph);

        // NOW, ALL METHODS IN THIS PRIMARY TYPE HAVE METHOD RESOLUTION SET

        // remove myself and all my enclosing types, and stay within the set of inspectedTypes
        // only add primary types!
        Set<TypeInfo> typeDependencies = shallowResolver ?
                new HashSet<>(superTypesExcludingJavaLangObject(expressionContextOfType.typeContext, typeInfo, null)
                        .stream().map(TypeInfo::primaryType).toList()) :
                typeInfo.typesReferenced().stream().map(Map.Entry::getKey)
                        .map(TypeInfo::primaryType)
                        .collect(Collectors.toCollection(HashSet::new));

        typeAndAllSubTypes.forEach(typeDependencies::remove);
        typeDependencies.remove(typeInfo);
        typeDependencies.retainAll(stayWithin);

        typeGraph.addNode(typeInfo, List.copyOf(typeDependencies));
        List<WithInspectionAndAnalysis> methodFieldSubTypeOrder = List.copyOf(methodFieldSubTypeGraph.sorted(x->{},
                Comparator.comparing(WithInspectionAndAnalysis::fullyQualifiedName)));

        if (isLogEnabled(RESOLVER)) {
            log(RESOLVER, "Method graph has {} relations", methodFieldSubTypeGraph.relations());
            log(RESOLVER, "Method and field order in {}: {}", typeInfo.fullyQualifiedName,
                    methodFieldSubTypeOrder.stream().map(WithInspectionAndAnalysis::name).collect(Collectors.joining(", ")));
            log(RESOLVER, "Types referred to in {}: {}", typeInfo.fullyQualifiedName, typeDependencies);
        }

        return new SortedType(typeInfo, methodFieldSubTypeOrder);
    }

    private List<TypeInfo> doType(TypeInfo typeInfo,
                                  ExpressionContext expressionContextOfType,
                                  DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        try {
            TypeInspection typeInspection = expressionContextOfType.typeContext.getTypeInspection(typeInfo);
            if (typeInspection.getInspectionState().le(TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION)) {
                // no need to inspect this method, we'll never use it
                return List.of(typeInfo);
            }
            typeInspection.subTypes().forEach(expressionContextOfType.typeContext::addToContext);

            // recursion, do sub-types first (no recursion at resolver level!)
            typeInspection.subTypes().forEach(subType -> {
                log(RESOLVER, "From {} into {}", typeInfo.fullyQualifiedName, subType.fullyQualifiedName);
                doType(subType, expressionContextOfType, methodFieldSubTypeGraph);
            });

            log(RESOLVER, "Resolving type {}", typeInfo.fullyQualifiedName);
            TypeInfo primaryType = typeInfo.primaryType();
            ExpressionContext expressionContextForBody = ExpressionContext.forTypeBodyParsing(typeInfo, primaryType, expressionContextOfType);
            TypeContext typeContext = expressionContextForBody.typeContext;
            typeContext.addToContext(typeInfo);
            typeInspection.typeParameters().forEach(typeContext::addToContext);

            // add visible types to the type context
            accessibleBySimpleNameTypeInfoStream(typeContext, typeInfo, primaryType).forEach(typeContext::addToContext);

            // add visible fields to variable context
            accessibleFieldsStream(typeContext, typeInfo, primaryType)
                    .forEach(fieldInfo -> expressionContextForBody.variableContext.add(new FieldReference(
                            typeContext,
                            fieldInfo,
                            fieldInfo.isStatic() ? null : new This(typeContext, fieldInfo.owner))));

            List<TypeInfo> typeAndAllSubTypes = typeAndAllSubTypes(typeInfo);
            Set<TypeInfo> restrictToType = new HashSet<>(typeAndAllSubTypes);

            doFields(typeInspection, expressionContextForBody, methodFieldSubTypeGraph, restrictToType);
            doMethodsAndConstructors(typeInspection, expressionContextForBody, methodFieldSubTypeGraph, restrictToType);

            // dependencies of the type

            Set<TypeInfo> typeDependencies = typeInspection.typesReferenced().stream()
                    .map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));
            typeDependencies.retainAll(restrictToType);
            methodFieldSubTypeGraph.addNode(typeInfo, List.copyOf(typeDependencies));
            return typeAndAllSubTypes;
        } catch (RuntimeException re) {
            LOGGER.warn("Caught exception resolving type {}", typeInfo.fullyQualifiedName);
            throw re;
        }
    }

    private void doFields(TypeInspection typeInspection,
                          ExpressionContext expressionContext,
                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph,
                          Set<TypeInfo> restrictToType) {
        typeInspection.fields().forEach(fieldInfo -> {
            FieldInspectionImpl.Builder fieldInspection = (FieldInspectionImpl.Builder)
                    expressionContext.typeContext.getFieldInspection(fieldInfo);
            if (!fieldInspection.fieldInitialiserIsSet() && fieldInspection.getInitialiserExpression() != null) {
                doFieldInitialiser(fieldInfo, fieldInspection, expressionContext, methodFieldSubTypeGraph, restrictToType);
            } else {
                methodFieldSubTypeGraph.addNode(fieldInfo, List.of());
            }
            assert !fieldInfo.fieldInspection.isSet() : "Field inspection for " + fieldInfo.fullyQualifiedName() + " has already been set";
            fieldInfo.fieldInspection.set(fieldInspection.build());
            log(RESOLVER, "Set field inspection of " + fieldInfo.fullyQualifiedName());
        });
    }

    private void doFieldInitialiser(FieldInfo fieldInfo,
                                    FieldInspectionImpl.Builder fieldInspectionBuilder,
                                    ExpressionContext expressionContext,
                                    DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph,
                                    Set<TypeInfo> restrictToType) {
        // we can cast here: no point in resolving if inspection has been set.

        Expression expression = fieldInspectionBuilder.getInitialiserExpression();
        FieldInspection.FieldInitialiser fieldInitialiser;
        List<WithInspectionAndAnalysis> dependencies;

        if (expression != FieldInspectionImpl.EMPTY) {
            ExpressionContext subContext = expressionContext.newTypeContext(fieldInfo);

            // fieldInfo.type can have concrete types; but the abstract method will not have them filled in
            MethodTypeParameterMap singleAbstractMethod = fieldInfo.type
                    .findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            if (singleAbstractMethod != null) {
                singleAbstractMethod = singleAbstractMethod
                        .expand(fieldInfo.type.initialTypeParameterMap(expressionContext.typeContext));
                log(RESOLVER, "Passing on functional interface method to field initializer of {}: {}",
                        fieldInfo.name, singleAbstractMethod);
            }
            org.e2immu.analyser.model.Expression parsedExpression = subContext.parseExpression(expression,
                    fieldInfo.type, singleAbstractMethod);
            // here we decide how to resolve the anonymous types created as we go along
            // the current implementation treats the anonymous types as subtypes in the current type, which is what we want to do
            subContext.streamNewlyCreatedTypes().forEach(anonymousType -> doType(anonymousType, subContext,
                    methodFieldSubTypeGraph));

            MethodInfo sam;
            boolean artificial;
            if (fieldInfo.type.isFunctionalInterface(subContext.typeContext)) {
                List<NewObject> newObjects = parsedExpression.collect(NewObject.class);
                artificial = newObjects.stream().filter(no -> no.parameterizedType().isFunctionalInterface()).count() != 1L;

                if (!artificial) {
                    NewObject newObject = newObjects.stream()
                            .filter(no -> no.parameterizedType().isFunctionalInterface()).findFirst().orElseThrow();
                    TypeInfo anonymousType = Objects.requireNonNull(newObject.anonymousClass());
                    sam = anonymousType.findOverriddenSingleAbstractMethod();
                } else {
                    // implicit anonymous type
                    // no point in creating something that we cannot (yet) deal with...
                    VariableExpression ve;
                    if (parsedExpression instanceof NullConstant || parsedExpression == EmptyExpression.EMPTY_EXPRESSION) {
                        sam = null;
                    } else if (parsedExpression instanceof Lambda lambda) {
                        assert lambda.implementation.typeInfo != null; // to keep IntelliJ happy
                        sam = lambda.implementation.typeInfo.findOverriddenSingleAbstractMethod();
                    } else if (parsedExpression instanceof MethodReference) {
                        sam = convertMethodReferenceIntoAnonymous(fieldInfo.type, fieldInfo.owner,
                                (MethodReference) parsedExpression, expressionContext);
                        doType(sam.typeInfo, subContext, methodFieldSubTypeGraph);
                    } else if ((ve = parsedExpression.asInstanceOf(VariableExpression.class)) != null) {
                        if (ve.variable() instanceof FieldReference) {
                            sam = null; // we can't know, there'll be an indirection
                        } else {
                            throw new UnsupportedOperationException("Can only deal with fields at the moment: " +
                                    ve.variable().fullyQualifiedName());
                        }
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
            MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited(restrictToType);
            methodsAndFieldsVisited.visit(toVisit);
            dependencies = List.copyOf(methodsAndFieldsVisited.methodsAndFields);
        } else {
            fieldInitialiser = new FieldInspection.FieldInitialiser(EmptyExpression.EMPTY_EXPRESSION, null, false);
            dependencies = List.of();
        }
        methodFieldSubTypeGraph.addNode(fieldInfo, dependencies);
        fieldInspectionBuilder.setFieldInitializer(fieldInitialiser);
    }

    private void doMethodsAndConstructors(TypeInspection typeInspection,
                                          ExpressionContext expressionContext,
                                          DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph,
                                          Set<TypeInfo> restrictToType) {
        // METHOD AND CONSTRUCTOR, without the SAMs in FIELDS
        typeInspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {

            MethodInspection methodInspection = expressionContext.typeContext.getMethodInspection(methodInfo);
            assert methodInspection != null :
                    "Method inspection for " + methodInfo.name + " in " + methodInfo.typeInfo.fullyQualifiedName + " not found";
            boolean haveCompanionMethods = !methodInspection.getCompanionMethods().isEmpty();
            if (haveCompanionMethods) {
                log(RESOLVER, "Start resolving companion methods of {}", methodInspection.getDistinguishingName());

                methodInspection.getCompanionMethods().values().forEach(companionMethod -> {
                    MethodInspection companionMethodInspection = expressionContext.typeContext.getMethodInspection(companionMethod);
                    try {
                        doMethodOrConstructor(typeInspection, companionMethod, (MethodInspectionImpl.Builder)
                                companionMethodInspection, expressionContext, methodFieldSubTypeGraph, restrictToType);
                    } catch (RuntimeException rte) {
                        LOGGER.warn("Caught runtime exception while resolving companion method {} in {}", companionMethod.name,
                                methodInfo.typeInfo.fullyQualifiedName);
                        throw rte;
                    }
                });

                log(RESOLVER, "Finished resolving companion methods of {}", methodInspection.getDistinguishingName());
            }
            try {
                doMethodOrConstructor(typeInspection, methodInfo, (MethodInspectionImpl.Builder) methodInspection,
                        expressionContext, methodFieldSubTypeGraph, restrictToType);
            } catch (RuntimeException rte) {
                LOGGER.warn("Caught runtime exception while resolving method {} in {}", methodInfo.name,
                        methodInfo.typeInfo.fullyQualifiedName);
                throw rte;
            }
        });
    }

    private void doMethodOrConstructor(TypeInspection typeInspection,
                                       MethodInfo methodInfo,
                                       MethodInspectionImpl.Builder methodInspection,
                                       ExpressionContext expressionContext,
                                       DependencyGraph<WithInspectionAndAnalysis> methodFieldSubTypeGraph,
                                       Set<TypeInfo> restrictToType) {
        log(RESOLVER, "Resolving {}", methodInfo.fullyQualifiedName);

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
            Block.BlockBuilder blockBuilder = new Block.BlockBuilder();
            if (methodInspection.compactConstructor) {
                addCompactConstructorSyntheticAssignments(expressionContext.typeContext, blockBuilder,
                        typeInspection, methodInspection);
            }
            BlockStmt block = methodInspection.getBlock();
            if (block != null && !block.getStatements().isEmpty()) {
                log(RESOLVER, "Parsing block of method {}", methodInfo.name);
                doBlock(subContext, methodInfo, methodInspection, block, blockBuilder);
            } else {
                methodInspection.setInspectedBlock(blockBuilder.build());
            }
        }
        MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited(restrictToType);
        methodsAndFieldsVisited.visit(methodInspection.getMethodBody());

        // finally, we build the method inspection and set it in the methodInfo object
        methodInspection.build(expressionContext.typeContext);

        // and only then, when the FQN is known, add to the sub-graph
        methodFieldSubTypeGraph.addNode(methodInfo, List.copyOf(methodsAndFieldsVisited.methodsAndFields));
    }

    private void addCompactConstructorSyntheticAssignments(InspectionProvider inspectionProvider,
                                                           Block.BlockBuilder blockBuilder,
                                                           TypeInspection typeInspection,
                                                           MethodInspectionImpl.Builder methodInspection) {
        int i = 0;
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            VariableExpression target = new VariableExpression(new FieldReference(inspectionProvider, fieldInfo,
                    new This(inspectionProvider, fieldInfo.owner)));
            VariableExpression parameter = new VariableExpression(methodInspection.getParameters().get(i++));
            Assignment assignment = new Assignment(inspectionProvider.getPrimitives(), target, parameter);
            blockBuilder.addStatement(new ExpressionAsStatement(assignment, true));
        }
    }

    private static class MethodsAndFieldsVisited {
        final Set<WithInspectionAndAnalysis> methodsAndFields = new HashSet<>();
        final Set<TypeInfo> restrictToType;

        public MethodsAndFieldsVisited(Set<TypeInfo> restrictToType) {
            this.restrictToType = restrictToType;
        }

        void visit(Element element) {
            element.visit(e -> {
                VariableExpression ve;
                if (e instanceof FieldAccess fieldAccess) {
                    if (fieldAccess.variable() instanceof FieldReference fieldReference &&
                            restrictToType.contains(fieldReference.fieldInfo.owner)) {
                        methodsAndFields.add(fieldReference.fieldInfo);
                    }
                } else if (e instanceof org.e2immu.analyser.model.Expression ex &&
                        (ve = ex.asInstanceOf(VariableExpression.class)) != null) {
                    if (ve.variable() instanceof FieldReference fieldReference &&
                            restrictToType.contains(fieldReference.fieldInfo.owner)) {
                        methodsAndFields.add(fieldReference.fieldInfo);
                    }
                } else if (e instanceof MethodCall methodCall && restrictToType.contains(methodCall.methodInfo.typeInfo)) {
                    methodsAndFields.add(methodCall.methodInfo);
                } else if (e instanceof MethodReference methodReference &&
                        restrictToType.contains(methodReference.methodInfo.typeInfo)) {
                    methodsAndFields.add(methodReference.methodInfo);
                } else if (e instanceof NewObject newObject && newObject.constructor() != null &&
                        restrictToType.contains(newObject.constructor().typeInfo)) {
                    methodsAndFields.add(newObject.constructor());
                } else if (e instanceof ExplicitConstructorInvocation eci) {
                    methodsAndFields.add(eci.methodInfo);
                }
            });
        }
    }

    private void doBlock(ExpressionContext expressionContext,
                         MethodInfo methodInfo,
                         MethodInspectionImpl.Builder methodInspection,
                         BlockStmt block,
                         Block.BlockBuilder blockBuilder) {
        try {
            MethodTypeParameterMap returnTypeSAM = methodInspection.getReturnType() == null ? null :
                    methodInspection.getReturnType().findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            ExpressionContext newContext = expressionContext.newVariableContext(methodInfo, returnTypeSAM);
            methodInspection.getParameters().forEach(newContext.variableContext::add);
            log(RESOLVER, "Parsing block with variable context {}", newContext.variableContext);
            Block parsedBlock = newContext.continueParsingBlock(block, blockBuilder);
            methodInspection.setInspectedBlock(parsedBlock);

            newContext.streamNewlyCreatedTypes().forEach(anonymousType -> {
                Resolver resolver = new Resolver(this, inspectionProvider, e2ImmuAnnotationExpressions, false);
                resolver.resolve(Map.of(anonymousType, newContext));
                // result can be ignored, because it is stored in the anonymousType's TypeResolution
            });

        } catch (RuntimeException rte) {
            LOGGER.warn("Caught runtime exception while resolving block starting at line {}", block.getBegin().orElse(null));
            throw rte;
        }
    }


    private void methodResolution(DependencyGraph<WithInspectionAndAnalysis> methodGraph) {
        // iterate twice, because we partial results on all MethodInfo objects for the setCallStatus computation
        Map<MethodInfo, MethodResolution.Builder> builders = new HashMap<>();
        methodGraph.visit((from, toList) -> {
            try {
                if (from instanceof MethodInfo methodInfo) {
                    Set<WithInspectionAndAnalysis> dependencies = methodGraph.dependenciesWithoutStartingPoint(from);
                    Set<MethodInfo> methodsReached = dependencies.stream()
                            .filter(w -> w instanceof MethodInfo).map(w -> (MethodInfo) w).collect(Collectors.toSet());

                    MethodResolution.Builder methodResolutionBuilder = new MethodResolution.Builder();
                    methodResolutionBuilder.setMethodsOfOwnClassReached(methodsReached);
                    boolean iHelpBreakTheCycle = AnalyseCycle.analyseCycle(methodInfo, methodsReached, methodGraph);
                    methodResolutionBuilder.isIgnoreMeBecauseOfPartOfCallCycle.set(iHelpBreakTheCycle);

                    methodCreatesObjectOfSelf(methodInfo, methodResolutionBuilder);
                    computeStaticMethodCallsOnly(methodInfo, methodResolutionBuilder);
                    methodResolutionBuilder.overrides.set(ShallowMethodResolver.overrides(inspectionProvider, methodInfo));

                    computeAllowsInterrupt(methodResolutionBuilder, builders, methodInfo, methodsReached, false);
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
                builder.partOfConstruction.set(computeCallStatus(builders, methodInfo));

                // two pass, since we have no order
                Set<MethodInfo> methodsReached = builder.getMethodsOfOwnClassReached();
                computeAllowsInterrupt(builder, builders, methodInfo, methodsReached, true);
                methodInfo.methodResolution.set(builder.build());
            }
        });
    }

    private void computeAllowsInterrupt(MethodResolution.Builder methodResolutionBuilder,
                                        Map<MethodInfo, MethodResolution.Builder> builders,
                                        MethodInfo methodInfo,
                                        Set<MethodInfo> methodsReached,
                                        boolean doNotDelay) {
        if (methodResolutionBuilder.allowsInterrupts.isSet()) return;
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        AnnotationExpression allowsInterruptAnnotation = methodInspection.getAnnotations().stream()
                .filter(ae -> ae.equals(e2ImmuAnnotationExpressions.allowsInterrupt))
                .findFirst().orElse(null);
        if (allowsInterruptAnnotation != null) {
            boolean value = allowsInterruptAnnotation.extract("value", true);
            methodResolutionBuilder.allowsInterrupts.set(value);
            return;
        }

        // first part of allowsInterrupt computation: look locally
        boolean allowsInterrupt;
        boolean delays;
        if (methodInspection.getModifiers().contains(MethodModifier.PRIVATE)) {
            allowsInterrupt = methodsReached.stream().anyMatch(reached -> !reached.isPrivate() ||
                    methodInfo.methodResolution.isSet() && methodInfo.methodResolution.get().allowsInterrupts() ||
                    builders.containsKey(reached) && builders.get(reached).allowsInterrupts.getOrDefault(false));
            delays = methodsReached.stream().anyMatch(reached -> reached.isPrivate() &&
                    builders.containsKey(reached) &&
                    !builders.get(reached).allowsInterrupts.isSet());
            if (!allowsInterrupt) {
                Block body = inspectionProvider.getMethodInspection(methodInfo).getMethodBody();
                allowsInterrupt = AllowInterruptVisitor.allowInterrupts(body, builders.keySet());
            }
        } else {
            allowsInterrupt = !shallowResolver;
            delays = false;
        }
        if (doNotDelay || !delays || allowsInterrupt) {
            methodResolutionBuilder.allowsInterrupts.set(allowsInterrupt);
        }
    }


    // part of @UtilityClass computation in the type analyser
    private static void methodCreatesObjectOfSelf(MethodInfo methodInfo, MethodResolution.Builder methodResolution) {
        AtomicBoolean createSelf = new AtomicBoolean();
        methodInfo.methodInspection.get().getMethodBody().visit(element -> {
            if (element instanceof NewObject newObject) {
                if (newObject.parameterizedType().typeInfo == methodInfo.typeInfo) {
                    createSelf.set(true);
                }
            }
        });
        methodResolution.createObjectOfSelf.set(createSelf.get());
    }

    private void computeStaticMethodCallsOnly(MethodInfo methodInfo,
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
                log(RESOLVER, "Method {} is not static, does it have no calls on <this> scope? {}",
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
    private boolean isCalledFromNonPrivateMethod(Map<MethodInfo, MethodResolution.Builder> builders,
                                                 MethodInfo methodInfo) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        for (MethodInfo other : typeInspection.methods()) {
            if (!other.isPrivate() && builders.get(other).getMethodsOfOwnClassReached().contains(methodInfo)) {
                return true;
            }
        }
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            if (fieldInfo.isNotPrivate() && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                if (fieldInitialiser.implementationOfSingleAbstractMethod() != null &&
                        builders.get(fieldInitialiser.implementationOfSingleAbstractMethod()).getMethodsOfOwnClassReached().contains(methodInfo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCalledFromConstructors(Map<MethodInfo, MethodResolution.Builder> builders,
                                             MethodInfo methodInfo) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        for (MethodInfo other : typeInspection.constructors()) {
            if (builders.get(other).getMethodsOfOwnClassReached().contains(methodInfo)) {
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
                            if (methodCall.methodInfo == methodInfo) {
                                found.set(true);
                            } else {
                                MethodResolution.Builder builder = builders.get(methodCall.methodInfo);
                                if (builder != null && builder.getMethodsOfOwnClassReached().contains(methodInfo)) {
                                    found.set(true);
                                }
                            }
                        }
                    });
                    return found.get();
                }
            }
        }
        return false;
    }


    private MethodResolution.CallStatus computeCallStatus(Map<MethodInfo, MethodResolution.Builder> builders,
                                                          MethodInfo methodInfo) {
        if (methodInfo.isConstructor) {
            return MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        }
        if (!methodInfo.isPrivate()) {
            return MethodResolution.CallStatus.NON_PRIVATE;
        }
        if (isCalledFromNonPrivateMethod(builders, methodInfo)) {
            return MethodResolution.CallStatus.CALLED_FROM_NON_PRIVATE_METHOD;
        }
        if (isCalledFromConstructors(builders, methodInfo)) {
            return MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        }
        return MethodResolution.CallStatus.NOT_CALLED_AT_ALL;
    }

    public static Set<TypeInfo> superTypesExcludingJavaLangObject(InspectionProvider inspectionProvider,
                                                                  TypeInfo typeInfo,
                                                                  Map<TypeInfo, TypeResolution.Builder> builders) {
        if (Primitives.isJavaLangObject(typeInfo)) return Set.of();
        List<TypeInfo> list = new ArrayList<>();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        boolean hasGeneratedAnnotation = typeInspection.getAnnotations().stream()
                .anyMatch(ae -> Generated.class.getCanonicalName().equals(ae.typeInfo().fullyQualifiedName));

        if (typeInspection.parentClass() != null && !Primitives.isJavaLangObject(typeInspection.parentClass())) {
            TypeInfo parent = Objects.requireNonNull(typeInspection.parentClass().typeInfo);
            list.add(parent);
            list.addAll(superTypesExcludingJavaLangObject(inspectionProvider, parent, builders));
            incrementImplementations(parent, typeInfo, hasGeneratedAnnotation, builders);
        } // else: silently ignore, we may be going out of bounds

        typeInspection.interfacesImplemented().forEach(i -> {
            list.add(i.typeInfo);
            assert i.typeInfo != null;
            list.addAll(superTypesExcludingJavaLangObject(inspectionProvider, i.typeInfo, builders));
            incrementImplementations(i.typeInfo, typeInfo, hasGeneratedAnnotation, builders);
        });
        return Set.copyOf(list);
    }

    private static void incrementImplementations(TypeInfo superType,
                                                 TypeInfo implementation,
                                                 boolean hasGeneratedAnnotation,
                                                 Map<TypeInfo, TypeResolution.Builder> builders) {
        if (builders != null) {
            TypeResolution.Builder builder = builders.get(superType);
            if (builder != null) {
                builder.incrementImplementations(implementation, hasGeneratedAnnotation);
            }
        }
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

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

package org.e2immu.analyser.resolver.impl;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.inspector.impl.ExpressionContextImpl;
import org.e2immu.analyser.inspector.impl.FieldAccessStore;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.LocalClassDeclaration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.analyser.resolver.ShallowMethodResolver;
import org.e2immu.analyser.resolver.SortedTypes;
import org.e2immu.analyser.resolver.TypeCycle;
import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.e2immu.graph.analyser.PackedInt;
import org.e2immu.analyser.util.TimedLogger;

import org.e2immu.graph.op.Common;
import org.e2immu.graph.op.Linearization;
import org.e2immu.support.FirstThen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.Generated;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.util.ConvertExpressionWithTypeCreations.convertExpressionIntoSupplier;
import static org.e2immu.analyser.model.util.ConvertMethodReference.convertMethodReferenceIntoAnonymous;

/*
The Resolver is recursive with respect to types defined in statements: anonymous types (new XXX() { }),
lambdas, and classes defined in methods.
These result in a "new" SortedType object that is stored in the local type's TypeResolution object.

Subtypes defined in the primary type go along with methods and fields.
 */
public class ResolverImpl implements Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolverImpl.class);

    private final Messages messages = new Messages();
    private final boolean shallowResolver;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final InspectionProvider inspectionProvider;
    private final Resolver parent;
    private final AnonymousTypeCounters anonymousTypeCounters;
    private final AtomicInteger typeCounterForDebugging;
    private final FirstThen<G.Builder<MethodInfo>, G<MethodInfo>> methodCallGraph;
    private final boolean storeComments;
    private final TimedLogger timedLogger;
    private final FirstThen<G.Builder<TypeInfo>, G<TypeInfo>> typeGraph
            = new FirstThen<>(new G.Builder<>(PackedInt::longSum));
    private final FirstThen<G.Builder<TypeInfo>, G<TypeInfo>> externalTypeGraph
            = new FirstThen<>(new G.Builder<>(PackedInt::longSum));
    private final boolean parallel;

    @Override
    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    @Override
    public boolean storeComments() {
        return storeComments;
    }

    @Override
    public Resolver child(InspectionProvider inspectionProvider,
                          E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                          boolean shallowResolver,
                          boolean parseComments) {
        return new ResolverImpl(this, inspectionProvider, e2ImmuAnnotationExpressions, shallowResolver, parseComments);
    }

    private ResolverImpl(ResolverImpl parent,
                         InspectionProvider inspectionProvider,
                         E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                         boolean shallowResolver,
                         boolean storeComments) {
        this.shallowResolver = shallowResolver;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inspectionProvider = inspectionProvider;
        this.parent = parent;
        this.anonymousTypeCounters = parent.anonymousTypeCounters;
        methodCallGraph = parent.methodCallGraph;
        this.storeComments = storeComments;
        timedLogger = parent.timedLogger;
        typeCounterForDebugging = parent.typeCounterForDebugging;
        this.parallel = parent.parallel;
    }

    public ResolverImpl(AnonymousTypeCounters anonymousTypeCounters,
                        InspectionProvider inspectionProvider,
                        E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                        boolean shallowResolver,
                        boolean storeComments,
                        boolean parallel) {
        this.shallowResolver = shallowResolver;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inspectionProvider = inspectionProvider;
        this.parent = null;
        this.anonymousTypeCounters = anonymousTypeCounters;
        methodCallGraph = new FirstThen<>(new G.Builder<>(Long::sum));
        this.storeComments = storeComments;
        timedLogger = new TimedLogger(LOGGER, 1000L);
        typeCounterForDebugging = new AtomicInteger();
        this.parallel = parallel;
    }

    /**
     * Responsible for resolving, circular dependency detection.
     *
     * @param inspectedTypes when a subResolver, the map contains only one type, and it will not be a primary type.
     *                       When not a subResolver, it only contains primary types.
     * @return A list of sorted primary types, each with their sub-elements (subtypes, fields, methods) sorted.
     */

    @Override
    public SortedTypes resolve(Map<TypeInfo, ExpressionContext> inspectedTypes) {
        Instant startTime;
        if (parent == null) {
            LOGGER.info("Start resolution phase on {} primary types", inspectedTypes.size());
            startTime = Instant.now();
        } else {
            startTime = null;
        }
        Set<TypeInfo> stayWithin = inspectedTypes.keySet().stream()
                .flatMap(typeInfo -> typeAndAllSubTypes(typeInfo).stream())
                .collect(Collectors.toUnmodifiableSet());

        Set<Map.Entry<TypeInfo, ExpressionContext>> entries = inspectedTypes.entrySet();
        Stream<Map.Entry<TypeInfo, ExpressionContext>> entryStream = parallel ? entries.parallelStream()
                : entries.stream();
        Map<TypeInfo, TypeResolution.Builder> resolutionBuilders = entryStream
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> resolveTypeAndCreateBuilder(entry, stayWithin)));
        // only at the top level, because we have only one call graph
        if (parent == null) {
            Instant endTime = Instant.now();
            long seconds = ChronoUnit.SECONDS.between(startTime, endTime);
            LOGGER.info("At end of main resolution loop, took {} seconds", seconds);
            LOGGER.info("Start filling method resolution objects on {} types", typeCounterForDebugging.get());
            synchronized (methodCallGraph) {
                methodResolution();
            }
            LOGGER.info("Computing analyser sequence from dependency graph, have {} builders",
                    resolutionBuilders.size());
        }
        if (parent == null) {
            LOGGER.info("Computing type resolution");
        }
        computeTypeResolution(resolutionBuilders, inspectedTypes);
        if (parent == null) {
            LOGGER.info("Linearize");
        }
        return linearize(resolutionBuilders);
    }

    private TypeResolution.Builder resolveTypeAndCreateBuilder(Map.Entry<TypeInfo, ExpressionContext> entry, Set<TypeInfo> stayWithin) {
        try {
            TypeInfo typeInfo = entry.getKey();
            ExpressionContext expressionContext = entry.getValue();

            if (parent == null) {
                assert typeInfo.isPrimaryType() : "Not a primary type: " + typeInfo.fullyQualifiedName;
            } else {
                assert !typeInfo.isPrimaryType() :
                        "?? in recursive situation we do not expect a primary type" + typeInfo.fullyQualifiedName;
            }
            SortedType sortedType = addToTypeGraph(stayWithin, typeInfo, expressionContext);
            return new TypeResolution.Builder().setSortedType(sortedType);
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception resolving type {}", entry.getKey().fullyQualifiedName);
            throw rte;
        }
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

    private SortedTypes linearize(Map<TypeInfo, TypeResolution.Builder> resolutionBuilders) {
        G<TypeInfo> g = builtTypeGraph();
        // we do not attempt to break cycles here! That's, for now, part of an external activity.
        Linearization.Result<TypeInfo> res = Linearization.linearize(g, Linearization.LinearizationMode.ALL);
        Stream<V<TypeInfo>> vertexStream = Stream.concat(res.linearized().stream().flatMap(Set::stream),
                Stream.concat(res.remainingCycles().stream().flatMap(Set::stream),
                        res.nonProblematic().stream()));
        List<TypeCycle> typeCycles = vertexStream.map(v -> {
            List<SortedType> sortedTypes = v.ts().stream()
                    .map(ti -> resolutionBuilders.get(ti).getSortedType()).toList();
            return (TypeCycle) new ListOfSortedTypes(sortedTypes);
        }).toList();
        return new SortedTypes(typeCycles);
    }

    private void computeTypeResolution(Map<TypeInfo, TypeResolution.Builder> resolutionBuilders,
                                       Map<TypeInfo, ExpressionContext> inspectedTypes) {
        /*
        The code that computes supertypes and counts implementations runs over all known types and subtypes,
        out of the standard sorting order, exactly because of circular dependencies.
         */
        if (parent == null) LOGGER.info("Prepare type resolution");
        Stream<Map.Entry<TypeInfo, TypeResolution.Builder>> subTypeStream = resolutionBuilders.entrySet().stream()
                .flatMap(e -> addSubtypeResolutionBuilders(inspectionProvider, e.getKey(), e.getValue()));
        Map<TypeInfo, TypeResolution.Builder> allBuilders = Stream.concat(resolutionBuilders.entrySet().stream(),
                subTypeStream).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        if (parent == null) LOGGER.info("Computing supertypes, have {} builders", allBuilders.size());
        Set<Map.Entry<TypeInfo, TypeResolution.Builder>> entries = allBuilders.entrySet();
        makeStream(entries)
                .forEach(e -> computeSuperTypes(inspectionProvider, e.getKey(), e.getValue(), allBuilders));
        if (parent == null) LOGGER.info("Computing field access");
        makeStream(entries).forEach(e -> computeFieldAccess(inspectionProvider, e.getKey(), e.getValue(),
                parent != null ? inspectedTypes.get(e.getKey()) : inspectedTypes.get(e.getKey().primaryType())));
        if (parent == null) LOGGER.info("Post-process type resolution");
        makeStream(entries).forEach(e -> e.getKey().typeResolution.set(e.getValue().build()));
    }

    private Stream<Map.Entry<TypeInfo, TypeResolution.Builder>> makeStream
            (Set<Map.Entry<TypeInfo, TypeResolution.Builder>> entries) {
        return parallel ? entries.parallelStream() : entries.stream();
    }

    private void computeFieldAccess(InspectionProvider inspectionProvider,
                                    TypeInfo typeInfo,
                                    TypeResolution.Builder builder,
                                    ExpressionContext expressionContext) {
        FieldAccessStore fieldAccessStore = expressionContext.fieldAccessStore();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        boolean accessed = fieldAccessStore.fieldsOfTypeAreAccessedOutsideTypeInsidePrimaryType(typeInfo, typeInspection);
        builder.setFieldsAccessedInRestOfPrimaryType(accessed);
    }

    private Stream<Map.Entry<TypeInfo, TypeResolution.Builder>> addSubtypeResolutionBuilders(InspectionProvider inspectionProvider,
                                                                                             TypeInfo typeInfo,
                                                                                             TypeResolution.Builder resolutionBuilder) {
        Set<TypeInfo> circularDependencies = resolutionBuilder.getCircularDependencies();
        return inspectionProvider.getTypeInspection(typeInfo).subTypes().stream()
                .map(subType -> {
                    // IMPROVE circularDependencies is at the level of primary types, can be better
                    TypeResolution.Builder builder = new TypeResolution.Builder().setCircularDependencies(circularDependencies);
                    return new Map.Entry<TypeInfo, TypeResolution.Builder>() {
                        @Override
                        public TypeInfo getKey() {
                            return subType;
                        }

                        @Override
                        public TypeResolution.Builder getValue() {
                            return builder;
                        }

                        @Override
                        public TypeResolution.Builder setValue(TypeResolution.Builder value) {
                            throw new UnsupportedOperationException();
                        }
                    };
                });
    }

    private static void computeSuperTypes(InspectionProvider inspectionProvider,
                                          TypeInfo typeInfo,
                                          TypeResolution.Builder builder,
                                          Map<TypeInfo, TypeResolution.Builder> allBuilders) {
        Set<TypeInfo> superTypes = superTypesExcludingJavaLangObject(inspectionProvider, typeInfo, allBuilders);
        builder.setSuperTypesExcludingJavaLangObject(superTypes);
    }

    private SortedType addToTypeGraph(Set<TypeInfo> stayWithin,
                                      TypeInfo typeInfo,
                                      ExpressionContext expressionContextOfFile) {

        // main call
        ExpressionContext expressionContextOfType = expressionContextOfFile.newTypeContext("Primary type");
        G.Builder<WithInspectionAndAnalysis> methodFieldSubTypeGraph = new G.Builder<>(PackedInt::longSum);
        List<TypeInfo> typeAndAllSubTypes = doType(typeInfo, typeInfo, expressionContextOfType, methodFieldSubTypeGraph);

        // FROM HERE ON, ALL INSPECTION HAS BEEN SET!

        // NOW, ALL METHODS IN THIS PRIMARY TYPE HAVE METHOD RESOLUTION SET

        // remove myself and all my enclosing types, and stay within the set of inspectedTypes
        // only add primary types!
        Map<TypeInfo, Integer> typeDependencies = new HashMap<>();
        Map<TypeInfo, Integer> externalTypeDependencies = new HashMap<>();
        TypeInfo.HardCoded hardCoded = TypeInfo.HARDCODED_TYPES.get(typeInfo.fullyQualifiedName);
        if (hardCoded == null || !hardCoded.eraseDependencies) {
            for (Map.Entry<TypeInfo, Integer> e : typeInfo.typesReferenced2()) {
                TypeInfo t = e.getKey();
                assert t.primaryType() == t : "Not a primary type! " + t;
                if (t != typeInfo) {
                    if (stayWithin.contains(t)) {
                        if (!typeAndAllSubTypes.contains(t)) {
                            typeDependencies.put(t, e.getValue());
                        }
                    } else {
                        externalTypeDependencies.put(t, e.getValue());
                    }
                }
            }
        }
        synchronized (typeGraph) {
            G.Builder<TypeInfo> builder = typeGraph.getFirst();
            builder.addVertex(typeInfo);
            typeDependencies.forEach((to, w) -> builder.mergeEdge(typeInfo, to, w));
        }
        synchronized (externalTypeGraph) {
            G.Builder<TypeInfo> builder = externalTypeGraph.getFirst();
            builder.addVertex(typeInfo);
            externalTypeDependencies.forEach((to, w) -> builder.mergeEdge(typeInfo, to, w));
        }
        G<WithInspectionAndAnalysis> g = methodFieldSubTypeGraph.build();
        Linearization.Result<WithInspectionAndAnalysis> lin = Linearization.linearize(g);
        List<WithInspectionAndAnalysis> sorted = lin.asList(
                Comparator.comparing(WithInspectionAndAnalysis::fullyQualifiedName));
        List<WithInspectionAndAnalysis> methodFieldSubTypeOrder = List.copyOf(sorted);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Method graph has {} edges", g.edgeStream().count());
            LOGGER.debug("Method and field order in {}: {}", typeInfo.fullyQualifiedName,
                    methodFieldSubTypeOrder.stream().map(WithInspectionAndAnalysis::name).collect(Collectors.joining(", ")));
            LOGGER.debug("Types referred to in {}: {}", typeInfo.fullyQualifiedName, typeDependencies);
        }

        return new SortedType(typeInfo, methodFieldSubTypeOrder);
    }

    private List<TypeInfo> doType(TypeInfo typeInfo,
                                  TypeInfo topType,
                                  ExpressionContext expressionContextOfType,
                                  G.Builder<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        try {
            TypeInspection typeInspection = expressionContextOfType.typeContext()
                    .typeMap().getTypeInspectionToStartResolving(typeInfo);
            if (typeInspection == null) {
                // no need to resolve this type, we'll never use it (state is still TRIGGER_BYTECODE_INSPECTION)
                return List.of(typeInfo);
            }
            typeInspection.subTypes().forEach(expressionContextOfType.typeContext()::addToContext);

            int cnt = typeCounterForDebugging.incrementAndGet();
            LOGGER.debug("Resolving type #{}: {}", cnt, typeInfo.fullyQualifiedName);

            TypeInfo primaryType = typeInfo.primaryType();
            ExpressionContext expressionContextForBody = ExpressionContextImpl.forTypeBodyParsing(this, typeInfo,
                    primaryType, expressionContextOfType);

            TypeContext typeContext = expressionContextForBody.typeContext();
            typeContext.addToContext(typeInfo);
            typeInspection.typeParameters().forEach(typeContext::addToContext);

            // add visible types to the type context; do not overwrite, see Constructor_9
            accessibleBySimpleNameTypeInfoStream(typeContext, typeInfo, primaryType)
                    .forEach(ti -> typeContext.addToContext(ti, false));

            // add visible fields to variable context, without those of enclosing types, they'll be recursively accessible
            // See e.g. AnonymousType_0
            accessibleFieldsStream(typeContext, typeInfo, primaryType, primaryType.packageName(),
                    false, false).forEach(fieldInfo ->
                    expressionContextForBody.variableContext()
                            .add(makeFieldReference(typeContext, typeInspection, fieldInfo)));

            // recursion, do subtypes first (no recursion at resolver level!)
            typeInspection.subTypes().forEach(subType -> {
                LOGGER.debug("From {} into {}", typeInfo.fullyQualifiedName, subType.fullyQualifiedName);
                ExpressionContext expressionContextForSubType = expressionContextForBody
                        .newTypeContext("sub-type definition");
                doType(subType, topType, expressionContextForSubType, methodFieldSubTypeGraph);
            });

            List<TypeInfo> typeAndAllSubTypes = typeAndAllSubTypes(typeInfo);
            Set<TypeInfo> restrictToType = new HashSet<>(typeAndAllSubTypes);

            ExpressionContext withFields = expressionContextForBody.newVariableContext("fields of " + typeInfo);
            doFields(typeInspection, topType, withFields, methodFieldSubTypeGraph);
            doMethodsAndConstructors(typeInspection, topType, withFields, methodFieldSubTypeGraph);
            doAnnotations(typeInspection.getAnnotations(), withFields);

            // dependencies of the type

            Set<TypeInfo> typeDependencies = typeInfo.typesReferenced().stream()
                    .map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));
            typeDependencies.retainAll(restrictToType);
            methodFieldSubTypeGraph.add(typeInfo, List.copyOf(typeDependencies));

            timedLogger.info("Resolved {} types", cnt);

            return typeAndAllSubTypes;
        } catch (Throwable re) {
            LOGGER.error("Caught exception resolving type {}", typeInfo.fullyQualifiedName);
            throw re;
        }
    }

    /*
       Note:
         Parent<E> contains: protected E field;
         Child<T extends X> extends Parent<T>
         now means that field is accessible in Child as type T extends X!

       procedure? if the field's type contains a type parameter which hase more concrete values, they'll have
       to be updated.
    */
    private FieldReference makeFieldReference(InspectionProvider inspectionProvider,
                                              TypeInspection typeInspection,
                                              FieldInfo fieldInfo) {
        // field should have a type parameter, and has to belong to a type in the hierarchy
        if (fieldInfo.type.hasTypeParameters() && !typeInspection.typeInfo().equals(fieldInfo.owner)) {
            ParameterizedType superType = fieldInfo.owner.asParameterizedType(inspectionProvider);
            // we need to obtain a translation map to get the concrete types or type bounds
            Map<NamedType, ParameterizedType> translate = typeInspection.typeInfo()
                    .mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (translate != null && !translate.isEmpty()) {
                ParameterizedType concrete = fieldInfo.type.applyTranslation(inspectionProvider.getPrimitives(),
                        translate);
                return new FieldReferenceImpl(inspectionProvider, fieldInfo, null, null,
                        concrete, typeInspection.typeInfo());
            }
        }
        return new FieldReferenceImpl(inspectionProvider, fieldInfo);
    }

    private void doAnnotations(List<AnnotationExpression> annotationExpressions, ExpressionContext expressionContext) {
        for (AnnotationExpression annotationExpression : annotationExpressions) {
            for (MemberValuePair mvp : annotationExpression.expressions()) {
                if (mvp.value().isVariable()) {
                    org.e2immu.analyser.model.Expression current = mvp.value().get();
                    if (current instanceof UnevaluatedAnnotationParameterValue unevaluated) {
                        org.e2immu.analyser.model.Expression resolved = expressionContext.parseExpression
                                (unevaluated.expression(), unevaluated.forwardReturnTypeInfo());
                        assert !(resolved instanceof UnevaluatedAnnotationParameterValue);
                        mvp.value().setFinal(resolved);
                    } else {
                        mvp.value().setFinal(mvp.value().get());
                    }
                }
            }
        }
    }

    private void doFields(TypeInspection typeInspection,
                          TypeInfo topType,
                          ExpressionContext expressionContext,
                          G.Builder<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        typeInspection.fields().forEach(fieldInfo -> {
            FieldInspectionImpl.Builder fieldInspection = (FieldInspectionImpl.Builder)
                    expressionContext.typeContext().getFieldInspection(fieldInfo);
            if (!fieldInspection.fieldInitialiserIsSet() && fieldInspection.getInitialiserExpression() != null) {
                doFieldInitialiser(fieldInfo, fieldInspection, topType, expressionContext, methodFieldSubTypeGraph);
            } else {
                methodFieldSubTypeGraph.addVertex(fieldInfo);
            }
            expressionContext.variableContext().add(new FieldReferenceImpl(expressionContext.typeContext(), fieldInfo));
            assert !fieldInfo.fieldInspection.isSet() : "Field inspection for " + fieldInfo.fullyQualifiedName() + " has already been set";
            fieldInfo.fieldInspection.set(fieldInspection.build(expressionContext.typeContext()));
            LOGGER.debug("Set field inspection of " + fieldInfo.fullyQualifiedName());

            doAnnotations(fieldInspection.getAnnotations(), expressionContext);

            // "touch" the type inspection of the field's type -- it may not explicitly appear in the import list
            // see NotNull_4_1
            if (fieldInfo.type.typeInfo != null) {
                TypeInspection fieldTypeInspection = expressionContext.typeContext().getTypeInspection(fieldInfo.type.typeInfo);
                assert fieldTypeInspection != null;
            }
        });
    }

    private void doFieldInitialiser(FieldInfo fieldInfo,
                                    FieldInspectionImpl.Builder fieldInspectionBuilder,
                                    TypeInfo topType,
                                    ExpressionContext expressionContext,
                                    G.Builder<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // we can cast here: no point in resolving if inspection has been set.

        Expression expression = fieldInspectionBuilder.getInitialiserExpression();
        FieldInspection.FieldInitialiser fieldInitialiser;
        List<WithInspectionAndAnalysis> dependencies;

        if (expression != FieldInspectionImpl.EMPTY) {

            // fieldInfo.type can have concrete types; but the abstract method will not have them filled in
            ForwardReturnTypeInfo forwardReturnTypeInfo = new ForwardReturnTypeInfo(fieldInfo.type);
            ExpressionContext subContext = expressionContext.newTypeContext(fieldInfo);

            org.e2immu.analyser.model.Expression parsedExpression = subContext.parseExpression(expression, forwardReturnTypeInfo);

            TypeInfo anonymousType;
            MethodInfo sam;
            boolean callGetOnSam;
            if (parsedExpression instanceof Lambda lambda) {
                anonymousType = lambda.implementation.typeInfo;
                assert anonymousType != null;
                sam = anonymousType.typeInspection.get().getSingleAbstractMethod().getMethodInfo();
                assert sam != null;
                callGetOnSam = false;
            } else if (parsedExpression instanceof MethodReference) {
                sam = convertMethodReferenceIntoAnonymous(fieldInfo.type, fieldInfo.owner,
                        (MethodReference) parsedExpression, expressionContext);
                anonymousType = sam.typeInfo;
                Resolver child = child(expressionContext.typeContext(), expressionContext.typeContext().typeMap()
                        .getE2ImmuAnnotationExpressions(), false, storeComments);
                child.resolve(Map.of(sam.typeInfo, subContext));
                callGetOnSam = false;
            } else if (hasTypesDefined(parsedExpression)) {
                if (parsedExpression instanceof ConstructorCall cc && cc.anonymousClass() != null) {
                    anonymousType = cc.anonymousClass();
                    if (fieldInfo.type.isFunctionalInterface(inspectionProvider)) {
                        sam = findOverriddenSingleAbstractMethodInAnonymousType(anonymousType.typeInspection.get());
                    } else {
                        sam = null;
                    }
                    callGetOnSam = false;
                } else {
                    // check if there are types created inside the expression
                    sam = convertExpressionIntoSupplier(fieldInfo.type, fieldInspectionBuilder.isStatic(), fieldInfo.owner,
                            parsedExpression, expressionContext, Identifier.from(expression));
                    anonymousType = sam.typeInfo;
                    Resolver child = child(expressionContext.typeContext(),
                            expressionContext.typeContext().typeMap().getE2ImmuAnnotationExpressions(),
                            false, storeComments);
                    child.resolve(Map.of(sam.typeInfo, subContext));
                    callGetOnSam = true;
                }
            } else {
                anonymousType = null;
                sam = null;
                callGetOnSam = false;
            }
            fieldInitialiser = new FieldInspection.FieldInitialiser(parsedExpression, anonymousType, sam, callGetOnSam,
                    Identifier.generate("resolved field initializer"));
            Element toVisit = sam != null ? inspectionProvider.getMethodInspection(sam).getMethodBody() : parsedExpression;
            MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited(topType);
            methodsAndFieldsVisited.visit(toVisit);
            if (sam != null) {
                new CallGraph(topType, sam).visit(toVisit);
            }
            dependencies = List.copyOf(methodsAndFieldsVisited.methodsAndFields);
        } else {
            fieldInitialiser = new FieldInspection.FieldInitialiser(EmptyExpression.EMPTY_EXPRESSION,
                    Identifier.generate("resolved empty initializer"));
            dependencies = List.of();
        }
        methodFieldSubTypeGraph.add(fieldInfo, dependencies);
        fieldInspectionBuilder.setFieldInitializer(fieldInitialiser);
    }

    /*
    typeInspection, as an anonymous class, is NOT a functional interface, so we'll have to actively look
    for the method
     */
    private MethodInfo findOverriddenSingleAbstractMethodInAnonymousType(TypeInspection typeInspection) {
        return typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(mi -> !mi.isDefault() && !mi.isStatic())
                .findFirst().orElseThrow();
    }

    private boolean hasTypesDefined(org.e2immu.analyser.model.Expression parsedExpression) {
        AtomicBoolean foundType = new AtomicBoolean();
        parsedExpression.visit(e -> {
            if (e.definesType() != null) {
                foundType.set(true);
            }
        });
        return foundType.get();
    }

    private void doMethodsAndConstructors(TypeInspection typeInspection,
                                          TypeInfo topType,
                                          ExpressionContext expressionContext,
                                          G.Builder<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        // METHOD AND CONSTRUCTOR, without the SAMs in FIELDS
        typeInspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {

            MethodInspection methodInspection = expressionContext.typeContext().getMethodInspection(methodInfo);
            assert methodInspection != null :
                    "Method inspection for " + methodInfo.name + " in " + methodInfo.typeInfo.fullyQualifiedName + " not found";
            boolean haveCompanionMethods = !methodInspection.getCompanionMethods().isEmpty();
            if (haveCompanionMethods) {
                LOGGER.debug("Start resolving companion methods of {}", methodInspection.getDistinguishingName());

                methodInspection.getCompanionMethods().values().forEach(companionMethod -> {
                    MethodInspection companionMethodInspection = expressionContext.typeContext().getMethodInspection(companionMethod);
                    try {
                        doMethodOrConstructor(typeInspection, companionMethod, (MethodInspectionImpl.Builder)
                                companionMethodInspection, topType, expressionContext, methodFieldSubTypeGraph);
                    } catch (RuntimeException rte) {
                        LOGGER.error("Caught exception resolving companion method {} in {}", companionMethod.name,
                                methodInfo.typeInfo.fullyQualifiedName);
                        throw rte;
                    }
                });

                LOGGER.debug("Finished resolving companion methods of {}", methodInspection.getDistinguishingName());
            }
            try {
                doMethodOrConstructor(typeInspection, methodInfo, (MethodInspectionImpl.Builder) methodInspection,
                        topType, expressionContext, methodFieldSubTypeGraph);
            } catch (RuntimeException rte) {
                LOGGER.error("Caught exception resolving method {} in {}", methodInfo.name,
                        methodInfo.typeInfo.fullyQualifiedName);
                throw rte;
            }
        });
    }

    private static final Consumer<Block.BlockBuilder> NOT_A_COMPACT_CONSTRUCTOR = bb -> {
    };

    private void doMethodOrConstructor(TypeInspection typeInspection,
                                       MethodInfo methodInfo,
                                       MethodInspectionImpl.Builder methodInspection,
                                       TypeInfo topType,
                                       ExpressionContext expressionContext,
                                       G.Builder<WithInspectionAndAnalysis> methodFieldSubTypeGraph) {
        LOGGER.debug("Resolving {}", methodInfo.fullyQualifiedName);

        // TYPE PARAMETERS OF METHOD

        List<TypeParameter> typeParameters = methodInspection.getTypeParameters();
        ExpressionContext subContext;
        if (typeParameters.isEmpty()) {
            subContext = expressionContext.newTypeContext("new method dependencies");
        } else {
            subContext = expressionContext.newTypeContext("new method dependencies and type parameters of " +
                    methodInfo.name);
            typeParameters.forEach(subContext.typeContext()::addToContext);
        }

        /* "touch" the type inspection of the method's return type -- it may not explicitly appear in the import list
           see NotNull_4_1
         */
        if (methodInspection.getReturnType() != null) {
            TypeInfo returnType = methodInspection.getReturnType().typeInfo;
            if (returnType != null) {
                TypeInspection returnTypeInspection = expressionContext.typeContext().getTypeInspection(returnType);
                assert returnTypeInspection != null;
            }
        }

        // BODY

        boolean doBlock = !methodInspection.inspectedBlockIsSet();
        if (doBlock) {
            BlockStmt block = methodInspection.getBlock();
            Block.BlockBuilder blockBuilder = new Block.BlockBuilder(block == null ?
                    Identifier.generate("resolved empty block") : Identifier.from(block));
            Consumer<Block.BlockBuilder> compactConstructorAppender = methodInfo.isCompactConstructor()
                    ? addCompactConstructorSyntheticAssignments(expressionContext.typeContext(), typeInspection, methodInspection)
                    : NOT_A_COMPACT_CONSTRUCTOR;
            if (block != null && !block.getStatements().isEmpty()) {
                LOGGER.debug("Parsing block of method {}", methodInfo.name);
                doBlock(subContext, methodInfo, methodInspection, block, blockBuilder, compactConstructorAppender);
            } else {
                compactConstructorAppender.accept(blockBuilder);
                methodInspection.setInspectedBlock(blockBuilder.build());
            }
        }
        MethodsAndFieldsVisited methodsAndFieldsVisited = new MethodsAndFieldsVisited(topType);
        methodsAndFieldsVisited.visit(methodInspection.getMethodBody());
        new CallGraph(topType, methodInfo).visit(methodInspection.getMethodBody());

        // finally, we build the method inspection and set it in the methodInfo object
        methodInspection.build(expressionContext.typeContext());

        if (methodInspection.staticBlockIdentifier > 0) {
            // add a dependency to the previous one!
            MethodInfo previousStaticBlock = typeInspection.findStaticBlock(methodInspection.staticBlockIdentifier - 1);
            assert previousStaticBlock != null;
            methodsAndFieldsVisited.methodsAndFields.add(previousStaticBlock);
        }

        // and only then, when the FQN is known, add to the sub-graph
        methodFieldSubTypeGraph.add(methodInfo, methodsAndFieldsVisited.methodsAndFields);

        doAnnotations(methodInspection.getAnnotations(), expressionContext);
    }

    private Consumer<Block.BlockBuilder> addCompactConstructorSyntheticAssignments(InspectionProvider inspectionProvider,
                                                                                   TypeInspection typeInspection,
                                                                                   MethodInspectionImpl.Builder methodInspection) {
        return blockBuilder -> {
            int i = 0;
            for (FieldInfo fieldInfo : typeInspection.fields()) {
                FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
                if (!fieldInspection.isStatic()) {
                    FieldReference fieldReference = new FieldReferenceImpl(inspectionProvider, fieldInfo);
                    VariableExpression target = new VariableExpression(fieldInfo.getIdentifier(), fieldReference);
                    ParameterInfo parameterInfo = methodInspection.getParameters().get(i++);
                    VariableExpression parameter = new VariableExpression(parameterInfo.identifier, parameterInfo);
                    Assignment assignment = new Assignment(inspectionProvider.getPrimitives(), target, parameter);
                    Identifier id = Identifier.generate("synthetic assignment compact constructor");
                    blockBuilder.addStatement(new ExpressionAsStatement(id, assignment, null, true));
                }
            }
        };
    }

    /*
    IMPORTANT: this visitor stays at the level of explicit (non-anonymous) subtypes, and fields, methods that are
    not part of anonymous subtypes.
     */
    private static class MethodsAndFieldsVisited {
        final Set<WithInspectionAndAnalysis> methodsAndFields = new HashSet<>();
        final TypeInfo topType;

        public MethodsAndFieldsVisited(TypeInfo topType) {
            this.topType = topType;
        }

        void visit(Element element) {
            element.visit(e -> {
                VariableExpression ve;
                ConstructorCall constructorCall;
                MethodCall methodCall;
                MethodReference methodReference;
                MethodInfo methodInfo;
                if (e instanceof org.e2immu.analyser.model.Expression ex &&
                        (ve = ex.asInstanceOf(VariableExpression.class)) != null) {
                    if (ve.variable() instanceof FieldReference fieldReference &&
                            fieldReference.fieldInfo().owner.isEnclosedInStopAtLambdaOrAnonymous(topType)) {
                        methodsAndFields.add(fieldReference.fieldInfo());
                    }
                    methodInfo = null;
                } else if ((methodCall = e.asInstanceOf(MethodCall.class)) != null) {
                    methodInfo = methodCall.methodInfo;
                } else if ((methodReference = e.asInstanceOf(MethodReference.class)) != null) {
                    methodInfo = methodReference.methodInfo;
                } else if ((constructorCall = e.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
                    methodInfo = constructorCall.constructor();
                } else if (e instanceof ExplicitConstructorInvocation eci) {
                    methodInfo = eci.methodInfo;
                } else if (e instanceof Lambda lambda) {
                    methodInfo = lambda.methodInfo;
                } else {
                    methodInfo = null;
                }
                if (methodInfo != null) {
                    if (methodInfo.typeInfo.isEnclosedInStopAtLambdaOrAnonymous(topType)) {
                        methodsAndFields.add(methodInfo);
                    }
                }
            });
        }
    }

    /*
     Similar, but not the same as MethodsAndFieldsVisited.
     Crucially, anonymous subtypes are seen as independent types.
     */
    private class CallGraph {
        final TypeInfo topType;
        final MethodInfo caller;

        public CallGraph(TypeInfo topType, MethodInfo caller) {
            this.topType = topType;
            this.caller = caller;
        }

        void visit(Element element) {
            AtomicBoolean created = new AtomicBoolean();
            element.visit(e -> {
                MethodInfo methodInfo;
                if (e instanceof MethodCall methodCall) {
                    methodInfo = methodCall.methodInfo;
                } else if (e instanceof MethodReference methodReference) {
                    methodInfo = methodReference.methodInfo;
                } else if (e instanceof ConstructorCall constructorCall && constructorCall.constructor() != null) {
                    methodInfo = constructorCall.constructor();
                } else if (e instanceof ExplicitConstructorInvocation eci) {
                    methodInfo = eci.methodInfo;
                } else if (e instanceof Lambda lambda) {
                    methodInfo = lambda.methodInfo;
                } else {
                    methodInfo = null;
                }
                // avoid synthetic constructors: they have no method inspection
                if (methodInfo != null && !methodInfo.isSyntheticConstructor()) {
                    if (caller != null) {
                        synchronized (methodCallGraph) {
                            methodCallGraph.getFirst().mergeEdge(caller, methodInfo, 1L);
                        }
                        created.set(true);
                    }
                }
                boolean drillDeeper;
                if (e instanceof Lambda lambda && !lambda.block.isEmpty()) {
                    drillDeeper = false;
                    new CallGraph(lambda.methodInfo.typeInfo, lambda.methodInfo).visit(lambda.block);
                } else if (e instanceof ConstructorCall cc && cc.anonymousClass() != null) {
                    drillDeeper = false;
                    callGraphOf(cc.anonymousClass());
                } else if (e instanceof LocalClassDeclaration lcd) {
                    callGraphOf(lcd.typeInfo);
                    drillDeeper = false;
                } else {
                    drillDeeper = true;
                }
                return drillDeeper;
            });
            if (caller != null && !created.get()) {
                synchronized (methodCallGraph) {
                    methodCallGraph.getFirst().addVertex(caller);
                }
            }
        }

        private void callGraphOf(TypeInfo subClass) {
            for (MethodInfo methodInfo : inspectionProvider.getTypeInspection(subClass).methodsAndConstructors()) {
                Block block = inspectionProvider.getMethodInspection(methodInfo).getMethodBody();
                if (!block.isEmpty()) {
                    new CallGraph(subClass, methodInfo).visit(block);
                }
            }
        }

    }

    private void doBlock(ExpressionContext expressionContext,
                         MethodInfo methodInfo,
                         MethodInspectionImpl.Builder methodInspection,
                         BlockStmt block,
                         Block.BlockBuilder blockBuilder,
                         Consumer<Block.BlockBuilder> compactConstructorAppender) {
        try {
            ForwardReturnTypeInfo forwardReturnTypeInfo = new ForwardReturnTypeInfo(methodInspection.getReturnType());
            ExpressionContext newContext = expressionContext.newVariableContext(methodInfo, forwardReturnTypeInfo);
            methodInspection.getParameters().forEach(newContext.variableContext()::add);
            LOGGER.debug("Parsing block with variable context {}", newContext.variableContext());
            Block parsedBlock = newContext.continueParsingBlock(block, blockBuilder, compactConstructorAppender);
            methodInspection.setInspectedBlock(parsedBlock);
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception resolving block starting at line {}", block.getBegin().orElse(null));
            throw rte;
        }
    }

    /*
    this method will be called twice, once for the AnnotatedAPI cycle, then for the source code.
    The method call graph in the first case will be rather sparse, it should be pretty dense in the second one.
    Important to note is that there is an overlap, as methods in the source will call methods in the AnnotatedAPI list.
    Because they're shallow, they cannot cause cycles.
     */

    private void methodResolution() {
        G<MethodInfo> g = builtMethodCallGraph();
        LOGGER.info("Linearizing method call graph of {} methods, {} edges", g.vertices().size(), g.edgeStream().count());
        Linearization.Result<MethodInfo> result = Linearization.linearize(g, Linearization.LinearizationMode.ALL);

        // iterate twice, because we have partial results on all MethodInfo objects for the setCallStatus computation
        Map<MethodInfo, MethodResolution.Builder> builders = new HashMap<>();
        AtomicInteger count = new AtomicInteger();
        for (V<MethodInfo> vertex : g.vertices()) {
            MethodInfo methodInfo = vertex.someElement();
            try {
                if (!methodInfo.methodResolution.isSet()) {
                    int cnt = count.incrementAndGet();
                    Set<V<MethodInfo>> methodsReached = Common.follow(g, vertex);

                    MethodResolution.Builder methodResolutionBuilder = new MethodResolution.Builder();
                    builders.put(methodInfo, methodResolutionBuilder);
                    boolean partOfCycle = result.remainingCycles().stream().anyMatch(cycle -> cycle.contains(vertex));

                    TypeInfo staticEnclosingType = methodInfo.typeInfo.firstStaticEnclosingType(inspectionProvider);
                    Set<MethodInfo> methodsOfOwnClassReached = methodsReached.stream()
                            .flatMap(v -> v.ts().stream())
                            .filter(m -> !methodInfo.equals(m) || partOfCycle)
                            .filter(m -> m.typeInfo.firstStaticEnclosingType(inspectionProvider) == staticEnclosingType)
                            .collect(Collectors.toUnmodifiableSet());
                    methodResolutionBuilder.setMethodsOfOwnClassReached(methodsOfOwnClassReached);
                    methodResolutionBuilder.setOverrides(methodInfo, ShallowMethodResolver.overrides(inspectionProvider, methodInfo));

                    computeAllowsInterrupt(methodResolutionBuilder, builders, methodInfo, methodsOfOwnClassReached, false);
                    timedLogger.info("Computed method resolution for {} methods", cnt);
                } // otherwise: already processed during AnnotatedAPI
            } catch (RuntimeException e) {
                LOGGER.error("Caught exception filling {}", methodInfo.fullyQualifiedName);
                throw e;
            }
        }

        Comparator<MethodInfo> sorter = (m1, m2) -> {
            int r1 = methodRank(m1);
            int r2 = methodRank(m2);
            int c = r1 - r2;
            if (c != 0) return c;
            return m1.fullyQualifiedName.compareTo(m2.fullyQualifiedName);
        };
        for (Set<V<MethodInfo>> cycle : result.remainingCycles()) {
            MethodInfo first = cycle.stream().map(V::someElement).min(sorter).orElseThrow();
            Set<MethodInfo> restrictedCycle = cycle.stream().map(V::someElement).collect(Collectors.toUnmodifiableSet());

            for (MethodInfo methodInfo : restrictedCycle) {
                MethodResolution.Builder builder = builders.get(methodInfo);
                boolean isFirst = methodInfo == first;
                builder.setIgnoreMeBecauseOfPartOfCallCycle(isFirst);
                builder.setCallCycle(restrictedCycle);
            }
        }
        for (V<MethodInfo> v : g.vertices()) {
            MethodInfo methodInfo = v.someElement();
            if (!methodInfo.methodResolution.isSet()) {
                MethodResolution.Builder builder = builders.get(methodInfo);
                try {
                    MethodResolution.CallStatus callStatus = computeCallStatus(builders, methodInfo);
                    builder.partOfConstruction.set(callStatus);
                    // two pass, since we have no order
                    Set<MethodInfo> methodsReached = builder.getMethodsOfOwnClassReached();
                    computeAllowsInterrupt(builder, builders, methodInfo, methodsReached, true);
                    methodInfo.methodResolution.set(builder.build());

                } catch (IllegalStateException ise) {
                    LOGGER.error("Caught exception: {} for method {}", ise, methodInfo.fullyQualifiedName);
                    throw ise;
                }
            } // otherwise: already processed during AnnotatedAPI
        }
    }

    /*
    In a call cycle, at least one method will have poorer analysis (ignoreMeBecauseOfPartOfCallCycle will be true).
    The more "important" the method is, the less likely do we want to this method to be the one in the cycle to have auto values.

    This can be an area of improvement, obviously. For now, we want to fix a null-pointer problem in
    DependencyGraph.recursivelyComputeDependenciesWithoutStartingPoint, where we'd rather see the functional interface
    method being the one that breaks the chain.
     */
    private int methodRank(MethodInfo methodInfo) {
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface()) return 0;
        if (methodInfo.methodInspection.get().isPrivate()) return 1;
        return 2;
    }

    private void computeAllowsInterrupt(MethodResolution.Builder methodResolutionBuilder,
                                        Map<MethodInfo, MethodResolution.Builder> builders,
                                        MethodInfo methodInfo,
                                        Set<MethodInfo> methodsReached,
                                        boolean doNotDelay) {
        if (methodResolutionBuilder.allowsInterruptsIsSet()) return;
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        AnnotationExpression allowsInterruptAnnotation = methodInspection.getAnnotations().stream()
                .filter(ae -> ae.equals(e2ImmuAnnotationExpressions.allowsInterrupt))
                .findFirst().orElse(null);
        if (allowsInterruptAnnotation != null) {
            boolean value = allowsInterruptAnnotation.extract("value", true);
            methodResolutionBuilder.allowsInterruptsSet(value);
            return;
        }

        // first part of allowsInterrupt computation: look locally
        boolean allowsInterrupt;
        boolean delays;
        if (methodInspection.getParsedModifiers().contains(MethodModifier.PRIVATE)) {
            allowsInterrupt = methodsReached.stream().anyMatch(reached ->
                    !inspectionProvider.getMethodInspection(reached).isPrivate() ||
                            methodInfo.methodResolution.isSet() && methodInfo.methodResolution.get().allowsInterrupts() ||
                            builders.containsKey(reached) && builders.get(reached).allowsInterruptsGetOrDefault(false));
            delays = !doNotDelay && methodsReached.stream().anyMatch(reached ->
                    !inspectionProvider.getMethodInspection(reached).isPrivate() &&
                            builders.containsKey(reached) &&
                            !builders.get(reached).allowsInterruptsIsSet());
            if (!allowsInterrupt) {
                Block body = inspectionProvider.getMethodInspection(methodInfo).getMethodBody();
                allowsInterrupt = body != null && AllowInterruptVisitor.allowInterrupts(body, builders.keySet());
            }
        } else {
            allowsInterrupt = !shallowResolver;
            delays = false;
        }
        if (doNotDelay || !delays || allowsInterrupt) {
            methodResolutionBuilder.allowsInterruptsSet(allowsInterrupt);
        }
    }

    private boolean notPartOfConstruction(MethodInfo methodInfo, MethodInspection methodInspection) {
        return !methodInspection.isPrivate() &&
                !methodInspection.getMethodInfo().isStatic() &&
                !methodInspection.getMethodInfo().typeInfo
                        .recursivelyInConstructionOrStaticWithRespectTo(inspectionProvider, methodInfo.typeInfo);
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
            MethodInspection otherInspection = other.methodInspection.get();
            if (other != methodInfo && notPartOfConstruction(other, otherInspection)) {
                MethodResolution.Builder builder = builders.get(other);
                //assert builder != null : "No method resolution builder found for " + other.fullyQualifiedName;
                if (builder != null && builder.getMethodsOfOwnClassReached().contains(methodInfo)) {
                    return true;
                }
            }
        }
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            FieldInspection fieldInspection = fieldInfo.fieldInspection.get();
            if (!fieldInspection.isPrivate() && fieldInspection.fieldInitialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.getFieldInitialiser();
                MethodInfo implementation = fieldInitialiser.implementationOfSingleAbstractMethod();
                if (implementation != null) {
                    MethodResolution.Builder builder = builders.get(implementation);
                    //assert builder != null : "No method resolution builder found for " + implementation.fullyQualifiedName;
                    if (builder != null && builder.getMethodsOfOwnClassReached().contains(methodInfo)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isCalledFromConstructorsOrStaticMethods(Map<MethodInfo, MethodResolution.Builder> builders,
                                                            MethodInfo methodInfo) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        for (MethodInfo other : typeInspection.constructors()) {
            MethodResolution.Builder builder = builders.get(other);
            //assert builder != null : "No method resolution builder found for " + other.fullyQualifiedName;
            if (builder != null && builder.getMethodsOfOwnClassReached().contains(methodInfo)) {
                return true;
            }
        }
        for (MethodInfo other : typeInspection.methods()) {
            MethodInspection otherInspection = other.methodInspection.get();
            if (other != methodInfo && !notPartOfConstruction(other, otherInspection)) {
                MethodResolution.Builder builder = builders.get(other);
                //assert builder != null : "No method resolution builder found for " + other.fullyQualifiedName;
                if (builder != null && builder.getMethodsOfOwnClassReached().contains(methodInfo)) {
                    return true;
                }
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
        if (methodInfo.isConstructor()) {
            return MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        }
        if (!inspectionProvider.getMethodInspection(methodInfo).isPrivate()) {
            return MethodResolution.CallStatus.NON_PRIVATE;
        }
        if (isCalledFromNonPrivateMethod(builders, methodInfo)) {
            return MethodResolution.CallStatus.CALLED_FROM_NON_PRIVATE_METHOD;
        }
        if (isCalledFromConstructorsOrStaticMethods(builders, methodInfo)) {
            return MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
        }
        return MethodResolution.CallStatus.NOT_CALLED_AT_ALL;
    }

    public static Set<TypeInfo> superTypesExcludingJavaLangObject(InspectionProvider inspectionProvider,
                                                                  TypeInfo typeInfo,
                                                                  Map<TypeInfo, TypeResolution.Builder> builders) {
        if (typeInfo.isJavaLangObject()) return Set.of();
        List<TypeInfo> list = new ArrayList<>();
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        boolean hasGeneratedAnnotation = typeInspection.getAnnotations().stream()
                .anyMatch(ae -> Generated.class.getCanonicalName().equals(ae.typeInfo().fullyQualifiedName));

        ParameterizedType parentClass = typeInspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            TypeInfo parent = Objects.requireNonNull(parentClass.typeInfo);
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
        boolean isJLO = typeInfo.isJavaLangObject();
        if (!isJLO && typeInspection.parentClass() != null) {
            parentStream = accessibleBySimpleNameTypeInfoStream(inspectionProvider,
                    typeInspection.parentClass().typeInfo, startingPoint, startingPointPackageName, visited);
        } else {
            // NOTE as of Java 21: java.lang.Compiler has no parent class
            parentStream = Stream.empty();
        }

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
        return inspection.isPublic() ||
                inSamePackage && inspection.isPackagePrivate() ||
                !inSamePackage && inspection.isProtected();
    }


    public static Stream<FieldInfo> accessibleFieldsStream(InspectionProvider inspectionProvider, TypeInfo typeInfo, TypeInfo primaryType) {
        return accessibleFieldsStream(inspectionProvider, typeInfo, typeInfo, primaryType.packageName(), false, true);
    }

    /*
    The order in which we add is important! First come, first served.
     */
    private static Stream<FieldInfo> accessibleFieldsStream(InspectionProvider inspectionProvider,
                                                            TypeInfo typeInfo,
                                                            TypeInfo startingPoint,
                                                            String startingPointPackageName,
                                                            boolean staticFieldsOnly,
                                                            boolean includeEnclosing) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        TypeInfo primaryType = typeInfo.primaryType();

        boolean inSameCompilationUnit = typeInfo == startingPoint || primaryType == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit && primaryType.packageName().equals(startingPointPackageName);

        // my own field
        Stream<FieldInfo> localStream = typeInspection.fields().stream()
                .filter(fieldInfo -> acceptFieldInHierarchy(inspectionProvider, fieldInfo, inSameCompilationUnit,
                        inSamePackage, staticFieldsOnly));

        // my parent's fields
        Stream<FieldInfo> parentStream;
        boolean haveNonTrivialParent = typeInspection.parentClass() != null
                && !typeInspection.parentClass().typeInfo.isJavaLangObject();
        if (haveNonTrivialParent) {
            parentStream = accessibleFieldsStream(inspectionProvider, typeInspection.parentClass().typeInfo,
                    startingPoint, startingPointPackageName, staticFieldsOnly, includeEnclosing);
        } else parentStream = Stream.empty();
        Stream<FieldInfo> joint = Stream.concat(localStream, parentStream);

        // my interfaces' fields
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented()) {
            assert interfaceType.typeInfo != null;
            Stream<FieldInfo> fromInterface = accessibleFieldsStream(inspectionProvider, interfaceType.typeInfo,
                    startingPoint, startingPointPackageName, staticFieldsOnly, includeEnclosing);
            joint = Stream.concat(joint, fromInterface);
        }

        // my enclosing type's fields, but statics only when I'm a static nested type!
        Stream<FieldInfo> enclosingStream;
        if (includeEnclosing && typeInfo.packageNameOrEnclosingType.isRight()) {
            enclosingStream = accessibleFieldsStream(inspectionProvider,
                    typeInfo.packageNameOrEnclosingType.getRight(), startingPoint, startingPointPackageName,
                    typeInspection.isStatic(), true);
        } else {
            enclosingStream = Stream.empty();
        }
        return Stream.concat(joint, enclosingStream);
    }

    // all the fields to accept are from the type itself, or from super-types.
    private static boolean acceptFieldInHierarchy(InspectionProvider inspectionProvider, FieldInfo fieldInfo,
                                                  boolean inSameCompilationUnit,
                                                  boolean inSamePackage,
                                                  boolean staticFieldsOnly) {
        if (inSameCompilationUnit) return true;
        FieldInspection inspection = inspectionProvider.getFieldInspection(fieldInfo);
        if (staticFieldsOnly && !inspection.isStatic()) return false;
        return inspection.isPublic() ||
                inSamePackage && !inspection.isPrivate() ||
                !inSamePackage && inspection.isProtected() ||
                inspection.isPackagePrivate();
    }

    public G<TypeInfo> builtExternalTypeGraph() {
        if (externalTypeGraph.isSet()) return externalTypeGraph.get();
        synchronized (externalTypeGraph) {
            if (externalTypeGraph.isFirst()) {
                externalTypeGraph.set(externalTypeGraph.getFirst().build());
            }
            return externalTypeGraph.get();
        }
    }

    public G<TypeInfo> builtTypeGraph() {
        if (typeGraph.isSet()) return typeGraph.get();
        synchronized (typeGraph) {
            if (typeGraph.isFirst()) {
                typeGraph.set(typeGraph.getFirst().build());
            }
            return typeGraph.get();
        }
    }

    public G<MethodInfo> builtMethodCallGraph() {
        if (methodCallGraph.isSet()) return methodCallGraph.get();
        if (methodCallGraph.isFirst()) {
            methodCallGraph.set(methodCallGraph.getFirst().build());
        }
        return methodCallGraph.get();
    }

}

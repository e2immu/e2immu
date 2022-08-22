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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.DependencyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analysis.Analysis.AnalysisMode.CONTRACTED;

/*
The AnnotatedAPI analyser analyses types (and their methods, companion methods, fields)
that have been augmented from byte code with companions and annotations in Annotated API files.
Inspection started with the byte code inspector, and the TypeInspector and MethodInspector have
added to this using the $ system.

Types and fields are "trivially" analysed, in the sense that annotations are contracted and simply copied.
The ShallowFieldAnalyser is simply a utility class, the shallow type analysis is included in this type.
Both mainly rely on the TypeAnalysisImpl.Builder, and FieldAnalysisImpl.Builder.

The situation of methods is more complex.
The majority of methods of the types in this analyser are not even mentioned in annotated API files.
If mentioned there, the only action is to add contracted annotations. Both situations are handled
by a ShallowMethodAnalyser, which mostly checks consistency of contracted annotations.
(It is because of this action that method analysis is done AFTER type (and field) analysis).
There is one ShallowMethodAnalyser created for each method, because the shallow method analyser
can also be called from the PrimaryTypeAnalyser.

Secondly, the CompanionAnalyser analyser analyses companion methods to the shallowly analysed methods.

Finally, AnnotatedAPI files can contain small helper methods, to facilitate the composition of companion methods.
They need to be processed by a normal ComputingMethodAnalyser.
The AggregatingMethodAnalyser plays no role in the AnnotatedAPI analyser.
 */

public class AnnotatedAPIAnalyser implements AnalyserContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedAPIAnalyser.class);

    private final Configuration configuration;
    private final Messages messages = new Messages();
    private final Primitives primitives;
    private final ImportantClasses importantClasses;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final ShallowFieldAnalyser shallowFieldAnalyser;

    private final Map<TypeInfo, TypeAnalysisImpl.Builder> typeAnalyses;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final TypeMap typeMap;
    private final AnalyserProgram analyserProgram;

    public AnnotatedAPIAnalyser(List<TypeInfo> types,
                                Configuration configuration,
                                Primitives primitives,
                                ImportantClasses importantClasses,
                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                TypeMap typeMap) {
        this.typeMap = typeMap;
        shallowFieldAnalyser = new ShallowFieldAnalyser(this, this,
                e2ImmuAnnotationExpressions);

        this.primitives = primitives;
        this.importantClasses = importantClasses;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.analyserProgram = AnalyserProgram.PROGRAM_ALL;

        LOGGER.debug("Have {} types", types.size());

        DependencyGraph<TypeInfo> dependencyGraph = new DependencyGraph<>();
        for (TypeInfo typeInfo : types) {
            if (!typeInfo.isJavaLangObject()) {
                dependencyGraph.addNode(typeInfo, typeInfo.typeResolution.get().superTypesExcludingJavaLangObject());
            }
        }
        List<TypeInfo> sorted = dependencyGraph.sorted();
        sorted.add(0, typeMap.get(Object.class));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Order of shallow analysis:");
            sorted.forEach(typeInfo -> LOGGER.debug("  Type {} {}",
                    typeInfo.fullyQualifiedName,
                    typeInfo.typeInspection.get().parentClass() == null ? "NO PARENT" : ""));
        }
        assert checksOnOrder(sorted);

        typeAnalyses = new LinkedHashMap<>(); // we keep the order provided
        methodAnalysers = new LinkedHashMap<>(); // we keep the order!
        for (TypeInfo typeInfo : sorted) {
            TypeAnalysisImpl.Builder typeAnalysis = new TypeAnalysisImpl.Builder(CONTRACTED,
                    primitives, typeInfo, null);
            typeAnalyses.put(typeInfo, typeAnalysis);
            AtomicBoolean hasFinalizers = new AtomicBoolean();
            typeInfo.typeInspection.get()
                    .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                    .filter(methodInfo -> methodInfo.methodInspection.get().isPubliclyAccessible()
                            // the Object.clone() method is protected, but has to be accessible because it can be called on arrays
                            // see Independent1_5
                            || methodInfo.typeInfo.isJavaLangObject())
                    .forEach(methodInfo -> {
                        try {
                            if (TypeInfo.IS_FACT_FQN.equals(methodInfo.fullyQualifiedName())) {
                                analyseIsFact(methodInfo);
                            } else if (TypeInfo.IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName())) {
                                analyseIsKnown(methodInfo);
                            } else {
                                MethodAnalyser methodAnalyser = createAnalyser(methodInfo, typeAnalysis);
                                MethodInspection methodInspection = methodInfo.methodInspection.get();
                                if (methodInspection.hasContractedFinalizer()) hasFinalizers.set(true);
                                methodAnalyser.initialize();
                                methodAnalysers.put(methodInfo, methodAnalyser);
                            }
                        } catch (RuntimeException rte) {
                            LOGGER.error("Caught runtime exception shallowly analysing method {}",
                                    methodInfo.fullyQualifiedName);
                            throw rte;
                        }
                    });
            if (hasFinalizers.get()) {
                typeAnalysis.setProperty(Property.FINALIZER, DV.TRUE_DV);
            }
        }
    }

    private boolean checksOnOrder(List<TypeInfo> sorted) {
        int indexOfCollection = sorted.indexOf(typeMap.get(Collection.class));
        int indexOfAbstractCollection = sorted.indexOf(typeMap.get(AbstractCollection.class));
        return indexOfCollection < indexOfAbstractCollection;
    }

    public static int typeComparator(TypeInfo t1, TypeInfo t2) {
        if (t1 == t2 || t1.equals(t2)) throw new IllegalArgumentException();
        if (t1.isJavaLangObject()) return -1;
        if (t2.isJavaLangObject()) return 1;

        Set<TypeInfo> super1 = t1.typeResolution.get(t1.fullyQualifiedName).superTypesExcludingJavaLangObject();
        if (super1.contains(t2)) {
            System.out.println(t1.fullyQualifiedName + " => " + t2.fullyQualifiedName);
            return 1;
        }
        Set<TypeInfo> super2 = t2.typeResolution.get(t2.fullyQualifiedName).superTypesExcludingJavaLangObject();
        if (super2.contains(t1)) {
            System.out.println(t1.fullyQualifiedName + " <= " + t2.fullyQualifiedName);
            return -1;
        }
        int c = t1.fullyQualifiedName.compareTo(t2.fullyQualifiedName);
        assert c != 0;
        System.out.println(t1.fullyQualifiedName + (c < 0 ? " <- " : " -> ") + t2.fullyQualifiedName);
        return c;
    }

    private MethodAnalyser createAnalyser(MethodInfo methodInfo, TypeAnalysis typeAnalysis) {
        boolean isHelperFunctionOrAnnotationMethod = methodInfo.hasStatements();
        if (isHelperFunctionOrAnnotationMethod) {
            assert methodInfo.methodInspection.get().getCompanionMethods().isEmpty();
            return MethodAnalyserFactory.create(methodInfo, typeAnalysis,
                    false, true, this);
        }
        // shallow method analysis, potentially with companion analysis
        return MethodAnalyserFactory.createShallowMethodAnalyser(methodInfo, this, false);
    }

    /**
     * Main entry point
     *
     * @return the message stream of all sub-analysers
     */
    public Stream<Message> analyse() {
        LOGGER.debug("Starting AnnotatedAPI analysis on {} types", typeAnalyses.size());

        hardcodedCrucialClasses();

        // do the types first,
        typeAnalyses.forEach((typeInfo, typeAnalysis) -> {
            try {
                shallowTypeAnalysis(typeInfo, typeAnalysis, e2ImmuAnnotationExpressions);
            } catch (RuntimeException runtimeException) {
                LOGGER.error("Caught exception while shallowly analysing type " + typeInfo.fullyQualifiedName);
                throw runtimeException;
            }
        });

        // and then the fields
        typeAnalyses.forEach((typeInfo, typeAnalysis) -> {
            try {
                shallowFieldAnalysis(typeInfo);
            } catch (RuntimeException runtimeException) {
                LOGGER.error("Caught exception while shallowly analysing fields of type " + typeInfo.fullyQualifiedName);
                throw runtimeException;
            }
        });

        LOGGER.info("Finished AnnotatedAPI type and field analysis of {} types; have {} messages of my own",
                typeAnalyses.size(), messages.size());

        Map<MethodInfo, MethodAnalyser> nonShallowOrWithCompanions = new HashMap<>();
        methodAnalysers.forEach((methodInfo, analyser) -> {
            if (analyser instanceof ShallowMethodAnalyser) {
                try {
                    analyser.analyse(new Analyser.SharedState(0, false, null));
                } catch (RuntimeException runtimeException) {
                    LOGGER.error("Caught exception while shallowly analysing method " + methodInfo.fullyQualifiedName);
                    throw runtimeException;
                }
                boolean hasNoCompanionMethods = methodInfo.methodInspection.get().getCompanionMethods().isEmpty();
                if (hasNoCompanionMethods) {
                    methodInfo.setAnalysis(analyser.getAnalysis().build());
                } else {
                    nonShallowOrWithCompanions.put(methodInfo, analyser);
                }
            } else {
                nonShallowOrWithCompanions.put(methodInfo, analyser);
            }
        });
        LOGGER.info("Finished AnnotatedAPI shallow method analysis on methods without companion methods, {} remaining",
                nonShallowOrWithCompanions.size());
        if (!nonShallowOrWithCompanions.isEmpty()) {
            iterativeMethodAnalysis(nonShallowOrWithCompanions);
        }
        validateIndependence();

        return Stream.concat(methodAnalysers.values().stream().flatMap(MethodAnalyser::getMessageStream),
                Stream.concat(shallowFieldAnalyser.getMessageStream(), messages.getMessageStream()));
    }

    private void hardcodedCrucialClasses() {
        for (Class<?> clazz : new Class[]{Object.class,  // every class derives from Object
                Annotation.class, // every annotation derives from Annotation
                Enum.class}) {// every enum type derives from Enum
            TypeInfo typeInfo = typeMap.get(clazz);
            TypeAnalysisImpl.Builder typeAnalysis = typeAnalyses.get(typeInfo);
            typeAnalysis.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            typeAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV);
            typeAnalysis.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
            typeAnalysis.setImmutableCanBeIncreasedByTypeParameters(false);
        }
        for (Class<?> clazz : new Class[]{
                String.class,
                Double.class, Boolean.class, Character.class,
                Integer.class, Short.class, Float.class,
                Void.class, Byte.class,
                Long.class}) {
            TypeInfo typeInfo = typeMap.get(clazz);
            TypeAnalysisImpl.Builder typeAnalysis = typeAnalyses.get(typeInfo);
            typeAnalysis.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            typeAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV);
            typeAnalysis.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
            typeAnalysis.setImmutableCanBeIncreasedByTypeParameters(false);
        }
    }

    private void validateIndependence() {
        typeAnalyses.forEach(((typeInfo, typeAnalysis) -> {
            try {
                DV inMap = typeAnalysis.getPropertyFromMapNeverDelay(Property.INDEPENDENT);
                ValueExplanation computed = computeIndependent(typeInfo, typeAnalysis);
                validateIndependent(typeInfo, inMap, computed);
            } catch (IllegalStateException ise) {
                LOGGER.error("Caught exception while validating independence of {}", typeInfo);
                throw ise;
            }
        }));
    }

    /*
    Either the user has hinted a value, or simpleComputeIndependent has provided one.
    This value then affects the independence values of methods and parameters.
    The end result is computed again using computeIndependent.

    If the computed value differs from the initial one, we raise an error.
    If the computed value is HIGHER, we have made a programming error
    If the computed value is LOWER, the user should put a higher one
     */
    private void validateIndependent(TypeInfo typeInfo, DV inMap, ValueExplanation computed) {
        if (computed.value.isDone()) {
            if (!inMap.equals(computed.value)) {
                if (typeInfo.typeInspection.get().isPublic() && typeInfo.isNotJDKInternal()) {
                    Message message = Message.newMessage(typeInfo.newLocation(),
                            Message.Label.TYPE_HAS_DIFFERENT_VALUE_FOR_INDEPENDENT,
                            "Found " + inMap + ", computed " + computed.value
                                    + " in " + computed.explanation);
                    messages.add(message);
                }
            }
        } // else: we're at the edge of the known/analysed types, we're not exploring further and rely on the value
    }

    private record ValueExplanation(DV value, String explanation) {
    }

    // dedicated method exactly for this "isFact" method
    private void analyseIsFact(MethodInfo methodInfo) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(
                getPrimitives(), this, parameterInfo);
        parameterAnalysis.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        parameterAnalysis.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        parameterAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);

        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(CONTRACTED, getPrimitives(),
                this, this, methodInfo, parameterAnalyses);
        builder.ensureIsNotEventualUnlessOtherwiseAnnotated();
        builder.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        builder.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
        builder.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV);
        builder.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
        builder.companionAnalyses.freeze();
        VariableExpression ve = new VariableExpression(parameterInfo);
        builder.setSingleReturnValue(new InlinedMethod(Identifier.generate("isFact"), methodInfo, ve, Set.of(ve),
                false));
        LOGGER.debug("Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
        methodInfo.setAnalysis(builder.build());
    }


    // dedicated method exactly for this "isKnown" method
    private void analyseIsKnown(MethodInfo methodInfo) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(
                getPrimitives(), this, parameterInfo);
        parameterAnalysis.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        parameterAnalysis.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        parameterAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);

        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(CONTRACTED, getPrimitives(),
                this, this, methodInfo, parameterAnalyses);
        builder.ensureIsNotEventualUnlessOtherwiseAnnotated();
        builder.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        builder.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
        builder.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV);
        builder.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);

        builder.companionAnalyses.freeze();
        builder.setSingleReturnValue(UnknownExpression.forHardcodedMethodReturnValue(methodInfo.identifier,
                primitives.booleanParameterizedType(), "isKnown return value"));
        LOGGER.debug("Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
        methodInfo.setAnalysis(builder.build());
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public PatternMatcher<StatementAnalyser> getPatternMatcher() {
        return PatternMatcher.NO_PATTERN_MATCHER;
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public FieldAnalyserImpl getFieldAnalyser(FieldInfo fieldInfo) {
        return null;
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return Stream.empty();
    }

    @Override
    public MethodAnalyserImpl getMethodAnalyser(MethodInfo methodInfo) {
        return null;
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        MethodAnalysis methodAnalysis = getMethodAnalysis(parameterInfo.owner);
        return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser analyser = methodAnalysers.get(methodInfo);
        if (analyser != null) return analyser.getMethodAnalysis();
        assert methodInfo.methodAnalysis.isSet() : "No method analysis set for " + methodInfo.fullyQualifiedName;
        return methodInfo.methodAnalysis.get(methodInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalyserImpl getTypeAnalyser(TypeInfo typeInfo) {
        return null; // we don't have those
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeAnalyses.get(typeInfo);
        if (typeAnalysis != null) return typeAnalysis;
        return typeInfo.typeAnalysis.get(typeInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeAnalyses.get(typeInfo);
        if (typeAnalysis != null) return typeAnalysis;
        return typeInfo.typeAnalysis.getOrDefaultNull();
    }

    private void iterativeMethodAnalysis(Map<MethodInfo, MethodAnalyser> nonShallowOrWithCompanions) {
        int iteration = 0;

        while (!nonShallowOrWithCompanions.isEmpty()) {
            List<MethodInfo> methodsToRemove = new LinkedList<>();

            int effectivelyFinalIteration = iteration;

            AtomicBoolean delayed = new AtomicBoolean();
            AtomicBoolean progress = new AtomicBoolean();

            for (Map.Entry<MethodInfo, MethodAnalyser> entry : nonShallowOrWithCompanions.entrySet()) {
                MethodInfo methodInfo = entry.getKey();
                LOGGER.debug("Analysing {}", methodInfo.fullyQualifiedName());

                AtomicReference<AnalysisStatus> methodAnalysisStatus = new AtomicReference<>(AnalysisStatus.DONE);
                if (entry.getValue() instanceof ShallowMethodAnalyser shallowMethodAnalyser) {
                    MethodAnalysisImpl.Builder builder = shallowMethodAnalyser.methodAnalysis;
                    for (Map.Entry<CompanionMethodName, MethodInfo> e : methodInfo.methodInspection.get().getCompanionMethods().entrySet()) {
                        CompanionMethodName cmn = e.getKey();
                        if (!builder.companionAnalyses.isSet(cmn)) {
                            LOGGER.debug("Starting companion analyser for {}", cmn);

                            CompanionAnalyser companionAnalyser = new CompanionAnalyser(this,
                                    getTypeAnalysis(methodInfo.typeInfo), cmn, e.getValue(),
                                    methodInfo, AnnotationParameters.CONTRACT);
                            AnalysisStatus analysisStatus = companionAnalyser.analyse(effectivelyFinalIteration);
                            if (analysisStatus.isDone()) {
                                CompanionAnalysis companionAnalysis = companionAnalyser.companionAnalysis.build();
                                builder.companionAnalyses.put(cmn, companionAnalysis);
                            } else {
                                assert analysisStatus.isDelayed();
                                LOGGER.debug("Delaying analysis of {} in {}", cmn, methodInfo.fullyQualifiedName());
                                methodAnalysisStatus.set(analysisStatus);
                            }
                        }
                    }
                    builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD, true,
                            methodInfo.methodInspection.get().getAnnotations(), e2ImmuAnnotationExpressions);
                } else if (entry.getValue() instanceof ComputingMethodAnalyser computingMethodAnalyser) {
                    Analyser.SharedState shared = new Analyser.SharedState(effectivelyFinalIteration, false, null);
                    AnalyserResult analyserResult = computingMethodAnalyser.analyse(shared);
                    AnalysisStatus analysisStatus = analyserResult.analysisStatus();
                    if (analysisStatus != AnalysisStatus.DONE) {
                        LOGGER.debug("{} in analysis of {}, computing method analyser", analysisStatus,
                                methodInfo.fullyQualifiedName());
                        methodAnalysisStatus.set(analysisStatus);
                    }
                }
                if (methodAnalysisStatus.get().isDone()) {
                    methodsToRemove.add(methodInfo);
                    methodInfo.setAnalysis(entry.getValue().getAnalysis().build());
                    progress.set(true);
                } else if (methodAnalysisStatus.get().isDelayed()) {
                    if (methodAnalysisStatus.get().isProgress()) progress.set(true);
                    else delayed.set(true);
                } else throw new UnsupportedOperationException();
            }

            methodsToRemove.forEach(nonShallowOrWithCompanions.keySet()::remove);
            if (delayed.get() && !progress.get()) {
                throw new UnsupportedOperationException("No changes after iteration " + iteration +
                        "; have left: " + nonShallowOrWithCompanions.size());
            }
            LOGGER.debug("**** At end of iteration {} in shallow method analysis, removed {}, remaining {}",
                    iteration, methodsToRemove.size(), nonShallowOrWithCompanions.size());
            iteration++;

            if (iteration >= 20) throw new UnsupportedOperationException();
        }

    }

    private void shallowFieldAnalysis(TypeInfo typeInfo) {
        TypeInspection typeInspection = typeInfo.typeInspection.get();
        boolean isEnum = typeInspection.typeNature() == TypeNature.ENUM;
        typeInspection.fields().forEach(fieldInfo -> shallowFieldAnalyser.analyser(fieldInfo, isEnum));
    }

    private void shallowTypeAnalysis(TypeInfo typeInfo,
                                     TypeAnalysisImpl.Builder typeAnalysisBuilder,
                                     E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        TypeInspection typeInspection = typeInfo.typeInspection.get();
        Analyser.AnalyserIdentification identification = typeInfo.isAbstract()
                ? Analyser.AnalyserIdentification.ABSTRACT_TYPE
                : Analyser.AnalyserIdentification.TYPE;
        messages.addAll(typeAnalysisBuilder.fromAnnotationsIntoProperties(identification,
                true, typeInspection.getAnnotations(), e2ImmuAnnotationExpressions));

        ComputingTypeAnalyser.findAspects(typeAnalysisBuilder, typeInfo);
        typeAnalysisBuilder.freezeApprovedPreconditionsE1();
        typeAnalysisBuilder.freezeApprovedPreconditionsE2();

        /*
        The computation of hidden content types proceeds as follows:
        1. all unbound type parameters are hidden content

        IMPROVE currently not implementing 2.
        2. to the hidden content we add all public field types, method return types and method parameter types
           that are immutable with hidden content.

        This computation does not differentiate between interfaces (which provide a specification only) and classes
        which provide specification and implementation: we cannot see inside the class anyway in this analyser.
         */
        Set<ParameterizedType> typeParametersAsParameterizedTypes = typeInspection.typeParameters().stream()
                .filter(TypeParameter::isUnbound)
                .map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toSet());
        SetOfTypes hiddenContentTypes = new SetOfTypes(typeParametersAsParameterizedTypes);
        typeAnalysisBuilder.setHiddenContentTypes(hiddenContentTypes);

        ensureImmutableAndContainerInShallowTypeAnalysis(typeAnalysisBuilder);
        simpleComputeIndependent(typeAnalysisBuilder);

        determineImmutableDeterminedByTypeParameters(typeInspection, typeAnalysisBuilder);

        // and close!
        TypeAnalysis typeAnalysis = typeAnalysisBuilder.build();
        typeInfo.typeAnalysis.set(typeAnalysis);
    }

    private void ensureImmutableAndContainerInShallowTypeAnalysis(TypeAnalysisImpl.Builder builder) {
        DV immutable = builder.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        if (immutable.isDelayed()) {
            builder.setProperty(Property.IMMUTABLE, MultiLevel.MUTABLE_DV);
        }
        DV container = builder.getPropertyFromMapDelayWhenAbsent(Property.CONTAINER);
        if (container.isDelayed()) {
            builder.setProperty(Property.CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        }
    }

    private void determineImmutableDeterminedByTypeParameters(TypeInspection typeInspection,
                                                              TypeAnalysisImpl.Builder typeAnalysisBuilder) {
        if (typeAnalysisBuilder.immutableDeterminedByTypeParameters().isDelayed()) {
            boolean res = typeInspection.typeParameters().stream()
                    .anyMatch(tp -> Boolean.TRUE != tp.isAnnotatedWithIndependent());
            typeAnalysisBuilder.setImmutableCanBeIncreasedByTypeParameters(res);
        }
    }

    /*
    relations to super-type:

    if the super-type is @Independent with hidden content, we can go anywhere (e.g. Serializable)
    if the super-type is @Independent without hidden content, we can go anywhere (minimum works well)
    if the super-type is @Dependent, we must have dependent
     */

    private ValueExplanation computeIndependent(TypeInfo typeInfo, TypeAnalysis typeAnalysis) {
        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
            return new ValueExplanation(MultiLevel.INDEPENDENT_DV, "immutable");
        }
        Stream<ValueExplanation> methodStream = typeInfo.typeInspection.get()
                .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodInspection.get().isPubliclyAccessible())
                .map(m -> new ValueExplanation(getMethodAnalysis(m).getProperty(Property.INDEPENDENT),
                        m.fullyQualifiedName));
        Stream<ValueExplanation> parameterStream = typeInfo.typeInspection.get()
                .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodInspection.get().isPubliclyAccessible())
                .flatMap(m -> getMethodAnalysis(m).getParameterAnalyses().stream())
                .map(p -> new ValueExplanation(p.getProperty(Property.INDEPENDENT), p.getParameterInfo().fullyQualifiedName));
        ValueExplanation myMethods =
                Stream.concat(methodStream, parameterStream)
                        .min(Comparator.comparing(p -> p.value.value()))
                        .orElse(new ValueExplanation(Property.INDEPENDENT.bestDv, "none"));

        Stream<TypeInfo> superTypes = typeInfo.typeResolution.get().superTypesExcludingJavaLangObject()
                .stream();
        ValueExplanation fromSuperTypes = superTypes
                .filter(t -> t.typeInspection.get().isPublic())
                .map(this::getTypeAnalysis)
                .map(ta -> new ValueExplanation(MultiLevel.dropHiddenContentOfIndependent(ta.getProperty(Property.INDEPENDENT)),
                        ta.getTypeInfo().fullyQualifiedName))
                .min(Comparator.comparing(p -> p.value.value()))
                .orElse(new ValueExplanation(Property.INDEPENDENT.bestDv, "none"));
        return myMethods.value.lt(fromSuperTypes.value) ? myMethods : fromSuperTypes;
    }

    /*
     In some situations, the INDEPENDENT value is easy to compute.
     Because we have a chicken-and-egg problem (the independent value can be computed from the methods, but the
     parameters may require an independent value, it is better to assign a value when obviously possible.
     */
    private void simpleComputeIndependent(TypeAnalysisImpl.Builder builder) {
        DV immutable = builder.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        DV independent = MultiLevel.independentCorrespondingToImmutableLevelDv(MultiLevel.level(immutable));
        if (inMap.isDelayed()) {
            if (immutable.ge(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV)) {
                // minimal value; we'd have an inconsistency otherwise
                builder.setProperty(Property.INDEPENDENT, independent);
                return;
            }

            boolean allMethodsOnlyPrimitives =
                    builder.getTypeInfo().typeInspection.get()
                            .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                            .filter(m -> m.methodInspection.get().isPubliclyAccessible())
                            .allMatch(m -> (m.isConstructor || m.isVoid() || m.returnType().isPrimitiveStringClass())
                                    && m.methodInspection.get().getParameters().stream().allMatch(p -> p.parameterizedType.isPrimitiveStringClass()));
            if (allMethodsOnlyPrimitives) {
                Stream<TypeInfo> superTypes = builder.typeInfo.typeResolution.get().superTypesExcludingJavaLangObject()
                        .stream();
                DV fromSuperTypes = superTypes
                        .filter(t -> t.typeInspection.get().isPublic())
                        .map(this::getTypeAnalysis)
                        .map(ta -> MultiLevel.dropHiddenContentOfIndependent(ta.getProperty(Property.INDEPENDENT)))
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                if (fromSuperTypes.isDone()) {
                    builder.setProperty(Property.INDEPENDENT, fromSuperTypes);
                    return;
                }
            }
            // fallback
            builder.setProperty(Property.INDEPENDENT, MultiLevel.DEPENDENT_DV);
        } else if (immutable.ge(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV) && inMap.lt(independent)) {
            messages.add(Message.newMessage(builder.typeInfo.newLocation(),
                    Message.Label.INCONSISTENT_INDEPENDENCE_VALUE));
        }
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return true;
    }

    @Override
    public AnalyserProgram getAnalyserProgram() {
        return analyserProgram;
    }

    @Override
    public ImportantClasses importantClasses() {
        return importantClasses;
    }
}

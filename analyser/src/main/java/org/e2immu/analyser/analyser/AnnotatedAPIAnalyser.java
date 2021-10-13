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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.util.Logger;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.Analysis.AnalysisMode.CONTRACTED;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

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

    public static final String IS_FACT_FQN = "org.e2immu.annotatedapi.AnnotatedAPI.isFact(boolean)";
    public static final String IS_KNOWN_FQN = "org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)";

    private final Configuration configuration;
    private final Messages messages = new Messages();
    private final Primitives primitives;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final ShallowFieldAnalyser shallowFieldAnalyser;

    private final Map<TypeInfo, TypeAnalysis> typeAnalyses;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final TypeMap typeMap;

    public AnnotatedAPIAnalyser(List<TypeInfo> types,
                                Configuration configuration,
                                Primitives primitives,
                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                TypeMap typeMap) {
        this.typeMap = typeMap;
        shallowFieldAnalyser = new ShallowFieldAnalyser(this, this,
                e2ImmuAnnotationExpressions);

        this.primitives = primitives;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;

        log(ANALYSER, "Have {} types", types.size());

        DependencyGraph<TypeInfo> dependencyGraph = new DependencyGraph<>();
        for (TypeInfo typeInfo : types) {
            if (!Primitives.isJavaLangObject(typeInfo)) {
                dependencyGraph.addNode(typeInfo, typeInfo.typeResolution.get().superTypesExcludingJavaLangObject());
            }
        }
        List<TypeInfo> sorted = dependencyGraph.sorted();
        sorted.add(0, typeMap.get(Object.class));
        if (Logger.isLogEnabled(ANALYSER)) {
            log(ANALYSER, "Order of shallow analysis:");
            sorted.forEach(typeInfo -> log(ANALYSER, "  Type {} {}",
                    typeInfo.fullyQualifiedName,
                    typeInfo.typeInspection.get().parentClass() == null ? "NO PARENT" : ""));
        }
        assert checksOnOrder(sorted);

        typeAnalyses = new LinkedHashMap<>(); // we keep the order provided
        methodAnalysers = new LinkedHashMap<>(); // we keep the order!
        for (TypeInfo typeInfo : sorted) {
            if (typeInfo.isPublic()) {
                TypeAnalysisImpl.Builder typeAnalysis = new TypeAnalysisImpl.Builder(CONTRACTED,
                        primitives, typeInfo, null);
                typeAnalyses.put(typeInfo, typeAnalysis);
                AtomicBoolean hasFinalizers = new AtomicBoolean();
                typeInfo.typeInspection.get()
                        .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                        .filter(methodInfo -> methodInfo.methodInspection.get().isPublic())
                        .forEach(methodInfo -> {
                            try {
                                if (IS_FACT_FQN.equals(methodInfo.fullyQualifiedName())) {
                                    analyseIsFact(methodInfo);
                                } else if (IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName())) {
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
                    typeAnalysis.setProperty(VariableProperty.FINALIZER, Level.TRUE);
                }
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
        if (Primitives.isJavaLangObject(t1)) return -1;
        if (Primitives.isJavaLangObject(t2)) return 1;

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
        log(ANALYSER, "Starting AnnotatedAPI analysis on {} types", typeAnalyses.size());

        hardcodedCrucialClasses();

        // do the types first,
        typeAnalyses.forEach((typeInfo, typeAnalysis) -> {
            try {
                shallowTypeAnalysis(typeInfo, (TypeAnalysisImpl.Builder) typeAnalysis, e2ImmuAnnotationExpressions);
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
                    analyser.analyse(0, null);
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

        return Stream.concat(methodAnalysers.values().stream().flatMap(AbstractAnalyser::getMessageStream),
                Stream.concat(shallowFieldAnalyser.getMessageStream(), messages.getMessageStream()));
    }

    private void hardcodedCrucialClasses() {
        for (Class<?> clazz : new Class[]{Object.class,  // every class derives from Object
                Annotation.class, // every annotation derives from Annotation
                Enum.class, // every enum type derives from Enum
                String.class, // every toString method
                Double.class,
                Integer.class,
                Long.class}) {
            TypeInfo typeInfo = typeMap.get(clazz);
            TypeAnalysisImpl.Builder typeAnalysis = (TypeAnalysisImpl.Builder) typeAnalyses.get(typeInfo);
            typeAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            typeAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE);
            typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.TRUE);
            typeAnalysis.immutableCanBeIncreasedByTypeParameters.set(false);
        }
    }

    private void validateIndependence() {
        typeAnalyses.forEach(((typeInfo, typeAnalysis) -> {
            int inMap = typeAnalysis.getPropertyFromMapNeverDelay(VariableProperty.INDEPENDENT);
            ValueExplanation computed = computeIndependent(typeInfo);
            // some "Type @Independent lower than its methods allow"-errors (a.o. java.lang.String)
            if (inMap > computed.value) {
                Message message = Message.newMessage(new Location(typeInfo),
                        Message.Label.TYPE_HAS_HIGHER_VALUE_FOR_INDEPENDENT,
                        "Found " + inMap + ", computed maximally " + computed.value
                                + " in " + computed.explanation);
                messages.add(message);
            }
        }));
    }

    private record ValueExplanation(int value, String explanation) {
    }

    private ValueExplanation computeIndependent(TypeInfo typeInfo) {
        ValueExplanation myMethods =
                typeInfo.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                        .filter(m -> m.methodInspection.get().isPublic())
                        .map(m -> new ValueExplanation(getMethodAnalysis(m).getProperty(VariableProperty.INDEPENDENT),
                                m.fullyQualifiedName))
                        .min(Comparator.comparing(p -> p.value))
                        .orElse(new ValueExplanation(VariableProperty.INDEPENDENT.best, "none"));
        Stream<TypeInfo> superTypes = typeInfo.typeResolution.get().superTypesExcludingJavaLangObject()
                .stream();
        ValueExplanation fromSuperTypes = superTypes
                .filter(TypeInfo::isPublic)
                .map(this::getTypeAnalysis)
                .map(ta -> new ValueExplanation(ta.getProperty(VariableProperty.INDEPENDENT),
                        ta.getTypeInfo().fullyQualifiedName))
                .min(Comparator.comparing(p -> p.value))
                .orElse(new ValueExplanation(VariableProperty.INDEPENDENT.best, "none"));
        return myMethods.value < fromSuperTypes.value ? myMethods : fromSuperTypes;
    }

    // dedicated method exactly for this "isFact" method
    private void analyseIsFact(MethodInfo methodInfo) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(
                getPrimitives(), this, parameterInfo);
        parameterAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        parameterAnalysis.setProperty(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL);
        parameterAnalysis.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        parameterAnalysis.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);

        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(CONTRACTED, getPrimitives(),
                this, this, methodInfo, parameterAnalyses);
        builder.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        builder.setProperty(VariableProperty.FLUENT, Level.FALSE);
        builder.setProperty(VariableProperty.MODIFIED_METHOD, Level.FALSE);
        builder.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
        builder.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE);
        builder.setProperty(VariableProperty.CONTAINER, Level.TRUE);
        builder.companionAnalyses.freeze();
        builder.singleReturnValue.set(new InlinedMethod(Identifier.generate(),
                methodInfo, new VariableExpression(parameterInfo), Set.of(parameterInfo), false));
        log(ANALYSER, "Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
        methodInfo.setAnalysis(builder.build());
    }


    // dedicated method exactly for this "isKnown" method
    private void analyseIsKnown(MethodInfo methodInfo) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(
                getPrimitives(), this, parameterInfo);
        parameterAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        parameterAnalysis.setProperty(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL);
        parameterAnalysis.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        parameterAnalysis.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        parameterAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);

        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(CONTRACTED, getPrimitives(),
                this, this, methodInfo, parameterAnalyses);
        builder.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        builder.setProperty(VariableProperty.FLUENT, Level.FALSE);
        builder.setProperty(VariableProperty.MODIFIED_METHOD, Level.FALSE);
        builder.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
        builder.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE);
        builder.setProperty(VariableProperty.CONTAINER, Level.TRUE);
        builder.companionAnalyses.freeze();
        builder.singleReturnValue.set(new UnknownExpression(primitives.booleanParameterizedType, "isKnown return value"));
        log(ANALYSER, "Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
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
    public FieldAnalyser getFieldAnalyser(FieldInfo fieldInfo) {
        return null; // IMPROVE ME
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return Stream.empty(); // IMPROVE ME
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        throw new UnsupportedOperationException();
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
        AbstractAnalyser analyser = methodAnalysers.get(methodInfo);
        if (analyser != null) return (MethodAnalysis) analyser.getAnalysis();
        return methodInfo.methodAnalysis.get(methodInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        throw new UnsupportedOperationException();
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
        return typeInfo.typeAnalysis.isSet() ? typeInfo.typeAnalysis.get(typeInfo.fullyQualifiedName) : null;
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
                log(ANALYSER, "Analysing {}", methodInfo.fullyQualifiedName());

                AtomicReference<AnalysisStatus> methodAnalysisStatus = new AtomicReference<>(AnalysisStatus.DONE);
                if (entry.getValue() instanceof ShallowMethodAnalyser shallowMethodAnalyser) {
                    MethodAnalysisImpl.Builder builder = shallowMethodAnalyser.methodAnalysis;
                    for (Map.Entry<CompanionMethodName, MethodInfo> e : methodInfo.methodInspection.get().getCompanionMethods().entrySet()) {
                        CompanionMethodName cmn = e.getKey();
                        if (!builder.companionAnalyses.isSet(cmn)) {
                            log(ANALYSER, "Starting companion analyser for {}", cmn);

                            CompanionAnalyser companionAnalyser = new CompanionAnalyser(this,
                                    getTypeAnalysis(methodInfo.typeInfo), cmn, e.getValue(),
                                    methodInfo, AnnotationParameters.CONTRACT);
                            AnalysisStatus analysisStatus = companionAnalyser.analyse(effectivelyFinalIteration);
                            if (analysisStatus == AnalysisStatus.DONE) {
                                CompanionAnalysis companionAnalysis = companionAnalyser.companionAnalysis.build();
                                builder.companionAnalyses.put(cmn, companionAnalysis);
                            } else {
                                log(DELAYED, "Delaying analysis of {} in {}", cmn, methodInfo.fullyQualifiedName());
                                methodAnalysisStatus.set(AnalysisStatus.DELAYS);
                            }
                        }
                    }
                    builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD, true,
                            methodInfo.methodInspection.get().getAnnotations(), e2ImmuAnnotationExpressions);
                } else if (entry.getValue() instanceof ComputingMethodAnalyser computingMethodAnalyser) {
                    AnalysisStatus analysisStatus = computingMethodAnalyser.analyse(effectivelyFinalIteration, null);
                    if (analysisStatus != AnalysisStatus.DONE) {
                        log(DELAYED, "{} in analysis of {}, full method analyser", analysisStatus,
                                methodInfo.fullyQualifiedName());
                        methodAnalysisStatus.set(analysisStatus);
                    }
                }
                switch (methodAnalysisStatus.get()) {
                    case DONE -> {
                        methodsToRemove.add(methodInfo);
                        methodInfo.setAnalysis(entry.getValue().getAnalysis().build());
                        progress.set(true);
                    }
                    case DELAYS -> delayed.set(true);
                    case PROGRESS -> progress.set(true);
                }
            }

            methodsToRemove.forEach(nonShallowOrWithCompanions.keySet()::remove);
            if (delayed.get() && !progress.get()) {
                throw new UnsupportedOperationException("No changes after iteration " + iteration +
                        "; have left: " + nonShallowOrWithCompanions.size());
            }
            log(ANALYSER, "**** At end of iteration {} in shallow method analysis, removed {}, remaining {}",
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
        messages.addAll(typeAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.TYPE,
                true, typeInspection.getAnnotations(), e2ImmuAnnotationExpressions));

        ComputingTypeAnalyser.findAspects(typeAnalysisBuilder, typeInfo);
        typeAnalysisBuilder.freezeApprovedPreconditionsE1();
        typeAnalysisBuilder.freezeApprovedPreconditionsE2();

        Set<ParameterizedType> typeParametersAsParameterizedTypes = typeInspection.typeParameters().stream()
                .map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toSet());
        typeAnalysisBuilder.transparentDataTypes.set(typeParametersAsParameterizedTypes);

        simpleComputeIndependent(typeAnalysisBuilder);

        determineImmutableCanBeIncreasedByTypeParameters(typeInspection, typeAnalysisBuilder);

        // and close!
        TypeAnalysis typeAnalysis = typeAnalysisBuilder.build();
        typeInfo.typeAnalysis.set(typeAnalysis);
    }

    private void determineImmutableCanBeIncreasedByTypeParameters(TypeInspection typeInspection,
                                                                  TypeAnalysisImpl.Builder typeAnalysisBuilder) {
        if (!typeAnalysisBuilder.immutableCanBeIncreasedByTypeParameters.isSet()) {
            boolean res = typeInspection.typeParameters().stream()
                    .anyMatch(tp -> Boolean.TRUE != tp.isAnnotatedWithIndependent());
            typeAnalysisBuilder.immutableCanBeIncreasedByTypeParameters.set(res);
        }
    }

    private void simpleComputeIndependent(TypeAnalysisImpl.Builder builder) {
        int immutable = builder.getPropertyFromMapDelayWhenAbsent(VariableProperty.IMMUTABLE);
        int inMap = builder.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT);
        int independent = MultiLevel.composeOneLevelLess(immutable);
        if (inMap == Level.DELAY) {
            boolean allMethodsOnlyPrimitives =
                    builder.getTypeInfo().typeInspection.get()
                            .methodsAndConstructors(TypeInspection.Methods.INCLUDE_SUPERTYPES)
                            .filter(m -> m.methodInspection.get().isPublic())
                            .allMatch(m -> (m.isConstructor || m.isVoid() || Primitives.isPrimitiveExcludingVoid(m.returnType()))
                                    && m.methodInspection.get().getParameters().stream().allMatch(p -> Primitives.isPrimitiveExcludingVoid(p.parameterizedType)));
            if (allMethodsOnlyPrimitives) {
                builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
                return;
            }
            if (immutable >= MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                // minimal value; we'd have an inconsistency otherwise
                builder.setProperty(VariableProperty.INDEPENDENT, independent);
            }
        } else if (immutable >= MultiLevel.EFFECTIVELY_E2IMMUTABLE && inMap < independent) {
            messages.add(Message.newMessage(new Location(builder.typeInfo),
                    Message.Label.INCONSISTENT_INDEPENDENCE_VALUE));
        }
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return true;
    }
}

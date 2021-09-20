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
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.Logger;

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

    public AnnotatedAPIAnalyser(List<TypeInfo> types,
                                Configuration configuration,
                                Primitives primitives,
                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        shallowFieldAnalyser = new ShallowFieldAnalyser(this, e2ImmuAnnotationExpressions);

        if (Logger.isLogEnabled(ANALYSER)) {
            log(ANALYSER, "Order of shallow analysis:");
            types.forEach(typeInfo -> log(ANALYSER, "  Type " + typeInfo.fullyQualifiedName));
        }
        this.primitives = primitives;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;

        typeAnalyses = new LinkedHashMap<>(); // we keep the order provided
        Map<MethodInfo, MethodAnalyser> methodAnalysers = new HashMap<>();
        for (TypeInfo typeInfo : types) {
            TypeAnalysisImpl.Builder typeAnalysis = new TypeAnalysisImpl.Builder(CONTRACTED,
                    primitives, typeInfo, null);
            typeAnalyses.put(typeInfo, typeAnalysis);
            AtomicBoolean hasFinalizers = new AtomicBoolean();
            typeInfo.typeInspection.get()
                    .methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
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
        this.methodAnalysers = Map.copyOf(methodAnalysers);
    }

    private MethodAnalyser createAnalyser(MethodInfo methodInfo, TypeAnalysis typeAnalysis) {
        boolean isHelperFunctionOrAnnotationMethod = methodInfo.hasStatements();
        if (isHelperFunctionOrAnnotationMethod) {
            assert methodInfo.methodInspection.get().getCompanionMethods().isEmpty();
            return MethodAnalyserFactory.create(methodInfo, typeAnalysis,
                    false, true, this);
        }
        // shallow method analysis, potentially with companion analysis
        return MethodAnalyserFactory.createShallowMethodAnalyser(methodInfo, this);
    }

    /**
     * Main entry point
     *
     * @return the message stream of all sub-analysers
     */
    public Stream<Message> analyse() {
        log(ANALYSER, "Starting AnnotatedAPI analysis on {} types", typeAnalyses.size());

        // do the types and fields
        typeAnalyses.forEach((typeInfo, typeAnalysis) -> {
            // process all types and fields; no need to recurse into sub-types, they're included among the primary types
            shallowTypeAndFieldAnalysis(typeInfo, (TypeAnalysisImpl.Builder) typeAnalysis, e2ImmuAnnotationExpressions);
        });

        Map<MethodInfo, MethodAnalyser> nonShallowOrWithCompanions = new HashMap<>();
        methodAnalysers.forEach((methodInfo, analyser) -> {
            if (analyser instanceof ShallowMethodAnalyser shallowMethodAnalyser) {
                shallowMethodAnalyser.analyse();
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
        if (!nonShallowOrWithCompanions.isEmpty()) {
            iterativeMethodAnalysis(nonShallowOrWithCompanions);
        }
        return Stream.concat(methodAnalysers.values().stream().flatMap(AbstractAnalyser::getMessageStream),
                Stream.concat(shallowFieldAnalyser.getMessageStream(), messages.getMessageStream()));
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
        builder.setProperty(VariableProperty.MODIFIED_METHOD, Level.FALSE);
        builder.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        builder.setProperty(VariableProperty.INDEPENDENT, Level.TRUE);
        builder.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
        builder.setProperty(VariableProperty.CONTAINER, Level.FALSE);
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
        builder.setProperty(VariableProperty.MODIFIED_METHOD, Level.FALSE);
        builder.setProperty(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
        builder.setProperty(VariableProperty.INDEPENDENT, Level.TRUE);
        builder.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE);
        builder.setProperty(VariableProperty.CONTAINER, Level.FALSE);
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
        return methodInfo.methodAnalysis.get();
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
                                    methodInfo.typeInfo.typeAnalysis.get(), cmn, e.getValue(),
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

    private void shallowTypeAndFieldAnalysis(TypeInfo typeInfo,
                                             TypeAnalysisImpl.Builder typeAnalysisBuilder,
                                             E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        TypeInspection typeInspection = typeInfo.typeInspection.get();
        messages.addAll(typeAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, typeInspection.getAnnotations(), e2ImmuAnnotationExpressions));

        ComputingTypeAnalyser.findAspects(typeAnalysisBuilder, typeInfo);
        typeAnalysisBuilder.freezeApprovedPreconditionsE1();
        typeAnalysisBuilder.freezeApprovedPreconditionsE2();

        Set<ParameterizedType> typeParametersAsParameterizedTypes = typeInspection.typeParameters().stream()
                .map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toSet());
        typeAnalysisBuilder.transparentDataTypes.set(typeParametersAsParameterizedTypes);
        TypeAnalysis typeAnalysis = typeAnalysisBuilder.build();
        typeInfo.typeAnalysis.set(typeAnalysis);

        boolean isEnum = typeInspection.typeNature() == TypeNature.ENUM;
        typeInspection.fields().forEach(fieldInfo -> shallowFieldAnalyser.analyser(fieldInfo, isEnum));
    }


    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return true;
    }
}

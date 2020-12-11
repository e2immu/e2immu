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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class ShallowTypeAnalyser implements AnalyserContext {

    public static final String IS_FACT_FQN = "org.e2immu.annotatedapi.AnnotatedAPI.isFact(boolean)";
    public static final String IS_KNOWN_FQN = "org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)";

    private final Configuration configuration;
    private final Messages messages = new Messages();
    private final Primitives primitives;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final ShallowMethodAnalyser shallowMethodAnalyser;

    private final Map<TypeInfo, TypeAnalysis> typeAnalyses;
    private final Map<MethodInfo, MethodAnalysis> methodAnalyses;
    private final Map<MethodInfo, Either<MethodAnalyser, MethodAnalysisImpl.Builder>> buildersForCompanionAnalysis = new LinkedHashMap<>();

    public ShallowTypeAnalyser(List<TypeInfo> types, Configuration configuration, Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

        if (Logger.isLogEnabled(ANALYSER)) {
            log(ANALYSER, "Order of shallow analysis:");
            types.forEach(typeInfo -> log(ANALYSER, "  Type " + typeInfo.fullyQualifiedName));
        }
        this.primitives = primitives;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.shallowMethodAnalyser = new ShallowMethodAnalyser(primitives, this, e2ImmuAnnotationExpressions);

        typeAnalyses = new LinkedHashMap<>(); // we keep the order provided
        ImmutableMap.Builder<MethodInfo, MethodAnalysis> methodAnalysesBuilder = new ImmutableMap.Builder<>();
        for (TypeInfo typeInfo : types) {
            TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(primitives, typeInfo);
            typeAnalyses.put(typeInfo, typeAnalysis);

            typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).forEach(methodInfo -> {

                if (IS_FACT_FQN.equals(methodInfo.fullyQualifiedName())) {
                    analyseIsFact(methodInfo);
                } else if (IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName())) {
                    analyseIsKnown(methodInfo);
                } else {
                    MethodAnalysisImpl.Builder methodAnalysisBuilder;

                    boolean hasNoCompanionMethods = methodInfo.methodInspection.get().getCompanionMethods().isEmpty();
                    if (hasNoCompanionMethods && methodInfo.hasStatements()) {
                        // normal method analysis

                        MethodAnalyser methodAnalyser = new MethodAnalyser(methodInfo, typeAnalysis, false, this);
                        methodAnalyser.initialize(); // sets the field analysers, not implemented yet.
                        buildersForCompanionAnalysis.put(methodInfo, Either.left(methodAnalyser));
                        methodAnalysisBuilder = methodAnalyser.methodAnalysis;
                    } else {
                        // shallow method analysis, companion analysis
                        methodAnalysisBuilder = shallowMethodAnalyser.copyAnnotationsIntoMethodAnalysisProperties(methodInfo);
                        if (hasNoCompanionMethods) {
                            MethodAnalysis methodAnalysis = (MethodAnalysis) methodAnalysisBuilder.build();
                            methodInfo.setAnalysis(methodAnalysis); // also sets parameter analyses
                        } else {
                            buildersForCompanionAnalysis.put(methodInfo, Either.right(methodAnalysisBuilder));
                        }
                    }

                    methodAnalysesBuilder.put(methodInfo, methodAnalysisBuilder);
                }
            });
        }
        methodAnalyses = methodAnalysesBuilder.build();
    }

    // dedicated method exactly for this "isFact" method
    private void analyseIsFact(MethodInfo methodInfo) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(getPrimitives(), this, parameterInfo);
        parameterAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        parameterAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        parameterAnalysis.setProperty(VariableProperty.MODIFIED, Level.FALSE);
        parameterAnalysis.isAssignedToAField.set(false);

        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(false, getPrimitives(), this, methodInfo, parameterAnalyses);
        builder.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        builder.setProperty(VariableProperty.MODIFIED, Level.FALSE);
        builder.setProperty(VariableProperty.INDEPENDENT, Level.TRUE);
        builder.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
        builder.setProperty(VariableProperty.CONTAINER, Level.FALSE);
        builder.companionAnalyses.freeze();
        builder.singleReturnValueImmutable.set(MultiLevel.MUTABLE);
        builder.singleReturnValue.set(new InlinedMethod(methodInfo, new VariableExpression(parameterInfo), InlinedMethod.Applicability.EVERYWHERE));
        log(ANALYSER, "Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
        methodInfo.setAnalysis(builder.build());
    }


    // dedicated method exactly for this "isKnown" method
    private void analyseIsKnown(MethodInfo methodInfo) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(getPrimitives(), this, parameterInfo);
        parameterAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        parameterAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        parameterAnalysis.setProperty(VariableProperty.MODIFIED, Level.FALSE);
        parameterAnalysis.isAssignedToAField.set(false);

        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(false, getPrimitives(), this, methodInfo, parameterAnalyses);
        builder.setProperty(VariableProperty.IDENTITY, Level.FALSE);
        builder.setProperty(VariableProperty.MODIFIED, Level.FALSE);
        builder.setProperty(VariableProperty.INDEPENDENT, Level.TRUE);
        builder.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        builder.setProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE);
        builder.setProperty(VariableProperty.CONTAINER, Level.FALSE);
        builder.companionAnalyses.freeze();
        builder.singleReturnValueImmutable.set(MultiLevel.EFFECTIVELY_E2IMMUTABLE);
        builder.singleReturnValue.set(new UnknownExpression(primitives.booleanParameterizedType, "<isKnown return value>"));
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
    public Map<FieldInfo, FieldAnalyser> getFieldAnalysers() {
        return Map.of(); // IMPROVE
    }

    @Override
    public Map<MethodInfo, MethodAnalyser> getMethodAnalysers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        MethodAnalysis methodAnalysis = getMethodAnalysis(parameterInfo.owner);
        return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalysis methodAnalysis = methodAnalyses.get(methodInfo);
        if (methodAnalysis != null) return methodAnalysis;
        return methodInfo.methodAnalysis.get();
    }

    @Override
    public Map<TypeInfo, TypeAnalyser> getTypeAnalysers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeAnalyses.get(typeInfo);
        if (typeAnalysis != null) return typeAnalysis;
        return typeInfo.typeAnalysis.get();
    }

    public Stream<Message> analyse() {
        shallowTypeAndFieldAnalysis();
        methodAnalysis();
        return Stream.concat(shallowMethodAnalyser.getMessageStream(), messages.getMessageStream());
    }

    private void shallowTypeAndFieldAnalysis() {
        log(ANALYSER, "Starting shallow analysis on {} types, {} methods", typeAnalyses.size(), methodAnalyses.size());

        // do the types and fields
        typeAnalyses.forEach((typeInfo, typeAnalysis) -> {
            // do types and fields; no need to recurse into sub-types, they're included among the primary types
            shallowTypeAndFieldAnalysis(typeInfo, (TypeAnalysisImpl.Builder) typeAnalysis, e2ImmuAnnotationExpressions);
        });
    }

    private void methodAnalysis() {
        int iteration = 0;
        while (!buildersForCompanionAnalysis.isEmpty()) {
            List<MethodInfo> keysToRemove = new LinkedList<>();

            int effectivelyFinalIteration = iteration;

            AtomicBoolean delayed = new AtomicBoolean();
            AtomicBoolean progress = new AtomicBoolean();

            buildersForCompanionAnalysis.forEach(((methodInfo, either) -> {
                log(ANALYSER, "Shallowly analysing {}", methodInfo.fullyQualifiedName());

                AtomicReference<AnalysisStatus> methodAnalysisStatus = new AtomicReference<>(AnalysisStatus.DONE);
                if (either.isRight()) {
                    MethodAnalysisImpl.Builder builder = either.getRight();
                    methodInfo.methodInspection.get().getCompanionMethods().forEach((cmn, companionMethod) -> {
                        if (!builder.companionAnalyses.isSet(cmn)) {
                            log(ANALYSER, "Starting companion analyser for {}", cmn);

                            CompanionAnalyser companionAnalyser = new CompanionAnalyser(this, methodInfo.typeInfo.typeAnalysis.get(),
                                    cmn, companionMethod, methodInfo, AnnotationParameters.CONTRACT);
                            AnalysisStatus analysisStatus = companionAnalyser.analyse(effectivelyFinalIteration);
                            if (analysisStatus == AnalysisStatus.DONE) {
                                CompanionAnalysis companionAnalysis = companionAnalyser.companionAnalysis.build();
                                builder.companionAnalyses.put(cmn, companionAnalysis);
                            } else {
                                log(DELAYED, "Delaying analysis of {} in {}", cmn, methodInfo.fullyQualifiedName());
                                methodAnalysisStatus.set(AnalysisStatus.DELAYS);
                            }
                        }
                    });
                    builder.fromAnnotationsIntoProperties(true, methodInfo.methodInspection.get().getAnnotations(),
                            e2ImmuAnnotationExpressions);
                } else {
                    MethodAnalyser methodAnalyser = either.getLeft();
                    AnalysisStatus analysisStatus = methodAnalyser.analyse(effectivelyFinalIteration);
                    if (analysisStatus != AnalysisStatus.DONE) {
                        log(DELAYED, "{} in analysis of {}, full method analyser", analysisStatus, methodInfo.fullyQualifiedName());
                        methodAnalysisStatus.set(analysisStatus);
                    }
                }
                switch (methodAnalysisStatus.get()) {
                    case DONE -> {
                        keysToRemove.add(methodInfo);
                        MethodAnalysis methodAnalysis = (MethodAnalysis) (either.isRight() ? either.getRight().build() : either.getLeft().methodAnalysis.build());
                        methodInfo.setAnalysis(methodAnalysis);
                        progress.set(true);
                    }
                    case DELAYS -> delayed.set(true);
                    case PROGRESS -> progress.set(true);
                }
            }));

            buildersForCompanionAnalysis.keySet().removeAll(keysToRemove);
            if (delayed.get() && !progress.get()) {
                throw new UnsupportedOperationException("No changes after iteration " + iteration + "; have left: " + buildersForCompanionAnalysis.size());
            }
            log(ANALYSER, "**** At end of iteration {} in shallow method analysis, removed {}, remaining {}", iteration,
                    keysToRemove.size(), buildersForCompanionAnalysis.size());
            iteration++;

            if (iteration >= 20) throw new UnsupportedOperationException();
        }

    }

    private void shallowTypeAndFieldAnalysis(TypeInfo typeInfo,
                                             TypeAnalysisImpl.Builder typeAnalysisBuilder,
                                             E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        TypeInspection typeInspection = typeInfo.typeInspection.get();
        messages.addAll(typeAnalysisBuilder.fromAnnotationsIntoProperties(true, typeInspection.getAnnotations(), e2ImmuAnnotationExpressions));

        TypeAnalyser.findAspects(typeAnalysisBuilder, typeInfo);
        typeAnalysisBuilder.approvedPreconditions.freeze();
        TypeAnalysis typeAnalysis = typeAnalysisBuilder.build();
        typeInfo.typeAnalysis.set(typeAnalysis);

        typeInspection.fields().forEach(this::shallowFieldAnalyser);
    }


    public void shallowFieldAnalyser(FieldInfo fieldInfo) {
        FieldAnalysisImpl.Builder fieldAnalysisBuilder = new FieldAnalysisImpl.Builder(primitives, AnalysisProvider.DEFAULT_PROVIDER,
                fieldInfo, fieldInfo.owner.typeAnalysis.get());

        messages.addAll(fieldAnalysisBuilder.fromAnnotationsIntoProperties(true,
                fieldInfo.fieldInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));

        // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
        if (fieldInfo.isExplicitlyFinal()) {
            fieldAnalysisBuilder.setProperty(VariableProperty.FINAL, Level.TRUE);
        }
        if (fieldAnalysisBuilder.getProperty(VariableProperty.FINAL) == Level.TRUE && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
            Expression initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser();
            Expression value;
            if (initialiser instanceof StringConstant stringConstant) {
                value = stringConstant;
            } else {
                value = EmptyExpression.NO_VALUE; // IMPROVE
            }
            fieldAnalysisBuilder.effectivelyFinalValue.set(value);
        }
        fieldInfo.setAnalysis(fieldAnalysisBuilder.build());
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return true;
    }
}

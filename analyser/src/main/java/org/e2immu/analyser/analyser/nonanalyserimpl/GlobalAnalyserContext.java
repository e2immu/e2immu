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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.shallow.ShallowFieldAnalyser;
import org.e2immu.analyser.analyser.impl.shallow.ShallowMethodAnalyser;
import org.e2immu.analyser.analyser.impl.shallow.ShallowTypeAnalyser;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.TimedLogger;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalAnalyserContext implements AnalyserContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalAnalyserContext.class);

    private final SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses;
    private final SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses;
    private final SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses;

    private final Primitives primitives;
    private final Configuration configuration;
    private final ImportantClasses importantClasses;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    private final Map<String, TypeAnalysis> hardCodedTypes;
    private final Map<String, MethodAnalysis> hardCodedMethods;
    private final Map<String, ParameterAnalysis> hardCodedParameters;

    private final TypeContext typeContext;
    private final FlipSwitch onDemandMode = new FlipSwitch();

    private final FlipSwitch setEndOfAnnotatedAPIAnalysis = new FlipSwitch();

    private final List<String> onDemandHistory = new ArrayList<>();
    private final TimedLogger onDemandLogger = new TimedLogger(LOGGER, 1000L);

    public GlobalAnalyserContext(TypeContext typeContext,
                                 Configuration configuration,
                                 ImportantClasses importantClasses,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this(typeContext, configuration, importantClasses, e2ImmuAnnotationExpressions,
                new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>());
    }

    private GlobalAnalyserContext(TypeContext typeContext,
                                  Configuration configuration,
                                  ImportantClasses importantClasses,
                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                  SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses,
                                  SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses,
                                  SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses) {
        this.primitives = typeContext.getPrimitives();
        this.typeContext = typeContext;
        this.configuration = configuration;
        this.importantClasses = importantClasses;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.typeAnalyses = typeAnalyses;
        this.methodAnalyses = methodAnalyses;
        this.fieldAnalyses = fieldAnalyses;

        hardCodedTypes = createHardCodedTypeAnalysis();
        // note! first parameters, then methods, because the methods use the parameter map
        hardCodedParameters = createHardCodedParameterAnalysis();
        hardCodedMethods = createHardCodedMethodAnalysis();
    }

    public void startOnDemandMode() {
        onDemandMode.set();
    }

    public void endOfAnnotatedAPIAnalysis() {
        setEndOfAnnotatedAPIAnalysis.set();
    }

    public static final String INTEGER_TO_STRING = "java.lang.Integer.toString(int)";

    public static Map<String, List<String>> PARAMETER_ANALYSES = Map.of(
            TypeInfo.IS_KNOWN_FQN, List.of(TypeInfo.IS_KNOWN_FQN + ":0:test"),
            TypeInfo.IS_FACT_FQN, List.of(TypeInfo.IS_FACT_FQN + ":0:b"),
            INTEGER_TO_STRING, List.of(INTEGER_TO_STRING + ":0:i"));

    private static final Set<String> HARDCODED_METHODS = Set.of(
            TypeInfo.IS_FACT_FQN,
            TypeInfo.IS_KNOWN_FQN,
            "java.lang.CharSequence.length()",
            "java.lang.String.length()",
            INTEGER_TO_STRING
    );

    private static final Set<String> HARDCODED_PARAMETERS = PARAMETER_ANALYSES
            .values().stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());

    private Map<String, TypeAnalysis> createHardCodedTypeAnalysis() {
        return TypeInfo.HARDCODED_TYPES.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> new HardCodedTypeAnalysis(e.getKey(), e.getValue())));
    }

    private Map<String, MethodAnalysis> createHardCodedMethodAnalysis() {
        return HARDCODED_METHODS.stream().collect(Collectors.toUnmodifiableMap(fqn -> fqn,
                fqn -> new HardCodedMethodAnalysis(primitives, hardCodedParameters, fqn)));
    }

    private Map<String, ParameterAnalysis> createHardCodedParameterAnalysis() {
        return HARDCODED_PARAMETERS.stream().collect(Collectors.toUnmodifiableMap(fqn -> fqn,
                HardCodedParameterAnalysis::new));
    }

    @Override
    public ImportantClasses importantClasses() {
        return importantClasses;
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return !setEndOfAnnotatedAPIAnalysis.isSet();
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        return Objects.requireNonNull(getMethodAnalysisNullWhenAbsent(methodInfo),
                "Cannot find method analysis of " + methodInfo);
    }

    @Override
    public MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        if (methodInfo.methodAnalysis.isSet()) return methodInfo.methodAnalysis.get();
        if (methodAnalyses.isSet(methodInfo)) return methodAnalyses.get(methodInfo);
        MethodAnalysis ma = hardCodedMethods.get(methodInfo.fullyQualifiedName);
        if (ma != null) return ma;
        if (onDemandMode.isSet()) {
            synchronized (methodAnalyses) {
                LOGGER.debug("On-demand shallow method analysis of {}", methodInfo);
                onDemandHistory.add(methodInfo.fullyQualifiedName);

                TypeAnalysis ownerTypeAnalysis = getTypeAnalysis(methodInfo.typeInfo);
                MethodInspection methodInspection = getMethodInspection(methodInfo);
                List<ParameterAnalysis> parameterAnalyses = methodInspection.getParameters().stream()
                        .map(pi -> new ParameterAnalysisImpl.Builder(primitives, this, pi))
                        .map(b -> (ParameterAnalysis) b)
                        .toList();
                MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                        primitives, this, this, methodInfo, ownerTypeAnalysis, parameterAnalyses);
                ShallowMethodAnalyser shallowMethodAnalyser = new ShallowMethodAnalyser(methodInfo,
                        methodAnalysisBuilder, parameterAnalyses, this);
                shallowMethodAnalyser.analyse(new Analyser.SharedState(0, BreakDelayLevel.NONE, null));
                MethodAnalysis methodAnalysis = shallowMethodAnalyser.methodAnalysis.build();
                methodInfo.setAnalysis(methodAnalysis);
                methodAnalyses.put(methodInfo, methodAnalysis);
                return methodAnalysis;
            }
        }
        return null;
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return Objects.requireNonNull(getParameterAnalysisNullWhenAbsent(parameterInfo),
                "Cannot find parameter analysis of " + parameterInfo);
    }

    @Override
    public ParameterAnalysis getParameterAnalysisNullWhenAbsent(ParameterInfo parameterInfo) {
        if (parameterInfo.parameterAnalysis.isSet()) {
            return parameterInfo.parameterAnalysis.get();
        }
        MethodAnalysis methodAnalysis = getMethodAnalysisNullWhenAbsent(parameterInfo.getMethod());
        if (methodAnalysis != null) {
            return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
        }
        return hardCodedParameters.get(parameterInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        return Objects.requireNonNull(getTypeAnalysisNullWhenAbsent(typeInfo),
                "Cannot find type analysis of " + typeInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        if (typeInfo.typeAnalysis.isSet()) {
            return typeInfo.typeAnalysis.get();
        }
        if (typeAnalyses.isSet(typeInfo)) {
            return typeAnalyses.get(typeInfo);
        }
        TypeAnalysis hardCoded = hardCodedTypes.get(typeInfo.fullyQualifiedName);
        if (hardCoded != null) {
            return hardCoded;
        }
        if (onDemandMode.isSet()) {
            synchronized (typeAnalyses) {
                LOGGER.debug("On-demand shallow type analysis of {}", typeInfo);
                try {
                    ShallowTypeAnalyser shallowTypeAnalyser = new ShallowTypeAnalyser(typeInfo, typeInfo.primaryType(),
                            this);
                    shallowTypeAnalyser.analyse(new Analyser.SharedState(0, BreakDelayLevel.NONE, null));
                    TypeAnalysis typeAnalysis = shallowTypeAnalyser.typeAnalysis.build();
                    typeInfo.setAnalysis(typeAnalysis);
                    typeAnalyses.put(typeInfo, typeAnalysis);

                    onDemandHistory.add(typeInfo.fullyQualifiedName);
                    onDemandLogger.info("On-demand analysis: {}", onDemandHistory.size());
                    return typeAnalysis;
                } catch (RuntimeException re) {
                    LOGGER.error("Caught exception while starting shallow type analyser on {}", typeInfo.fullyQualifiedName);
                    throw re;
                }
            }
        }
        return null;
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        if (fieldInfo.fieldAnalysis.isSet()) return fieldInfo.fieldAnalysis.get();
        FieldAnalysis fieldAnalysis = fieldAnalyses.getOrDefaultNull(fieldInfo);
        if (fieldAnalysis != null) return fieldAnalysis;
        if (onDemandMode.isSet()) {
            synchronized (fieldAnalyses) {
                LOGGER.debug("On-demand shallow field analysis of {}", fieldInfo);
                onDemandHistory.add(fieldInfo.fullyQualifiedName);

                TypeAnalysis ownerTypeAnalysis = getTypeAnalysis(fieldInfo.owner);
                ShallowFieldAnalyser shallowFieldAnalyser = new ShallowFieldAnalyser(fieldInfo, ownerTypeAnalysis,
                        this);
                shallowFieldAnalyser.analyse(new Analyser.SharedState(0, BreakDelayLevel.NONE, null));
                FieldAnalysis fa = shallowFieldAnalyser.getFieldAnalysis();
                fieldInfo.setAnalysis(fa);
                fieldAnalyses.put(fieldInfo, fa);
                return fa;
            }
        }
        throw new UnsupportedOperationException("Cannot find field analysis for " + fieldInfo);
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isStore() {
        return true;
    }

    @Override
    public void store(Analysis analysis) {
        try {
            if (analysis instanceof TypeAnalysis typeAnalysis) {
                typeAnalyses.put(typeAnalysis.getTypeInfo(), typeAnalysis);
            } else if (analysis instanceof MethodAnalysis methodAnalysis) {
                methodAnalyses.put(methodAnalysis.getMethodInfo(), methodAnalysis);
            } else if (analysis instanceof FieldAnalysis fieldAnalysis) {
                fieldAnalyses.put(fieldAnalysis.getFieldInfo(), fieldAnalysis);
            } else if (!(analysis instanceof ParameterAnalysis)) {
                throw new UnsupportedOperationException();
            }
        } catch (IllegalStateException ise) {
            LOGGER.error("On-demand history:\n{}", String.join("\n", onDemandHistory));
            throw ise;
        }
    }

    public void writeAll() {
        typeAnalyses.stream().forEach(e -> {
            if (!e.getKey().typeAnalysis.isSet()) e.getKey().setAnalysis(e.getValue());
        });
        methodAnalyses.stream().forEach(e -> {
            if (!e.getKey().methodAnalysis.isSet()) e.getKey().setAnalysis(e.getValue());
        });
        fieldAnalyses.stream().forEach(e -> {
            if (!e.getKey().fieldAnalysis.isSet()) e.getKey().setAnalysis(e.getValue());
        });
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        if (typeInfo.typeInspection.isSet()) return typeInfo.typeInspection.get();
        return typeContext.getTypeInspection(typeInfo);
    }
}

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
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.log;

public class PrimaryTypeAnalyser implements AnalyserContext, Analyser, HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryTypeAnalyser.class);

    private final PatternMatcher<StatementAnalyser> patternMatcher;
    public final TypeInfo primaryType;
    public final List<Analyser> analysers;
    public final Configuration configuration;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<ParameterInfo, ParameterAnalyser> parameterAnalysers;
    private final Messages messages = new Messages();
    private final Primitives primitives;
    private final AnalyserContext parent;

    private final AnalyserComponents<Analyser, SharedState> analyserComponents;

    record SharedState(int iteration, EvaluationContext closure) {
    }

    public PrimaryTypeAnalyser(AnalyserContext parent,
                               SortedType sortedType,
                               Configuration configuration,
                               Primitives primitives,
                               E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.parent = parent;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        Objects.requireNonNull(primitives);
        patternMatcher = configuration.analyserConfiguration.newPatternMatcher();
        this.primitives = primitives;
        this.primaryType = Objects.requireNonNull(sortedType.primaryType());
        assert (parent == null) == this.primaryType.isPrimaryType();

        // do the types first, so we can pass on a TypeAnalysis objects
        Map<TypeInfo, TypeAnalyser> typeAnalysersBuilder = new HashMap<>();
        SetOnce<TypeAnalyser> primaryTypeAnalyser = new SetOnce<>();
        sortedType.methodsFieldsSubTypes().forEach(mfs -> {
            if (mfs instanceof TypeInfo typeInfo && !typeInfo.typeAnalysis.isSet()) {
                TypeAnalyser typeAnalyser = new TypeAnalyser(typeInfo, primaryType, this);
                typeAnalysersBuilder.put(typeInfo, typeAnalyser);
                if (typeInfo == primaryType) primaryTypeAnalyser.set(typeAnalyser);
            }
        });
        typeAnalysers = Map.copyOf(typeAnalysersBuilder);

        // then methods
        // filtering out those methods that have not been defined is not a good idea, since the MethodAnalysisImpl object
        // can only reach TypeAnalysisImpl, and not its builder. We'd better live with empty methods in the method analyser.
        Map<ParameterInfo, ParameterAnalyser> parameterAnalysersBuilder = new HashMap<>();
        Map<MethodInfo, MethodAnalyser> methodAnalysersBuilder = new HashMap<>();
        sortedType.methodsFieldsSubTypes().forEach(mfs -> {
            if (mfs instanceof MethodInfo methodInfo && !methodInfo.methodAnalysis.isSet()) {
                MethodAnalyser analyser = new MethodAnalyser(methodInfo, typeAnalysers.get(methodInfo.typeInfo).typeAnalysis,
                        false, this);
                for (ParameterAnalyser parameterAnalyser : analyser.getParameterAnalysers()) {
                    parameterAnalysersBuilder.put(parameterAnalyser.parameterInfo, parameterAnalyser);
                }
                methodAnalysersBuilder.put(methodInfo, analyser);
                // finalizers are done early, before the first assignments
                if (methodInfo.methodInspection.get().hasContractedFinalizer()) {
                    TypeAnalyser typeAnalyser = typeAnalysers.get(methodInfo.typeInfo);
                    typeAnalyser.typeAnalysis.setProperty(VariableProperty.FINALIZER, Level.TRUE);
                }
            }
        });

        parameterAnalysers = Map.copyOf(parameterAnalysersBuilder);
        methodAnalysers = Map.copyOf(methodAnalysersBuilder);

        // finally fields, and wire everything together
        Map<FieldInfo, FieldAnalyser> fieldAnalysersBuilder = new HashMap<>();
        List<Analyser> allAnalysers = sortedType.methodsFieldsSubTypes().stream().flatMap(mfs -> {
            Analyser analyser;
            if (mfs instanceof FieldInfo fieldInfo && !fieldInfo.fieldAnalysis.isSet()) {
                MethodAnalyser samAnalyser;
                if (fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                    MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod();
                    if (sam != null) {
                        samAnalyser = Objects.requireNonNull(methodAnalysers.get(sam),
                                "No method analyser for "+sam.fullyQualifiedName);
                    } else samAnalyser = null;
                } else samAnalyser = null;
                TypeAnalysis ownerTypeAnalysis = typeAnalysers.get(fieldInfo.owner).typeAnalysis;
                analyser = new FieldAnalyser(fieldInfo, primaryType, ownerTypeAnalysis, samAnalyser, this);
                fieldAnalysersBuilder.put(fieldInfo, (FieldAnalyser) analyser);
            } else if (mfs instanceof MethodInfo) {
                analyser = methodAnalysers.get(mfs);
            } else if (mfs instanceof TypeInfo) {
                analyser = typeAnalysers.get(mfs);
            } else throw new UnsupportedOperationException();
            return analyser == null ? Stream.empty() : Stream.of(analyser);
        }).collect(Collectors.toList());
        fieldAnalysers = Map.copyOf(fieldAnalysersBuilder);

        List<MethodAnalyser> methodAnalysersInOrder = new ArrayList<>(methodAnalysers.size());
        List<FieldAnalyser> fieldAnalysersInOrder = new ArrayList<>(fieldAnalysers.size());
        List<TypeAnalyser> typeAnalysersInOrder = new ArrayList<>(typeAnalysers.size());
        allAnalysers.forEach(analyser -> {
            if (analyser instanceof MethodAnalyser ma) methodAnalysersInOrder.add(ma);
            else if (analyser instanceof TypeAnalyser ta) typeAnalysersInOrder.add(ta);
            else if (analyser instanceof FieldAnalyser fa) fieldAnalysersInOrder.add(fa);
            else throw new UnsupportedOperationException();
        });
        analysers = ListUtil.immutableConcat(methodAnalysersInOrder, fieldAnalysersInOrder, typeAnalysersInOrder);
        assert analysers.size() == new HashSet<>(analysers).size() : "There are be duplicates among the analysers?";

        // all important fields of the interface have been set.
        analysers.forEach(Analyser::initialize);

        AnalyserComponents.Builder<Analyser, SharedState> builder = new AnalyserComponents.Builder<>();
        for (Analyser analyser : analysers) {
            builder.add(analyser, sharedState -> analyser.analyse(sharedState.iteration, sharedState.closure));
        }
        analyserComponents = builder.build();
    }

    @Override
    public AnalyserContext getParent() {
        return parent;
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public Stream<Message> getMessageStream() {
        return Stream.concat(messages.getMessageStream(), analysers.stream().flatMap(Analyser::getMessageStream));
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return primaryType;
    }

    @Override
    public Analysis getAnalysis() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return "PTA " + primaryType.fullyQualifiedName;
    }

    @Override
    public void check() {
        analysers.forEach(Analyser::check);
    }

    public void analyse() {
        int iteration = 0;
        AnalysisStatus analysisStatus = AnalysisStatus.PROGRESS;

        while (analysisStatus != AnalysisStatus.DONE) {
            log(ANALYSER, "\n******\nStarting iteration {} of the primary type analyser on {}\n******", iteration, primaryType.fullyQualifiedName);

            analysisStatus = analyse(iteration, null);
            iteration++;
            if (iteration > 10) {
                logAnalysisStatuses(analyserComponents);
                throw new UnsupportedOperationException("More than 10 iterations needed for primary type " + primaryType.fullyQualifiedName + "?");
            }
        }
    }

    private void logAnalysisStatuses(AnalyserComponents<Analyser, SharedState> analyserComponents) {
        LOGGER.warn("Status of analysers:\n{}", analyserComponents.details());
        for (Pair<Analyser, AnalysisStatus> pair : analyserComponents.getStatuses()) {
            if (pair.v == AnalysisStatus.DELAYS) {
                LOGGER.warn("Analyser components of {}:\n{}", pair.k.getName(), pair.k.getAnalyserComponents().details());
                if (pair.k instanceof MethodAnalyser methodAnalyser) {
                    methodAnalyser.logAnalysisStatuses();
                }
            }
        }
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() {
        // nothing to be done
    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        patternMatcher.startNewIteration();
        return analyserComponents.run(new SharedState(iteration, closure));
    }

    @Override
    public void write() {
        analysers.forEach(Analyser::write);
    }

    @Override
    public void makeImmutable() {
        analysers.forEach(analyser -> {
            analyser.getMember().setAnalysis(analyser.getAnalysis().build());
            if (analyser instanceof HoldsAnalysers holdsAnalysers) holdsAnalysers.makeImmutable();
        });
    }

    @Override
    public TypeInfo getPrimaryType() {
        return primaryType;
    }

    @Override
    public PatternMatcher<StatementAnalyser> getPatternMatcher() {
        return patternMatcher;
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public FieldAnalyser getFieldAnalyser(FieldInfo fieldInfo) {
        return fieldAnalysers.get(fieldInfo);
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return fieldAnalysers.values().stream();
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        return methodAnalysers.get(methodInfo);
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream() {
        return methodAnalysers.values().stream();
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        return typeAnalysers.get(typeInfo);
    }

    @Override
    public ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        return parameterAnalysers.get(parameterInfo);
    }
}

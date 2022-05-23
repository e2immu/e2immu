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
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.resolver.AnalyserGenerator;
import org.e2immu.analyser.resolver.TypeCycle;
import org.e2immu.analyser.util.Pair;
import org.e2immu.support.Either;
import org.e2immu.support.FlipSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.config.AnalyserProgram.PROGRAM_ALL;
import static org.e2immu.analyser.config.AnalyserProgram.Step.*;

/*
Recursive, but only for types inside statements, not for subtypes.

Holds either a single primary type and its subtypes, or multiple primary types,
when there is a circular dependency that cannot easily be ignored.
 */
public class PrimaryTypeAnalyserImpl implements PrimaryTypeAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryTypeAnalyserImpl.class);

    private final PatternMatcher<StatementAnalyser> patternMatcher;
    public final String name;
    public final Set<TypeInfo> primaryTypes;
    public final List<Analyser> analysers;
    public final Configuration configuration;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<ParameterInfo, ParameterAnalyser> parameterAnalysers;
    private final AnalyserResult.Builder analyserResultBuilder = new AnalyserResult.Builder();
    private final Primitives primitives;
    private final AnalyserContext parent;
    private final Set<PrimaryTypeAnalyser> localPrimaryTypeAnalysers = new HashSet<>();
    private final AnalyserComponents<Analyser, SharedState> analyserComponents;
    private final FlipSwitch unreachable = new FlipSwitch();
    private final Set<Integer> iterationsWithAllowBreakDelay = new HashSet<>();

    public PrimaryTypeAnalyserImpl(AnalyserContext parent,
                                   TypeCycle typeCycle,
                                   Configuration configuration,
                                   Primitives primitives,
                                   Either<PatternMatcher<StatementAnalyser>, TypeContext> patternMatcherOrTypeContext,
                                   E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.parent = parent;
        this.configuration = configuration;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        Objects.requireNonNull(primitives);
        this.patternMatcher = patternMatcherOrTypeContext.isLeft() ? patternMatcherOrTypeContext.getLeft() :
                configuration.analyserConfiguration().newPatternMatcher(patternMatcherOrTypeContext.getRight(), this);
        this.primitives = primitives;

        AnalyserGenerator analyserGenerator = typeCycle
                .createAnalyserGeneratorAndGenerateAnalysers(configuration, this);
        this.name = analyserGenerator.getName();
        this.analysers = analyserGenerator.getAnalysers();
        this.primaryTypes = analyserGenerator.getPrimaryTypes();
        this.methodAnalysers = analyserGenerator.getMethodAnalysers();
        this.fieldAnalysers = analyserGenerator.getFieldAnalysers();
        this.parameterAnalysers = analyserGenerator.getParameterAnalysers();
        this.typeAnalysers = analyserGenerator.getTypeAnalysers();

        // all important fields of the interface have been set.
        analysers.forEach(Analyser::initialize);

        AnalyserComponents.Builder<Analyser, SharedState> builder = new AnalyserComponents.Builder<>(PROGRAM_ALL);
        builder.setLimitCausesOfDelay(true);

        for (Analyser analyser : analysers) {
            AnalysisStatus.AnalysisResultSupplier<SharedState> supplier = sharedState -> {
                analyser.receiveAdditionalTypeAnalysers(localPrimaryTypeAnalysers);
                AnalyserResult analyserResult = analyser.analyse(sharedState);
                analyserResultBuilder.add(analyserResult, true, true);
                if (analyser instanceof MethodAnalyser methodAnalyser) {
                    methodAnalyser.getLocallyCreatedPrimaryTypeAnalysers().forEach(localPrimaryTypeAnalysers::add);
                }
                return analyserResult.analysisStatus();
            };

            builder.add(analyser, supplier);
        }
        // TODO in larger contexts, removing the allowBreakDelay immediately may be excessively slow
        // maybe we should do that per PrimaryType, keeping a map?
        analyserComponents = builder
                .setUpdateUponProgress(SharedState::removeAllowBreakDelay)
                .build();
        LOGGER.debug("List of analysers: {}", analysers);
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "PTA " + name;
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
        return analysers.stream().flatMap(Analyser::getMessageStream);
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Analysis getAnalysis() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return "PTA " + name;
    }

    @Override
    public void check() {
        if (!isUnreachable()) analysers.forEach(Analyser::check);
    }

    @Override
    public void analyse() {
        assert !isUnreachable();
        if (typeAnalysers.size() > 10) {
            LOGGER.info("Starting to process {} types, {} methods, {} fields", typeAnalysers.size(), methodAnalysers.size(), fieldAnalysers.size());
        }

        if (!configuration.analyserConfiguration().analyserProgram().accepts(ITERATION_0)) return;
        int iteration = 0;
        boolean allowBreakDelay = false;
        AnalysisStatus analysisStatus;

        int MAX_ITERATION = 20;
        do {
            LOGGER.debug("\n******\nStarting iteration {} (break? {}) of the primary type analyser on {}\n******",
                    iteration, allowBreakDelay, name);
            if (allowBreakDelay) iterationsWithAllowBreakDelay.add(iteration);

            analyserComponents.resetDelayHistogram();

            SharedState sharedState = new SharedState(iteration, allowBreakDelay, null);
            AnalyserResult analyserResult = analyse(sharedState);
            iteration++;

            dumpDelayHistogram(analyserComponents.getDelayHistogram());

            if (!configuration.analyserConfiguration().analyserProgram().accepts(ITERATION_1PLUS)) {
                LOGGER.debug("\n******\nStopping after iteration 0 according to program\n******");
                return;
            }
            if (iteration > 1 && !configuration.analyserConfiguration().analyserProgram().accepts(ITERATION_2)) {
                LOGGER.debug("\n******\nStopping after iteration 1 according to program\n******");
                return;
            }
            if (iteration > 2 && !configuration.analyserConfiguration().analyserProgram().accepts(ALL)) {
                LOGGER.debug("\n******\nStopping after iteration 2 according to program\n******");
                return;
            }
            analysisStatus = analyserResult.analysisStatus();
            if (analysisStatus == AnalysisStatus.DONE) break;
            if (allowBreakDelay && !analysisStatus.isProgress()) {
                // no point in continuing
                break;
            }
            allowBreakDelay = !analysisStatus.isProgress();
        } while (iteration < MAX_ITERATION);
        if (analysisStatus.isDelayed()) {
            logAnalysisStatuses(analyserComponents);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Delays: {}", analysisStatus.causesOfDelay());
            }
            if (analysisStatus.isProgress() && iteration == MAX_ITERATION) {
                throw new NoProgressException("Looks like there is an infinite PROGRESS going on, pt " + name);
            }
            throw new NoProgressException("No progress after " + iteration + " iterations for primary type(s) " + name);
        }
    }

    private static void dumpDelayHistogram(Map<WithInspectionAndAnalysis, AnalyserComponents.Info> delayHistogram) {
        LOGGER.info("Delay histogram:\n{}",
                delayHistogram.entrySet().stream().sorted((e1, e2) -> e2.getValue().getCnt() - e1.getValue().getCnt())
                        .limit(20)
                        .map(e -> e.getValue().getCnt() + ": " + (e.getKey() == null ? "?" : (e.getKey().niceClassName() + " " + e.getKey().fullyQualifiedName()) + ": " + e.getValue()))
                                .collect(Collectors.joining("\n")));
    }

    private void logAnalysisStatuses(AnalyserComponents<Analyser, SharedState> analyserComponents) {
        LOGGER.warn("Status of analysers:\n{}", analyserComponents.details());
        for (Pair<Analyser, AnalysisStatus> pair : analyserComponents.getStatuses()) {
            if (pair.v.isDelayed()) {
                AnalyserComponents<String, ?> acs = pair.k.getAnalyserComponents();
                LOGGER.warn("Analyser components of {}:\n{}", pair.k.getName()
                        , acs == null ? "(shallow method analyser)" : acs.details());
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
    public AnalyserResult analyse(SharedState sharedState) {
        patternMatcher.startNewIteration();
        AnalysisStatus analysisStatus = analyserComponents.run(sharedState);
        LOGGER.info("At end of PTA analysis, done {} of {} components",
                analyserComponents.getStatuses().stream().filter(p -> p.getV().isDone()).count(),
                analyserComponents.getStatuses().size());
        analyserResultBuilder.setAnalysisStatus(analysisStatus);
        return analyserResultBuilder.build();
    }

    @Override
    public void write() {
        analysers.forEach(Analyser::write);
    }

    @Override
    public void makeImmutable() {
        analysers.forEach(analyser -> {
            analyser.getMember().setAnalysis(analyser.getAnalysis().build());
            analyser.makeImmutable();
        });
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        return new MethodInspectionImpl.Builder(identifier, typeInfo, methodName);
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
        FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
        if (fieldAnalyser == null && parent != null) {
            return parent.getFieldAnalyser(fieldInfo);
        }
        return fieldAnalyser;
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return fieldAnalysers.values().stream();
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = methodAnalysers.get(methodInfo);
        if (methodAnalyser == null && parent != null) {
            return parent.getMethodAnalyser(methodInfo);
        }
        return methodAnalyser;
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream() {
        return methodAnalysers.values().stream();
    }

    /*
        @Override
        public Stream<MethodAnalyser> parallelMethodAnalyserStream() {
            return methodAnalysers.values().stream();
        }
    */
    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = typeAnalysers.get(typeInfo);
        if (typeAnalyser == null && parent != null) {
            return parent.getTypeAnalyser(typeInfo);
        }
        return typeAnalyser;
    }

    @Override
    public ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        ParameterAnalyser parameterAnalyser = parameterAnalysers.get(parameterInfo);
        if (parameterAnalyser == null && parent != null) {
            return parent.getParameterAnalyser(parameterInfo);
        }
        return parameterAnalyser;
    }

    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsPrimaryType(TypeInfo typeInfo) {
        return primaryTypes.contains(typeInfo);
    }

    @Override
    public void loopOverAnalysers(Consumer<Analyser> consumer) {
        analysers.forEach(consumer);
    }

    @Override
    public boolean makeUnreachable() {
        if (!unreachable.isSet()) {
            unreachable.set();
            analysers.forEach(Analyser::makeUnreachable);
            return true;
        }
        return false;
    }

    @Override
    public boolean isUnreachable() {
        return unreachable.isSet();
    }

    public Set<Integer> getIterationsWithAllowBreakDelay() {
        return Set.copyOf(iterationsWithAllowBreakDelay);
    }
}

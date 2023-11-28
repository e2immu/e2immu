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

package org.e2immu.analyser.analyser.impl.primary;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.AnalyserGenerator;
import org.e2immu.analyser.resolver.TypeCycle;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.TimedLogger;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.support.FlipSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Recursive, but only for types inside statements, not for subtypes.

Holds either a single primary type and its subtypes, or multiple primary types,
when there is a circular dependency that cannot easily be ignored.
 */
public class PrimaryTypeAnalyserImpl implements PrimaryTypeAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryTypeAnalyserImpl.class);

    public final String name;
    public final Set<TypeInfo> primaryTypes;
    public final List<Analyser> analysers;
    public final Configuration configuration;
    public final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Map<TypeInfo, TypeAnalyser> typeAnalysers;
    private final Map<MethodInfo, MethodAnalyser> methodAnalysers;
    private final Map<TypeInfo, List<MethodAnalyser>> methodAnalysersPerPrimaryType;
    private final Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final Map<TypeInfo, List<FieldAnalyser>> fieldAnalysersPerType;
    private AnalyserResult.Builder analyserResultBuilder;
    private final Primitives primitives;
    private final ImportantClasses importantClasses;
    private final AnalyserContext parent;
    private final Set<PrimaryTypeAnalyser> localPrimaryTypeAnalysers = new HashSet<>();
    private final AnalyserComponents<Analyser, SharedState> analyserComponents;
    private final FlipSwitch unreachable = new FlipSwitch();
    private final List<BreakDelayLevel> delaySequence = new LinkedList<>();
    private final Marker marker;

    public PrimaryTypeAnalyserImpl(AnalyserContext parent, TypeCycle typeCycle) {
        this.parent = Objects.requireNonNull(parent);
        this.configuration = parent.getConfiguration();
        this.e2ImmuAnnotationExpressions = parent.getE2ImmuAnnotationExpressions();
        this.primitives = parent.getPrimitives();
        this.importantClasses = parent.importantClasses();

        AnalyserGenerator analyserGenerator = typeCycle.createAnalyserGeneratorAndGenerateAnalysers(this);
        this.name = analyserGenerator.getName();
        this.analysers = analyserGenerator.getAnalysers();
        this.primaryTypes = analyserGenerator.getPrimaryTypes();
        this.methodAnalysers = analyserGenerator.getMethodAnalysers();
        this.methodAnalysersPerPrimaryType = new HashMap<>();
        for (Map.Entry<MethodInfo, MethodAnalyser> entry : methodAnalysers.entrySet()) {
            methodAnalysersPerPrimaryType.computeIfAbsent(entry.getKey().typeInfo.primaryType(),
                    e -> new ArrayList<>()).add(entry.getValue());
        }
        this.fieldAnalysers = analyserGenerator.getFieldAnalysers();
        this.fieldAnalysersPerType = new HashMap<>();
        for (Map.Entry<FieldInfo, FieldAnalyser> entry : fieldAnalysers.entrySet()) {
            fieldAnalysersPerType.computeIfAbsent(entry.getKey().owner, e -> new ArrayList<>()).add(entry.getValue());
        }
        this.typeAnalysers = analyserGenerator.getTypeAnalysers();

        // all important fields of the interface have been set.
        int count = 0;
        TimedLogger timedLogger = new TimedLogger(LOGGER, 1000L);
        for (Analyser a : analysers) {
            try {
                a.initialize();
                count++;
                timedLogger.info("Initialized {} analysers", count);
            } catch (RuntimeException re) {
                LOGGER.error("Caught exception initializing analyser {}", a);
                throw re;
            }
        }

        AnalyserComponents.Builder<Analyser, SharedState> builder = new AnalyserComponents.Builder<>();
        builder.setLimitCausesOfDelay(true);

        for (Analyser analyser : analysers) {
            AnalysisStatus.AnalysisResultSupplier<SharedState> supplier = sharedState -> {
                analyser.receiveAdditionalTypeAnalysers(localPrimaryTypeAnalysers);
                AnalyserResult analyserResult = analyser.analyse(sharedState);
                analyserResultBuilder.add(analyserResult, true, true, false);
                if (analyser instanceof MethodAnalyser methodAnalyser) {
                    methodAnalyser.getLocallyCreatedPrimaryTypeAnalysers().forEach(localPrimaryTypeAnalysers::add);
                }
                return analyserResult.analysisStatus();
            };

            builder.add(analyser, supplier);
        }
        marker = inAnnotatedAPIAnalysis() ? LogTarget.A_API : LogTarget.SOURCE;
        // In larger contexts, removing the allowBreakDelay immediately may be excessively slow
        // maybe we should do that per PrimaryType, keeping a map?
        analyserComponents = builder
                .setUpdateUponProgress(SharedState::removeAllowBreakDelay)
                .setExecuteConditionally(this::executeConditionally)
                .setMarker(marker)
                .build();
        LOGGER.trace(marker, "List of analysers: {}", analysers);
    }

    private boolean executeConditionally(Analyser analyser, SharedState sharedState) {
        return switch (sharedState.breakDelayLevel()) {
            case FIELD -> analyser instanceof FieldAnalyser || analyser instanceof TypeAnalyser;
            case TYPE -> analyser instanceof TypeAnalyser;
            default -> true;
        };
    }

    @Override
    public ImportantClasses importantClasses() {
        return importantClasses;
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
            LOGGER.info("Starting to process {} types, {} methods, {} fields", typeAnalysers.size(),
                    methodAnalysers.size(), fieldAnalysers.size());
        }

        int iteration = 0;
        BreakDelayLevel breakDelayLevel = BreakDelayLevel.NONE;
        AnalysisStatus analysisStatus;

        int MAX_ITERATION = 1000;
        do {
            delaySequence.add(breakDelayLevel);

            LOGGER.info(marker, "\n******\nStarting iteration {} (break? {}) of the primary type analyser on {}; sequence {}\n******",
                    iteration, breakDelayLevel, name, delaySequence);
            analyserComponents.resetDelayHistogram();

            SharedState sharedState = new SharedState(iteration, breakDelayLevel, null);
            AnalyserResult analyserResult = analyse(sharedState);
            iteration++;

            dumpDelayHistogram(analyserComponents.getDelayHistogram());

            analysisStatus = analyserResult.analysisStatus();
            if (analysisStatus == AnalysisStatus.DONE) break;
            if (!analysisStatus.isProgress()) {
                if (breakDelayLevel.stop()) {
                    // no point in continuing
                    break;
                }
                breakDelayLevel = breakDelayLevel.next();
            } else {
                /* should we have the type analyser do only one type?
                  assert delaySequence.stream().filter(b -> BreakDelayLevel.TYPE == b).count() < 2L
                        : "Only once can we have progress from Type!";
                 */
                breakDelayLevel = BreakDelayLevel.NONE;
            }
        } while (iteration < MAX_ITERATION);

        if (!inAnnotatedAPIAnalysis()) {
            List<BreakDelayVisitor> visitors = configuration.debugConfiguration().breakDelayVisitors();
            for (BreakDelayVisitor breakDelayVisitor : visitors) {
                for (TypeInfo typeInfo : primaryTypes) {
                    String delaySequenceString = delaySequence
                            .stream().map(b -> Character.toString(b.symbol))
                            .collect(Collectors.joining());
                    breakDelayVisitor.visit(new BreakDelayVisitor.Data(iteration, delaySequenceString, typeInfo));
                }
            }
        }

        if (analysisStatus.isDelayed()) {
            logAnalysisStatuses(analyserComponents);
            LOGGER.debug(marker, "Delays: {}", analysisStatus.causesOfDelay());
            if (analysisStatus.isProgress() && iteration == MAX_ITERATION) {
                throw new NoProgressException("Looks like there is an infinite PROGRESS going on, pt " + name);
            }
            throw new NoProgressException("No progress after " + iteration + " iterations for primary type(s) " + name);
        }
    }

    private void dumpDelayHistogram(Map<InfoObject, AnalyserComponents.Info> delayHistogram) {
        LOGGER.debug(marker, "Delay histogram:\n{}",
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
        analyserResultBuilder = new AnalyserResult.Builder();
        AnalysisStatus analysisStatus = analyserComponents.run(sharedState);
        LOGGER.debug(marker, "At end of PTA analysis, done {} of {} components, progress? {}",
                analyserComponents.getStatuses().stream().filter(p -> p.getV().isDone()).count(),
                analyserComponents.getStatuses().size(),
                analysisStatus.isProgress());
        analyserResultBuilder.setAnalysisStatus(analysisStatus);
        return analyserResultBuilder.build();
    }

    @Override
    public void write() {
        analysers.forEach(Analyser::write);
        if (parent.isStore()) {
            analysers.forEach(a -> parent.store(a.getAnalysis()));
        }
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
        return new MethodInspectionImpl.Builder(identifier, typeInfo, methodName, MethodInfo.MethodType.METHOD);
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
    public Stream<FieldAnalyser> fieldAnalyserStream(TypeInfo typeInfo) {
        return fieldAnalysersPerType.getOrDefault(typeInfo, List.of()).stream();
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
    public Stream<MethodAnalyser> methodAnalyserStream(TypeInfo primaryType) {
        assert primaryType.isPrimaryType();
        return methodAnalysersPerPrimaryType.getOrDefault(primaryType, List.of()).stream();
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = typeAnalysers.get(typeInfo);
        if (typeAnalyser == null && parent != null) {
            return parent.getTypeAnalyser(typeInfo);
        }
        return typeAnalyser;
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return Objects.requireNonNull(getParameterAnalysisNullWhenAbsent(parameterInfo),
                "Cannot find parameter analysis for " + parameterInfo);
    }

    @Override
    public ParameterAnalysis getParameterAnalysisNullWhenAbsent(ParameterInfo parameterInfo) {
        MethodAnalyser methodAnalyser = methodAnalysers.get(parameterInfo.owner);
        if (methodAnalyser != null) return methodAnalyser.getParameterAnalyses().get(parameterInfo.index);
        return parent.getParameterAnalysisNullWhenAbsent(parameterInfo);
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fieldAnalyser = fieldAnalysers.get(fieldInfo);
        return fieldAnalyser != null ? fieldAnalyser.getFieldAnalysis() : parent.getFieldAnalysis(fieldInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = typeAnalysers.get(typeInfo);
        return typeAnalyser != null ? typeAnalyser.getTypeAnalysis() : parent.getTypeAnalysis(typeInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = typeAnalysers.get(typeInfo);
        return typeAnalyser != null ? typeAnalyser.getTypeAnalysis() : parent.getTypeAnalysisNullWhenAbsent(typeInfo);
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = methodAnalysers.get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getMethodAnalysis() : parent.getMethodAnalysis(methodInfo);
    }

    @Override
    public MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = methodAnalysers.get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getMethodAnalysis()
                : parent.getMethodAnalysisNullWhenAbsent(methodInfo);
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

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return parent.inAnnotatedAPIAnalysis();
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        if (typeInfo.typeInspection.isSet()) return typeInfo.typeInspection.get();
        return parent.getTypeInspection(typeInfo);
    }
}

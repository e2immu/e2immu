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

package org.e2immu.analyser.analyser.impl.aggregating;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.TypeAnalyserImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;

public class AggregatingTypeAnalyser extends TypeAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatingTypeAnalyser.class);

    public static final String IMMUTABLE = "immutable";
    public static final String INDEPENDENT = "independent";
    public static final String CONTAINER = "container";
    public static final String HIDDEN_CONTENT = "hiddenContent";
    public static final String IMMUTABLE_CAN_BE_INCREASED = "immutableCanBeIncreased";

    private final SetOnce<List<TypeAnalysis>> implementingAnalyses = new SetOnce<>();
    private final SetOnce<List<TypeAnalyser>> implementingAnalysers = new SetOnce<>();
    private final AnalyserComponents<String, Integer> analyserComponents;

    public AggregatingTypeAnalyser(TypeInfo typeInfo,
                                   TypeInfo primaryType,
                                   AnalyserContext analyserContextInput) {
        super(typeInfo, primaryType, analyserContextInput, Analysis.AnalysisMode.AGGREGATED);

        // IMPROVE but we have not thought yet about how to deal with eventual immutability and sealed types
        typeAnalysis.freezeApprovedPreconditionsFinalFields();
        typeAnalysis.freezeApprovedPreconditionsImmutable();
        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>()
                .add(IMMUTABLE, iteration -> this.aggregate(Property.IMMUTABLE))
                .add(INDEPENDENT, iteration -> this.aggregate(Property.INDEPENDENT))
                .add(CONTAINER, iteration -> this.aggregate(Property.CONTAINER))
                .add(HIDDEN_CONTENT, iteration -> this.aggregateHiddenContentTypes())
                .add(IMMUTABLE_CAN_BE_INCREASED, iteration -> super.analyseImmutableDeterminedByTypeParameters());

        analyserComponents = builder.build();
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "ATA " + typeInfo.fullyQualifiedName;
    }

    @Override
    public void initialize() {
        Stream<TypeInfo> implementations = obtainImplementingTypes();
        List<TypeAnalyser> analysers = implementations.map(analyserContext::getTypeAnalyser).toList();
        implementingAnalysers.set(analysers);
        List<TypeAnalysis> analyses = analysers.stream().map(TypeAnalyser::getTypeAnalysis).toList();
        implementingAnalyses.set(analyses);
    }

    private Stream<TypeInfo> obtainImplementingTypes() {
        TypeInspection myTypeInspection = typeInfo.typeInspection.get();
        if (myTypeInspection.isSealed()) {
            return myTypeInspection.permittedWhenSealed().stream();
        }
        TypeInfo generated = typeInfo.typeResolution.get().generatedImplementation();
        assert generated != null : typeInfo.fullyQualifiedName
                + " is not a sealed class, so it must have a unique generated implementation";
        return Stream.of(generated);
    }

    @Override
    public Stream<MethodAnalyser> allMethodAnalysersIncludingSubTypes() {
        return implementingAnalysers.get().stream().flatMap(TypeAnalyser::allMethodAnalysersIncludingSubTypes);
    }

    @Override
    public Stream<FieldAnalyser> allFieldAnalysers() {
        return implementingAnalysers.get().stream().flatMap(TypeAnalyser::allFieldAnalysers);
    }

    @Override
    public AnalyserResult analyse(SharedState sharedState) {
        AnalysisStatus analysisStatus = analyserComponents.run(sharedState.iteration());
        if (analysisStatus.isDone()) typeAnalysis.internalAllDoneCheck();
        analyserResultBuilder.setAnalysisStatus(analysisStatus);

        List<TypeAnalyserVisitor> visitors = analyserContext.getConfiguration()
                .debugConfiguration().afterTypePropertyComputations();
        if (!visitors.isEmpty()) {
            for (TypeAnalyserVisitor typeAnalyserVisitor : visitors) {
                typeAnalyserVisitor.visit(new TypeAnalyserVisitor.Data(sharedState.iteration(),
                        sharedState.breakDelayLevel(),
                        analyserContext.getPrimitives(),
                        typeInfo,
                        analyserContext.getTypeInspection(typeInfo),
                        typeAnalysis,
                        analyserComponents.getStatusesAsMap(),
                        analyserContext));
            }
        }
        return analyserResultBuilder.build();
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return analyserComponents;
    }

    private AnalysisStatus aggregate(Property property) {
        DV current = typeAnalysis.getProperty(property);
        if (current.isDelayed()) {
            DV value = implementingAnalyses.get().stream()
                    .map(a -> a.getProperty(property))
                    .reduce(property.bestDv, DV::min);
            if (value.isDelayed()) {
                LOGGER.debug("Delaying aggregate of {} for {}", property, typeInfo.fullyQualifiedName);
                return value.causesOfDelay();
            }
            LOGGER.debug("Set aggregate of {} to {} for {}", property, value, typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(property, value);
        }
        return DONE;
    }

    private AnalysisStatus aggregateHiddenContentTypes() {
        if (typeAnalysis.hiddenContentDelays().isDone()) return DONE;
        CausesOfDelay delays = implementingAnalyses.get().stream().map(a -> a.hiddenContentDelays().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delays.isDelayed()) {
            typeAnalysis.setHiddenContentTypesDelay(delays);
            return delays;
        }
        SetOfTypes unionHiddenContent = implementingAnalyses.get().stream()
                .map(TypeAnalysis::getHiddenContentTypes)
                .reduce(SetOfTypes.EMPTY, SetOfTypes::union);
        typeAnalysis.setHiddenContentTypes(unionHiddenContent);
        return DONE;
    }


    @Override
    public boolean ignorePrivateConstructorsForFieldValue() {
        return true; // there are no constructors
    }
}

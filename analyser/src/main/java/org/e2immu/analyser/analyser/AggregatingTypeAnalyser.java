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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.support.SetOnce;

import java.util.List;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class AggregatingTypeAnalyser extends TypeAnalyser {

    public static final String IMMUTABLE = "immutable";
    public static final String INDEPENDENT = "independent";
    public static final String CONTAINER = "container";
    private final SetOnce<List<TypeAnalysis>> implementingAnalyses = new SetOnce<>();
    private final AnalyserComponents<String, Integer> analyserComponents;

    AggregatingTypeAnalyser(TypeInfo typeInfo,
                            TypeInfo primaryType,
                            AnalyserContext analyserContextInput) {
        super(typeInfo, primaryType, analyserContextInput, Analysis.AnalysisMode.AGGREGATED);

        // TODO improve; but we have not thought yet about how to deal with eventual immutability and sealed types
        typeAnalysis.freezeApprovedPreconditionsE1();
        typeAnalysis.freezeApprovedPreconditionsE2();

        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>()
                .add(IMMUTABLE, iteration -> this.aggregate(VariableProperty.IMMUTABLE))
                .add(INDEPENDENT, iteration -> this.aggregate(VariableProperty.INDEPENDENT))
                .add(CONTAINER, iteration -> this.aggregate(VariableProperty.CONTAINER));

        analyserComponents = builder.build();
    }

    @Override
    public void initialize() {
        Stream<TypeInfo> implementations = obtainImplementingTypes();
        List<TypeAnalysis> analysers = implementations.map(analyserContext::getTypeAnalysis).toList();
        implementingAnalyses.set(analysers);
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
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        AnalysisStatus analysisStatus = analyserComponents.run(iteration);
        List<TypeAnalyserVisitor> visitors = analyserContext.getConfiguration()
                .debugConfiguration().afterTypePropertyComputations();
        if (!visitors.isEmpty()) {
            for (TypeAnalyserVisitor typeAnalyserVisitor : visitors) {
                typeAnalyserVisitor.visit(new TypeAnalyserVisitor.Data(iteration,
                        analyserContext.getPrimitives(),
                        typeInfo,
                        analyserContext.getTypeInspection(typeInfo),
                        typeAnalysis,
                        analyserComponents.getStatusesAsMap(),
                        analyserContext));
            }
        }
        return analysisStatus;
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return analyserComponents;
    }

    private AnalysisStatus aggregate(VariableProperty variableProperty) {
        int current = typeAnalysis.getProperty(variableProperty);
        if (current == Level.DELAY) {
            int value = implementingAnalyses.get().stream()
                    .mapToInt(a -> a.getProperty(variableProperty))
                    .reduce(variableProperty.best, VariableInfoImpl.MIN);
            if (value == Level.DELAY) {
                log(DELAYED, "Delaying aggregate of {} for {}", variableProperty, typeInfo.fullyQualifiedName);
                assert translatedDelay("AGG:" + variableProperty,
                        implementingAnalyses.get().stream().filter(a -> a.getProperty(variableProperty) == Level.DELAY)
                                .findFirst().orElseThrow().getTypeInfo().fullyQualifiedName + "." + variableProperty.name(),
                        typeInfo.fullyQualifiedName + "." + variableProperty.name());

                return DELAYS;
            }
            log(ANALYSER, "Set aggregate of {} to {} for {}", variableProperty, value, typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(variableProperty, value);
        }
        return DONE;
    }

    @Override
    public boolean ignorePrivateConstructorsForFieldValue() {
        return true; // there are no constructors
    }

    @Override
    protected String where(String componentName) {
        return typeInfo.fullyQualifiedName + ":AGG:" + componentName;
    }

}

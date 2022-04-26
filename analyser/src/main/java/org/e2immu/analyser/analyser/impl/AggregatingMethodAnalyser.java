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
import org.e2immu.analyser.analyser.statementanalyser.StatementAnalyserImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.config.AnalyserProgram.Step.ALL;

public class AggregatingMethodAnalyser extends MethodAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatingMethodAnalyser.class);

    public static final String MODIFIED = "modified";
    public static final String IMMUTABLE = "immutable";
    public static final String INDEPENDENT = "independent";
    public static final String FLUENT = "fluent";
    public static final String IDENTITY = "identity";
    public static final String IGNORE_MODS = "ignoreModifications";
    public static final String NOT_NULL = "notNull";
    public static final String METHOD_VALUE = "methodValue";
    private final SetOnce<List<MethodAnalysis>> implementingAnalyses = new SetOnce<>();
    private final AnalyserComponents<String, Integer> analyserComponents;

    AggregatingMethodAnalyser(MethodInfo methodInfo,
                              MethodAnalysisImpl.Builder methodAnalysis,
                              List<? extends ParameterAnalyser> parameterAnalysers,
                              List<ParameterAnalysis> parameterAnalyses,
                              AnalyserContext analyserContextInput) {
        super(methodInfo, methodAnalysis, parameterAnalysers,
                parameterAnalyses, Map.of(), false, analyserContextInput);
        assert methodAnalysis.analysisMode == Analysis.AnalysisMode.AGGREGATED;

        // TODO improve!
        methodAnalysis.ensureIsNotEventualUnlessOtherwiseAnnotated();

        AnalyserProgram analyserProgram = analyserContextInput.getAnalyserProgram();
        AnalyserComponents.Builder<String, Integer> builder = new AnalyserComponents.Builder<String, Integer>(analyserProgram)
                .add(MODIFIED, iteration -> this.aggregate(Property.MODIFIED_METHOD, DV::max, DV.MIN_INT_DV))
                .add(IMMUTABLE, iteration -> this.aggregate(Property.IMMUTABLE, DV::min, DV.MAX_INT_DV))
                .add(INDEPENDENT, iteration -> this.aggregate(Property.INDEPENDENT, DV::min, DV.MAX_INT_DV))
                .add(FLUENT, iteration -> this.aggregate(Property.FLUENT, DV::max, DV.MIN_INT_DV))
                .add(IDENTITY, iteration -> this.aggregate(Property.IDENTITY, DV::min, DV.MAX_INT_DV))
                .add(IGNORE_MODS, iteration -> this.aggregate(Property.IGNORE_MODIFICATIONS, DV::min, DV.MAX_INT_DV))
                .add(NOT_NULL, iteration -> this.aggregate(Property.NOT_NULL_EXPRESSION, DV::min, DV.MAX_INT_DV))
                .add(METHOD_VALUE, iteration -> this.aggregateMethodValue());

        analyserComponents = builder.build();
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "AMA " + methodInfo.fullyQualifiedName;
    }

    @Override
    public void initialize() {
        Stream<MethodInfo> implementations = obtainImplementingTypes().map(ti -> ti.findMethodImplementing(methodInfo));
        List<MethodAnalysis> analysers = implementations.map(analyserContext::getMethodAnalysis).toList();
        implementingAnalyses.set(analysers);
    }

    private Stream<TypeInfo> obtainImplementingTypes() {
        TypeInspection myTypeInspection = methodInfo.typeInfo.typeInspection.get();
        if (myTypeInspection.isSealed()) {
            return myTypeInspection.permittedWhenSealed().stream();
        }
        TypeInfo generated = methodInfo.typeInfo.typeResolution.get().generatedImplementation();
        assert generated != null : methodInfo.fullyQualifiedName
                + " does not belong to a sealed class, so it must have a unique generated implementation";
        return Stream.of(generated);
    }

    @Override
    public AnalyserResult analyse(int iteration, EvaluationContext closure) {
        AnalysisStatus analysisStatus = analyserComponents.run(iteration);
        if(analysisStatus.isDone() && analyserContext.getConfiguration().analyserConfiguration().analyserProgram().accepts(ALL)) methodAnalysis.internalAllDoneCheck();
        analyserResultBuilder.setAnalysisStatus(analysisStatus);
        List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                .debugConfiguration().afterMethodAnalyserVisitors();
        if (!visitors.isEmpty()) {
            for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                        null, methodInfo, methodAnalysis,
                        parameterAnalyses, analyserComponents.getStatusesAsMap(),
                        analyserResultBuilder::getMessageStream));
            }
        }
        return analyserResultBuilder.build();
    }

    private AnalysisStatus aggregateMethodValue() {
        if (!methodAnalysis.singleReturnValueIsFinal()) {
            CausesOfDelay delays = implementingAnalyses.get().stream()
                    .map(a -> a.getSingleReturnValue().causesOfDelay())
                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
            if (delays.isDelayed()) {
                methodAnalysis.setSingleReturnValue(delayedSrv(delays, true));
                return delays;
            }
            Expression singleValue = implementingAnalyses.get().stream().map(MethodAnalysis::getSingleReturnValue).findFirst().orElseThrow();
            // unless it is a constant, a parameter of the method, or statically assigned to a constructor (?) we can't do much
            Expression value;
            if (singleValue.isConstant()) {
                value = singleValue;
            } else {
                // TODO implement other cases, such as parameter values
                value = UnknownExpression.forHardcodedMethodReturnValue(methodInfo.identifier,
                        methodInfo.returnType(), "interface method");
            }
            methodAnalysis.setSingleReturnValue(value);
            LOGGER.debug("Set single value of {} to aggregate {}", methodInfo.fullyQualifiedName, singleValue);
        }
        return DONE;
    }

    private AnalysisStatus aggregate(Property property, BinaryOperator<DV> operator, DV start) {
        DV current = methodAnalysis.getProperty(property);
        if (current.isDelayed()) {
            DV value = implementingAnalyses.get().stream()
                    .map(a -> a.getProperty(property))
                    .reduce(start, operator);
            if (value.isDelayed()) {
                LOGGER.debug("Delaying aggregate of {} for {}", property, methodInfo.fullyQualifiedName);
                return value.causesOfDelay();
            }
            LOGGER.debug("Set aggregate of {} to {} for {}", property, value, methodInfo.fullyQualifiedName);
            methodAnalysis.setProperty(property, value);
        }
        return DONE;
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo) {
        return Stream.of();
    }

    @Override
    public StatementAnalyserImpl findStatementAnalyser(String index) {
        return null;
    }

    @Override
    public void logAnalysisStatuses() {
        // nothing
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return analyserComponents;
    }
}

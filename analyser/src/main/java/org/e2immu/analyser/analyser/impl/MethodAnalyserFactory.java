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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.MethodAnalyser;
import org.e2immu.analyser.analyser.ParameterAnalyser;
import org.e2immu.analyser.analyser.impl.aggregating.AggregatingMethodAnalyser;
import org.e2immu.analyser.analyser.impl.aggregating.AggregatingParameterAnalyser;
import org.e2immu.analyser.analyser.impl.computing.ComputedParameterAnalyser;
import org.e2immu.analyser.analyser.impl.computing.ComputingMethodAnalyser;
import org.e2immu.analyser.analyser.impl.shallow.CompanionAnalyser;
import org.e2immu.analyser.analyser.impl.shallow.ShallowMethodAnalyser;
import org.e2immu.analyser.analyser.nonanalyserimpl.LocalAnalyserContext;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodAnalyserFactory {
    public static MethodAnalyser create(MethodInfo methodInfo,
                                        TypeAnalysis typeAnalysis,
                                        boolean isSAM,
                                        boolean allowComputed,
                                        AnalyserContext analyserContextInput,
                                        boolean inAnnotatedAPI) {
        TypeInspection typeInspection = analyserContextInput.getTypeInspection(methodInfo.typeInfo);
        TypeResolution typeResolution = methodInfo.typeInfo.typeResolution.get();
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        Analysis.AnalysisMode analysisMode = computeAnalysisMode(methodInspection, typeInspection, typeResolution,
                allowComputed, inAnnotatedAPI);

        return switch (analysisMode) {
            case CONTRACTED -> createShallowMethodAnalyser(methodInfo, typeAnalysis, analyserContextInput);
            case AGGREGATED -> {
                assert !isSAM;
                List<? extends ParameterAnalyser> parameterAnalysers = methodInspection.getParameters().stream()
                        .map(parameterInfo -> new AggregatingParameterAnalyser(analyserContextInput, parameterInfo)).toList();
                List<ParameterAnalysis> parameterAnalyses = parameterAnalysers.stream()
                        .map(ParameterAnalyser::getParameterAnalysis).toList();
                MethodAnalysisImpl.Builder methodAnalysis = new MethodAnalysisImpl.Builder(analysisMode,
                        analyserContextInput.getPrimitives(), analyserContextInput, analyserContextInput,
                        methodInfo, typeAnalysis, parameterAnalyses);
                parameterAnalyses.forEach(pa -> ((ParameterAnalysisImpl.Builder) pa).setMethodAnalysis(methodAnalysis));
                yield new AggregatingMethodAnalyser(methodInfo, methodAnalysis, parameterAnalysers,
                        parameterAnalyses, analyserContextInput);
            }
            case COMPUTED -> {
                AnalyserContext analyserContext = new LocalAnalyserContext(analyserContextInput);
                List<? extends ParameterAnalyser> parameterAnalysers = methodInspection.getParameters().stream()
                        .map(parameterInfo -> new ComputedParameterAnalyser(analyserContext, parameterInfo)).toList();
                List<ParameterAnalysis> parameterAnalyses = parameterAnalysers.stream()
                        .map(ParameterAnalyser::getParameterAnalysis).toList();
                MethodAnalysisImpl.Builder methodAnalysis = new MethodAnalysisImpl.Builder(analysisMode,
                        analyserContext.getPrimitives(), analyserContext, analyserContext, methodInfo,
                        typeAnalysis, parameterAnalyses);
                parameterAnalyses.forEach(pa -> ((ParameterAnalysisImpl.Builder) pa).setMethodAnalysis(methodAnalysis));
                Map<CompanionMethodName, CompanionAnalyser> companionAnalysers = createCompanionAnalysers(methodInfo,
                        analyserContext, typeAnalysis);
                companionAnalysers.forEach((cmn, ca) -> {
                    methodAnalysis.companionAnalyses.put(cmn, ca.companionAnalysis);
                    if (cmn.aspect() != null && cmn.action() == CompanionMethodName.Action.ASPECT
                            && !typeAnalysis.getAspects().containsKey(cmn.aspect())) {
                        typeAnalysis.setAspect(cmn.aspect(), methodInfo);
                    }
                });
                yield new ComputingMethodAnalyser(methodInfo, typeAnalysis,
                        methodAnalysis, parameterAnalysers, parameterAnalyses, companionAnalysers, isSAM, analyserContext);
            }
        };
    }

    public static ShallowMethodAnalyser createShallowMethodAnalyser(MethodInfo methodInfo,
                                                                    TypeAnalysis typeAnalysis,
                                                                    AnalyserContext analyserContext) {
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        List<ParameterAnalysis> parameterAnalyses = methodInspection.getParameters().stream()
                .map(parameterInfo -> (ParameterAnalysis) new ParameterAnalysisImpl
                        .Builder(analyserContext.getPrimitives(), analyserContext, parameterInfo))
                .toList();
        MethodAnalysisImpl.Builder methodAnalysis = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                analyserContext.getPrimitives(), analyserContext, analyserContext,
                methodInfo, typeAnalysis, parameterAnalyses);
        parameterAnalyses.forEach(pa -> ((ParameterAnalysisImpl.Builder) pa).setMethodAnalysis(methodAnalysis));
        return new ShallowMethodAnalyser(methodInfo, methodAnalysis, parameterAnalyses, analyserContext);
    }

    private static Analysis.AnalysisMode computeAnalysisMode(MethodInspection methodInspection,
                                                             TypeInspection typeInspection,
                                                             TypeResolution typeResolution,
                                                             boolean allowComputed,
                                                             boolean inAnnotatedAPI) {
        boolean helper = "JavaUtil".equals(methodInspection.getMethodInfo().typeInfo.simpleName);
        if (inAnnotatedAPI && !helper) {
            return Analysis.AnalysisMode.CONTRACTED;
        }
        boolean isAbstract = (typeInspection.isInterface() || typeInspection.isAnnotation()) &&
                !methodInspection.isDefault() && !methodInspection.isStatic() ||
                methodInspection.isAbstract();
        if (isAbstract) {
            if (typeInspection.isSealed() || typeResolution.hasOneKnownGeneratedImplementation()) {
                return Analysis.AnalysisMode.AGGREGATED;
            }
            return Analysis.AnalysisMode.CONTRACTED;
        }
        if (methodInspection.getMethodBody().isEmpty()) return Analysis.AnalysisMode.CONTRACTED;

        return allowComputed ? Analysis.AnalysisMode.COMPUTED : Analysis.AnalysisMode.CONTRACTED;
    }


    private static Map<CompanionMethodName, CompanionAnalyser> createCompanionAnalysers(
            MethodInfo methodInfo,
            AnalyserContext analyserContext,
            TypeAnalysis typeAnalysis) {
        Map<CompanionMethodName, CompanionAnalyser> companionAnalysersBuilder = new HashMap<>();
        for (Map.Entry<CompanionMethodName, MethodInfo> entry : methodInfo.methodInspection.get().getCompanionMethods().entrySet()) {
            companionAnalysersBuilder.put(entry.getKey(),
                    new CompanionAnalyser(analyserContext, typeAnalysis, entry.getKey(), entry.getValue(),
                            methodInfo, AnnotationParameters.DEFAULT));
        }
        return Map.copyOf(companionAnalysersBuilder);
    }
}

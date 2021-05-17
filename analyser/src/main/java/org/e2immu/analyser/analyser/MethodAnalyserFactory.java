package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodAnalyserFactory {
    public static MethodAnalyser create(MethodInfo methodInfo,
                                          TypeAnalysis typeAnalysis,
                                          boolean isSAM,
                                          boolean allowComputed,
                                          AnalyserContext analyserContextInput) {
        TypeInspection typeInspection = analyserContextInput.getTypeInspection(methodInfo.typeInfo);
        TypeResolution typeResolution = methodInfo.typeInfo.typeResolution.get();
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        Analysis.AnalysisMode analysisMode = computeAnalysisMode(methodInspection, typeInspection, typeResolution,
                allowComputed);

        return switch (analysisMode) {
            case CONTRACTED -> createShallowMethodAnalyser(methodInfo, analyserContextInput);
            case AGGREGATED -> {
                assert !isSAM;
                List<? extends ParameterAnalyser> parameterAnalysers = methodInspection.getParameters().stream()
                        .map(parameterInfo -> new AggregatingParameterAnalyser(analyserContextInput, parameterInfo)).toList();
                List<ParameterAnalysis> parameterAnalyses = parameterAnalysers.stream()
                        .map(ParameterAnalyser::getParameterAnalysis).toList();
                MethodAnalysisImpl.Builder methodAnalysis = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.COMPUTED,
                        analyserContextInput.getPrimitives(), analyserContextInput, analyserContextInput,
                        methodInfo, parameterAnalyses);
                yield new AggregatingMethodAnalyser(methodInfo, methodAnalysis, parameterAnalysers,
                        parameterAnalyses, analyserContextInput);
            }
            case COMPUTED -> {
                AnalyserContext analyserContext = new ExpandableAnalyserContextImpl(analyserContextInput);
                List<? extends ParameterAnalyser> parameterAnalysers = methodInspection.getParameters().stream()
                        .map(parameterInfo -> new ComputedParameterAnalyser(analyserContext, parameterInfo)).toList();
                List<ParameterAnalysis> parameterAnalyses = parameterAnalysers.stream()
                        .map(ParameterAnalyser::getParameterAnalysis).toList();
                MethodAnalysisImpl.Builder methodAnalysis = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.COMPUTED,
                        analyserContext.getPrimitives(), analyserContext, analyserContext, methodInfo, parameterAnalyses);
                Map<CompanionMethodName, CompanionAnalyser> companionAnalysers = createCompanionAnalysers(methodInfo,
                        analyserContext, typeAnalysis);
                yield new ComputingMethodAnalyser(methodInfo, typeAnalysis,
                        methodAnalysis, parameterAnalysers, parameterAnalyses, companionAnalysers, isSAM, analyserContext);
            }
        };
    }

    public static ShallowMethodAnalyser createShallowMethodAnalyser(MethodInfo methodInfo, AnalyserContext analyserContext) {
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        List<ParameterAnalysis> parameterAnalyses = methodInspection.getParameters().stream()
                .map(parameterInfo -> (ParameterAnalysis) new ParameterAnalysisImpl
                        .Builder(Analysis.AnalysisMode.CONTRACTED, analyserContext.getPrimitives(),
                        analyserContext, parameterInfo))
                .toList();
        MethodAnalysisImpl.Builder methodAnalysis = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                analyserContext.getPrimitives(), analyserContext, analyserContext,
                methodInfo, parameterAnalyses);
        return new ShallowMethodAnalyser(methodInfo, methodAnalysis, parameterAnalyses, analyserContext);
    }

    private static Analysis.AnalysisMode computeAnalysisMode(MethodInspection methodInspection,
                                                             TypeInspection typeInspection,
                                                             TypeResolution typeResolution, boolean allowComputed) {
        boolean isAbstract = typeInspection.isInterface() &&
                !methodInspection.isDefault() && !methodInspection.isStatic() ||
                methodInspection.isAbstract();
        if (isAbstract) {
            if (typeInspection.isSealed() || typeResolution.hasOneKnownGeneratedImplementation()) {
                return Analysis.AnalysisMode.AGGREGATED;
            }
            return Analysis.AnalysisMode.CONTRACTED;
        }
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

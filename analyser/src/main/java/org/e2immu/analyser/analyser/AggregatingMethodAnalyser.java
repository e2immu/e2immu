package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AggregatingMethodAnalyser extends MethodAnalyser {

    AggregatingMethodAnalyser(MethodInfo methodInfo,
                              MethodAnalysisImpl.Builder methodAnalysis,
                              List<? extends ParameterAnalyser> parameterAnalysers,
                              List<ParameterAnalysis> parameterAnalyses,
                              AnalyserContext analyserContextInput) {
        super(methodInfo, methodAnalysis, parameterAnalysers,
                parameterAnalyses, Map.of(), false, analyserContextInput);
    }

    @Override
    public void initialize() {

    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        return null;
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return null;
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        return null;
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        return null;
    }

    @Override
    public void logAnalysisStatuses() {

    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return null;
    }

    @Override
    public void makeImmutable() {

    }

    @Override
    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean b) {
        return List.of();
    }
}

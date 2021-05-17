package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.ParameterInfo;

import java.util.stream.Stream;

public class AggregatingParameterAnalyser extends ParameterAnalyser {


    public AggregatingParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super(analyserContext, parameterInfo, Analysis.AnalysisMode.AGGREGATED);
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize(Stream<FieldAnalyser> fieldAnalyserStream) {

    }

    @Override
    protected AnalysisStatus analyse(int iteration) {
        return AnalysisStatus.DONE;
    }
}

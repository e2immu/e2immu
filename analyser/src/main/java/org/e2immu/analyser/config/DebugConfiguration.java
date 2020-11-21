package org.e2immu.analyser.config;

import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Fluent;

import java.util.ArrayList;
import java.util.List;

@E2Container
public class DebugConfiguration {

    public final List<TypeMapVisitor> typeMapVisitors;
    public final List<FieldAnalyserVisitor> afterFieldAnalyserVisitors;
    public final List<MethodAnalyserVisitor> afterMethodAnalyserVisitors;
    public final List<TypeAnalyserVisitor> afterTypePropertyComputations;
    public final List<StatementAnalyserVisitor> statementAnalyserVisitors;
    public final List<StatementAnalyserVariableVisitor> statementAnalyserVariableVisitors;
    public final List<EvaluationResultVisitor> evaluationResultVisitors;
    public final List<CompanionAnalyserVisitor> afterCompanionAnalyserVisitors;

    private DebugConfiguration(List<TypeMapVisitor> typeMapVisitors,
                               List<TypeAnalyserVisitor> afterTypePropertyComputations,
                               List<FieldAnalyserVisitor> afterFieldAnalyserVisitors,
                               List<MethodAnalyserVisitor> afterMethodAnalyserVisitors,
                               List<CompanionAnalyserVisitor> afterCompanionAnalyserVisitors,
                               List<StatementAnalyserVisitor> statementAnalyserVisitors,
                               List<StatementAnalyserVariableVisitor> statementAnalyserVariableVisitors,
                               List<EvaluationResultVisitor> evaluationResultVisitors) {
        this.afterFieldAnalyserVisitors = afterFieldAnalyserVisitors;
        this.afterMethodAnalyserVisitors = afterMethodAnalyserVisitors;
        this.statementAnalyserVisitors = statementAnalyserVisitors;
        this.statementAnalyserVariableVisitors = statementAnalyserVariableVisitors;
        this.afterTypePropertyComputations = afterTypePropertyComputations;
        this.typeMapVisitors = typeMapVisitors;
        this.evaluationResultVisitors = evaluationResultVisitors;
        this.afterCompanionAnalyserVisitors = afterCompanionAnalyserVisitors;
    }

    @Container(builds = DebugConfiguration.class)
    public static class Builder {
        private final List<TypeMapVisitor> typeMapVisitors = new ArrayList<>();
        private final List<FieldAnalyserVisitor> afterFieldAnalyserVisitors = new ArrayList<>();
        private final List<MethodAnalyserVisitor> afterMethodAnalyserVisitors = new ArrayList<>();
        private final List<StatementAnalyserVisitor> statementAnalyserVisitors = new ArrayList<>();
        private final List<StatementAnalyserVariableVisitor> statementAnalyserVariableVisitors = new ArrayList<>();
        private final List<TypeAnalyserVisitor> afterTypePropertyComputations = new ArrayList<>();
        private final List<EvaluationResultVisitor> evaluationResultVisitors = new ArrayList<>();
        private final List<CompanionAnalyserVisitor> afterCompanionAnalyserVisitors = new ArrayList<>();

        @Fluent
        public Builder addAfterFieldAnalyserVisitor(FieldAnalyserVisitor fieldAnalyserVisitor) {
            this.afterFieldAnalyserVisitors.add(fieldAnalyserVisitor);
            return this;
        }

        @Fluent
        public Builder addAfterMethodAnalyserVisitor(MethodAnalyserVisitor methodAnalyserVisitor) {
            this.afterMethodAnalyserVisitors.add(methodAnalyserVisitor);
            return this;
        }

        @Fluent
        public Builder addStatementAnalyserVisitor(StatementAnalyserVisitor statementAnalyserVisitor) {
            this.statementAnalyserVisitors.add(statementAnalyserVisitor);
            return this;
        }

        @Fluent
        public Builder addStatementAnalyserVariableVisitor(StatementAnalyserVariableVisitor statementAnalyserVariableVisitor) {
            this.statementAnalyserVariableVisitors.add(statementAnalyserVariableVisitor);
            return this;
        }

        @Fluent
        public Builder addAfterTypePropertyComputationsVisitor(TypeAnalyserVisitor typeAnalyserVisitor) {
            this.afterTypePropertyComputations.add(typeAnalyserVisitor);
            return this;
        }

        @Fluent
        public Builder addTypeContextVisitor(TypeMapVisitor typeMapVisitor) {
            this.typeMapVisitors.add(typeMapVisitor);
            return this;
        }

        @Fluent
        public Builder addEvaluationResultVisitor(EvaluationResultVisitor evaluationResultVisitor) {
            this.evaluationResultVisitors.add(evaluationResultVisitor);
            return this;
        }

        @Fluent
        public Builder addAfterCompanionAnalyserVisitor(CompanionAnalyserVisitor companionAnalyserVisitor) {
            this.afterCompanionAnalyserVisitors.add(companionAnalyserVisitor);
            return this;
        }

        public DebugConfiguration build() {
            return new DebugConfiguration(
                    ImmutableList.copyOf(typeMapVisitors),
                    ImmutableList.copyOf(afterTypePropertyComputations),
                    ImmutableList.copyOf(afterFieldAnalyserVisitors),
                    ImmutableList.copyOf(afterMethodAnalyserVisitors),
                    ImmutableList.copyOf(afterCompanionAnalyserVisitors),
                    ImmutableList.copyOf(statementAnalyserVisitors),
                    ImmutableList.copyOf(statementAnalyserVariableVisitors),
                    ImmutableList.copyOf(evaluationResultVisitors));
        }
    }
}

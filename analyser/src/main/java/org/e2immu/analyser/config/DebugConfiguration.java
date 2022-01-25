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

package org.e2immu.analyser.config;

import org.e2immu.analyser.visitor.*;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Fluent;

import java.util.ArrayList;
import java.util.List;

public record DebugConfiguration(List<TypeMapVisitor> typeMapVisitors,
                                 List<TypeAnalyserVisitor> afterTypePropertyComputations,
                                 List<FieldAnalyserVisitor> afterFieldAnalyserVisitors,
                                 List<MethodAnalyserVisitor> afterMethodAnalyserVisitors,
                                 List<StatementAnalyserVisitor> statementAnalyserVisitors,
                                 List<StatementAnalyserVariableVisitor> statementAnalyserVariableVisitors,
                                 List<EvaluationResultVisitor> evaluationResultVisitors) {

    @Container(builds = DebugConfiguration.class)
    public static class Builder {
        private final List<TypeMapVisitor> typeMapVisitors = new ArrayList<>();
        private final List<FieldAnalyserVisitor> afterFieldAnalyserVisitors = new ArrayList<>();
        private final List<MethodAnalyserVisitor> afterMethodAnalyserVisitors = new ArrayList<>();
        private final List<StatementAnalyserVisitor> statementAnalyserVisitors = new ArrayList<>();
        private final List<StatementAnalyserVariableVisitor> statementAnalyserVariableVisitors = new ArrayList<>();
        private final List<TypeAnalyserVisitor> afterTypePropertyComputations = new ArrayList<>();
        private final List<EvaluationResultVisitor> evaluationResultVisitors = new ArrayList<>();

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
        public Builder addAfterTypeAnalyserVisitor(TypeAnalyserVisitor typeAnalyserVisitor) {
            this.afterTypePropertyComputations.add(typeAnalyserVisitor);
            return this;
        }

        @Fluent
        public Builder addTypeMapVisitor(TypeMapVisitor typeMapVisitor) {
            this.typeMapVisitors.add(typeMapVisitor);
            return this;
        }

        @Fluent
        public Builder addEvaluationResultVisitor(EvaluationResultVisitor evaluationResultVisitor) {
            this.evaluationResultVisitors.add(evaluationResultVisitor);
            return this;
        }

        public DebugConfiguration build() {
            return new DebugConfiguration(
                    List.copyOf(typeMapVisitors),
                    List.copyOf(afterTypePropertyComputations),
                    List.copyOf(afterFieldAnalyserVisitors),
                    List.copyOf(afterMethodAnalyserVisitors),
                    List.copyOf(statementAnalyserVisitors),
                    List.copyOf(statementAnalyserVariableVisitors),
                    List.copyOf(evaluationResultVisitors));
        }
    }
}

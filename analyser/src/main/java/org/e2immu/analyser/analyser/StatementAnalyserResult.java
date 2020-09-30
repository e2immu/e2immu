/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Analysis;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class StatementAnalyserResult {
    private final Stream<Analysis.Modification> modifications;
    public final AnalysisStatus analysisStatus;

    private StatementAnalyserResult(AnalysisStatus analysisStatus, Stream<Analysis.Modification> modifications) {
        this.modifications = modifications;
        this.analysisStatus = analysisStatus;
    }

    public Stream<Analysis.Modification> getModifications() {
        return modifications;
    }

    public static class Builder {
        private List<Analysis.Modification> modifications;
        private AnalysisStatus analysisStatus;

        public Builder add(StatementAnalyserResult other) {
            other.modifications.forEach(modifications::add);
            analysisStatus = analysisStatus == null ? other.analysisStatus: analysisStatus.combine(other.analysisStatus);
            return this;
        }

        public Builder add(Analysis.Modification modification) {
            if (modifications == null) {
                modifications = new LinkedList<>();
            }
            modifications.add(modification);
            return this;
        }

        public Builder setAnalysisStatus(AnalysisStatus analysisStatus) {
            this.analysisStatus = analysisStatus;
            return this;
        }

        public StatementAnalyserResult build() {
            assert analysisStatus != null;
            return new StatementAnalyserResult(analysisStatus, modifications == null ? Stream.empty() : modifications.stream());
        }
    }
}

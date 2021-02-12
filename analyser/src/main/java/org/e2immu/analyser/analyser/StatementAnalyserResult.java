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

import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.stream.Stream;

public class StatementAnalyserResult {
    public final AnalysisStatus analysisStatus;
    public final Messages messages;

    private StatementAnalyserResult(AnalysisStatus analysisStatus, Messages messages) {
        this.analysisStatus = analysisStatus;
        this.messages = messages;
    }

    public static class Builder {
        private AnalysisStatus analysisStatus;
        public final Messages messages = new Messages();

        public Builder add(StatementAnalyserResult other) {
            analysisStatus = analysisStatus == null ? other.analysisStatus : analysisStatus.combine(other.analysisStatus);
            messages.addAll(other.messages);
            return this;
        }

        public Builder setAnalysisStatus(AnalysisStatus analysisStatus) {
            this.analysisStatus = analysisStatus;
            return this;
        }

        public Builder combineAnalysisStatus(AnalysisStatus other) {
            assert other != null;
            analysisStatus = other.combine(analysisStatus);
            return this;
        }

        public StatementAnalyserResult build() {
            assert analysisStatus != null;
            return new StatementAnalyserResult(analysisStatus, messages);
        }

        public Builder addMessages(Stream<Message> messageStream) {
            this.messages.addAll(messageStream);
            return this;
        }
    }
}

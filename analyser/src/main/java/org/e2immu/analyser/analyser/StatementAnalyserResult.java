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

import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record StatementAnalyserResult(AnalysisStatus analysisStatus,
                                      Messages messages,
                                      List<PrimaryTypeAnalyser> localAnalysers) {

    public static class Builder {
        private AnalysisStatus analysisStatus;
        public final Messages messages = new Messages();
        private final List<PrimaryTypeAnalyser> localAnalysers = new ArrayList<>();

        public Builder add(StatementAnalyserResult other) {
            analysisStatus = analysisStatus == null ? other.analysisStatus : analysisStatus.combine(other.analysisStatus);
            messages.addAll(other.messages);
            localAnalysers.addAll(other.localAnalysers);
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

        public Builder addMessages(Stream<Message> messageStream) {
            this.messages.addAll(messageStream);
            return this;
        }

        public Builder addTypeAnalysers(List<PrimaryTypeAnalyser> primaryTypeAnalysers) {
            this.localAnalysers.addAll(primaryTypeAnalysers);
            return this;
        }

        public StatementAnalyserResult build() {
            assert analysisStatus != null;
            return new StatementAnalyserResult(analysisStatus, messages, List.copyOf(localAnalysers));
        }
    }
}

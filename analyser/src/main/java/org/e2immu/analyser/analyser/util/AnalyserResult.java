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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.PrimaryTypeAnalyser;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record AnalyserResult(AnalysisStatus analysisStatus,
                             Messages messages,
                             VariableAccessReport variableAccessReport,
                             List<PrimaryTypeAnalyser> localAnalysers) {

    public static final AnalyserResult EMPTY = new AnalyserResult(AnalysisStatus.DONE,
            Messages.EMPTY, VariableAccessReport.EMPTY, List.of());

    public AnalyserResult combine(AnalyserResult other) {
        return new AnalyserResult(analysisStatus.combine(other.analysisStatus),
                messages.combine(other.messages),
                variableAccessReport.combine(other.variableAccessReport),
                combineLocalAnalysers(other.localAnalysers));
    }

    private List<PrimaryTypeAnalyser> combineLocalAnalysers(List<PrimaryTypeAnalyser> other) {
        if (localAnalysers.isEmpty()) return other;
        if (other.isEmpty()) return localAnalysers;
        return Stream.concat(localAnalysers.stream(), other.stream()).toList();
    }

    public static class Builder {
        private AnalysisStatus analysisStatus = AnalysisStatus.DONE;
        private final Messages messages = new Messages();
        private VariableAccessReport variableAccessReport = VariableAccessReport.EMPTY;
        private final List<PrimaryTypeAnalyser> localAnalysers = new ArrayList<>();

        public void add(AnalyserResult other) {
            add(other, true, false);
        }

        public void add(AnalyserResult other, boolean addLocalAnalysers, boolean limit) {
            this.variableAccessReport = variableAccessReport.combine(other.variableAccessReport);
            if (addLocalAnalysers) this.localAnalysers.addAll(other.localAnalysers);
            combineAnalysisStatus(other.analysisStatus, limit);
            this.messages.addAll(other.messages().getMessageStream());
        }

        public void addWithoutVariableAccess(AnalyserResult other) {
            this.localAnalysers.addAll(other.localAnalysers);
            combineAnalysisStatus(other.analysisStatus, false);
            this.messages.addAll(other.messages().getMessageStream());
        }

        public Builder setAnalysisStatus(AnalysisStatus analysisStatus) {
            this.analysisStatus = analysisStatus;
            return this;
        }

        public AnalysisStatus getAnalysisStatus() {
            return analysisStatus;
        }

        public Builder addMessages(Stream<Message> messageStream) {
            this.messages.addAll(messageStream);
            return this;
        }

        public void addMessages(Messages messages) {
            this.messages.addAll(messages.getMessageStream());
        }

        public void add(Message message) {
            if (message != null) messages.add(message);
        }

        public Builder setVariableAccessReport(VariableAccessReport variableAccessReport) {
            this.variableAccessReport = variableAccessReport;
            return this;
        }

        public Builder addTypeAnalysers(List<PrimaryTypeAnalyser> primaryTypeAnalysers) {
            this.localAnalysers.addAll(primaryTypeAnalysers);
            return this;
        }

        public AnalyserResult build() {
            assert analysisStatus != null;
            assert variableAccessReport != null;
            return new AnalyserResult(analysisStatus, messages, variableAccessReport, List.copyOf(localAnalysers));
        }

        public Builder combineAnalysisStatus(AnalysisStatus other, boolean limit) {
            analysisStatus = analysisStatus.combine(other, limit);
            return this;
        }

        public Stream<Message> getMessageStream() {
            return messages.getMessageStream();
        }
    }
}

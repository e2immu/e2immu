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

import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Message;

import java.util.Collection;
import java.util.stream.Stream;

public interface Analyser extends DelayDebugger {

    enum AnalyserIdentification {
        TYPE(null), // type does not have notNull
        FIELD(VariableProperty.EXTERNAL_NOT_NULL),
        PARAMETER(VariableProperty.NOT_NULL_PARAMETER),
        METHOD(VariableProperty.NOT_NULL_EXPRESSION);

        public final VariableProperty notNull;

        AnalyserIdentification(VariableProperty notNull) {
            this.notNull = notNull;
        }
    }

    // four stages

    void initialize();

    /*
    closure is null when called from primary type analyser, is not null when a type/method/... is being
    analysed from the statement analyser
     */
    AnalysisStatus analyse(int iteration, EvaluationContext closure);

    void write();

    void check();

    // other methods

    Stream<Message> getMessageStream();

    WithInspectionAndAnalysis getMember();

    Analysis getAnalysis();

    String getName();

    AnalyserComponents<String, ?> getAnalyserComponents();

    void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers);
}

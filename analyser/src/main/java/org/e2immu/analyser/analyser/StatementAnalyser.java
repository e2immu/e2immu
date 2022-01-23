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

import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.HasNavigationData;
import org.e2immu.analyser.parser.Message;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface StatementAnalyser extends HasNavigationData<StatementAnalyser> {
    AnalyserComponents<String, StatementAnalyserSharedState> getAnalyserComponents();

    // identical code in statement analysis
    StatementAnalyser navigateTo(String target);

    List<StatementAnalyser> lastStatementsOfNonEmptySubBlocks();

    EvaluationContext newEvaluationContextForOutside();

    StatementAnalysis getStatementAnalysis();

    StatementAnalyser lastStatement();

    //navigationData.next.get()
    Optional<StatementAnalyser> navigationDataNextGet();

    boolean navigationDataNextIsSet();

    boolean isDone();

    NavigationData<StatementAnalyser> navigationData();

    StatementAnalyser lastStatementOfSwitchOldStyle(String key);

    void makeImmutable();

    Stream<Message> getMessageStream();
}

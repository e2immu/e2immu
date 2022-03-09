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

package org.e2immu.analyser.parser.loops.testexample;

// debugging part of ComputingMethodAnalyser

import java.util.Optional;

public class VariableInLoop_1 {

    record NavigationData(Optional<Optional<StatementAnalyser>> next) {

    }
    interface StatementAnalyser {
        NavigationData navigationData();
    }

    private StatementAnalyser findFirstStatementWithDelays(StatementAnalyser firstStatementAnalyser) {
        StatementAnalyser sa = firstStatementAnalyser;
        while (sa != null) {
            //AnalyserComponents<String, StatementAnalyserSharedState> components = sa.getAnalyserComponents();
            //if (components.getStatusesAsMap().values().stream().anyMatch(AnalysisStatus::isDelayed)) {
            //    return sa;
            //}
            if (!sa.navigationData().next.isPresent()) return sa;
            Optional<StatementAnalyser> opt = sa.navigationData().next.get();
            if (opt.orElse(null) == null) return sa;
            sa = opt.get();
        }
        return null;
    }
}

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

/*
important: navigationData() is a single abstract method,
making StatementAnalyser a functional interface type.

this activates the @NotNull1 requirement when sa.navigationData() appears in
@NotNull context (we want the result of navigationData() to be not null as well)
(see notNullRequirementOnScope in MethodCall)

this observation comes in contrast with a not-null requirement in the condition
manager. In the competition between nullable-not nullable and not null-content not null
wins the former.

thirdly, the context not null built up in the while block is erased by the last statement.
so we end the block with sa = nullable.

the 'return null' statement hides a complex expression consisting of 3 exit points.
in iteration 0, it contains a number of delayed expressions that do NOT contain sa.
in iteration 1, 'sa' appears all of a sudden; this is problematic.
 */

import org.e2immu.annotation.Nullable;

import java.util.Optional;

public class VariableInLoop_1 {

    record NavigationData(Optional<Optional<StatementAnalyser>> next) {
        public NavigationData {
            assert next != null;
        }
    }

    interface StatementAnalyser {
        @Nullable
        NavigationData navigationData();
    }

    private static StatementAnalyser findFirstStatementWithDelays(StatementAnalyser firstStatementAnalyser) {
        StatementAnalyser sa = firstStatementAnalyser;
        while (sa != null) {
            if (!sa.navigationData().next.isPresent()) return sa;
            Optional<StatementAnalyser> opt = sa.navigationData().next.get();
            if (opt.orElse(null) == null) return sa;
            sa = opt.get();
        }
        return null;
    }
}

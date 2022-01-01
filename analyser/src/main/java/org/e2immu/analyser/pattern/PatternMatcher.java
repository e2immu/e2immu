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

package org.e2immu.analyser.pattern;

import org.e2immu.analyser.analyser.impl.StatementAnalyser;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.HasNavigationData;
import org.e2immu.analyser.model.MethodInfo;

public interface PatternMatcher<T extends HasNavigationData<T>> {
    PatternMatcher<StatementAnalyser> NO_PATTERN_MATCHER = new NoPatternMatcher<>();

    boolean matchAndReplace(MethodInfo methodInfo, T hasNavigationData, EvaluationContext evaluationContext);

    void startNewIteration();

    class NoPatternMatcher<T extends HasNavigationData<T>> implements PatternMatcher<T> {

        @Override
        public boolean matchAndReplace(MethodInfo methodInfo, T hasNavigationData, EvaluationContext evaluationContext) {
            return false;
        }

        @Override
        public void startNewIteration() {
            // nothing here
        }
    }
}

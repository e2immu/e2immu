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

package org.e2immu.analyser.pattern;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.model.EvaluationContext;
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

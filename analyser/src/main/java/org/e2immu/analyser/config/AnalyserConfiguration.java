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

package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;

import java.util.Objects;
import java.util.function.Supplier;

@E2Container
public class AnalyserConfiguration {

    public final boolean skipTransformations;
    private final Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier;

    public AnalyserConfiguration(boolean skipTransformations, Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier) {
        this.skipTransformations = skipTransformations;
        this.patternMatcherSupplier = Objects.requireNonNull(patternMatcherSupplier);
    }

    public PatternMatcher<StatementAnalyser> newPatternMatcher() {
        return patternMatcherSupplier.get();
    }

    @Container(builds = AnalyserConfiguration.class)
    public static class Builder {
        private boolean skipTransformations;
        private Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier;

        public Builder setSkipTransformations(boolean skipTransformations) {
            this.skipTransformations = skipTransformations;
            return this;
        }

        public void setPatternMatcherSupplier(Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier) {
            this.patternMatcherSupplier = patternMatcherSupplier;
        }

        public AnalyserConfiguration build() {
            return new AnalyserConfiguration(skipTransformations, patternMatcherSupplier == null ?
                    () -> PatternMatcher.NO_PATTERN_MATCHER : patternMatcherSupplier);
        }
    }
}

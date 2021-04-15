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

package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;

import java.util.Objects;
import java.util.function.Supplier;

@E2Container
public record AnalyserConfiguration(boolean skipTransformations,
                                    Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier) {

    public AnalyserConfiguration(boolean skipTransformations,
                                 Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier) {
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

        public Builder setPatternMatcherSupplier(Supplier<PatternMatcher<StatementAnalyser>> patternMatcherSupplier) {
            this.patternMatcherSupplier = patternMatcherSupplier;
            return this;
        }

        public AnalyserConfiguration build() {
            return new AnalyserConfiguration(skipTransformations, patternMatcherSupplier == null ?
                    () -> PatternMatcher.NO_PATTERN_MATCHER : patternMatcherSupplier);
        }
    }

    @Override
    public String toString() {
        return "AnalyserConfiguration:" +
                "\n    skipTransformations=" + skipTransformations;
    }
}

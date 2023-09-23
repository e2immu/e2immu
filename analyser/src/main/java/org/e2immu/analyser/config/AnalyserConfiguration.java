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

import org.e2immu.annotation.Container;

public record AnalyserConfiguration(boolean skipTransformations,
                                    boolean computeContextPropertiesOverAllMethods,
                                    boolean computeFieldAnalyserAcrossAllMethods,
                                    boolean forceExtraDelayForTesting,
                                    boolean forceAlphabeticAnalysisInPrimaryType,
                                    boolean normalizeMore) {

    @Container(builds = AnalyserConfiguration.class)
    public static class Builder {
        private boolean skipTransformations;

        // see @NotNull in FieldAnalyser for an explanation
        private boolean computeContextPropertiesOverAllMethods;
        private boolean computeFieldAnalyserAcrossAllMethods;
        private boolean forceExtraDelayForTesting;
        private boolean forceAlphabeticAnalysisInPrimaryType;
        private boolean normalizeMore;

        public Builder setSkipTransformations(boolean skipTransformations) {
            this.skipTransformations = skipTransformations;
            return this;
        }

        public Builder setNormalizeMore(boolean normalizeMore) {
            this.normalizeMore = normalizeMore;
            return this;
        }

        public Builder setForceAlphabeticAnalysisInPrimaryType(boolean forceAlphabeticAnalysisInPrimaryType) {
            this.forceAlphabeticAnalysisInPrimaryType = forceAlphabeticAnalysisInPrimaryType;
            return this;
        }

        public Builder setForceExtraDelayForTesting(boolean forceExtraDelayForTesting) {
            this.forceExtraDelayForTesting = forceExtraDelayForTesting;
            return this;
        }

        public Builder setComputeContextPropertiesOverAllMethods(boolean computeContextPropertiesOverAllMethods) {
            this.computeContextPropertiesOverAllMethods = computeContextPropertiesOverAllMethods;
            return this;
        }

        public Builder setComputeFieldAnalyserAcrossAllMethods(boolean computeFieldAnalyserAcrossAllMethods) {
            this.computeFieldAnalyserAcrossAllMethods = computeFieldAnalyserAcrossAllMethods;
            return this;
        }

        public AnalyserConfiguration build() {
            return new AnalyserConfiguration(skipTransformations,
                    computeContextPropertiesOverAllMethods,
                    computeFieldAnalyserAcrossAllMethods,
                    forceExtraDelayForTesting,
                    forceAlphabeticAnalysisInPrimaryType,
                    normalizeMore);
        }
    }

    @Override
    public String toString() {
        return "AnalyserConfiguration:" +
                "\n    skipTransformations=" + skipTransformations +
                "\n    computeContextPropertiesOverAllMethods=" + computeContextPropertiesOverAllMethods +
                "\n    computeFieldAnalyserAcrossAllMethods=" + computeFieldAnalyserAcrossAllMethods +
                "\n    forceExtraDelayForTesting=" + forceExtraDelayForTesting +
                "\n    normalizeMore=" + normalizeMore;
    }
}

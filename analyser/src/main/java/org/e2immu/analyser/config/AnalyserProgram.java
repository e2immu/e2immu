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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record AnalyserProgram(Step step, Set<Step> accepted) {

    public enum Step {

        NONE,
        INITIALISE,

        ITERATION_0(INITIALISE),

        TRANSPARENT(ITERATION_0),

        FIELD_FINAL(TRANSPARENT),

        MODIFIED(FIELD_FINAL),

        ITERATION_1PLUS(FIELD_FINAL, MODIFIED),

        ITERATION_1(ITERATION_1PLUS),

        ITERATION_2(ITERATION_1),

        ALL(ITERATION_2);

        Step() {
            this.dependsOn = Set.of();
        }

        Step(Step... dependsOn) {
            this.dependsOn = Arrays.stream(dependsOn).collect(Collectors.toUnmodifiableSet());
        }

        private final Set<Step> dependsOn;
    }

    public static final AnalyserProgram PROGRAM_ALL = from(Step.ALL);

    public static AnalyserProgram from(Step step) {
        Set<Step> accepted = new HashSet<>();
        recursivelyCollect(step, accepted, new HashSet<>());
        return new AnalyserProgram(step, accepted);
    }

    private static void recursivelyCollect(Step step, Set<Step> accepted, Set<Step> visited) {
        accepted.add(step);
        visited.add(step);
        for (Step s : step.dependsOn) {
            accepted.add(s);
            if (!visited.contains(s)) {
                recursivelyCollect(s, accepted, visited);
            }
        }
    }

    public boolean accepts(Step current) {
        return accepted.contains(current);
    }

    @Override
    public String toString() {
        return step + "=" + accepted;
    }
}

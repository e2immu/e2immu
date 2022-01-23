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

package org.e2immu.analyser.parser.minor.testexample;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

// trying to catch a bug in the merge system, caused when analysing the code of ComputingTypeAnalyser
// fails miserably, however, now catches an infinite loop.

// this code is semantic nonsense!!!
public class FieldReference_3 {
    public static final String NOT_NULL_PARAMETER = "nnp";

    record DV(int v) {
    }

    record ParameterAnalysis(Set<String> set) {
        public DV getProperty(String s) {
            return set.contains(s) ? new DV(3) : new DV(4);
        }

        public ParameterAnalysis copy(String s) {
            Set<String> newSet = new HashSet<>(set);
            newSet.add(s);
            return new ParameterAnalysis(newSet);
        }
    }

    static class AnalyserContext {
        private final ParameterAnalysis parameterAnalysis;

        public AnalyserContext(Set<String> set) {
            this.parameterAnalysis = new ParameterAnalysis(set);
        }

        private ParameterAnalysis getParameterAnalysis(String s) {
            return parameterAnalysis.copy(s);
        }
    }


    public static DV method(List<String> parameters) {
        DV notNull;
        AnalyserContext analyserContext = new AnalyserContext(Set.of("abc"));
        if (parameters.size() < 10) {
            if (parameters.isEmpty()) {
                notNull = null;
            } else {
                ParameterAnalysis p0 = analyserContext.getParameterAnalysis(parameters.get(0));
                notNull = p0.getProperty(NOT_NULL_PARAMETER);
            }
        } else {
            return new DV(0);
        }
        return notNull;
    }
}

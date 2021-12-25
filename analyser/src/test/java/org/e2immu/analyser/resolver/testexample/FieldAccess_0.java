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

package org.e2immu.analyser.resolver.testexample;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FieldAccess_0 {

    interface Analysis {
    }

    interface ParameterAnalysis extends Analysis {

    }

    static abstract class AnalysisImpl implements Analysis {
        Set<Integer> properties = new HashSet<>();
    }

    static abstract class AbstractAnalysisBuilder implements Analysis {
        Map<Integer, String> properties = new HashMap<>();
    }

    static class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {

        static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {

        }
    }

    static class Test1 {
        ParameterAnalysisImpl parameterAnalysis = new ParameterAnalysisImpl();

        public boolean method(int i) {
            return parameterAnalysis.properties.contains(i);
        }
    }

    static class Test2 {
        ParameterAnalysisImpl.Builder parameterAnalysis2 = new ParameterAnalysisImpl.Builder();

        public boolean method(int i) {
            return parameterAnalysis2.properties.containsKey(i);
        }
    }
}

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

package org.e2immu.analyser.parser.functional.testexample;

import java.util.List;
import java.util.Set;

public class InlinedMethod_8 {

    static class ParameterizedType {
        private final String s;

        public ParameterizedType(String in) {
            s = in;
        }

        boolean isLong() {
            return s == null || s.startsWith("x");
        }

        boolean isInt() {
            return s != null && s.endsWith("i");
        }
    }

    static class MethodInspection {
        private boolean b;

        public void setB(boolean b) {
            this.b = b;
        }

        List<ParameterInfo> getParameters() {
            return b ? List.of(new ParameterInfo(new ParameterizedType("i"), 0)) : List.of();
        }
    }

    interface InspectionProvider {
        MethodInspection getMethodInspection(InlinedMethod_8 inlinedMethods8);
    }

    record ParameterInfo(ParameterizedType parameterizedType, int index) {
    }

    private final String name;

    public InlinedMethod_8(String name) {
        this.name = name;
    }

    private static final Set<String> ZERO_PARAMS = Set.of("toString", "hashCode", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait");

    public boolean method(InspectionProvider inspectionProvider) {
        List<ParameterInfo> parameters = inspectionProvider.getMethodInspection(this).getParameters();
        int numParameters = parameters.size();
        if (numParameters == 0) {
            return ZERO_PARAMS.contains(name);
        }
        if (numParameters == 1) {
            return "equals".equals(name) || "wait".equals(name) && parameters.get(0).parameterizedType.isLong();
        }
        return numParameters == 2 && "wait".equals(name) && parameters.get(0).parameterizedType.isLong() &&
                parameters.get(1).parameterizedType.isInt();
    }
}

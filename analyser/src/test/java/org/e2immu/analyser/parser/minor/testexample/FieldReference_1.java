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


import java.util.Map;

/*
Captures a case where "cd" is not properly removed from the variable list, causing
an exception in the return statement "2"
 */
public class FieldReference_1 {

    interface EvaluationContext { }
    record ChangeData(Map<String, Integer> properties) {
        public Integer get(String s) { return properties().get(s); }
    }

    private final EvaluationContext evaluationContext = new EvaluationContext() {};
    private final Map<String, ChangeData> changeData = Map.of("X", new ChangeData(Map.of("3", 3)));

    public EvaluationContext method(boolean useEnnInsteadOfCnn) {
        if (useEnnInsteadOfCnn) {
            ChangeData cd = changeData.get("abc");
            if (cd != null) {
                Integer inChangeData = cd.properties.getOrDefault("x", null);
                if (inChangeData != null ) return null;
            }
        }
        return evaluationContext;
    }
}

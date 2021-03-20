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

package org.e2immu.analyser.analyser;

/*
statement id: id of the loop
assignment id: id of the location of assignment (creation of variable with 2nd $)
 */
public record VariableInLoop(String statementId, String assignmentId, VariableType variableType) {
    public static final VariableInLoop NOT_IN_LOOP = new VariableInLoop(null, null, VariableType.NOT_IN_LOOP);
    public static final VariableInLoop COPY_FROM_ENCLOSING_METHOD = new VariableInLoop(null, null, VariableType.COPY_FROM_ENCLOSING_METHOD);

    public String statementId(VariableType type) {
        return variableType == type ? statementId : null;
    }

    public String statementId(VariableType type1, VariableType type2) {
        return variableType == type1 || variableType == type2 ? statementId : null;
    }

    public enum VariableType {
        NOT_IN_LOOP, LOOP, LOOP_COPY, IN_LOOP_DEFINED_OUTSIDE, COPY_FROM_ENCLOSING_METHOD;
    }
}

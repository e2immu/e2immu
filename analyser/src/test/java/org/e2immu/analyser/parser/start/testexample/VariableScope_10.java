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

package org.e2immu.analyser.parser.start.testexample;

// differs from VS_9 in the absence of the suffix() implementation in VariableDefinedOutsideLoop
// this version mimics the current error in TestAnalyseCode; the _9 version shows a new error

public interface VariableScope_10 {

    default boolean removeInSubBlockMerge(String index) {
        return false;
    }


    record VariableDefinedOutsideLoop(VariableScope_10 previousVariableNature,
                                      String statementIndex) implements VariableScope_10 {

        @Override
        public boolean removeInSubBlockMerge(String index) {
            VariableScope_10 vn = this;
            while (vn instanceof VariableDefinedOutsideLoop vdol && vdol.statementIndex.startsWith(index + ".")) {
                vn = vdol.previousVariableNature;
            }
            if (vn != null && vn != this) {
                return vn.removeInSubBlockMerge(index);
            }
            return false;
        }
    }

}

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

package org.e2immu.analyser.parser.conditional.testexample;


import java.util.List;

// quasi identical to _9, but with "private" in front of the typeParameter field. Cause(s/d) a crash.
public class IfStatement_10 {

    static class ParameterizedType {
        String typeInfo;
        private String typeParameter;

        List<ParameterizedType> getTypeBounds() {
            return List.of();
        }

        int size() {
            return typeInfo.length();
        }
    }

    private int targetIsATypeParameter(ParameterizedType from, ParameterizedType target) {
        assert target.typeParameter != null;

        List<ParameterizedType> targetTypeBounds = target.getTypeBounds();
        if (targetTypeBounds.isEmpty()) {
            return 5;
        } // ** because we don't know that List.of().isEmpty() is true, the current state is !List.of().isEmpty
        // other is a type
        if (from.typeInfo != null) {
            return 6;
        }
        if (from.typeParameter != null) {
            List<ParameterizedType> fromTypeBounds = from.getTypeBounds();
            if (fromTypeBounds.isEmpty()) {  // because of **, this can never be true
                return 7; // unreachable code
            }
            // we both have type bounds; we go for the best combination
            int min = Integer.MAX_VALUE;
            for (ParameterizedType myBound : targetTypeBounds) {
                for (ParameterizedType otherBound : fromTypeBounds) {
                    int value = otherBound.size(); // potential null pointer on typeInfo, method expansion
                    if (value < min) min = value;
                }
            }
            return min + 8;

        }
        return 9;
    }
}

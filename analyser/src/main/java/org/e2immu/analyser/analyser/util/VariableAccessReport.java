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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record VariableAccessReport(Set<Variable> variablesRead) {

    public static final VariableAccessReport EMPTY = new VariableAccessReport(Set.of());

    public VariableAccessReport combine(VariableAccessReport other) {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;
        return new VariableAccessReport(
                Stream.concat(variablesRead.stream(), other.variablesRead.stream()).collect(Collectors.toUnmodifiableSet()));
    }

    private boolean isEmpty() {
        return variablesRead.isEmpty();
    }

    public static class Builder {
        private final Set<Variable> variablesRead = new HashSet<>();
        private final Map<FieldReference, VariableInfo> fieldsAssigned = new HashMap<>();

        public void addVariableRead(Variable v) {
            variablesRead.add(v);
        }

        public void addFieldAssigned(FieldReference fieldReference, VariableInfo variableInfo) {
            assert fieldReference == variableInfo.variable();
            fieldsAssigned.put(fieldReference, variableInfo);
        }

        public VariableAccessReport build() {
            if (variablesRead.isEmpty() && fieldsAssigned.isEmpty()) return EMPTY;
            return new VariableAccessReport(Set.copyOf(variablesRead));
        }
    }
}

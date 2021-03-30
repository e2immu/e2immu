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

import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GroupPropertyValues {

    public static final Set<VariableProperty> PROPERTIES = Set.of(VariableProperty.CONTEXT_PROPAGATE_MOD,
            VariableProperty.CONTEXT_MODIFIED, VariableProperty.CONTEXT_NOT_NULL, VariableProperty.EXTERNAL_NOT_NULL);

    private final Map<VariableProperty, Map<Variable, Integer>> map = new HashMap<>();

    public GroupPropertyValues() {
        for (VariableProperty variableProperty : PROPERTIES) {
            map.put(variableProperty, new HashMap<>());
        }
    }

    public Map<Variable, Integer> getMap(VariableProperty variableProperty) {
        return Objects.requireNonNull(map.get(variableProperty));
    }

    public void set(VariableProperty variableProperty, Variable variable, int value) {
        getMap(variableProperty).put(variable, value);
    }

    public int get(VariableProperty variableProperty, Variable variable, int defaultValue) {
        return getMap(variableProperty).getOrDefault(variable, defaultValue);
    }

    public void setIfKeyAbsent(VariableProperty variableProperty, Variable variable, int value) {
        Map<Variable, Integer> vpMap = getMap(variableProperty);
        if (!vpMap.containsKey(variable)) {
            vpMap.put(variable, value);
        }
    }
}

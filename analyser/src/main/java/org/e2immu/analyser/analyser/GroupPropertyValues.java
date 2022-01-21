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

import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;

public class GroupPropertyValues {

    public static final Set<Property> PROPERTIES = Set.of(
            Property.CONTEXT_MODIFIED,
            Property.CONTEXT_NOT_NULL,
            Property.CONTEXT_IMMUTABLE,
            Property.CONTEXT_CONTAINER,
            Property.EXTERNAL_NOT_NULL,
            Property.EXTERNAL_IMMUTABLE,
            Property.EXTERNAL_CONTAINER);

    private final Map<Property, Map<Variable, DV>> map = new HashMap<>();

    public GroupPropertyValues() {
        for (Property property : PROPERTIES) {
            map.put(property, new HashMap<>());
        }
    }

    public Map<Variable, DV> getMap(Property property) {
        return Objects.requireNonNull(map.get(property));
    }

    public void set(Property property, Variable variable, DV value) {
        getMap(property).put(variable, value);
    }

    public DV get(Property property, Variable variable, DV defaultValue) {
        return getMap(property).getOrDefault(variable, defaultValue);
    }

    public void setIfKeyAbsent(Property property, Variable variable, DV value) {
        Map<Variable, DV> vpMap = getMap(property);
        if (!vpMap.containsKey(variable)) {
            vpMap.put(variable, value);
        }
    }

    public void translate(TranslationMap translationMap) {
        for (Map<Variable, DV> dvMap : map.values()) {
            Set<Variable> toRemove = null;
            Map<Variable, DV> toAdd = null;
            for (Map.Entry<Variable, DV> entry : dvMap.entrySet()) {
                Variable translated = translationMap.translateVariable(entry.getKey());
                if (!translated.equals(entry.getKey())) {
                    if (!dvMap.containsKey(translated)) {
                        if (toAdd == null) toAdd = new HashMap<>();
                        toAdd.put(translated, entry.getValue());
                    }
                    if (toRemove == null) toRemove = new HashSet<>();
                    toRemove.add(entry.getKey());
                }
            }
            if (toRemove != null) dvMap.keySet().removeAll(toRemove);
            if (toAdd != null) dvMap.putAll(toAdd);
        }
    }
}

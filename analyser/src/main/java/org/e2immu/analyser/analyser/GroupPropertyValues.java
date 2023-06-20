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

import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;

public class GroupPropertyValues {

    public static final Set<Property> PROPERTIES = Set.of(
            CONTEXT_MODIFIED,
            CONTEXT_NOT_NULL,
            CONTEXT_IMMUTABLE,
            CONTEXT_CONTAINER,
            EXTERNAL_NOT_NULL,
            EXTERNAL_IMMUTABLE,
            CONTAINER_RESTRICTION,
            EXTERNAL_IGNORE_MODIFICATIONS);

    static {
        Set<Property> computed = Arrays.stream(values()).filter(Property::isGroupProperty).collect(Collectors.toUnmodifiableSet());
        assert PROPERTIES.equals(computed);
    }

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

    public void translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        for (Map<Variable, DV> dvMap : map.values()) {
            Set<Variable> toRemove = null;
            Map<Variable, DV> toAdd = null;
            for (Map.Entry<Variable, DV> entry : dvMap.entrySet()) {
                Variable translated = translationMap.translateVariable(inspectionProvider, entry.getKey());
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

    public void addToMap(StatementAnalysis statementAnalysis) {
        for (Property property : EXTERNALS) {
            addToMap(statementAnalysis, property);
        }
        for (Property property : CONTEXTS) {
            addToMap(statementAnalysis, property);
        }
    }

    private void addToMap(StatementAnalysis statementAnalysis, Property property) {
        Map<Variable, DV> map = getMap(property);
        statementAnalysis.rawVariableStream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            Variable variable = vi1.variable();
            if (!map.containsKey(variable)) { // variables that don't occur in contextNotNull
                DV prev = vi1.getProperty(property);
                if (prev.isDone()) {
                    if (vic.hasEvaluation()) {
                        VariableInfo vi = vic.best(EVALUATION);
                        DV eval = vi.getProperty(property);
                        if (eval.isDelayed()) {
                            // no value yet, nothing from evaluation
                            map.put(variable, prev);
                        } else {
                            // there already is a done value
                            map.put(variable, eval);
                        }
                    } else {
                        // there is no evaluation
                        map.put(variable, prev);
                    }
                } else {
                    // there is no previous yet
                    map.put(variable, prev);
                    if (property.propertyType == PropertyType.CONTEXT && "0".equals(statementAnalysis.index())) {
                        throw new UnsupportedOperationException(
                                "Impossible, all context properties start with non-delay: " + variable.fullyQualifiedName()
                                        + ", prop " + property);
                    }
                }
            }
        });
    }

    public void setDefaultsForScopeVariable(LocalVariableReference lvr) {
        for (Property property : PROPERTIES) {
            if (property == CONTEXT_NOT_NULL) {
                set(property, lvr, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            } else {
                set(property, lvr, property.falseDv);
            }
        }
    }
}

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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;
import java.util.function.Function;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;

public class GroupPropertyValues {

    public static final Set<Property> PROPERTIES = Set.of(
            Property.CONTEXT_MODIFIED,
            Property.CONTEXT_NOT_NULL,
            Property.CONTEXT_IMMUTABLE,
            Property.CONTEXT_CONTAINER,
            Property.EXTERNAL_NOT_NULL,
            Property.EXTERNAL_IMMUTABLE,
            Property.EXTERNAL_CONTAINER,
            EXTERNAL_IGNORE_MODIFICATIONS);

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


    public void addToMap(StatementAnalysis statementAnalysis, AnalyserContext analyserContext, Stage stage) {
        addToMap(statementAnalysis, CONTEXT_NOT_NULL, x -> AnalysisProvider.defaultNotNull(x.parameterizedType()), true);
        addToMap(statementAnalysis, EXTERNAL_NOT_NULL, x ->
                DelayFactory.createDelay(new VariableCause(x, statementAnalysis.location(stage),
                        CauseOfDelay.Cause.EXTERNAL_NOT_NULL)), false);
        addToMap(statementAnalysis, EXTERNAL_IMMUTABLE, x -> analyserContext.defaultImmutable(x.parameterizedType(), false), false);
        addToMap(statementAnalysis, EXTERNAL_CONTAINER, x -> EXTERNAL_CONTAINER.valueWhenAbsent(), false);
        addToMap(statementAnalysis, EXTERNAL_IGNORE_MODIFICATIONS, x -> EXTERNAL_IGNORE_MODIFICATIONS.valueWhenAbsent(), false);
        addToMap(statementAnalysis, CONTEXT_IMMUTABLE, x -> MultiLevel.NOT_INVOLVED_DV, false);
        addToMap(statementAnalysis, CONTEXT_MODIFIED, x -> DV.FALSE_DV, true);
        addToMap(statementAnalysis, CONTEXT_CONTAINER, x -> MultiLevel.NOT_CONTAINER_DV, true);
    }

    private void addToMap(StatementAnalysis statementAnalysis,
                          Property property,
                          Function<Variable, DV> falseValue,
                          boolean complainDelay0) {
        Map<Variable, DV> map = getMap(property);
        statementAnalysis.rawVariableStream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            if (!map.containsKey(vi1.variable())) { // variables that don't occur in contextNotNull
                DV prev = vi1.getProperty(property);
                if (prev.isDone()) {
                    if (vic.hasEvaluation()) {
                        VariableInfo vi = vic.best(EVALUATION);
                        DV eval = vi.getProperty(property);
                        if (eval.isDelayed()) {
                            map.put(vi.variable(), prev.maxIgnoreDelay(falseValue.apply(vi.variable())));
                        } else {
                            map.put(vi.variable(), eval);
                        }
                    } else {
                        map.put(vi1.variable(), prev);
                    }
                } else {
                    map.put(vi1.variable(), prev);
                    if (complainDelay0 && "0".equals(statementAnalysis.index())) {
                        throw new UnsupportedOperationException(
                                "Impossible, all variables start with non-delay: " + vi1.variable().fullyQualifiedName()
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

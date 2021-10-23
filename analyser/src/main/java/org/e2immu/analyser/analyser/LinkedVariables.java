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

import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record LinkedVariables(Map<Variable, Integer> variables, boolean isDelayed) {


    public LinkedVariables(Map<Variable, Integer> variables) {
        this(variables, variables.values().stream().anyMatch(v -> v == DELAYED_VALUE));
    }

    public LinkedVariables(Map<Variable, Integer> variables, boolean isDelayed) {
        assert variables != null;
        this.variables = Map.copyOf(variables);
        this.isDelayed = isDelayed;
        assert variables.isEmpty() || variables.values().stream().anyMatch(v -> v == DELAYED_VALUE) == isDelayed;
    }

    public static final int DELAYED_VALUE = -1;
    public static final int ASSIGNED = 0;
    public static final int DEPENDENT = 1;
    public static final int INDEPENDENT1 = 2;
    public static final int NO_LINKING = MultiLevel.MAX_LEVEL;

    public static final LinkedVariables EMPTY = new LinkedVariables(Map.of(), false);
    public static final LinkedVariables DELAYED_EMPTY = new LinkedVariables(Map.of(), true);

    // different object from DELAYED_EMPTY, used to ensure that EMPTY is set when there is no "normal" delay
    public static final LinkedVariables NOT_INVOLVED_DELAYED_EMPTY = new LinkedVariables(Map.of(), true);

    public static final String DELAY_STRING = "<delay>";

    public LinkedVariables merge(LinkedVariables other) {
        HashMap<Variable, Integer> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            Integer inMap = map.get(v);
            if (inMap == null) {
                map.put(v, i);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                int merged = inMap == ASSIGNED ? ASSIGNED : Math.min(i, inMap);
                map.put(v, merged);
            }
        });
        return new LinkedVariables(map);
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    @Override
    public String toString() {
        if (this == EMPTY) return "";
        if (isDelayed) return DELAY_STRING;

        return variables.entrySet().stream().map(e ->
                        e.getKey().output(Qualification.EMPTY).add(Symbol.COLON).add(new Text(e.getValue() + "")))
                .sorted()
                .collect(OutputBuilder.joining(Symbol.COMMA)).debug();
    }

    public String toSimpleString() {
        if (this == EMPTY) return "";
        return (isDelayed ? "*" : "") + variables.entrySet().stream()
                .map(e -> e.getKey().simpleName() + ":" + e.getValue())
                .sorted()
                .collect(Collectors.joining(","));
    }

    public String toDetailedString() {
        if (this == EMPTY) return "";

        return (isDelayed ? "*" : "") + variables.entrySet().stream().map(e ->
                        e.getKey().output(Qualification.EMPTY).add(Symbol.COLON).add(new Text(e.getValue() + "")))
                .sorted()
                .collect(OutputBuilder.joining(Symbol.COMMA)).debug();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedVariables that = (LinkedVariables) o;
        return variables.equals(that.variables) && isDelayed == that.isDelayed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables, isDelayed);
    }

    public LinkedVariables removeAllButLocalCopiesOf(Variable variable) {
        if (isEmpty()) return this;
        Map<Variable, Integer> remaining = variables.entrySet().stream()
                .filter(e -> e.getKey() instanceof LocalVariableReference lvr &&
                        variable.equals(lvr.variable.nature().localCopyOf()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LinkedVariables(remaining, isDelayed);
    }

    public boolean contains(Variable variable) {
        return variables.containsKey(variable);
    }

    public List<Variable> variablesAsList(int maxLevel) {
        return variables.entrySet().stream()
                .filter(e -> e.getValue() <= maxLevel)
                .map(Map.Entry::getKey)
                .toList();
    }

    public LinkedVariables translate(TranslationMap translationMap) {
        if (isEmpty()) return this;
        var translatedVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(e -> translationMap.translateVariable(e.getKey()), Map.Entry::getValue));
        return new LinkedVariables(translatedVariables, isDelayed);
    }
}

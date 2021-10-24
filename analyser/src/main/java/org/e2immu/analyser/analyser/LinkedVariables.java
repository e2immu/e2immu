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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Convention for spotting delays:

1. at assignment level: no delays, never
2. at dependent, independent1 level: add the variable, with DELAYED_VALUE
 */
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

    public static LinkedVariables sameValue(Stream<Variable> variables, int value) {
        return new LinkedVariables(variables.collect(Collectors.toMap(v -> v, v -> value)));
    }

    public static LinkedVariables of(Variable variable, int value) {
        return new LinkedVariables(Map.of(variable, value), value == DELAYED_VALUE);
    }

    public LinkedVariables mergeDelay(LinkedVariables other) {
        HashMap<Variable, Integer> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            Integer inMap = map.get(v);
            if (inMap == null) {
                map.put(v, DELAYED_VALUE);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                int merged = inMap == ASSIGNED ? ASSIGNED : DELAYED_VALUE;
                map.put(v, merged);
            }
        });
        return new LinkedVariables(map);
    }

    /*
    goal of the 'minimum' parameter:

    x = new X(b); expression b has linked variables b,0 when b is a variable expression
    the variable independent gives the independence of the first parameter of the constructor X(b)

    if DEPENDENT, then x is linked in an accessible way to b (maybe it stores b); max(0,0)=0
    if INDEPENDENT_1, then changes to b may imply changes to the hidden content of x; max(0,1)=1

    if the expression is e.g. c,1, as in new B(c)

    if DEPENDENT, then x is linked in an accessible way to an object b which is content linked to c
    changes to c have an impact on the hidden content of b, which is linked in an accessible way
    (leaving the hidden content hidden?) to x  max(1,0)=1
    if INDEPENDENT_1, then x is hidden content linked to b is hidden content linked to c, max(1,1)=1
     */
    public LinkedVariables merge(LinkedVariables other, int minimum) {
        HashMap<Variable, Integer> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            int newValue = Math.max(i, minimum);
            Integer inMap = map.get(v);
            if (inMap == null) {
                map.put(v, newValue);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                int merged = inMap == ASSIGNED ? ASSIGNED : Math.min(newValue, inMap);
                map.put(v, merged);
            }
        });
        return new LinkedVariables(map);
    }

    public LinkedVariables merge(LinkedVariables other) {
        return merge(other, Integer.MIN_VALUE); // no effect
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

    public Stream<Variable> variablesWithLevel(int level) {
        return variables.entrySet().stream()
                .filter(e -> e.getValue() == level)
                .map(Map.Entry::getKey);
    }

    public Stream<Variable> independent1Variables() {
        return variables.entrySet().stream()
                .filter(e -> e.getValue() > DEPENDENT)
                .map(Map.Entry::getKey);
    }

    public LinkedVariables translate(TranslationMap translationMap) {
        if (isEmpty()) return this;
        var translatedVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(e -> translationMap.translateVariable(e.getKey()), Map.Entry::getValue));
        return new LinkedVariables(translatedVariables, isDelayed);
    }

    public LinkedVariables changeToDelay() {
        Map<Variable, Integer> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == ASSIGNED ? ASSIGNED : DELAYED_VALUE));
        return new LinkedVariables(map);
    }

    public Integer value(Variable variable) {
        return variables.get(variable);
    }

    public static int mergeValues(int v1, int v2) {
        assert v1 > DELAYED_VALUE;
        assert v2 > DELAYED_VALUE;
        return Math.min(v1, v2);
    }
}

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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.LV.*;

/*
Convention for spotting delays:

1. at assignment level: no delays, never
2. at dependent, independent (hc) level: add the variable, with DELAYED_VALUE
 */
public class LinkedVariables implements Comparable<LinkedVariables>, Iterable<Map.Entry<Variable, LV>> {


    private final Map<Variable, LV> variables;

    private LinkedVariables(Map<Variable, LV> variables) {
        assert variables != null;
        this.variables = variables;
        assert variables.values().stream().noneMatch(lv -> lv == LINK_INDEPENDENT || lv == LINK_COMMON_HC);
    }

    // never use .equals() here, marker
    public static final LinkedVariables NOT_YET_SET = new LinkedVariables(Map.of());
    // use .equals, not a marker
    public static final LinkedVariables EMPTY = new LinkedVariables(Map.of());

    public static LV fromIndependentToLinkedVariableLevel(DV independent) {
        if (independent.isDelayed()) return LV.delay(independent.causesOfDelay());
        if (MultiLevel.INDEPENDENT_DV.equals(independent)) return LINK_INDEPENDENT;
        if (MultiLevel.DEPENDENT_DV.equals(independent)) return LINK_DEPENDENT;
        return LINK_COMMON_HC;
    }

    public boolean isDelayed() {
        if (this == NOT_YET_SET) return true;
        return variables.values().stream().anyMatch(LV::isDelayed);
    }

    public static LinkedVariables of(Variable variable, LV value) {
        return new LinkedVariables(Map.of(variable, value));
    }

    public static LinkedVariables of(Map<Variable, LV> map) {
        return new LinkedVariables(Map.copyOf(map));
    }

    public static LinkedVariables of(Variable var1, LV v1, Variable var2, LV v2) {
        return new LinkedVariables(Map.of(var1, v1, var2, v2));
    }

    public static boolean isAssigned(LV level) {
        return level == LINK_STATICALLY_ASSIGNED || level == LINK_ASSIGNED;
    }

    @Override
    public Iterator<Map.Entry<Variable, LV>> iterator() {
        return variables.entrySet().iterator();
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
    public LinkedVariables merge(LinkedVariables other, LV minimum) {
        if (this == NOT_YET_SET || other == NOT_YET_SET) return NOT_YET_SET;

        HashMap<Variable, LV> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            LV newValue = minimum.causesOfDelay().isInitialDelay() ? i : i.max(minimum);
            LV inMap = map.get(v);
            if (inMap == null) {
                map.put(v, newValue);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                LV merged = newValue.equals(LINK_STATICALLY_ASSIGNED) || inMap.equals(LINK_STATICALLY_ASSIGNED)
                        ? LINK_STATICALLY_ASSIGNED : newValue.min(inMap);
                map.put(v, merged);
            }
        });
        return of(map);
    }

    public LinkedVariables merge(LinkedVariables other) {
        return merge(other, LV.delay(DelayFactory.initialDelay())); // no effect
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    @Override
    public String toString() {
        if (this == NOT_YET_SET) return "NOT_YET_SET";
        if (this == EMPTY || variables.isEmpty()) return "";
        return variables.entrySet().stream()
                .map(e -> e.getKey().minimalOutput() + ":" + e.getValue().value())
                .sorted()
                .collect(Collectors.joining(","));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedVariables that = (LinkedVariables) o;
        return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
        return variables.hashCode();
    }

    public boolean contains(Variable variable) {
        return variables.containsKey(variable);
    }

    public Stream<Map.Entry<Variable, LV>> stream() {
        return variables.entrySet().stream();
    }

    public LinkedVariables maximum(LV other) {
        if (this == NOT_YET_SET) return NOT_YET_SET;
        return of(variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> other.max(e.getValue()))));
    }

    public Stream<Variable> variablesAssigned() {
        return variables.entrySet().stream()
                .filter(e -> isAssigned(e.getValue()))
                .map(Map.Entry::getKey);
    }

    public LinkedVariables translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        var translatedVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(e -> translationMap.translateVariable(inspectionProvider,
                        e.getKey()), Map.Entry::getValue, LV::min));
        if (translatedVariables.equals(variables)) return this;
        return of(translatedVariables);
    }

    public LinkedVariables changeToDelay(LV delay) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        assert delay.isDelayed();
        Map<Variable, LV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().equals(LINK_STATICALLY_ASSIGNED) ? LINK_STATICALLY_ASSIGNED : delay.min(e.getValue())));
        return of(map);
    }

    public LinkedVariables remove(Set<Variable> reassigned) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, LV> map = variables.entrySet().stream()
                .filter(e -> !reassigned.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }

    public LinkedVariables remove(Predicate<Variable> remove) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, LV> map = variables.entrySet().stream()
                .filter(e -> !remove.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }

    public LinkedVariables changeNonStaticallyAssignedToDelay(CausesOfDelay delay) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        assert delay.isDelayed();
        LV delayedLV = LV.delay(delay);
        Map<Variable, LV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    LV lv = e.getValue();
                    return LINK_STATICALLY_ASSIGNED.equals(lv) ? LINK_STATICALLY_ASSIGNED : delayedLV;
                }));
        return of(map);
    }

    public LinkedVariables changeAllTo(LV value) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, LV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> value));
        return of(map);
    }

    public LinkedVariables changeAllToUnlessDelayed(LV value) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, LV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().isDelayed() ? e.getValue() : value));
        return of(map);
    }

    public LV value(Variable variable) {
        return variables.get(variable);
    }

    public static boolean isAssignedOrLinked(LV dependent) {
        return dependent.ge(LINK_STATICALLY_ASSIGNED) && dependent.le(LINK_DEPENDENT);
    }

    public static final CausesOfDelay NOT_YET_SET_DELAY = DelayFactory.createDelay(Location.NOT_YET_SET,
            CauseOfDelay.Cause.LINKING);

    public CausesOfDelay causesOfDelay() {
        if (this == NOT_YET_SET) {
            return NOT_YET_SET_DELAY;
        }
        return variables.values().stream()
                .map(LV::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    public Map<Variable, LV> variables() {
        return variables;
    }

    public boolean isDone() {
        if (this == NOT_YET_SET) return false;
        return causesOfDelay().isDone();
    }

    @Override
    public int compareTo(LinkedVariables o) {
        return Properties.compareMaps(variables, o.variables);
    }

    private Set<Variable> staticallyAssigned() {
        return staticallyAssignedStream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean identicalStaticallyAssigned(LinkedVariables linkedVariables) {
        return staticallyAssigned().equals(linkedVariables.staticallyAssigned());
    }

    public Set<Variable> directAssignmentVariables() {
        if (isEmpty() || this == NOT_YET_SET) return Set.of();
        return variables.entrySet().stream().filter(e -> e.getValue().equals(LINK_STATICALLY_ASSIGNED))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public Stream<Variable> staticallyAssignedStream() {
        return variables.entrySet().stream().filter(e -> LINK_STATICALLY_ASSIGNED.equals(e.getValue())).map(Map.Entry::getKey);
    }

    public Stream<Variable> dependentVariables() {
        return variables.entrySet().stream().filter(e -> LINK_DEPENDENT.equals(e.getValue())).map(Map.Entry::getKey);
    }

    public Stream<Variable> assignedOrDependentVariables() {
        return variables.entrySet().stream().filter(e -> isAssignedOrLinked(e.getValue())).map(Map.Entry::getKey);
    }

    public LinkedVariables removeStaticallyAssigned() {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, LV> map = variables.entrySet().stream()
                .filter(e -> !e.getValue().equals(LINK_STATICALLY_ASSIGNED))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }
}

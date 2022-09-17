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
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.analyser.util.ComputeIndependent;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Convention for spotting delays:

1. at assignment level: no delays, never
2. at dependent, independent1 level: add the variable, with DELAYED_VALUE
 */
public class LinkedVariables implements Comparable<LinkedVariables>, Iterable<Map.Entry<Variable, DV>> {

    private final Map<Variable, DV> variables;

    private LinkedVariables(Map<Variable, DV> variables) {
        assert variables != null;
        this.variables = variables;
        assert variables.values().stream().noneMatch(dv -> dv == DV.FALSE_DV
                || dv == MultiLevel.INDEPENDENT_HC_DV
                || dv == MultiLevel.DEPENDENT_DV
                || dv == LINK_INDEPENDENT);
    }

    // never use .equals() here, marker
    public static final LinkedVariables NOT_YET_SET = new LinkedVariables(Map.of());
    // use .equals, not a marker
    public static final LinkedVariables EMPTY = new LinkedVariables(Map.of());

    public static DV fromIndependentToLinkedVariableLevel(DV independent, ParameterizedType sourceType, SetOfTypes hiddenContentOfTargetType) {
        if (independent.isDelayed()) return independent;
        if (MultiLevel.INDEPENDENT_DV.equals(independent)) return LinkedVariables.LINK_INDEPENDENT;
        if (MultiLevel.DEPENDENT_DV.equals(independent)) return LinkedVariables.LINK_DEPENDENT;
        return hiddenContentOfTargetType.contains(sourceType) ? LINK_IS_HC_OF : LINK_COMMON_HC;
    }

    public static DV fromImmutableToLinkedVariableLevel(DV immutable,
                                                        AnalyserContext analyserContext,
                                                        TypeInfo currentType,
                                                        ParameterizedType sourceType,
                                                        ParameterizedType targetType) {
        if (immutable.isDelayed()) return immutable;
        // REC IMM -> NO_LINKING
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return LinkedVariables.LINK_INDEPENDENT;
        ComputeIndependent computeIndependent = new ComputeIndependent(analyserContext, currentType);
        return computeIndependent.linkLevelOfTwoHCRelatedTypes(sourceType, targetType);
    }

    public boolean isDelayed() {
        if (this == NOT_YET_SET) return true;
        return variables.values().stream().anyMatch(DV::isDelayed);
    }

    public static final DV LINK_STATICALLY_ASSIGNED = new NoDelay(0, "statically_assigned");
    public static final DV LINK_ASSIGNED = new NoDelay(1, "assigned");
    public static final DV LINK_DEPENDENT = new NoDelay(2, "dependent");
    public static final DV LINK_IS_HC_OF = new NoDelay(3, "is_hc_of");
    public static final DV LINK_COMMON_HC = new NoDelay(4, "common_hc");
    public static final DV LINK_INDEPENDENT = new NoDelay(5, "independent");

    public static LinkedVariables of(Variable variable, DV value) {
        return new LinkedVariables(Map.of(variable, value));
    }

    public static LinkedVariables of(Map<Variable, DV> map) {
        return new LinkedVariables(Map.copyOf(map));
    }

    public static LinkedVariables of(Variable var1, DV v1, Variable var2, DV v2) {
        return new LinkedVariables(Map.of(var1, v1, var2, v2));
    }

    public static boolean isAssigned(DV level) {
        return level.equals(LINK_STATICALLY_ASSIGNED) || level.equals(LINK_ASSIGNED);
    }

    @Override
    public Iterator<Map.Entry<Variable, DV>> iterator() {
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
    public LinkedVariables merge(LinkedVariables other, DV minimum) {
        if (this == NOT_YET_SET || other == NOT_YET_SET) return NOT_YET_SET;

        HashMap<Variable, DV> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            DV newValue = minimum == DV.MIN_INT_DV ? i : i.max(minimum);
            DV inMap = map.get(v);
            if (inMap == null) {
                map.put(v, newValue);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                DV merged = newValue.equals(LINK_STATICALLY_ASSIGNED) || inMap.equals(LINK_STATICALLY_ASSIGNED)
                        ? LINK_STATICALLY_ASSIGNED : newValue.min(inMap);
                map.put(v, merged);
            }
        });
        return of(map);
    }

    public LinkedVariables merge(LinkedVariables other) {
        return merge(other, DV.MIN_INT_DV); // no effect
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

    public Stream<Map.Entry<Variable, DV>> stream() {
        return variables.entrySet().stream();
    }

    public LinkedVariables minimum(DV minimum) {
        if (this == NOT_YET_SET) return NOT_YET_SET;
        return of(variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> minimum.max(e.getValue()))));
    }

    public Stream<Variable> variablesAssigned() {
        return variables.entrySet().stream()
                .filter(e -> isAssigned(e.getValue()))
                .map(Map.Entry::getKey);
    }

    public LinkedVariables translate(TranslationMap translationMap) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        var translatedVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(e -> translationMap.translateVariable(e.getKey()), Map.Entry::getValue, DV::min));
        if (translatedVariables.equals(variables)) return this;
        return of(translatedVariables);
    }

    public LinkedVariables changeToDelay(DV delay) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        assert delay.isDelayed();
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().equals(LINK_STATICALLY_ASSIGNED) ? LINK_STATICALLY_ASSIGNED : delay.min(e.getValue())));
        return of(map);
    }

    public LinkedVariables remove(Set<Variable> reassigned) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .filter(e -> !reassigned.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }

    public LinkedVariables remove(Predicate<Variable> remove) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .filter(e -> !remove.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }

    public LinkedVariables changeNonStaticallyAssignedToDelay(DV delay) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        assert delay.isDelayed();
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    DV lv = e.getValue();
                    return LINK_STATICALLY_ASSIGNED.equals(lv) ? LINK_STATICALLY_ASSIGNED : delay.max(lv);
                }));
        return of(map);
    }

    public LinkedVariables changeAllTo(DV value) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> value));
        return of(map);
    }

    public LinkedVariables changeAllToUnlessDelayed(DV value) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().isDelayed() ? e.getValue() : value));
        return of(map);
    }

    public DV value(Variable variable) {
        return variables.get(variable);
    }

    public static boolean isAssignedOrLinked(DV dependent) {
        return dependent.ge(LINK_STATICALLY_ASSIGNED) && dependent.le(LINK_DEPENDENT);
    }

    public static final CausesOfDelay NOT_YET_SET_DELAY = DelayFactory.createDelay(Location.NOT_YET_SET,
            CauseOfDelay.Cause.LINKING);

    public CausesOfDelay causesOfDelay() {
        if (this == NOT_YET_SET) {
            return NOT_YET_SET_DELAY;
        }
        return variables.values().stream()
                .map(DV::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    public Map<Variable, DV> variables() {
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
}

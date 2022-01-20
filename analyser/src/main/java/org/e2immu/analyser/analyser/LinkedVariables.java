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

import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Convention for spotting delays:

1. at assignment level: no delays, never
2. at dependent, independent1 level: add the variable, with DELAYED_VALUE
 */
public class LinkedVariables {

    private final Map<Variable, DV> variables;
    private final CausesOfDelay causesOfDelay;

    public LinkedVariables(Map<Variable, DV> variables) {
        this(variables, variables.values().stream()
                .map(DV::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge));
    }

    public LinkedVariables(Map<Variable, DV> variables, CausesOfDelay otherCausesOfDelay) {
        assert variables != null;
        this.variables = Map.copyOf(variables);
        assert variables.values().stream().noneMatch(dv -> dv == DV.FALSE_DV);
        CausesOfDelay causesOfDelay = variables.values().stream()
                .map(DV::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        this.causesOfDelay = causesOfDelay.merge(otherCausesOfDelay);
    }

    public static LinkedVariables delayedEmpty(CausesOfDelay causes) {
        return new LinkedVariables(Map.of(), causes);
    }

    public static DV fromIndependentToLinkedVariableLevel(DV dv) {
        assert dv.lt(MultiLevel.INDEPENDENT_DV); // cannot be linked
        if (dv.equals(MultiLevel.DEPENDENT_DV)) return DEPENDENT_DV;
        if (dv.equals(MultiLevel.INDEPENDENT_1_DV)) return INDEPENDENT1_DV;
        return new NoDelay(MultiLevel.level(dv) + 2);
    }

    public boolean isDelayed() {
        return causesOfDelay.isDelayed();
    }

    public static final DV STATICALLY_ASSIGNED_DV = new NoDelay(0, "statically_assigned");
    public static final DV ASSIGNED_DV = new NoDelay(1, "assigned");
    public static final DV DEPENDENT_DV = new NoDelay(2, "dependent");
    public static final DV INDEPENDENT1_DV = new NoDelay(3, "independent1");
    public static final DV NO_LINKING_DV = new NoDelay(MultiLevel.MAX_LEVEL, "no");

    public static final LinkedVariables EMPTY = new LinkedVariables(Map.of(), CausesOfDelay.EMPTY);

    public static LinkedVariables sameValue(Stream<Variable> variables, DV value) {
        return new LinkedVariables(variables.collect(Collectors.toMap(v -> v, v -> value)));
    }

    public static LinkedVariables of(Variable variable, DV value) {
        return new LinkedVariables(Map.of(variable, value));
    }

    public static LinkedVariables of(Variable var1, DV v1, Variable var2, DV v2) {
        return new LinkedVariables(Map.of(var1, v1, var2, v2));
    }

    public static boolean isNotIndependent(DV assignedOrLinked) {
        return assignedOrLinked.ge(STATICALLY_ASSIGNED_DV) && assignedOrLinked.lt(NO_LINKING_DV);
    }

    public static boolean isAssigned(DV level) {
        return level.equals(STATICALLY_ASSIGNED_DV) || level.equals(ASSIGNED_DV);
    }

    public LinkedVariables mergeDelay(LinkedVariables other, DV whenMissing) {
        assert whenMissing.isDelayed();
        HashMap<Variable, DV> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            DV inMap = map.get(v);
            if (inMap == null) {
                map.put(v, whenMissing);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                DV merged = inMap.equals(STATICALLY_ASSIGNED_DV) ? STATICALLY_ASSIGNED_DV : whenMissing;
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
    public LinkedVariables merge(LinkedVariables other, DV minimum) {
        HashMap<Variable, DV> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            DV newValue = minimum == DV.MIN_INT_DV ? i : i.max(minimum);
            DV inMap = map.get(v);
            if (inMap == null) {
                map.put(v, newValue);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                DV merged = newValue.equals(STATICALLY_ASSIGNED_DV) || inMap.equals(STATICALLY_ASSIGNED_DV)
                        ? STATICALLY_ASSIGNED_DV : newValue.min(inMap);
                map.put(v, merged);
            }
        });
        return new LinkedVariables(map);
    }

    public LinkedVariables merge(LinkedVariables other) {
        return merge(other, DV.MIN_INT_DV); // no effect
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    @Override
    public String toString() {
        if (this == EMPTY) return "";
        return variables.entrySet().stream()
                .map(e -> e.getKey().debug() + ":" + e.getValue().value())
                .sorted()
                .collect(Collectors.joining(","));

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedVariables that = (LinkedVariables) o;
        return variables.equals(that.variables) && causesOfDelay.equals(that.causesOfDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables, causesOfDelay);
    }

    public boolean contains(Variable variable) {
        return variables.containsKey(variable);
    }

    public LinkedVariables removeDelays() {
        return new LinkedVariables(variables.entrySet().stream()
                .filter(e -> e.getValue().value() >= 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public LinkedVariables minimum(DV minimum) {
        return new LinkedVariables(variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> minimum.max(e.getValue()))));
    }

    public Stream<Variable> variablesAssignedOrDependent() {
        return variables.entrySet().stream()
                .filter(e -> isAssignedOrLinked(e.getValue()))
                .map(Map.Entry::getKey);
    }

    public Stream<Variable> variablesWithLevel(int level) {
        return variables.entrySet().stream()
                .filter(e -> e.getValue().value() == level)
                .map(Map.Entry::getKey);
    }

    public Stream<Variable> independent1Variables() {
        return variables.entrySet().stream()
                .filter(e -> e.getValue().gt(DEPENDENT_DV))
                .map(Map.Entry::getKey);
    }

    public LinkedVariables translate(TranslationMap translationMap) {
        if (isEmpty()) return this;
        var translatedVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(e -> translationMap.translateVariable(e.getKey()), Map.Entry::getValue, DV::min));
        return new LinkedVariables(translatedVariables);
    }

    public LinkedVariables changeToDelay(DV delay) {
        assert delay.isDelayed();
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().equals(STATICALLY_ASSIGNED_DV) ? STATICALLY_ASSIGNED_DV : delay.min(e.getValue())));
        return new LinkedVariables(map);
    }

    public LinkedVariables remove(Set<Variable> reassigned) {
        Map<Variable, DV> map = variables.entrySet().stream()
                .filter(e -> !reassigned.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LinkedVariables(map);
    }

    public LinkedVariables remove(Predicate<Variable> remove) {
        Map<Variable, DV> map = variables.entrySet().stream()
                .filter(e -> !remove.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LinkedVariables(map);
    }

    public LinkedVariables changeAllToDelay(DV delay) {
        assert delay.isDelayed();
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> delay.max(e.getValue())));
        return new LinkedVariables(map);
    }

    public LinkedVariables changeAllTo(DV value) {
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> value));
        return new LinkedVariables(map);
    }

    public DV value(Variable variable) {
        return variables.get(variable);
    }

    /*
    we prune a linked variables map, based on immutable values.
    if the source is @ERImmutable, then there cannot be linked; but the same holds for the targets!
     */
    public LinkedVariables removeIncompatibleWithImmutable(DV sourceImmutable,
                                                           Predicate<Variable> myself,
                                                           Function<Variable, DV> computeImmutable,
                                                           Function<Variable, DV> immutableCanBeIncreasedByTypeParameters,
                                                           Function<Variable, DV> computeImmutableHiddenContent) {
        if (sourceImmutable.isDelayed()) {
            return changeToDelay(sourceImmutable); // but keep the 0
        }

        Map<Variable, DV> adjustedSource;
        if (!variables.isEmpty() && sourceImmutable.ge(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV)) {
            // level 2+ -> remove all @Dependent
            boolean recursivelyImmutable = sourceImmutable.equals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV);
            adjustedSource = variables.entrySet().stream()
                    .filter(e -> recursivelyImmutable ? e.getValue().le(ASSIGNED_DV) :
                            !e.getValue().equals(DEPENDENT_DV))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            adjustedSource = variables;
        }
        Map<Variable, DV> result = new HashMap<>();
        for (Map.Entry<Variable, DV> entry : adjustedSource.entrySet()) {
            DV linkLevel = entry.getValue();
            Variable target = entry.getKey();
            if (myself.test(target)) {
                result.put(target, linkLevel);
            } else {
                DV targetImmutable = computeImmutable.apply(target);
                if (targetImmutable.isDelayed()) {
                    result.put(target, targetImmutable);
                } else if (targetImmutable.lt(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)) {
                    if (linkLevel.le(DEPENDENT_DV)) {
                        result.put(target, linkLevel);
                    } else { // INDEPENDENT1+
                        DV canIncrease = immutableCanBeIncreasedByTypeParameters.apply(target);
                        if (canIncrease.isDelayed()) {
                            result.put(target, canIncrease);
                        } else if (canIncrease.valueIsTrue()) {
                            DV immutableHidden = computeImmutableHiddenContent.apply(target);
                            if (immutableHidden.lt(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)) {
                                result.put(target, linkLevel);
                            }
                        } else {
                            result.put(target, linkLevel);
                        }
                    }
                } else {
                    // targetImmutable is @ERImmutable
                    if (linkLevel.le(ASSIGNED_DV)) {
                        result.put(target, linkLevel);
                    }
                }
            }
        }
        return new LinkedVariables(result);
    }

    public static boolean isAssignedOrLinked(DV dependent) {
        return dependent.ge(STATICALLY_ASSIGNED_DV) && dependent.le(DEPENDENT_DV);
    }

    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    public Map<Variable, DV> variables() {
        return variables;
    }

    public boolean isDone() {
        return causesOfDelay.isDone();
    }
}

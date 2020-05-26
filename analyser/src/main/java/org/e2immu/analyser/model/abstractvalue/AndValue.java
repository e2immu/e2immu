/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CNF;
import static org.e2immu.analyser.util.Logger.log;

public class AndValue implements Value {
    public final List<Value> values;

    public AndValue() {
        values = List.of();
    }

    private AndValue(Value... values) {
        this.values = List.of(values);
    }

    private AndValue(List<Value> values) {
        this.values = values;
    }

    // we try to maintain a CNF
    public Value append(Value... values) {

        // STEP 1: trivial reductions

        if (this.values.isEmpty() && values.length == 1 && values[0] instanceof AndValue) return values[0];

        // STEP 2: concat everything

        ArrayList<Value> concat = new ArrayList<>(values.length + this.values.size());
        concat.addAll(this.values);
        recursivelyAdd(concat, Arrays.stream(values).collect(Collectors.toList()));

        // STEP 3: one-off observations

        if (concat.stream().anyMatch(v -> v instanceof UnknownValue)) {
            log(CNF, "Return Unknown value in And, found Unknown value");
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }

        // STEP 4: loop

        boolean changes = true;
        while (changes) {
            changes = false;

            // STEP 4a: sort

            Collections.sort(concat);

            // STEP 4b: observations

            for (Value value : concat) {
                if (value instanceof BoolValue && !((BoolValue) value).value) {
                    log(CNF, "Return FALSE in And, found FALSE", value);
                    return BoolValue.FALSE;
                }
            }
            concat.removeIf(value -> value instanceof BoolValue); // TRUE can go

            // STEP 4c: reductions

            ArrayList<Value> newConcat = new ArrayList<>(concat.size());
            Value prev = null;
            int pos = 0;
            for (Value value : concat) {

                // this works because of sorting
                // A && !A will always sit next to each other
                if (value instanceof NegatedValue && ((NegatedValue) value).value.equals(prev)) {
                    log(CNF, "Return FALSE in And, found opposites for {}", value);
                    return BoolValue.FALSE;
                }

                // A && !B && (!A || B)
                if (value instanceof OrValue) {
                    boolean allAroundInNegativeWay = true;
                    for (Value value1 : components(value)) {
                        Value negated1 = NegatedValue.negate(value1, true);
                        boolean found = false;
                        for (int pos2 = 0; pos2 < concat.size(); pos2++) {
                            if (pos2 != pos && negated1.equals(concat.get(pos2))) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) allAroundInNegativeWay = false;
                    }
                    if (allAroundInNegativeWay) {
                        log(CNF, "Return FALSE in And, found opposite for {}", value);
                        return BoolValue.FALSE;
                    }
                }

                // more complicated variant: (A || B) && (A || !B)
                List<Value> components = components(value);
                for (Value value1 : components) {
                    Value negated1 = NegatedValue.negate(value1, true);
                    for (int i = 0; i < pos; i++) {
                        List<Value> components2 = components(concat.get(i));
                        for (Value value2 : components2) {
                            if (negated1.equals(value2)) {
                                log(CNF, "Return TRUE in And, found {} and {}", value2, value1);
                                return BoolValue.TRUE;
                            }
                        }
                    }
                }

                // A && A
                if (value.equals(prev)) {
                    changes = true;
                } else if (value instanceof OrValue) {
                    OrValue orValue = (OrValue) value;
                    if (orValue.values.size() == 1) {
                        newConcat.add(orValue.values.get(0));
                        changes = true;
                    } else {
                        newConcat.add(value);
                    }
                } else {
                    newConcat.add(value);
                }
                prev = value;
                pos++;
            }
            concat = newConcat;
        }
        if (concat.isEmpty()) {
            log(CNF, "And reduced to 0 components, return true");
            return BoolValue.TRUE;
        }
        if (concat.size() == 1) {
            log(CNF, "And reduced to 1 component: {}", concat.get(0));
            return concat.get(0);
        }
        AndValue res = new AndValue(ImmutableList.copyOf(concat));
        log(CNF, "Constructed {}", res);
        return res;
    }

    private List<Value> components(Value value) {
        if (value instanceof OrValue) {
            return ((OrValue) value).values;
        }
        return List.of(value);
    }

    private static void recursivelyAdd(ArrayList<Value> concat, List<Value> values) {
        for (Value value : values) {
            if (value instanceof AndValue) {
                recursivelyAdd(concat, ((AndValue) value).values);
            } else {
                concat.add(value);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AndValue andValue = (AndValue) o;
        return values.equals(andValue.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "(" + values.stream().map(Value::toString).collect(Collectors.joining(" and ")) + ")";
    }

    @Override
    public int order() {
        return ORDER_AND;
    }

    @Override
    public int internalCompareTo(Value v) {
        AndValue andValue = (AndValue) v;
        return ListUtil.compare(values, andValue.values);
    }

    @Override
    public Map<Variable, Boolean> individualNullClauses() {
        return values.stream().flatMap(v -> v.individualNullClauses().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    public Value removeClausesInvolving(Variable variable) {
        return new AndValue(values.stream().filter(value -> !value.variables().contains(variable)).collect(Collectors.toList()));
    }
}

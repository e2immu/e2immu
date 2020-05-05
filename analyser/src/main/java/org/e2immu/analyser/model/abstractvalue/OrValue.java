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

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CNF;
import static org.e2immu.analyser.util.Logger.log;

public class OrValue implements Value {
    public final List<Value> values;

    public OrValue() {
        values = List.of();
    }

    OrValue(Value... values) {
        this.values = List.of(values);
    }

    private OrValue(List<Value> values) {
        this.values = values;
    }

    public Value append(Value... values) {
        return append(Arrays.asList(values));
    }

    // we try to maintain a CNF
    public Value append(List<Value> values) {

        // STEP 1: trivial reductions

        if (this.values.isEmpty() && values.size() == 1) {
            if (values.get(0) instanceof OrValue || values.get(0) instanceof AndValue) {
                log(CNF, "Return immediately in Or: {}", values.get(0));
                return values.get(0);
            }
        }

        // STEP 2: concat everything

        ArrayList<Value> concat = new ArrayList<>(values.size() + this.values.size());
        concat.addAll(this.values);
        recursivelyAdd(concat, values);

        // STEP 3: one-off observations

        if (concat.stream().anyMatch(v -> v instanceof UnknownValue)) {
            log(CNF, "Return Instance in Or, found unknown value");
            return new Instance(Primitives.PRIMITIVES.booleanParameterizedType);
        }
        // STEP 4: loop

        AndValue firstAnd = null;
        boolean changes = true;
        while (changes) {
            changes = false;

            // STEP 4a: sort

            Collections.sort(concat);

            // STEP 4b: observations

            for (Value value : concat) {
                if (value instanceof BoolValue && ((BoolValue) value).value) {
                    log(CNF, "Return TRUE in Or, found TRUE");
                    return BoolValue.TRUE;
                }
            }
            concat.removeIf(value -> value instanceof BoolValue); // FALSE can go

            // STEP 4c: reductions

            ArrayList<Value> newConcat = new ArrayList<>(concat.size());
            Value prev = null;
            int pos = 0;
            for (Value value : concat) {

                // this works because of sorting
                // A || !A will always sit next to each other
                if (value instanceof NegatedValue && ((NegatedValue) value).value.equals(prev)) {
                    log(CNF, "Return TRUE in Or, found opposites {}", value);
                    return BoolValue.TRUE;
                }

                // A || A
                if (value.equals(prev)) {
                    changes = true;
                } else if (value instanceof AndValue) {
                    AndValue andValue = (AndValue) value;
                    if (andValue.values.size() == 1) {
                        newConcat.add(andValue.values.get(0));
                        changes = true;
                    } else if (firstAnd == null) {
                        firstAnd = andValue;
                        changes = true;
                    } else {
                        newConcat.add(andValue); // for later
                    }
                } else {
                    newConcat.add(value);
                }
                prev = value;
                pos++;
            }
            concat = newConcat;
        }
        ArrayList<Value> finalValues = concat;
        if (firstAnd != null) {
            Value[] components = firstAnd.values.stream()
                    .map(v -> append(ListUtil.immutableConcat(finalValues, List.of(v))))
                    .toArray(Value[]::new);
            log(CNF, "Found And-clause {} in {}, components for new And are {}", firstAnd, this, Arrays.toString(components));
            return new AndValue().append(components);
        }
        if (finalValues.size() == 1) return finalValues.get(0);
        return new OrValue(finalValues);
    }

    private void recursivelyAdd(ArrayList<Value> concat, List<Value> collect) {
        for (Value value : collect) {
            if (value instanceof OrValue) {
                concat.addAll(((OrValue) value).values);
            } else {
                concat.add(value);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrValue orValue = (OrValue) o;
        return values.equals(orValue.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "(" + values.stream().map(Value::toString).collect(Collectors.joining(" or ")) + ")";
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof OrValue) {
            OrValue orValue = (OrValue) o;
            int c = values.size() - orValue.values.size();
            if (c != 0) return c;
            for (int i = 0; i < values.size(); i++) {
                c = values.get(i).compareTo(orValue.values.get(i));
                if (c != 0) return c;
            }
            return 0; // the same! will be removed in And...
        }
        if (o instanceof UnknownValue) return -1;
        return 1; // go to the back
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }
}

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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CNF;
import static org.e2immu.analyser.util.Logger.log;

public class OrValue extends PrimitiveValue {
    public final List<Value> values;

    // testing only
    public OrValue() {
        this(ObjectFlow.NO_FLOW);
    }

    public OrValue(ObjectFlow objectFlow) {
        this(objectFlow, List.of());
    }

    OrValue(ObjectFlow objectFlow, Value... values) {
        this(objectFlow, List.of(values));
    }

    private OrValue(ObjectFlow objectFlow, List<Value> values) {
        super(objectFlow);
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
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
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
            return new AndValue(objectFlow).append(components);
        }
        if (finalValues.size() == 1) return finalValues.get(0);
        return new OrValue(objectFlow, finalValues);
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
    public int order() {
        return ORDER_OR;
    }

    @Override
    public int internalCompareTo(Value v) {
        OrValue orValue = (OrValue) v;
        return ListUtil.compare(values, orValue.values);
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    public boolean isExpressionOfParameters() {
        return values.stream().allMatch(Value::isExpressionOfParameters);
    }

    // no implementation of any of the filters

    @Override
    public FilterResult filter(boolean preconditionSide, FilterMethod... filterMethods) {
        if (preconditionSide) return new FilterResult(Map.of(), this);

        List<FilterResult> results = values.stream().map(v -> v.filter(false, filterMethods)).collect(Collectors.toList());
        List<Value> restList = results.stream().map(r -> r.rest).filter(r -> r != UnknownValue.NO_VALUE).collect(Collectors.toList());

        Map<Variable, Value> acceptedCombined = results.stream().flatMap(r -> r.accepted.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Value rest;
        if (restList.isEmpty()) rest = UnknownValue.NO_VALUE;
        else if (restList.size() == 1) rest = restList.get(0);
        else rest = new OrValue().append(restList);

        return new FilterResult(acceptedCombined, rest);
    }

    @Override
    public Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        return new OrValue(objectFlow).append(values.stream().map(v -> v.reEvaluate(evaluationContext, translation)).toArray(Value[]::new));
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        values.forEach(v -> v.visit(consumer));
        consumer.accept(this);
    }
}

/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.EvaluationResult;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is the multi-entry variant of ConditionalValue; it is generally created from the evaluation
 * of a SwitchExpression, in cases where that makes sense.
 * <p>
 * The default label is represented by UnknownValue.EMPTY
 */
public class SwitchValue implements Value {

    public final Value selector;
    public final List<SwitchValueEntry> entries;
    public final ObjectFlow objectFlow;

    // the default entry has an empty label set
    public record SwitchValueEntry(Set<Value> labels, Value value,
                                   ObjectFlow objectFlow) implements Comparable<SwitchValueEntry> {


        public Value conditionFromLabels(EvaluationContext evaluationContext, Value selector) {
            return new OrValue(evaluationContext.getPrimitives()).append(evaluationContext,
                    labels.stream().map(v -> EqualsValue.equals(evaluationContext, selector, v, objectFlow)).toArray(Value[]::new));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SwitchValueEntry that = (SwitchValueEntry) o;
            return Objects.equals(labels, that.labels) &&
                    Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(labels, value);
        }

        @Override
        public String toString() {
            return print(PrintMode.FOR_DEBUG);
        }

        public String print(PrintMode printMode) {
            if (labels.contains(UnknownValue.EMPTY)) {
                return "default->" + value.print(printMode);
            }
            return "case " + labels.stream().map(v -> v.print(printMode)).collect(Collectors.joining(",")) + "->" + value.print(printMode);
        }

        @Override
        public int compareTo(SwitchValueEntry o) {
            Iterator<Value> it1 = labels.iterator();
            Iterator<Value> it2 = o.labels.iterator();
            while (true) {
                boolean hasNext1 = it1.hasNext();
                boolean hasNext2 = it2.hasNext();
                if (hasNext1 && !hasNext2) return 1;
                if (!hasNext1 && hasNext2) return -1;
                if (!hasNext1) return 0;
                int cc = it1.next().compareTo(it2.next());
                if (cc != 0) return cc;
            }
        }
    }

    private SwitchValue(Value selector, List<SwitchValueEntry> entries, ObjectFlow objectFlow) {
        this.selector = Objects.requireNonNull(selector);
        this.entries = Objects.requireNonNull(entries);
        this.objectFlow = Objects.requireNonNull(objectFlow);
        if (entries.size() <= 2) {
            throw new IllegalArgumentException("Expect at least 3 entries to have a bit of a reasonable switch value");
        }
        entries.forEach(e -> {
            Objects.requireNonNull(e.value);
            Objects.requireNonNull(e.labels);
            if (e.labels.contains(UnknownValue.EMPTY) && e.labels.size() != 1)
                throw new UnsupportedOperationException();
            if (e.labels.isEmpty()) throw new UnsupportedOperationException();
        });
    }

    public static EvaluationResult switchValue(EvaluationContext evaluationContext,
                                               Value selector,
                                               List<SwitchValueEntry> originalEntries,
                                               ObjectFlow objectFlow) {
        List<SwitchValueEntry> entries = cleanUpEntries(originalEntries);

        // we intercept some silly cases here
        if (selector == UnknownValue.NO_VALUE || entries.stream().anyMatch(e -> e.value == UnknownValue.NO_VALUE)) {
            return new EvaluationResult.Builder().setValue(selector).build();
        }

        // direct hit?
        if (selector.isConstant()) {
            Value singleResult = entries.stream().filter(e -> e.labels.contains(selector)).findFirst()
                    .map(e -> e.value).orElse(UnknownValue.NO_VALUE);
            return new EvaluationResult.Builder().setValue(singleResult != UnknownValue.NO_VALUE ?
                    singleResult : // one of the cases
                    entries.get(entries.size() - 1).value)  // default, otherwise we would have found the result
                    .build();
        }

        return switch (entries.size()) {
            case 1 -> new EvaluationResult.Builder().setValue(entries.get(0).value).build();
            case 2 -> ConditionalValue.conditionalValueCurrentState(evaluationContext,
                    entries.get(0).conditionFromLabels(evaluationContext, selector),
                    entries.get(0).value, entries.get(1).value, objectFlow);
            default -> new EvaluationResult.Builder().setValue(new SwitchValue(selector, entries, objectFlow)).build();
        };
    }

    static List<SwitchValueEntry> cleanUpEntries(List<SwitchValueEntry> originalEntries) {
        SwitchValueEntry[] array = originalEntries.toArray(new SwitchValueEntry[0]);
        Arrays.sort(array);
        List<SwitchValueEntry> result = new ArrayList<>(originalEntries.size());
        int i = 0;
        while (i < array.length) {
            Set<Value> newLabels = null;
            while (i + 1 < array.length && array[i].value.equals(array[i + 1].value)) {
                if (newLabels == null) newLabels = new HashSet<>(array[i].labels);
                newLabels.addAll(array[i + 1].labels);
                ++i;
            }
            if (newLabels != null) {
                if (newLabels.contains(UnknownValue.EMPTY)) {
                    newLabels = Set.of(UnknownValue.EMPTY);
                }
                SwitchValueEntry newSve = new SwitchValueEntry(newLabels, array[i].value, array[i].objectFlow);
                result.add(newSve);
            } else {
                result.add(array[i]);
            }
            ++i;
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SwitchValue that = (SwitchValue) o;
        return selector.equals(that.selector) &&
                entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, entries);
    }

    @Override
    public int order() {
        return ORDER_SWITCH;
    }

    @Override
    public int internalCompareTo(Value v) {
        SwitchValue sv = (SwitchValue) v;
        int c = selector.compareTo(sv.selector);
        if (c != 0) return c;

        Iterator<SwitchValueEntry> it1 = entries.iterator();
        Iterator<SwitchValueEntry> it2 = sv.entries.iterator();
        while (true) {
            boolean hasNext1 = it1.hasNext();
            boolean hasNext2 = it2.hasNext();
            if (hasNext1 && !hasNext2) return 1;
            if (!hasNext1 && hasNext2) return -1;
            if (!hasNext1) return 0;
            int cc = it1.next().compareTo(it2.next());
            if (cc != 0) return cc;
        }
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "switch(" + selector.print(printMode) + "){" + entries.stream().map(sve -> sve.print(printMode)).collect(Collectors.joining("; ")) + "}";
    }

    @Override
    public Stream<Value> individualBooleanClauses(FilterMode filterMode) {
        if (Primitives.isBooleanOrBoxedBoolean(type())) return Stream.of(this);
        return Stream.empty();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW; // TODO
    }
}

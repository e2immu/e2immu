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

package org.e2immu.analyser.model.value;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CNF;
import static org.e2immu.analyser.util.Logger.log;

public class AndValue extends PrimitiveValue {
    public final List<Value> values;
    private final Primitives primitives;

    // testing only
    public AndValue(Primitives primitives) {
        this(primitives, ObjectFlow.NO_FLOW);
    }

    public AndValue(Primitives primitives, ObjectFlow objectFlow) {
        this(primitives, objectFlow, List.of());
    }

    private AndValue(Primitives primitives, ObjectFlow objectFlow, List<Value> values) {
        super(objectFlow);
        this.values = values;
        this.primitives = primitives;
    }

    private enum Action {
        SKIP, REPLACE, FALSE, TRUE, ADD
    }

    // we try to maintain a CNF
    public Value append(EvaluationContext evaluationContext, Value... values) {

        // STEP 0: check that all values return boolean!
        if (Arrays.stream(values).anyMatch(v -> v.type() == null || Primitives.isNotBooleanOrBoxedBoolean(v.type()))) {
            throw new UnsupportedOperationException("Internal error, values are " + Arrays.toString(values));
        }

        // STEP 1: trivial reductions

        if (this.values.isEmpty() && values.length == 1 && values[0] instanceof AndValue) return values[0];

        // STEP 2: concat everything

        ArrayList<Value> concat = new ArrayList<>(values.length + this.values.size());
        concat.addAll(this.values);
        recursivelyAdd(concat, Arrays.stream(values).collect(Collectors.toList()));

        // some protection against EMPTY, coming in from state and preconditions
        concat.removeIf(v -> v == UnknownValue.EMPTY);
        if (concat.isEmpty()) return UnknownValue.EMPTY;

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
                    return BoolValue.createFalse(primitives);
                }
            }
            concat.removeIf(value -> value instanceof BoolValue); // TRUE can go

            // STEP 4c: reductions

            ArrayList<Value> newConcat = new ArrayList<>(concat.size());
            Value prev = null;
            int pos = 0;
            for (Value value : concat) {

                Action action = analyse(evaluationContext, pos, newConcat, prev, value);
                switch (action) {
                    case FALSE:
                        return BoolValue.createFalse(primitives);
                    case TRUE:
                        return BoolValue.createTrue(primitives);
                    case ADD:
                        newConcat.add(value);
                        break;
                    case REPLACE:
                        newConcat.set(newConcat.size() - 1, value);
                        changes = true;
                        break;
                    case SKIP:
                        changes = true;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }

                prev = value;
                pos++;
            }
            concat = newConcat;
        }
        if (concat.isEmpty()) {
            log(CNF, "And reduced to 0 components, return true");
            return BoolValue.createTrue(primitives);
        }
        if (concat.size() == 1) {
            log(CNF, "And reduced to 1 component: {}", concat.get(0));
            return concat.get(0);
        }
        AndValue res = new AndValue(primitives, objectFlow, ImmutableList.copyOf(concat));
        log(CNF, "Constructed {}", res);
        return res;
    }

    private Action analyse(EvaluationContext evaluationContext, int pos, ArrayList<Value> newConcat, Value prev, Value value) {
        // A && A
        if (value.equals(prev)) return Action.SKIP;

        // this works because of sorting
        // A && !A will always sit next to each other
        if (value instanceof NegatedValue negatedValue && negatedValue.value.equals(prev)) {
            log(CNF, "Return FALSE in And, found opposites for {}", value);
            return Action.FALSE;
        }

        // A && A ? B : C --> A && B
        if (value instanceof ConditionalValue conditionalValue && conditionalValue.condition.equals(prev)) {
            newConcat.add(conditionalValue.ifTrue);
            return Action.SKIP;
        }
        // !A && A ? B : C --> !A && C
        if (value instanceof ConditionalValue conditionalValue && conditionalValue.condition.
                equals(NegatedValue.negate(evaluationContext, prev))) {
            newConcat.add(conditionalValue.ifFalse);
            return Action.SKIP;
        }

        // A && (!A || ...) ==> we can remove the !A
        // if we keep doing this, the OrValue empties out, and we are in the situation:
        // A && !B && (!A || B) ==> each of the components of an OR occur in negative form earlier on
        // this is the more complicated form of A && !A
        if (value instanceof OrValue) {
            List<Value> remaining = new ArrayList<>(components(value));
            Iterator<Value> iterator = remaining.iterator();
            boolean changed = false;
            while (iterator.hasNext()) {
                Value value1 = iterator.next();
                Value negated1 = NegatedValue.negate(evaluationContext, value1);
                boolean found = false;
                for (int pos2 = 0; pos2 < newConcat.size(); pos2++) {
                    if (pos2 != pos && negated1.equals(newConcat.get(pos2))) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    iterator.remove();
                    changed = true;
                }
            }
            if (changed) {
                if (remaining.isEmpty()) {
                    log(CNF, "Return FALSE in And, found opposite for {}", value);
                    return Action.FALSE;
                }
                // replace
                Value orValue = new OrValue(primitives, objectFlow).append(evaluationContext, remaining);
                newConcat.add(orValue);
                return Action.SKIP;
            }
        }

        // the more complicated variant of A && A => A
        // A && (A || xxx) ==> A
        if (value instanceof OrValue) {
            List<Value> components = components(value);
            for (Value value1 : components) {
                for (Value value2 : newConcat) {
                    if (value1.equals(value2)) {
                        return Action.SKIP;
                    }
                }
            }
        }
        // A || B &&  A || !B ==> A
        if (value instanceof OrValue && prev instanceof OrValue) {
            List<Value> components = components(value);
            List<Value> prevComponents = components(prev);
            List<Value> equal = new ArrayList<>();
            boolean ok = true;
            for (Value value1 : components) {
                if (prevComponents.contains(value1)) {
                    equal.add(value1);
                } else if (!prevComponents.contains(NegatedValue.negate(evaluationContext, value1))) {
                    // not opposite, e.g. C
                    ok = false;
                    break;
                }
            }
            if (ok && !equal.isEmpty()) {
                Value orValue = new OrValue(primitives, objectFlow).append(evaluationContext, equal);
                newConcat.set(newConcat.size() - 1, orValue);
                return Action.SKIP;
            }
        }

        // simplification of the OrValue

        if (value instanceof OrValue orValue) {
            if (orValue.values.size() == 1) {
                newConcat.add(orValue.values.get(0));
                return Action.SKIP;
            }
            return Action.ADD;
        }

        // combinations with equality

        if (prev instanceof NegatedValue && ((NegatedValue) prev).value instanceof EqualsValue) {
            if (value instanceof EqualsValue ev2) {
                EqualsValue ev1 = (EqualsValue) ((NegatedValue) prev).value;
                // not (3 == a) && (4 == a)  (the situation 3 == a && not (3 == a) has been solved as A && not A == False
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    newConcat.remove(newConcat.size() - 1); // full replace
                    return Action.ADD;
                }
            }
        }

        if (prev instanceof EqualsValue ev1) {
            if (value instanceof EqualsValue ev2) {
                // 3 == a && 4 == a
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.FALSE;
                }
            }

            // EQ and NOT EQ
            if (value instanceof NegatedValue && ((NegatedValue) value).value instanceof EqualsValue ev2) {
                // 3 == a && not (4 == a)  (the situation 3 == a && not (3 == a) has been solved as A && not A == False
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.SKIP;
                }
            }

            // GE and EQ (note: GE always comes after EQ)
            if (value instanceof GreaterThanZeroValue ge) {
                GreaterThanZeroValue.XB xb = ge.extract(evaluationContext);
                if (ev1.lhs instanceof NumericValue && ev1.rhs.equals(xb.x)) {
                    double y = ((NumericValue) ev1.lhs).getNumber().doubleValue();
                    if (xb.lessThan) {
                        // y==x and x <= b
                        if (ge.allowEquals && y <= xb.b || !ge.allowEquals && y < xb.b) {
                            return Action.SKIP;
                        }
                    } else {
                        // y==x and x >= b
                        if (ge.allowEquals && y >= xb.b || !ge.allowEquals && y > xb.b) {
                            return Action.SKIP;
                        }
                    }
                    return Action.FALSE;
                }
            }
            return Action.ADD;
        }

        //  GE and NOT EQ
        if (value instanceof GreaterThanZeroValue ge && prev instanceof NegatedValue && ((NegatedValue) prev).value instanceof EqualsValue equalsValue) {
            GreaterThanZeroValue.XB xb = ge.extract(evaluationContext);
            if (equalsValue.lhs instanceof NumericValue && equalsValue.rhs.equals(xb.x)) {
                double y = ((NumericValue) equalsValue.lhs).getNumber().doubleValue();

                // y != x && -b + x >= 0, in other words, x!=y && x >= b
                if (ge.allowEquals && y < xb.b || !ge.allowEquals && y <= xb.b) {
                    return Action.REPLACE;
                }
            }
        }

        // GE and GE
        if (value instanceof GreaterThanZeroValue ge2 && prev instanceof GreaterThanZeroValue ge1) {
            GreaterThanZeroValue.XB xb1 = ge1.extract(evaluationContext);
            GreaterThanZeroValue.XB xb2 = ge2.extract(evaluationContext);
            if (xb1.x.equals(xb2.x)) {
                // x>= b1 && x >= b2, with < or > on either
                if (xb1.lessThan && xb2.lessThan) {
                    // x <= b1 && x <= b2
                    // (1) b1 > b2 -> keep b2
                    if (xb1.b > xb2.b) return Action.REPLACE;
                    // (2) b1 < b2 -> keep b1
                    if (xb1.b < xb2.b) return Action.SKIP;
                    if (ge1.allowEquals) return Action.REPLACE;
                    return Action.SKIP;
                }
                if (!xb1.lessThan && !xb2.lessThan) {
                    // x >= b1 && x >= b2
                    // (1) b1 > b2 -> keep b1
                    if (xb1.b > xb2.b) return Action.SKIP;
                    // (2) b1 < b2 -> keep b2
                    if (xb1.b < xb2.b) return Action.REPLACE;
                    // (3) b1 == b2 -> > or >=
                    if (ge1.allowEquals) return Action.REPLACE;
                    return Action.SKIP;
                }

                // !xb1.lessThan: x >= b1 && x <= b2; otherwise: x <= b1 && x >= b2
                if (xb1.b > xb2.b) return !xb1.lessThan ? Action.FALSE : Action.ADD;
                if (xb1.b < xb2.b) return !xb1.lessThan ? Action.ADD : Action.FALSE;
                if (ge1.allowEquals && ge2.allowEquals) {
                    Value newValue = EqualsValue.equals(evaluationContext,
                            NumericValue.intOrDouble(primitives, xb1.b, ge1.getObjectFlow()),
                            xb1.x, ge1.getObjectFlow()); // null-checks are irrelevant here
                    newConcat.set(newConcat.size() - 1, newValue);
                    return Action.SKIP;
                }
                return Action.FALSE;
            }
        }

        return Action.ADD;
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
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "(" + values.stream().map(v -> v.print(printMode)).collect(Collectors.joining(" and ")) + ")";
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
    public ParameterizedType type() {
        return primitives.booleanParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        List<EvaluationResult> reClauseERs = values.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        Value[] reClauses = reClauseERs.stream().map(er -> er.value).toArray(Value[]::new);
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setValue(new AndValue(primitives, objectFlow).append(evaluationContext, reClauses))
                .build();
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if (predicate.test(this)) {
            values.forEach(v -> v.visit(predicate));
        }
    }
}

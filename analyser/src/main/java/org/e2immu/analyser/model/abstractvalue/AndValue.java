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
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.CNF;
import static org.e2immu.analyser.util.Logger.log;

public class AndValue extends PrimitiveValue {
    public final List<Value> values;

    // testing only
    public AndValue() {
        this(null);
    }

    public AndValue(ObjectFlow objectFlow) {
        this(objectFlow, List.of());
    }

    private AndValue(ObjectFlow objectFlow, List<Value> values) {
        super(objectFlow);
        this.values = values;
    }

    private enum Action {
        SKIP, REPLACE, FALSE, TRUE, ADD
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

                Action action = analyse(pos, newConcat, prev, value);
                switch (action) {
                    case FALSE:
                        return BoolValue.FALSE;
                    case TRUE:
                        return BoolValue.TRUE;
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
            return BoolValue.TRUE;
        }
        if (concat.size() == 1) {
            log(CNF, "And reduced to 1 component: {}", concat.get(0));
            return concat.get(0);
        }
        AndValue res = new AndValue(objectFlow, ImmutableList.copyOf(concat));
        log(CNF, "Constructed {}", res);
        return res;
    }

    private Action analyse(int pos, ArrayList<Value> newConcat, Value prev, Value value) {
        // A && A
        if (value.equals(prev)) return Action.SKIP;

        // this works because of sorting
        // A && !A will always sit next to each other
        if (value instanceof NegatedValue && ((NegatedValue) value).value.equals(prev)) {
            log(CNF, "Return FALSE in And, found opposites for {}", value);
            return Action.FALSE;
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
                Value negated1 = NegatedValue.negate(value1);
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
                Value orValue = new OrValue(objectFlow).append(remaining);
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
                } else if (!prevComponents.contains(NegatedValue.negate(value1))) {
                    // not opposite, e.g. C
                    ok = false;
                    break;
                }
            }
            if (ok && !equal.isEmpty()) {
                Value orValue = new OrValue(objectFlow).append(equal);
                newConcat.set(newConcat.size() - 1, orValue);
                return Action.SKIP;
            }
        }

        // simplification of the OrValue

        if (value instanceof OrValue) {
            OrValue orValue = (OrValue) value;
            if (orValue.values.size() == 1) {
                newConcat.add(orValue.values.get(0));
                return Action.SKIP;
            }
            return Action.ADD;
        }

        // combinations with equality

        if (prev instanceof NegatedValue && ((NegatedValue) prev).value instanceof EqualsValue) {
            if (value instanceof EqualsValue) {
                EqualsValue ev1 = (EqualsValue) ((NegatedValue) prev).value;
                EqualsValue ev2 = (EqualsValue) value;
                // not (3 == a) && (4 == a)  (the situation 3 == a && not (3 == a) has been solved as A && not A == False
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    newConcat.remove(newConcat.size() - 1); // full replace
                    return Action.ADD;
                }
            }
        }

        if (prev instanceof EqualsValue) {
            if (value instanceof EqualsValue) {
                EqualsValue ev1 = (EqualsValue) prev;
                EqualsValue ev2 = (EqualsValue) value;
                // 3 == a && 4 == a
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.FALSE;
                }
            }

            // EQ and NOT EQ
            if (value instanceof NegatedValue && ((NegatedValue) value).value instanceof EqualsValue) {
                EqualsValue ev1 = (EqualsValue) prev;
                EqualsValue ev2 = (EqualsValue) ((NegatedValue) value).value;
                // 3 == a && not (4 == a)  (the situation 3 == a && not (3 == a) has been solved as A && not A == False
                if (ev1.rhs.equals(ev2.rhs) && !ev1.lhs.equals(ev2.lhs)) {
                    return Action.SKIP;
                }
            }

            // GE and EQ (note: GE always comes after EQ)
            if (value instanceof GreaterThanZeroValue) {
                GreaterThanZeroValue ge = (GreaterThanZeroValue) value;
                GreaterThanZeroValue.XB xb = ge.extract();
                EqualsValue equalsValue = (EqualsValue) prev;
                if (equalsValue.lhs instanceof NumericValue && equalsValue.rhs.equals(xb.x)) {
                    double y = ((NumericValue) equalsValue.lhs).getNumber().doubleValue();
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
        if (value instanceof GreaterThanZeroValue && prev instanceof NegatedValue && ((NegatedValue) prev).value instanceof EqualsValue) {
            GreaterThanZeroValue ge = (GreaterThanZeroValue) value;
            GreaterThanZeroValue.XB xb = ge.extract();
            EqualsValue equalsValue = (EqualsValue) ((NegatedValue) prev).value;
            if (equalsValue.lhs instanceof NumericValue && equalsValue.rhs.equals(xb.x)) {
                double y = ((NumericValue) equalsValue.lhs).getNumber().doubleValue();

                // y != x && -b + x >= 0, in other words, x!=y && x >= b
                if (ge.allowEquals && y < xb.b || !ge.allowEquals && y <= xb.b) {
                    return Action.REPLACE;
                }
            }
        }

        // GE and GE
        if (value instanceof GreaterThanZeroValue && prev instanceof GreaterThanZeroValue) {
            GreaterThanZeroValue ge1 = (GreaterThanZeroValue) prev;
            GreaterThanZeroValue.XB xb1 = ge1.extract();
            GreaterThanZeroValue ge2 = (GreaterThanZeroValue) value;
            GreaterThanZeroValue.XB xb2 = ge2.extract();
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
                    Value newValue = EqualsValue.equals(NumericValue.intOrDouble(xb1.b, ge1.getObjectFlow()), xb1.x, ge1.getObjectFlow());
                    newConcat.set(newConcat.size() - 1, newValue);
                    return Action.SKIP;
                }
                return Action.FALSE;
            }
            if (xb1.x instanceof ConstrainedNumericValue && xb2.x instanceof ConstrainedNumericValue) {
                ConstrainedNumericValue cnv1 = (ConstrainedNumericValue) xb1.x;
                ConstrainedNumericValue cnv2 = (ConstrainedNumericValue) xb2.x;

                // x,?>=a1 >= b1 && x,?>=a2 >= b2
                if (!xb1.lessThan && !xb2.lessThan && cnv1.value.equals(cnv2.value) && cnv1.onlyLowerBound() && cnv2.onlyLowerBound()) {
                    // we know that a1<b1 and a2<b2, otherwise there would be no CNV; the greatest one survives
                    if (xb1.b < xb2.b) {
                        // remove previous
                        newConcat.remove(newConcat.size() - 1);
                        return Action.ADD;
                    } // else keep the 1st, so skip
                    return Action.SKIP;
                }
                // TODO other combinations are very possible!
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
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    public Value removeClausesInvolving(Variable variable) {
        return new AndValue(objectFlow, values.stream().filter(value -> !value.variables().contains(variable)).collect(Collectors.toList()));
    }

    @Override
    public boolean isExpressionOfParameters() {
        return values.stream().allMatch(Value::isExpressionOfParameters);
    }

    @Override
    public Value nonIndividualCondition() {
        // double checking we're not dealing with an And-clause of size 1
        if (values.size() == 1) return values.get(0).nonIndividualCondition();
        return this;
    }

    @Override
    public Value reEvaluate(Map<Value, Value> translation) {
        return new AndValue(objectFlow).append(values.stream().map(v -> v.reEvaluate(translation)).toArray(Value[]::new));
    }
}

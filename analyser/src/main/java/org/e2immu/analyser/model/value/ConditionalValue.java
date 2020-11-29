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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.*;
import java.util.function.Predicate;

public class ConditionalValue implements Value {

    public final Value condition;
    public final Value ifTrue;
    public final Value ifFalse;
    public final Value combinedValue;
    public final ObjectFlow objectFlow;

    public ConditionalValue(Primitives primitives, Value condition, Value ifTrue, Value ifFalse, ObjectFlow objectFlow) {
        this.condition = condition;
        this.ifFalse = ifFalse;
        this.ifTrue = ifTrue;
        combinedValue = CombinedValue.create(primitives, List.of(ifTrue, ifFalse));
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionalValue that = (ConditionalValue) o;
        return condition.equals(that.condition) &&
                ifTrue.equals(that.ifTrue) &&
                ifFalse.equals(that.ifFalse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, ifTrue, ifFalse);
    }

    @Override
    public ParameterizedType type() {
        return combinedValue.type();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    public static EvaluationResult conditionalValueCurrentState(EvaluationContext evaluationContext, Value conditionBeforeState, Value ifTrue, Value ifFalse, ObjectFlow objectFlow) {
        Value condition = checkState(evaluationContext,
                evaluationContext.getPrimitives(),
                evaluationContext.getConditionManager().state, conditionBeforeState);
        return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, ifFalse, objectFlow);
    }

    public static EvaluationResult conditionalValueConditionResolved(EvaluationContext evaluationContext, Value condition, Value ifTrue, Value ifFalse, ObjectFlow objectFlow) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        if (condition instanceof BoolValue) {
            boolean first = ((BoolValue) condition).value;
            builder.raiseError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT);
            return builder.setValue(first ? ifTrue : ifFalse).build();
        }

        // not x ? a: b --> x ? b: a
        NegatedValue negatedCondition;
        if ((negatedCondition = condition.asInstanceOf(NegatedValue.class)) != null) {
            return conditionalValueConditionResolved(evaluationContext, negatedCondition.value, ifFalse, ifTrue, objectFlow);
        }

        ConditionalValue secondCv;
        // x ? (x? a: b): c == x ? a : c
        if ((secondCv = ifTrue.asInstanceOf(ConditionalValue.class)) != null && secondCv.condition.equals(condition)) {
            return conditionalValueConditionResolved(evaluationContext, condition, secondCv.ifTrue, ifFalse, objectFlow);
        }

        Value edgeCase = edgeCases(evaluationContext, evaluationContext.getPrimitives(), condition, ifTrue, ifFalse);
        if (edgeCase != null) return builder.setValue(edgeCase).build();

        // standardization... we swap!
        // this will result in  a != null ? a: x ==>  null == a ? x : a as the default form

        return builder.setValue(new ConditionalValue(evaluationContext.getPrimitives(), condition, ifTrue, ifFalse, objectFlow)).build();
        // TODO more advanced! if a "large" part of ifTrue or ifFalse appears in condition, we should create a temp variable
    }

    private static Value checkState(EvaluationContext evaluationContext, Primitives primitives, Value state, Value condition) {
        if (state == UnknownValue.EMPTY) return condition;
        Value and = new AndValue(primitives).append(evaluationContext, state, condition);
        if (and.equals(condition)) {
            return BoolValue.createTrue(primitives);
        }
        if (and instanceof BoolValue) return and;
        return condition;
    }

    private static Value edgeCases(EvaluationContext evaluationContext, Primitives primitives,
                                   Value condition, Value ifTrue, Value ifFalse) {
        // x ? a : a == a
        if (ifTrue.equals(ifFalse)) return ifTrue;
        // a ? a : !a == a == !a ? !a : a
        if (condition.equals(ifTrue) && condition.equals(NegatedValue.negate(evaluationContext, ifFalse))) {
            return BoolValue.createTrue(primitives);
        }
        // !a ? a : !a == !a == a ? !a : a --> will not happen, as we've already swapped
        return null;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reCondition = condition.reEvaluate(evaluationContext, translation);
        EvaluationResult reTrue = ifTrue.reEvaluate(evaluationContext, translation);
        EvaluationResult reFalse = ifFalse.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reCondition, reTrue, reFalse);
        if (reCondition.value.isBoolValueTrue()) {
            return builder.setValue(reTrue.value).build();
        }
        if (reCondition.value.isBoolValueFalse()) {
            return builder.setValue(reFalse.value).build();
        }
        return builder.setValue(new ConditionalValue(evaluationContext.getPrimitives(),
                reCondition.value, reTrue.value, reFalse.value, getObjectFlow())).build();
    }

    @Override
    public int order() {
        return ORDER_CONDITIONAL;
    }

    @Override
    public int internalCompareTo(Value v) {
        ConditionalValue cv = (ConditionalValue) v;
        int c = condition.compareTo(cv.condition);
        if (c == 0) {
            c = ifTrue.compareTo(cv.ifTrue);
        }
        if (c == 0) {
            c = ifFalse.compareTo(cv.ifFalse);
        }
        return c;
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return condition.print(printMode) + "?" + ifTrue.print(printMode) + ":" + ifFalse.print(printMode);
    }

    private static final int NO_PATTERN = -2;

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        int inCondition = lookForPatterns(evaluationContext, variableProperty);
        if (inCondition != NO_PATTERN) return inCondition;
        return evaluationContext.getProperty(combinedValue, variableProperty);
    }

    /*
    There are a few patterns that we can look out for.

    (1) a == null ? xx : a

    (3)-(4) same wit SIZE, e.g.
    (3) 0 == a.size() ? xx : a
    (4) (-3) + a.size() >= 0 ? a : x
     */
    private int lookForPatterns(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) {
            Value c = condition;
            boolean not = false;
            if (c.isInstanceOf(NegatedValue.class)) {
                c = ((NegatedValue) c).value;
                not = true;
            }
            EqualsValue equalsValue;
            if ((equalsValue = c.asInstanceOf(EqualsValue.class)) != null && equalsValue.lhs.isInstanceOf(NullValue.class)) {
                // null == rhs or not (null == rhs), now check that rhs appears left or right
                Value rhs = equalsValue.rhs;
                if (ifTrue.equals(rhs)) {
                    // null == a ? a : something;  null != a ? a : something
                    return not ? evaluationContext.getProperty(ifFalse, variableProperty) : MultiLevel.NULLABLE;
                }
                if (ifFalse.equals(rhs)) {
                    // null == a ? something: a
                    return not ? MultiLevel.NULLABLE : evaluationContext.getProperty(ifTrue, variableProperty);
                }
            }
        }
        return NO_PATTERN;
    }

    // simple add-all, but return null upon delay

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        Set<Variable> result = null;
        for (Variable variable : variables()) {
            Set<Variable> links = evaluationContext.linkedVariables(variable);
            if (links == null) {
                return null;
            }
            if (result == null) {
                result = new HashSet<>(links);
            } else {
                result.addAll(links);
            }
        }
        return result;
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(condition.variables(), ifTrue.variables(), ifFalse.variables());
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if (predicate.test(this)) {
            condition.visit(predicate);
            ifTrue.visit(predicate);
            ifFalse.visit(predicate);
        }
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        if (Primitives.isPrimitiveExcludingVoid(type())) return null;
        return new Instance(type(), getObjectFlow(), UnknownValue.EMPTY);
    }
}
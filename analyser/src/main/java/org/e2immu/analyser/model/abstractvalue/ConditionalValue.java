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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.util.SetUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConditionalValue implements Value {

    public final Value condition;
    public final Value ifTrue;
    public final Value ifFalse;
    public final Value combinedValue;

    public ConditionalValue(Value condition, Value ifTrue, Value ifFalse) {
        this.condition = condition;
        this.ifFalse = ifFalse;
        this.ifTrue = ifTrue;
        combinedValue = CombinedValue.create(List.of(ifTrue, ifFalse));
    }

    @Override
    public Value reEvaluate(Map<Value, Value> translation) {
        Value reCondition = condition.reEvaluate(translation);
        Value reTrue = ifTrue.reEvaluate(translation);
        Value reFalse = ifFalse.reEvaluate(translation);
        if (reCondition == BoolValue.TRUE) {
            return reTrue;
        }
        if (reCondition == BoolValue.FALSE) {
            return reFalse;
        }
        return new ConditionalValue(reCondition, reTrue, reFalse);
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
        return condition.toString() + "?" + ifTrue.toString() + ":" + ifFalse.toString();
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return evaluationContext.getProperty(combinedValue, variableProperty);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return combinedValue.getPropertyOutsideContext(variableProperty);
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return combinedValue.variables();
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(condition.variables(), combinedValue.variables());
    }

    @Override
    public boolean isExpressionOfParameters() {
        return combinedValue.isExpressionOfParameters();
    }
}

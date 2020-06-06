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

import org.e2immu.analyser.analyser.ConditionalManager;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.util.SetUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AlternativeAssignmentValue implements Value {

    /*
      we start from value
      we have a condition / predicate which relies on value
      if the condition is true, we keep value
      otherwise we take alternativeValue

     */
    public final Value value;
    public final Value predicate;
    public final Value alternativeValue;

    public AlternativeAssignmentValue(Value value, Value predicate, Value alternativeValue) {
        this.value = value;
        this.predicate = predicate;
        this.alternativeValue = alternativeValue;
    }

    @Override
    public Value reEvaluate(Map<Value, Value> translation) {
        Value reValue = value.reEvaluate(translation);
        Map<Value, Value> translationWithValue = new HashMap<>(translation);
        translationWithValue.put(value, reValue);
        Value rePredicate = predicate.reEvaluate(translationWithValue);
        if (rePredicate == BoolValue.TRUE) return reValue;
        Value reAlt = alternativeValue.reEvaluate(translationWithValue);
        if (rePredicate == BoolValue.FALSE) return reAlt;
        return new AlternativeAssignmentValue(reValue, rePredicate, reAlt);
    }

    @Override
    public int order() {
        return ORDER_ALT_ASSIGNMENT;
    }

    @Override
    public int internalCompareTo(Value v) {
        AlternativeAssignmentValue cv = (AlternativeAssignmentValue) v;
        int c = value.compareTo(cv.value);
        if (c == 0) {
            c = predicate.compareTo(cv.predicate);
        }
        if (c == 0) {
            c = alternativeValue.compareTo(cv.alternativeValue);
        }
        return c;
    }

    @Override
    public String toString() {
        // looks like Python ternary operator
        return value.toString() + " if " + predicate.toString() + " else " + alternativeValue.toString();
    }

    /*
     one of the typical uses of this construct is to get rid of null pointers:

     Value v = map.get(x);
     if(v == null) v = otherValue;

     which now becomes

     map.get(x) if max.get(x) != null else otherValue

     If otherValue is guaranteed to be not null, then v will be not-null as well.

     Similarly, we can obtain a set

     Set<String> set = map.get(x)
     if(set == null || set.isEmpty()) set = Set.of("abc")

     Here, the result is both not-null and not-empty

     So we implement special cases for NOT_NULL, SIZE and go the same way as CombinedValue for the others
     */

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        // generally, we take the worst value
        int v = evaluationContext.getProperty(value, variableProperty);
        int vAlt = evaluationContext.getProperty(alternativeValue, variableProperty);
        if (variableProperty == VariableProperty.NOT_NULL) return handleNotNull(v, vAlt);
        if (variableProperty == VariableProperty.SIZE) return handleSize(v, vAlt);
        return Math.min(v, vAlt);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        // generally, we take the worst value
        int v = value.getPropertyOutsideContext(variableProperty);
        int vAlt = alternativeValue.getPropertyOutsideContext(variableProperty);
        if (variableProperty == VariableProperty.NOT_NULL) return handleNotNull(v, vAlt);
        if (variableProperty == VariableProperty.SIZE) return handleSize(v, vAlt);
        return Math.min(v, vAlt);
    }

    private int handleNotNull(int v, int vAlt) {
        ConditionalManager conditionalManager = new ConditionalManager(predicate);
        int notNull = conditionalManager.notNull(value);
        if (notNull >= Level.TRUE) {
            return Math.min(Level.TRUE, vAlt);
        }
        return Math.min(v, vAlt);
    }

    private int handleSize(int v, int vAlt) {
        ConditionalManager conditionalManager = new ConditionalManager(predicate);
        int sizeRestrictionValue = conditionalManager.sizeRestriction(value); // this tells us the size restriction IF the predicate is true (in which case we keep the value)
        if (sizeRestrictionValue >= Level.TRUE) {
            // there is a real size restriction if we keep value; before there was a weaker one than in the condition
            return Analysis.joinSizeRestrictions(sizeRestrictionValue, vAlt);
        }
        return Analysis.joinSizeRestrictions(v, vAlt);
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        // the predicate has no influence over the outcome
        return SetUtil.immutableUnion(value.linkedVariables(bestCase, evaluationContext), alternativeValue.linkedVariables(bestCase, evaluationContext));
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(predicate.variables(), value.variables(), alternativeValue.variables());
    }

    // meaning, can we re-evaluate this? can it be part of an inline operation?
    @Override
    public boolean isExpressionOfParameters() {
        return value.isExpressionOfParameters() && predicate.isExpressionOfParameters() && alternativeValue.isExpressionOfParameters();
    }
}

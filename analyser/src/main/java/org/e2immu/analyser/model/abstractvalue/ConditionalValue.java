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

import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetUtil;

import java.util.Set;

public class ConditionalValue implements Value {

    public final Value condition;
    public final Value ifTrue;
    public final Value ifFalse;

    public ConditionalValue(Value condition, Value ifTrue, Value ifFalse) {
        this.condition = condition;
        this.ifFalse = ifFalse;
        this.ifTrue = ifTrue;
    }

    @Override
    public int compareTo(Value o) {
        return 0;
    }

    @Override
    public String asString() {
        return condition.asString() + "?" + ifTrue.asString() + ":" + ifFalse.asString();
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNullTrue = ifTrue.getProperty(evaluationContext, variableProperty);
            int notNullFalse = ifFalse.getProperty(evaluationContext, variableProperty);
            return Level.best(notNullTrue, notNullFalse);
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNullTrue = ifTrue.getPropertyOutsideContext(variableProperty);
            int notNullFalse = ifFalse.getPropertyOutsideContext(variableProperty);
            return Level.best(notNullTrue, notNullFalse);
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return SetUtil.immutableUnion(ifTrue.linkedVariables(bestCase, evaluationContext),
                ifFalse.linkedVariables(bestCase, evaluationContext));
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(condition.variables(), ifTrue.variables(), ifFalse.variables());
    }
}

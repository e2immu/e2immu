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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.objectflow.ObjectFlow;

public abstract class AbstractEvaluationContextImpl implements EvaluationContext {

    public final int iteration;
    public final EvaluationContext closure;
    public final ConditionManager conditionManager;

    protected AbstractEvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
        this.iteration = iteration;
        this.conditionManager = conditionManager;
        this.closure = closure;
    }

    @Override
    public EvaluationContext getClosure() {
        return closure;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    @Override
    public ConditionManager getConditionManager() {
        return conditionManager;
    }

    @Override
    public boolean isNotNull0(Expression value) {
        Expression valueIsNull = new Equals(getPrimitives(), NullConstant.NULL_CONSTANT, value, ObjectFlow.NO_FLOW);
        Expression combined = conditionManager.evaluate(this, valueIsNull);
        if (combined instanceof BooleanConstant boolValue) {
            return boolValue.constant();
        }
        return MultiLevel.isEffectivelyNotNull(getProperty(value, VariableProperty.NOT_NULL));
    }
}

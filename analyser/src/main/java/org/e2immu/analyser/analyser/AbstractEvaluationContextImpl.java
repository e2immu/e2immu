/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
    public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
        Expression valueIsNull = new Equals(getPrimitives(), NullConstant.NULL_CONSTANT, value, ObjectFlow.NO_FLOW);
        Expression combined = conditionManager.evaluate(this, valueIsNull);
        if (combined instanceof BooleanConstant boolValue) {
            return !boolValue.constant();
        }
        return MultiLevel.isEffectivelyNotNull(getProperty(value, VariableProperty.NOT_NULL_EXPRESSION, true));
    }
}

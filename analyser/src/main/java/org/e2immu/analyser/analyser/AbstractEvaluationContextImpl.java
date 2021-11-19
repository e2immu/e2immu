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
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.Primitives;

import java.util.Set;
import java.util.function.Function;

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
        Expression valueIsNull = new Equals(Identifier.generate(), getPrimitives(), NullConstant.NULL_CONSTANT, value);
        Expression combined = conditionManager.evaluate(this, valueIsNull);
        if (combined instanceof BooleanConstant boolValue) {
            return !boolValue.constant();
        }
        DV nne = getProperty(value, Property.NOT_NULL_EXPRESSION, true, true);
        return MultiLevel.isEffectivelyNotNull(nne);
    }

    @Override
    public boolean notNullAccordingToConditionManager(Expression expression) {
        if (Primitives.isNotBooleanOrBoxedBoolean(expression.returnType())) {
            // do not use the Condition manager to check for null in creation of isNull
            Expression isNull = Equals.equals(expression.getIdentifier(),
                    this, expression, NullConstant.NULL_CONSTANT, false);
            if (isNull.isBoolValueFalse()) {
                // this is not according to the condition manager, but always not null
                return false;
            }
            return conditionManager.evaluate(this, isNull).isBoolValueFalse();
        }
        return conditionManager.evaluate(this, expression).isBoolValueTrue();
    }

    /*
    code here because shared between EC in StatementAnalyser, InlinedMethod
     */

    protected boolean notNullAccordingToConditionManager(Variable variable, Function<FieldReference, VariableInfo> findField) {
        Set<Variable> notNullVariablesInState = conditionManager.findIndividualNullInState(this, false);
        if (notNullVariablesInState.contains(variable)) return true;
        Set<Variable> notNullVariablesInCondition = conditionManager
                .findIndividualNullInCondition(this, false);
        if (notNullVariablesInCondition.contains(variable)) return true;
        FieldReference fieldReference;
        if (variable instanceof FieldReference fr) {
            fieldReference = fr;
        } else if (variable instanceof LocalVariableReference lvr &&
                lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy) {
            fieldReference = copy.localCopyOf();
            VariableInfo variableInfo = findField.apply(fieldReference);
            if (variableInfo.isAssigned()) return false;
            // IMPROVE this is only valid if the statement time of the local copy is the same as that of the precondition
            // but how to do that?
        } else return false;

        Set<Variable> notNullVariablesInPrecondition = conditionManager
                .findIndividualNullInPrecondition(this, false);
        return notNullVariablesInPrecondition.contains(fieldReference);
    }
}

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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractEvaluationContextImpl implements EvaluationContext {

    public final int depth; // how many closures in each other?
    public final int iteration;
    public final EvaluationContext closure;
    public final ConditionManager conditionManager;
    public final BreakDelayLevel breakDelayLevel;

    protected AbstractEvaluationContextImpl(int depth,
                                            int iteration,
                                            BreakDelayLevel breakDelayLevel,
                                            ConditionManager conditionManager,
                                            EvaluationContext closure) {
        this.iteration = iteration;
        this.conditionManager = conditionManager;
        this.breakDelayLevel = breakDelayLevel;
        this.closure = closure;
        this.depth = depth;
        assert depth < 20 : "Depth of " + depth + " reached";
    }

    @Override
    public BreakDelayLevel breakDelayLevel() {
        return breakDelayLevel;
    }

    @Override
    public int getDepth() {
        return depth;
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

    /**
     * @return delay, DV.TRUE_DV, DV.FALSE_DV
     */
    @Override
    public DV isNotNull0(Expression value, boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.isOnlySort()) return DV.FALSE_DV;
        Expression valueIsNull = new Equals(value.getIdentifier(), getPrimitives(), NullConstant.NULL_CONSTANT, value);
        Expression inCm = conditionManager.evaluate(EvaluationResultImpl.from(this), valueIsNull, true);
        DV negated = inCm.isDelayed() ? inCm.causesOfDelay() : inCm.isBoolValueFalse() ? DV.TRUE_DV : DV.FALSE_DV;
        DV nne = getProperty(value, Property.NOT_NULL_EXPRESSION, true, true);
        DV nneToTF;
        if (nne.isDelayed()) {
            nneToTF = value.isDelayed() ? nne : DV.FALSE_DV;
        } else {
            nneToTF = nne.equals(MultiLevel.NULLABLE_DV) ? DV.FALSE_DV : DV.TRUE_DV;
        }
        return negated.max(nneToTF);
    }

    @Override
    public DV notNullAccordingToConditionManager(Expression expression) {
        EvaluationResult context = EvaluationResultImpl.from(this);
        if (expression.returnType().isNotBooleanOrBoxedBoolean()) {
            // do not use the Condition manager to check for null in creation of isNull
            Expression isNull = Equals.equals(expression.getIdentifier(),
                    context, expression, NullConstant.NULL_CONSTANT, false, ForwardEvaluationInfo.DEFAULT);
            if (isNull.isBoolValueFalse()) {
                // this is not according to the condition manager, but always not null
                return DV.FALSE_DV;
            }
            if (isNull.isDelayed()) return isNull.causesOfDelay();
            return conditionManager.evaluate(context, isNull, true).invertTrueFalse();
        }
        return conditionManager.evaluate(context, expression, false).invertTrueFalse();
    }

    /*
    code here because shared between EC in StatementAnalyser, InlinedMethod
     */

    @Override
    public DV notNullAccordingToConditionManager(Variable variable) {
        LinkedVariables linkedVariables = linkedVariables(variable);
        Set<Variable> assignedVariables = linkedVariables == null ? Set.of(variable)
                // always include myself!
                : Stream.concat(Stream.of(variable), linkedVariables.variablesAssigned())
                .collect(Collectors.toUnmodifiableSet());

        EvaluationResult context = EvaluationResultImpl.from(this);
        Set<Variable> notNullVariablesInState = conditionManager.findIndividualNullInState(context, false);
        if (!Collections.disjoint(notNullVariablesInState, assignedVariables)) return DV.TRUE_DV;

        Set<Variable> notNullVariablesInCondition = conditionManager
                .findIndividualNullInCondition(context, false);
        if (!Collections.disjoint(notNullVariablesInCondition, assignedVariables)) return DV.TRUE_DV;
        if (variable instanceof FieldReference) {
            Set<Variable> notNullVariablesInPrecondition = conditionManager
                    .findIndividualNullInPrecondition(context, false);
            if (!Collections.disjoint(notNullVariablesInPrecondition, assignedVariables)) return DV.TRUE_DV;
        }
        // a -1 can turn into a 1, ASSIGNED
        if (linkedVariables != null && linkedVariables.isDelayed()) return linkedVariables.causesOfDelay();
        return DV.FALSE_DV;
    }
}

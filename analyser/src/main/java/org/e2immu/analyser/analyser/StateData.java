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
import org.e2immu.analyser.util.FlipSwitch;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.stream.Stream;

public class StateData {

    /*
     precondition = conditions that cause an escape
     they are generated in the throws statement, assert statement
     are copied upwards, and to the next statement

     this variable contains the precondition of one single statement; an aggregate is computed in MethodLevelData
     */

    private final SetOnce<Expression> precondition = new SetOnce<>();
    private Expression currentDelayedPrecondition;

    /*
    contains the change in state (not condition, not precondition) when going from one statement to the next
    in the same block

    going down into a block changes condition (not state); this is contained into the ForwardInfo
    this value is set before the complete precondition is computed in method level data; therefore,
    the local condition manager of a subsequent statement in the same block needs to combine this value
    and the method level data's combined precondition.
     */
    private final SetOnce<ConditionManager> conditionManagerForNextStatement = new SetOnce<>();
    private ConditionManager currentDelayedConditionManagerForNextStatement;

    private final SetOnce<Expression> valueOfExpression = new SetOnce<>();
    private Expression currentDelayedExpression;

    public final FlipSwitch statementContributesToPrecondition = new FlipSwitch();

    static class CurrentDelayedAndFinalExpression {

        final SetOnce<Expression> expression = new SetOnce<>();
        Expression currentDelayedExpression;

        public Expression getExpression() {
            return expression.getOrElse(currentDelayedExpression);
        }

    }

    private final SetOnceMap<String, CurrentDelayedAndFinalExpression> statesOfInterrupts;

    public StateData(boolean isLoop) {
        statesOfInterrupts = isLoop ? new SetOnceMap<>() : null;
    }


    // condition manager
    public ConditionManager getConditionManagerForNextStatement() {
        return conditionManagerForNextStatement.getOrElse(currentDelayedConditionManagerForNextStatement);
    }

    public boolean conditionManagerIsNotYetSet() {
        return !conditionManagerForNextStatement.isSet();
    }

    public void setLocalConditionManagerForNextStatement(ConditionManager conditionManager) {
        if (conditionManager.isDelayed()) {
            currentDelayedConditionManagerForNextStatement = conditionManager;
        } else if (!conditionManagerForNextStatement.isSet() || !conditionManager.equals(conditionManagerForNextStatement.get())) {
            conditionManagerForNextStatement.set(conditionManager);
        }
    }

    // value of expression
    public void setValueOfExpression(Expression expression, boolean expressionIsDelayed) {
        if (expressionIsDelayed) {
            currentDelayedExpression = expression;
        } else if (!valueOfExpression.isSet() || !valueOfExpression.get().equals(expression)) {
            valueOfExpression.set(expression);
        }
    }

    public Expression getValueOfExpression() {
        return valueOfExpression.getOrElse(currentDelayedExpression);
    }

    public boolean valueOfExpressionIsSet() {
        return valueOfExpression.isSet();
    }

    public boolean valueOfExpressionIsDelayed() {
        return !valueOfExpression.isSet();
    }

    // states of interrupt

    public void addStateOfInterrupt(String index, Expression state, boolean stateIsDelayed) {
        CurrentDelayedAndFinalExpression cd = statesOfInterrupts.getOrCreate(index, i -> new CurrentDelayedAndFinalExpression());
        if (stateIsDelayed) {
            cd.currentDelayedExpression = state;
        } else if (!cd.expression.isSet() || !cd.expression.get().equals(state)) {
            cd.expression.set(state);
        }
    }

    public Stream<Expression> statesOfInterruptsStream() {
        return statesOfInterrupts.stream().map(e -> e.getValue().getExpression());
    }

    // precondition

    public void setPrecondition(Expression expression, boolean expressionIsDelayed) {
        if (expressionIsDelayed) {
            currentDelayedPrecondition = expression;
        } else if (!precondition.isSet() || !expression.equals(precondition.get())) {
            precondition.set(expression);
        }
    }

    public Expression getPrecondition() {
        return precondition.getOrElse(currentDelayedPrecondition);
    }

    public boolean preconditionIsDelayed() {
        return !precondition.isSet();
    }

    public boolean preconditionIsSet() {
        return precondition.isSet();
    }

    public boolean preconditionIsEmpty() {
        return currentDelayedPrecondition == null && !precondition.isSet();
    }
}

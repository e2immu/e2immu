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
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnceMap;

import java.util.stream.Stream;

public class StateData {

    /*
     precondition = conditions that cause an escape
     they are generated in the throws statement, assert statement
     are copied upwards, and to the next statement

     this variable contains the precondition of one single statement; an aggregate is computed in MethodLevelData
     */

    public final EventuallyFinal<Expression> precondition = new EventuallyFinal<>();

    public boolean preconditionIsEmpty() {
        return precondition.isVariable() && precondition.get() == null;
    }

    public void setPrecondition(Expression expression, boolean isDelayed) {
        if (isDelayed) precondition.setVariable(expression);
        else precondition.setFinal(expression);
    }

    /*
    contains the change in state (not condition, not precondition) when going from one statement to the next
    in the same block

    going down into a block changes condition (not state); this is contained into the ForwardInfo
    this value is set before the complete precondition is computed in method level data; therefore,
    the local condition manager of a subsequent statement in the same block needs to combine this value
    and the method level data's combined precondition.
     */
    public final EventuallyFinal<ConditionManager> conditionManagerForNextStatement = new EventuallyFinal<>();

    public void setLocalConditionManagerForNextStatement(ConditionManager localConditionManager) {
        if (localConditionManager.isDelayed()) conditionManagerForNextStatement.setVariable(localConditionManager);
        else conditionManagerForNextStatement.setFinal(localConditionManager);
    }

    public final EventuallyFinal<Expression> valueOfExpression = new EventuallyFinal<>();

    public void setValueOfExpression(Expression value, boolean isDelayed) {
        if (isDelayed) valueOfExpression.setVariable(value);
        else valueOfExpression.setFinal(value);
    }

    private final SetOnceMap<String, EventuallyFinal<Expression>> statesOfInterrupts;

    public StateData(boolean isLoop) {
        statesOfInterrupts = isLoop ? new SetOnceMap<>() : null;
    }

    // states of interrupt

    public void addStateOfInterrupt(String index, Expression state, boolean stateIsDelayed) {
        EventuallyFinal<Expression> cd = statesOfInterrupts.getOrCreate(index, i -> new EventuallyFinal<>());
        if (stateIsDelayed) {
            cd.setVariable(state);
        } else {
            cd.setFinal(state);
        }
    }

    public Stream<Expression> statesOfInterruptsStream() {
        return statesOfInterrupts.stream().map(e -> e.getValue().get());
    }
}

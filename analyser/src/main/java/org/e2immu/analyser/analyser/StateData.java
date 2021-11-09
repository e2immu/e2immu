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

import org.e2immu.analyser.analyser.util.DelayDebugCollector;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnceMap;

import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

public class StateData implements DelayDebugger {
    private final DelayDebugger delayDebugCollector = new DelayDebugCollector();
    private final String methodFqnIndex;

    public StateData(boolean isLoop, String methodFqnIndex) {
        this.methodFqnIndex = methodFqnIndex;
        statesOfInterrupts = isLoop ? new SetOnceMap<>() : null;
    }

    /*
     precondition = conditions that cause an escape
     they are generated in the throws statement, assert statement
     are copied upwards, and to the next statement

     this variable contains the precondition of one single statement; an aggregate is computed in MethodLevelData
     */

    private final EventuallyFinal<Precondition> precondition = new EventuallyFinal<>();

    public boolean preconditionIsEmpty() {
        return precondition.isVariable() && precondition.get() == null;
    }

    public void setPrecondition(Precondition expression, boolean isDelayed) {
        if (isDelayed) {
            precondition.setVariable(expression);
            assert createDelay(methodFqnIndex + ":STD", methodFqnIndex + D_PRECONDITION);
        } else setFinalAllowEquals(precondition, expression);
    }

    public void setPreconditionAllowEquals(Precondition expression) {
        setFinalAllowEquals(precondition, expression);
    }

    public Precondition getPrecondition() {
        return precondition.get();
    }

    public boolean preconditionIsFinal() {
        return precondition.isFinal();
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
        if (localConditionManager.isSafeDelayed()) {
            conditionManagerForNextStatement.setVariable(localConditionManager);
            assert createDelay(methodFqnIndex+":STD", methodFqnIndex+ D_CONDITION_MANAGER_FOR_NEXT_STMT);
        } else setFinalAllowEquals(conditionManagerForNextStatement, localConditionManager);
    }

    public final EventuallyFinal<Expression> valueOfExpression = new EventuallyFinal<>();

    public void setValueOfExpression(Expression value, boolean isDelayed) {
        if (isDelayed) {
            valueOfExpression.setVariable(value);
            assert createDelay(methodFqnIndex + ":STD", methodFqnIndex + D_VALUE_OF_EXPRESSION);
        } else setFinalAllowEquals(valueOfExpression, value);
    }

    private final SetOnceMap<String, EventuallyFinal<Expression>> statesOfInterrupts;

    // states of interrupt

    public void addStateOfInterrupt(String index, Expression state, boolean stateIsDelayed) {
        EventuallyFinal<Expression> cd = statesOfInterrupts.getOrCreate(index, i -> new EventuallyFinal<>());
        if (stateIsDelayed) {
            cd.setVariable(state);
        } else {
            setFinalAllowEquals(cd, state);
        }
    }

    public Stream<Expression> statesOfInterruptsStream() {
        return statesOfInterrupts.stream().map(e -> e.getValue().get());
    }

    public Set<Variable> valueOfExpressionIsDelayed() {
        if (valueOfExpression.isFinal()) return null;
        Expression value = valueOfExpression.get();
        if (value instanceof DelayedVariableExpression dve) {
            return Set.of(dve.variable());
        }
        return Set.of();
    }

    @Override
    public boolean createDelay(String where, String delayFqn) {
        return delayDebugCollector.createDelay(where, delayFqn);
    }

    @Override
    public Stream<DelayDebugNode> streamNodes() {
        return delayDebugCollector.streamNodes();
    }

    public CausesOfDelay conditionManagerForNextStatementStatus() {
    }
}

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
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.Or;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnceMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

public class StateData {

    public StateData(boolean isLoop, Primitives primitives) {
        statesOfInterrupts = isLoop ? new SetOnceMap<>() : null;
        statesOfReturnInLoop = isLoop ? new SetOnceMap<>() : null;
        conditionManagerForNextStatement.setVariable(ConditionManager.initialConditionManager(primitives));
    }


    /*
     precondition = conditions that cause an escape
     they are generated in the "throw" statement, assert statement
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
    private final EventuallyFinal<ConditionManager> conditionManagerForNextStatement = new EventuallyFinal<>();

    public void setLocalConditionManagerForNextStatement(ConditionManager localConditionManager) {
        if (localConditionManager.isSafeDelayed()) {
            conditionManagerForNextStatement.setVariable(localConditionManager);
        } else setFinalAllowEquals(conditionManagerForNextStatement, localConditionManager);
    }

    public CausesOfDelay conditionManagerForNextStatementStatus() {
        if (conditionManagerForNextStatement.isFinal()) return CausesOfDelay.EMPTY;
        return conditionManagerForNextStatement.get().causesOfDelay();
    }

    public ConditionManager getConditionManagerForNextStatement() {
        return conditionManagerForNextStatement.get();
    }

    public final EventuallyFinal<Expression> valueOfExpression = new EventuallyFinal<>();

    public void setValueOfExpression(Expression value, boolean isDelayed) {
        if (isDelayed) {
            valueOfExpression.setVariable(value);
        } else setFinalAllowEquals(valueOfExpression, value);
    }

    private final SetOnceMap<String, EventuallyFinal<Expression>> statesOfInterrupts;
    private final SetOnceMap<String, EventuallyFinal<Expression>> statesOfReturnInLoop;

    // mainly for testing
    public Stream<Map.Entry<String, EventuallyFinal<Expression>>> statesOfInterruptsStream() {
        return statesOfInterrupts == null ? Stream.of() : statesOfInterrupts.stream();
    }

    // states of interrupt

    // we're adding the break and return states
    public void addStateOfInterrupt(String index, Expression state, boolean stateIsDelayed) {
        EventuallyFinal<Expression> cd = statesOfInterrupts.getOrCreate(index, i -> new EventuallyFinal<>());
        if (stateIsDelayed) {
            cd.setVariable(state);
        } else {
            setFinalAllowEquals(cd, state);
        }
    }

    public void addStateOfReturnInLoop(String index, Expression state, boolean stateIsDelayed) {
        EventuallyFinal<Expression> cd = statesOfReturnInLoop.getOrCreate(index, i -> new EventuallyFinal<>());
        if (stateIsDelayed) {
            cd.setVariable(state);
        } else {
            setFinalAllowEquals(cd, state);
        }
    }

    public CausesOfDelay valueOfExpressionIsDelayed() {
        if (valueOfExpression.isFinal()) return CausesOfDelay.EMPTY;
        return valueOfExpression.get().causesOfDelay();
    }

    // (break 1 || break 2 ||...|| breakN) && return 1 && ... && return N && !condition
    public Expression combineInterruptsAndExit(LoopStatement loopStatement,
                                               Expression negatedConditionOrExitState,
                                               EvaluationContext evaluationContext) {

        List<Expression> ors = new ArrayList<>();
        statesOfInterrupts.stream().map(Map.Entry::getValue).forEach(e ->
                ors.add(evaluationContext.replaceLocalVariables(e.get())));
        List<Expression> ands = new ArrayList<>();
        statesOfReturnInLoop.stream().map(Map.Entry::getValue).forEach(e ->
                ands.add(evaluationContext.replaceLocalVariables(e.get())));
        if (!ors.isEmpty()) {
            ands.add(Or.or(evaluationContext, ors.toArray(Expression[]::new)));
        }
        if (loopStatement.hasExitCondition() && !negatedConditionOrExitState.isBoolValueFalse()) {
            // the exit condition cannot contain local variables
            ands.add(evaluationContext.replaceLocalVariables(negatedConditionOrExitState));
        }
        return And.and(evaluationContext, ands.toArray(Expression[]::new));
    }

    public boolean noExitViaReturnOrBreak() {
        return statesOfInterrupts.isEmpty() && statesOfReturnInLoop.isEmpty();
    }
}

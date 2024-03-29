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
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.Or;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

public class StateData {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateData.class);

    private final EventuallyFinal<ConditionManager> conditionManagerForNextStatement = new EventuallyFinal<>();
    private final SetOnceMap<VariableExpression, Expression> equalityAccordingToState = new SetOnceMap<>();
    private final EventuallyFinal<Precondition> precondition = new EventuallyFinal<>();
    // used for transfer from SAApply / StatementAnalysis.applyPrecondition to SASubBlocks
    private final EventuallyFinal<Precondition> preconditionFromMethodCalls = new EventuallyFinal<>();
    private final SetOnceMap<String, EventuallyFinal<Expression>> statesOfInterrupts;
    private final SetOnceMap<String, EventuallyFinal<Expression>> statesOfReturnInLoop;
    public final EventuallyFinal<Expression> valueOfExpression = new EventuallyFinal<>();

    public StateData(Location location, boolean isLoop, Primitives primitives) {
        statesOfInterrupts = isLoop ? new SetOnceMap<>() : null;
        statesOfReturnInLoop = isLoop ? new SetOnceMap<>() : null;
        conditionManagerForNextStatement.setVariable(ConditionManager.initialConditionManager(primitives));
        precondition.setVariable(Precondition.noInformationYet(location, primitives));
    }

    public void internalAllDoneCheck() {
        assert conditionManagerForNextStatement.isFinal();
        assert precondition.isFinal();
        assert preconditionFromMethodCalls.isFinal();
        assert valueOfExpression.isFinal();
        assert statesOfInterrupts == null || statesOfInterrupts.stream().allMatch(e -> e.getValue().isFinal());
        assert statesOfReturnInLoop == null || statesOfReturnInLoop.stream().allMatch(e -> e.getValue().isFinal());
    }

    public void makeUnreachable(Primitives primitives) {
        if (conditionManagerForNextStatement.isVariable()) {
            conditionManagerForNextStatement.setFinal(ConditionManager.impossibleConditionManager(primitives));
        }
        if (precondition.isVariable()) {
            precondition.setFinal(Precondition.empty(primitives));
        }
        if (preconditionFromMethodCalls.isVariable()) {
            preconditionFromMethodCalls.setFinal(Precondition.empty(primitives));
        }
        Expression unreachable = UnknownExpression.forUnreachableStatement();
        if (valueOfExpression.isVariable()) {
            valueOfExpression.setFinal(unreachable);
        }
        if (statesOfInterrupts != null) {
            statesOfInterrupts.stream().forEach(e -> {
                if (e.getValue().isVariable()) e.getValue().setFinal(unreachable);
            });
        }
        if (statesOfReturnInLoop != null) {
            statesOfReturnInLoop.stream().forEach(e -> {
                if (e.getValue().isVariable()) e.getValue().setFinal(unreachable);
            });
        }
    }

    public boolean equalityAccordingToStateIsSet(VariableExpression variable) {
        return equalityAccordingToState.isSet(variable);
    }

    public void equalityAccordingToStatePut(VariableExpression variable, Expression lhs) {
        equalityAccordingToState.put(variable, lhs);
    }

    public Stream<Map.Entry<VariableExpression, Expression>> equalityAccordingToStateStream() {
        return equalityAccordingToState.stream();
    }

    public Expression equalityAccordingToStateGetOrDefaultNull(VariableExpression v) {
        return equalityAccordingToState.getOrDefaultNull(v);
    }

    /*
     precondition = conditions that cause an escape
     they are generated in the "throw" statement, assert statement
     are copied upwards, and to the next statement

     this variable contains the precondition of one single statement; an aggregate is computed in MethodLevelData
     */

    public boolean preconditionNoInformationYet(MethodInfo currentMethod) {
        return precondition.isVariable() && precondition.get().isNoInformationYet(currentMethod);
    }

    /*
    conventions: Precondition.DELAYED_NO_INFORMATION (exactly this object) means no information yet, variable.
    Expression true means: no information, but final
    Expression false: impossible (not allowed to call the method?)
    delayed expression: there will be info, but it is delayed
    any other: real PC

    return progress
     */
    public boolean setPrecondition(Precondition pc) {
        assert pc != null;
        if (pc.expression().isDelayed()) {
            try {
                precondition.setVariable(pc);
                return false;
            } catch (IllegalStateException ise) {
                LOGGER.error("Try to set delayed {}, already have {}", pc, precondition.get());
                throw ise;
            }
        }
        return setFinalAllowEquals(precondition, pc);
    }

    // return progress
    public boolean setPreconditionAllowEquals(Precondition expression) {
        return setFinalAllowEquals(precondition, expression);
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

    public boolean setLocalConditionManagerForNextStatement(ConditionManager localConditionManager) {
        try {
            if (localConditionManager.isSafeDelayed()) {
                conditionManagerForNextStatement.setVariable(localConditionManager);
                return false;
            }
            return setFinalAllowEquals(conditionManagerForNextStatement, localConditionManager);
        } catch (IllegalStateException ise) {
            LOGGER.error("Error setting new localConditionManager {}, already have {}", localConditionManager,
                    conditionManagerForNextStatement.get());
            throw ise;
        }
    }

    public CausesOfDelay conditionManagerForNextStatementStatus() {
        if (conditionManagerForNextStatement.isFinal()) return CausesOfDelay.EMPTY;
        return conditionManagerForNextStatement.get().causesOfDelay();
    }

    public ConditionManager getConditionManagerForNextStatement() {
        return conditionManagerForNextStatement.get();
    }

    public Precondition getPreconditionFromMethodCalls() {
        return preconditionFromMethodCalls.get();
    }

    // return progress
    public boolean setPreconditionFromMethodCalls(Precondition precondition) {
        try {
            if (precondition.isDelayed()) {
                preconditionFromMethodCalls.setVariable(precondition);
                return false;
            }
            boolean variable = preconditionFromMethodCalls.isVariable();
            if (variable || !precondition.equals(preconditionFromMethodCalls.get())) {
                preconditionFromMethodCalls.setFinal(precondition);
            }
            return variable;
        } catch (IllegalStateException ise) {
            LOGGER.error("Value was {}, new value {}", preconditionFromMethodCalls.get(), precondition);
            throw ise;
        }
    }

    public boolean setValueOfExpression(Expression value) {
        if (value.isDelayed()) {
            assert valueOfExpression.isVariable()
                    : "Already have final value '" + valueOfExpression.get() + "'; trying to write delayed value '" + value + "'";
            valueOfExpression.setVariable(value);
            return false;
        }
        return setFinalAllowEquals(valueOfExpression, value);
    }

    // mainly for testing
    public Stream<Map.Entry<String, EventuallyFinal<Expression>>> statesOfInterruptsStream() {
        return statesOfInterrupts == null ? Stream.of() : statesOfInterrupts.stream();
    }

    // states of interrupt

    // we're adding the break and return states
    public boolean addStateOfInterrupt(String index, Expression state) {
        EventuallyFinal<Expression> cd = statesOfInterrupts.getOrCreate(index, i -> new EventuallyFinal<>());
        if (state.isDelayed()) {
            cd.setVariable(state);
            return false;
        }
        return setFinalAllowEquals(cd, state);
    }

    public void addStateOfReturnInLoop(String index, Expression state) {
        EventuallyFinal<Expression> cd = statesOfReturnInLoop.getOrCreate(index, i -> new EventuallyFinal<>());
        if (state.isDelayed()) {
            cd.setVariable(state);
        } else {
            setFinalAllowEquals(cd, state);
        }
    }

    public void stateOfReturnInLoopUnreachable(String index) {
        EventuallyFinal<Expression> cd = statesOfReturnInLoop.getOrDefaultNull(index);
        if (cd != null && cd.isVariable()) {
            cd.setFinal(UnknownExpression.forUnreachableStatement());
        }
    }

    public CausesOfDelay valueOfExpressionIsDelayed() {
        if (valueOfExpression.isFinal()) return CausesOfDelay.EMPTY;
        return valueOfExpression.get().causesOfDelay();
    }

    // negated condition: did not participate in the loop
    // exit state: went through loop, this is exit value

    // (break 1 || ...|| breakN || !condition) && return 1 state && ... && return N state
    public Expression combineInterruptsAndExit(LoopStatement loopStatement,
                                               Expression negatedConditionOrExitState,
                                               EvaluationResult context,
                                               Predicate<String> isStillReachable) {

        List<Expression> ors = new ArrayList<>();
        if (loopStatement.hasExitCondition()) {
            statesOfInterrupts.stream().map(Map.Entry::getValue).forEach(e ->
                    ors.add(context.evaluationContext().replaceLocalVariables(e.get())));
            if (!negatedConditionOrExitState.isBoolValueFalse()) {
                // the exit condition cannot contain local variables
                ors.add(context.evaluationContext().replaceLocalVariables(negatedConditionOrExitState));
            }
        }
        List<Expression> ands = new ArrayList<>();
        statesOfReturnInLoop.stream()
                .filter(e -> isStillReachable.test(e.getKey()))
                .map(Map.Entry::getValue)
                .forEach(e -> ands.add(context.evaluationContext().replaceLocalVariables(e.get())));
        if (!ors.isEmpty()) {
            ands.add(Or.or(context, ors.toArray(Expression[]::new)));
        }
        return And.and(context, ands.toArray(Expression[]::new));
    }

    public boolean noExitViaReturnOrBreak() {
        return statesOfInterrupts.isEmpty() && statesOfReturnInLoop.isEmpty();
    }
}

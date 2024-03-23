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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface StateData {

    void makeUnreachable(Primitives primitives);

    Expression setAbsoluteState(EvaluationContext evaluationContext);

    Expression getAbsoluteState();

    void equalityAccordingToStatePut(IsVariableExpression variable, Expression lhs);

    Stream<Map.Entry<IsVariableExpression, Expression>> equalityAccordingToStateStream();

    void eraseEqualityAccordingToState(Variable variable);

    Expression equalityAccordingToStateGetOrDefaultNull(IsVariableExpression v);

    /*
     precondition = conditions that cause an escape
     they are generated in the "throw" statement, assert statement
     are copied upwards, and to the next statement

     this variable contains the precondition of one single statement; an aggregate is computed in MethodLevelData
     */

    boolean preconditionNoInformationYet(MethodInfo currentMethod);

    boolean postConditionNoInformationYet();

    /*
    conventions: Precondition.DELAYED_NO_INFORMATION (exactly this object) means no information yet, variable.
    Expression true means: no information, but final
    Expression false: impossible (not allowed to call the method?)
    delayed expression: there will be info, but it is delayed
    any other: real PC

    return progress
     */
    boolean setPrecondition(Precondition pc);

    // return progress
    boolean setPreconditionAllowEquals(Precondition expression);

    Precondition getPrecondition();

    boolean preconditionIsFinal();

    PostCondition getPostCondition();

    boolean postConditionIsFinal();

    boolean havePostCondition();

    boolean setPostCondition(PostCondition pc);

    /*
    contains the change in state (not condition, not precondition) when going from one statement to the next
    in the same block

    going down into a block changes condition (not state); this is contained into the ForwardInfo
    this value is set before the complete precondition is computed in method level data; therefore,
    the local condition manager of a subsequent statement in the same block needs to combine this value
    and the method level data's combined precondition.
     */

    boolean setLocalConditionManagerForNextStatement(ConditionManager localConditionManager);

    CausesOfDelay conditionManagerForNextStatementStatus();

    ConditionManager getConditionManagerForNextStatement();

    Precondition getPreconditionFromMethodCalls();

    // return progress
    boolean setPreconditionFromMethodCalls(Precondition precondition);

    boolean setValueOfExpression(Expression value);

    // mainly for testing
    Stream<Map.Entry<String, EventuallyFinal<Expression>>> statesOfInterruptsStream();

    // states of interrupt

    // we're adding the break and return states
    boolean addStateOfInterrupt(String index, Expression state);

    void addStateOfReturnInLoop(String index, Expression state);

    void stateOfReturnInLoopUnreachable(String index);

    CausesOfDelay valueOfExpressionIsDelayed();

    // negated condition: did not participate in the loop
    // exit state: went through loop, this is exit value

    // (break 1 || ...|| breakN || !condition) && return 1 state && ... && return N state
    Expression combineInterruptsAndExit(LoopStatement loopStatement,
                                        Expression negatedConditionOrExitState,
                                        EvaluationResult context,
                                        Predicate<String> isStillReachable);

    boolean noExitViaReturnOrBreak();

    boolean isEscapeNotInPreOrPostConditions();

    void ensureEscapeNotInPreOrPostConditions();

    Expression staticSideEffect();

    /*
    Convention: EmptyExpression when the current expression is not an SSE
     */
    boolean setStaticSideEffect(Expression expression);

    boolean staticSideEffectIsSet();

    void internalAllDoneCheck();

    Expression valueOfExpressionGet();

    LinkedVariables linkedVariablesOfExpressionGet();

    boolean valueOfExpressionIsVariable();

    boolean writeValueOfExpression(Expression expression);

}

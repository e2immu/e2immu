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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Set;

/*
condition = the condition in the parent statement that leads to this block. Default: true

state = the cumulative state in the current block, before execution of the statement (level 1-2, not 3).
The state is carried over to the next statement unless there is some interrupt in the flow (break, return, throw...)

precondition = the cumulative precondition of the method.
In SAI.analyseSingleStatement, the cumulative precondition from MethodLevelData is added via
ConditionManagerHelper.makeLocalConditionManager.

In a recursion of inline conditionals, the state remains true, and the condition equals the condition of each inline.
Default value: true

Concerning delays: only condition and state are recursively combined, precondition is not.
 */
public interface ConditionManager {
    Expression condition();

    Set<Variable> conditionVariables();

    Expression state();

    Set<Variable> stateVariables();

    Precondition precondition();

    Set<Variable> ignore();

    ConditionManager parent();


    boolean isDelayed();

    boolean isReasonForDelay(Variable variable);


    /*
    adds a new layer (parent this)
    Used in CompanionAnalyser, ComputingMethodAnalyser, FieldAnalyser
     */
    ConditionManager newAtStartOfNewBlock(Primitives primitives, Expression condition,
                                          Set<Variable> conditionVariables, Precondition precondition);

    /*
    we guarantee a parent so that the condition counts!
    Used in: StatementAnalyserImpl.analyseAllStatementsInBlock
     */
    ConditionManager withCondition(EvaluationResult context, Expression switchCondition,
                                   Set<Variable> conditionVariables);

    ConditionManager withConditionCompute(EvaluationResult context, Expression switchCondition);

    /*
    adds a new layer (parent this)
    Widely used, mostly in SASubBlocks to create the CM of the ExecutionOfBlock objects
    */
    ConditionManager newAtStartOfNewBlockDoNotChangePrecondition(Primitives primitives,
                                                                 Expression condition,
                                                                 Set<Variable> conditionVariables);

    /*
    adds a new layer (parent this)
    Used to: create a child CM that has more state
    */
    ConditionManager addState(Expression state, Set<Variable> stateVariables);

    /*
    stays at the same level (parent=parent)
    Used in: ConditionManagerHelper.makeLocalConditionManager, used in StatementAnalyserImpl.analyseSingleStatement
    This is the feedback loop from MethodLevelData.combinedPrecondition back into the condition manager
     */
    ConditionManager withPrecondition(Precondition combinedPrecondition);

    /*
    stays at the same level
    Used in EvaluationContext.nneForValue
     */
    ConditionManager withoutState(Primitives primitives);

    /*
    stays at the same level (parent=parent)
    Used in SASubBlocks
     */
    ConditionManager newForNextStatementDoNotChangePrecondition(EvaluationResult evaluationContext,
                                                                Expression addToState,
                                                                Set<Variable> addToStateVariables);

    /*
    Re-assignments of variables... affects absoluteState computations!
     */
    ConditionManager removeVariables(Set<Variable> variablesAssigned);

    Expression absoluteState(EvaluationResult evaluationContext);

    Expression expressionWithoutVariables(EvaluationResult context,
                                          Expression expression,
                                          Set<Variable> cumulativeIgnore);

    Identifier getIdentifier();

    Expression stateUpTo(EvaluationResult context, int recursions);

    //i>3?i:3, for example. Result is non-boolean. CM may have a state saying that i<0, which solves this one
    // this method is called for scopes and indices of array access, and for scopes of field references
    Expression evaluateNonBoolean(EvaluationResult context, Expression value);

    /**
     * computes a value in the context of the current condition manager.
     *
     * @param doingNullCheck a boolean to prevent a stack overflow, repeatedly trying to detect not-null situations
     *                       (see e.g. Store_0)
     * @return a value without the precondition attached
     */
    Expression evaluate(EvaluationResult context, Expression value, boolean doingNullCheck);

    /**
     * Extract NOT_NULL properties from the current condition in ACCEPT mode.
     * See enum ACCEPT for more explanation of the difference between ACCEPT and REJECT.
     *
     * @return individual variables that appear in a top-level conjunction as variable == null
     */
    Set<Variable> findIndividualNullInCondition(EvaluationResult evaluationContext, boolean requireEqualsNull);

    /**
     * Extract NOT_NULL properties from the current absolute state, seen as a conjunction (filter mode ACCEPT)
     *
     * @return individual variables that appear in the conjunction as variable == null
     */
    Set<Variable> findIndividualNullInState(EvaluationResult context, boolean requireEqualsNull);

    Set<Variable> findIndividualNullInPrecondition(EvaluationResult evaluationContext, boolean requireEqualsNull);

    /*
     return that part of the absolute conditional that is NOT covered by @NotNull (individual not null clauses), as
     an AND of negations of the remainder after getting rid of != null, == null clauses.
     */
    Expression precondition(EvaluationResult evaluationContext);

    /*
    any info there is about this variable
     */
    Expression individualStateInfo(EvaluationResult evaluationContext, Variable variable);

    /*
    why a separate version? because preconditions do not work 'cumulatively', preconditionIsDelayed
    has no info about delays in the parent. This is not compatible with writing an eventually final version.
    See Project_0 ...
     */
    boolean isSafeDelayed();

    CausesOfDelay stateDelayedOrPreconditionDelayed();

    CausesOfDelay causesOfDelay();

    List<Variable> variables();

    Expression multiExpression();
}

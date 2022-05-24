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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public record ConditionManager(Expression condition,
                               Expression state,
                               Precondition precondition,
                               ConditionManager parent) {

    private static final ConditionManager SPECIAL = new ConditionManager();

    public static final int LIMIT_ON_COMPLEXITY = 200;

    private ConditionManager() {
        this(UnknownExpression.forSpecial(), UnknownExpression.forSpecial(),
                new Precondition(UnknownExpression.forSpecial(), List.of()), null);
    }

    public ConditionManager {
        checkBooleanOrUnknown(Objects.requireNonNull(condition));
        checkBooleanOrUnknown(Objects.requireNonNull(state));
        Objects.requireNonNull(precondition);
    }

    public boolean isDelayed() {
        return condition.isDelayed() || state.isDelayed() || precondition.expression().isDelayed()
                || (parent != null && parent.isDelayed());
    }

    public boolean isReasonForDelay(Variable variable) {
        return state.causesOfDelay().contains(variable)
                || condition.causesOfDelay().contains(variable)
                || precondition.expression().causesOfDelay().contains(variable)
                || (parent != null && parent().isReasonForDelay(variable));
    }

    /*
    EMPTY -> some value, no clue which one, we'll never know
    NO_VALUE -> delay
     */
    private static void checkBooleanOrUnknown(Expression v) {
        if (!v.isUnknown() && v.returnType().isNotBooleanOrBoxedBoolean()) {
            throw new UnsupportedOperationException("Need an unknown or boolean value in the condition manager; got " + v
                    + " with return type " + v.returnType());
        }
    }

    public static ConditionManager initialConditionManager(Primitives primitives) {
        BooleanConstant TRUE = new BooleanConstant(primitives, true);
        return new ConditionManager(TRUE, TRUE, Precondition.empty(TRUE), null);
    }

    public static ConditionManager impossibleConditionManager(Primitives primitives) {
        BooleanConstant FALSE = new BooleanConstant(primitives, true);
        return new ConditionManager(FALSE, FALSE, new Precondition(FALSE, List.of()), null);
    }

    /*
    adds a new layer (parent this)
    Used in CompanionAnalyser, ComputingMethodAnalyser, FieldAnalyser
     */
    public ConditionManager newAtStartOfNewBlock(Primitives primitives, Expression condition, Precondition precondition) {
        return new ConditionManager(condition, new BooleanConstant(primitives, true), precondition, this);
    }

    /*
    we guarantee a parent so that the condition counts!
    Used in: StatementAnalyserImpl.analyseAllStatementsInBlock
     */
    public ConditionManager withCondition(EvaluationResult context, Expression switchCondition) {
        return new ConditionManager(combine(context, condition, switchCondition), state, precondition, this);
    }

    /*
    adds a new layer (parent this)
    Widely used, mostly in SASubBlocks to create the CM of the ExecutionOfBlock objects
    */
    public ConditionManager newAtStartOfNewBlockDoNotChangePrecondition(Primitives primitives, Expression condition) {
        return new ConditionManager(condition, new BooleanConstant(primitives, true), precondition, this);
    }

    /*
    adds a new layer (parent this)
    Used to: create a child CM that has more state
    */
    public ConditionManager addState(Expression state) {
        return new ConditionManager(condition, state, precondition, this);
    }

    /*
    stays at the same level (parent=parent)
    Used in: ConditionManagerHelper.makeLocalConditionManager, used in StatementAnalyserImpl.analyseSingleStatement
    This is the feedback loop from MethodLevelData.combinedPrecondition back into the condition manager
     */
    public ConditionManager withPrecondition(Precondition combinedPrecondition) {
        return new ConditionManager(condition, state, combinedPrecondition, parent);
    }

    /*
    stays at the same level
    Used in EvaluationContext.nneForValue
     */
    public ConditionManager withoutState(Primitives primitives) {
        return new ConditionManager(condition, new BooleanConstant(primitives, true), precondition, parent);
    }

    /*
    stays at the same level (parent=parent)
    Used in SASubBlocks
     */
    public ConditionManager newForNextStatementDoNotChangePrecondition(EvaluationResult evaluationContext,
                                                                       Expression addToState) {
        Objects.requireNonNull(addToState);
        if (addToState.isBoolValueTrue()) return this;
        Expression newState = combine(evaluationContext, state, addToState);
        return new ConditionManager(condition, newState, precondition, parent);
    }

    public Expression absoluteState(EvaluationResult evaluationContext) {
        return absoluteState(evaluationContext, false);
    }

    private Expression absoluteState(EvaluationResult evaluationContext, boolean doingNullCheck) {
        Expression[] expressions;
        int complexity;
        if (parent == null) {
            expressions = new Expression[]{state};
            complexity = state.getComplexity();
        } else {
            Expression parentAbsolute = parent.absoluteState(evaluationContext, doingNullCheck);
            expressions = new Expression[]{condition, state, parentAbsolute};
            complexity = condition.getComplexity() + state.getComplexity() + parentAbsolute.getComplexity();
        }
        if (complexity > LIMIT_ON_COMPLEXITY) {
            return Instance.forTooComplex(getIdentifier(), evaluationContext.getPrimitives().booleanParameterizedType());
        }
        return And.and(evaluationContext, doingNullCheck, expressions);
    }

    public Identifier getIdentifier() {
        List<Identifier> list;
        if (parent == null) {
            list = List.of(condition.getIdentifier(), state.getIdentifier(), precondition.expression().getIdentifier());
        } else {
            list = List.of(condition.getIdentifier(), state.getIdentifier(), precondition.expression().getIdentifier(), parent.getIdentifier());
        }
        return Identifier.joined("cm", list);
    }

    public Expression stateUpTo(EvaluationResult context, int recursions) {
        Expression[] expressions;
        if (parent == null) {
            expressions = new Expression[]{state};
        } else if (recursions == 0) {
            expressions = new Expression[]{condition};
        } else {
            expressions = new Expression[]{condition, state, parent.stateUpTo(context, recursions - 1)};
        }
        return And.and(context, expressions);
    }

    //i>3?i:3, for example. Result is non-boolean. CM may have a state saying that i<0, which solves this one
    // this method is called for scopes and indices of array access, and for scopes of field references
    public Expression evaluateNonBoolean(EvaluationResult context, Expression value) {
        assert !value.returnType().isBooleanOrBoxedBoolean() : "Got " + value.getClass() + ", type " + value.returnType();
        Expression conditionalPart = value.extractConditions(context.getPrimitives());
        if (conditionalPart.isBoolValueTrue()) return value;
        Expression absoluteState = absoluteState(context, false);
        if (absoluteState.isEmpty() || absoluteState.isBoolValueTrue()) return value;
        Expression newState = And.and(context, absoluteState, conditionalPart);
        if (newState.equals(conditionalPart) || newState.equals(absoluteState)) {
            return value.applyCondition(new BooleanConstant(context.getPrimitives(), true));
        }
        if (newState instanceof And and && and.getExpressions().stream().anyMatch(conditionalPart::equals)) {
            return value; // no improvement can be made
        }
        return value.applyCondition(newState);
    }

    /**
     * computes a value in the context of the current condition manager.
     *
     * @param doingNullCheck a boolean to prevent a stack overflow, repeatedly trying to detect not-null situations
     *                       (see e.g. Store_0)
     * @return a value without the precondition attached
     */
    public Expression evaluate(EvaluationResult context, Expression value, boolean doingNullCheck) {
        assert value.returnType().isBooleanOrBoxedBoolean() : "Got " + value.getClass() + ", type " + value.returnType();
        if (value.isBoolValueFalse()) return value; // no matter what the conditions and state is

        Expression absoluteState = absoluteState(context, doingNullCheck);
        if (absoluteState.isEmpty() || value.isEmpty()) throw new UnsupportedOperationException();
        /*
        check on true: no state, so don't do anything
         */
        Expression combinedWithPrecondition;
        if (precondition.isEmpty()) {
            combinedWithPrecondition = absoluteState;
        } else {
            combinedWithPrecondition = And.and(context, doingNullCheck, absoluteState, precondition.expression());
        }
        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Expression resultWithPrecondition = And.and(context, doingNullCheck, combinedWithPrecondition, value);
        if (resultWithPrecondition.equals(combinedWithPrecondition)) {
            // constant true: adding the value has no effect at all
            return new BooleanConstant(context.getPrimitives(), true);
        }
        // return the result without precondition
        Expression result = And.and(context, doingNullCheck, absoluteState, value);
        if (result instanceof And and && and.getExpressions().stream().anyMatch(value::equals)) {
            return value;
        }
        return result;
    }


    private Expression combine(EvaluationResult evaluationContext, Expression e1, Expression e2) {
        Objects.requireNonNull(e2);
        if (e1.isEmpty() || e2.isEmpty()) throw new UnsupportedOperationException();
        int complexity = e1.getComplexity() + e2.getComplexity();
        if (complexity > LIMIT_ON_COMPLEXITY) {
            return Instance.forTooComplex(getIdentifier(), evaluationContext.getPrimitives().booleanParameterizedType());
        }
        return And.and(evaluationContext, e1, e2);
    }

    /**
     * Extract NOT_NULL properties from the current condition in ACCEPT mode.
     * See enum ACCEPT for more explanation of the difference between ACCEPT and REJECT.
     *
     * @return individual variables that appear in a top-level conjunction as variable == null
     */
    public Set<Variable> findIndividualNullInCondition(EvaluationResult evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(condition, evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current absolute state, seen as a conjunction (filter mode ACCEPT)
     *
     * @return individual variables that appear in the conjunction as variable == null
     */
    public Set<Variable> findIndividualNullInState(EvaluationResult context, boolean requireEqualsNull) {
        Expression state;
        if (context.evaluationContext().preventAbsoluteStateComputation()) {
            state = this.state;
        } else {
            state = absoluteState(context, false);
        }
        return findIndividualNull(state, context, Filter.FilterMode.ACCEPT, requireEqualsNull);

    }

    public Set<Variable> findIndividualNullInPrecondition(EvaluationResult evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(precondition.expression(), evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level conjunction or disjunction as variable == null
     */
    public static Set<Variable> findIndividualNull(Expression value,
                                                   EvaluationResult evaluationContext,
                                                   Filter.FilterMode filterMode,
                                                   boolean requireEqualsNull) {
        if (value.isEmpty()) {
            return Set.of();
        }
        Filter filter = new Filter(evaluationContext, filterMode);
        Map<Variable, Expression> individualNullClauses = filter.filter(value, filter.individualNullOrNotNullClause()).accepted();
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> requireEqualsNull == (e.getValue().equalsNull()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /*
     return that part of the absolute conditional that is NOT covered by @NotNull (individual not null clauses), as
     an AND of negations of the remainder after getting rid of != null, == null clauses.
     */
    public Expression precondition(EvaluationResult evaluationContext) {
        Expression absoluteState = absoluteState(evaluationContext, false);
        if (absoluteState.isEmpty()) throw new UnsupportedOperationException();
        Expression negated = Negation.negate(evaluationContext, absoluteState);

        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<ParameterInfo> filterResult = filter.filter(negated, filter.individualNullOrNotNullClauseOnParameter());
        // those parts that have nothing to do with individual clauses
        return filterResult.rest();
    }

    private static Filter.FilterResult<Variable> obtainVariableFilter(Expression defaultRest, Variable variable, Expression value) {
        List<Variable> variables = value.variables(true);
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Filter.FilterResult<>(Map.of(variable, value), defaultRest);
        }
        return null;
    }

    /*
    any info there is about this variable
     */
    public Expression individualStateInfo(EvaluationResult evaluationContext, Variable variable) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Expression absoluteState = absoluteState(evaluationContext, false);
        Expression combinedWithPrecondition;
        if (precondition.isEmpty()) {
            combinedWithPrecondition = absoluteState;
        } else {
            combinedWithPrecondition = And.and(evaluationContext, absoluteState, precondition.expression());
        }

        Filter.FilterResult<Variable> filterResult = filter.filter(combinedWithPrecondition,
                value -> obtainVariableFilter(filter.getDefaultRest(), variable, value));
        return filterResult.accepted().getOrDefault(variable, filter.getDefaultRest());
    }

    /*
    why a separate version? because preconditions do not work 'cumulatively', preconditionIsDelayed
    has no info about delays in the parent. This is not compatible with writing an eventually final version.
    See Project_0 ...
     */
    public boolean isSafeDelayed() {
        return isDelayed() || parent != null && parent.isDelayed() || parent == SPECIAL;
    }

    public CausesOfDelay stateDelayedOrPreconditionDelayed() {
        return state.causesOfDelay().merge(precondition.expression().causesOfDelay());
    }

    public CausesOfDelay causesOfDelay() {
        CausesOfDelay mine = condition.causesOfDelay().merge(state.causesOfDelay()).merge(precondition.expression().causesOfDelay());
        return parent == null ? mine : mine.merge(parent.causesOfDelay());
    }

    @Override
    public String toString() {
        return "CM{" +
                (condition.isBoolValueTrue() ? "" : "condition=" + condition + ";") +
                (state.isBoolValueTrue() ? "" : "state=" + state + ";") +
                (precondition.isEmpty() ? "" : "pc=" + precondition + ";") +
                (parent == null ? "" : parent == SPECIAL ? "**" : "parent=" + parent) + '}';
    }

    public ConditionManager removeFromState(EvaluationResult evaluationContext, Set<Variable> variablesAssigned) {
        Primitives primitives = evaluationContext.getPrimitives();
        Expression withoutNegation = state instanceof Negation negation ? negation.expression : state;
        if (withoutNegation instanceof Equals equals && !Collections.disjoint(equals.variables(true), variablesAssigned)) {
            Expression newState = state.isDelayed()
                    ? DelayedExpression.forSimplification(getIdentifier(), primitives.booleanParameterizedType(),
                    state, state.causesOfDelay())
                    : new BooleanConstant(primitives, true);
            return new ConditionManager(condition, newState, precondition, parent);
        }
        if (withoutNegation instanceof And and) {
            Expression[] expressions = and.getExpressions().stream()
                    .filter(e -> Collections.disjoint(e.variables(true), variablesAssigned))
                    .toArray(Expression[]::new);
            Expression s = expressions.length == 0 ? new BooleanConstant(primitives, true) :
                    And.and(evaluationContext, expressions);
            return new ConditionManager(condition, s, precondition, parent);
        }
        return this;
    }

    public boolean isEmpty() {
        return condition.isBoolValueTrue() && state.isBoolValueTrue() && precondition().isEmpty() && (parent == null || parent().isEmpty());
    }

    public List<Variable> variables() {
        return Stream.concat(parent == null ? Stream.of() : parent.variables().stream(),
                Stream.concat(condition.variables(true).stream(),
                        Stream.concat(precondition.expression().variables(true).stream(),
                                state.variables(true).stream()))).toList();
    }

    public Expression multiExpression() {
        return MultiExpressions.from(getIdentifier(), variables());
    }

    public record EvaluationContextImpl(AnalyserContext analyserContext) implements EvaluationContext {

        @Override
        public int getDepth() {
            return 1;
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Primitives getPrimitives() {
            return analyserContext.getPrimitives();
        }

        @Override
        public DV isNotNull0(Expression value, boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo) {
            return DV.FALSE_DV;
        }

        @Override
        public Expression currentValue(Variable variable,
                                       Expression scopeValue,
                                       Expression indexValue,
                                       ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable, VariableExpression.NO_SUFFIX, scopeValue, indexValue);
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            return LinkedVariables.EMPTY;
        }
    }
}

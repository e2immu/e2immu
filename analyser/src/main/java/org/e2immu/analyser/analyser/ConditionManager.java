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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
condition = the condition in the parent statement that leads to this block. Default: true

state = the cumulative state in the current block, before execution of the statement (level 1-2, not 3).
The state is carried over to the next statement unless there is some interrupt in the flow (break, return, throw...)

precondition = the cumulative precondition of the method, as in the previous statement's stateData.precondition

In a recursion of inline conditionals, the state remains true, and the condition equals the condition of each inline.
Default value: true

Concerning delays: only condition and state are recursively combined, precondition is not.
 */
public record ConditionManager(Expression condition,
                               CausesOfDelay conditionIsDelayed,
                               Expression state,
                               CausesOfDelay stateIsDelayed,
                               Precondition precondition,
                               CausesOfDelay preconditionIsDelayed,
                               ConditionManager parent) {

    public ConditionManager {
        checkBooleanOrUnknown(Objects.requireNonNull(condition));
        checkBooleanOrUnknown(Objects.requireNonNull(state));
        Objects.requireNonNull(precondition);
        Objects.requireNonNull(conditionIsDelayed);
        Objects.requireNonNull(stateIsDelayed);
        Objects.requireNonNull(preconditionIsDelayed);
    }

    public boolean isDelayed() {
        return stateIsDelayed.isDelayed() || conditionIsDelayed.isDelayed() || preconditionIsDelayed.isDelayed();
    }

    public boolean isReasonForDelay(Variable variable) {
        return stateIsDelayed.contains(variable)
                || conditionIsDelayed.contains(variable)
                || preconditionIsDelayed.contains(variable);
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
        return new ConditionManager(TRUE, CausesOfDelay.EMPTY, TRUE, CausesOfDelay.EMPTY,
                Precondition.empty(TRUE), CausesOfDelay.EMPTY, null);
    }

    public static ConditionManager impossibleConditionManager(Primitives primitives) {
        BooleanConstant FALSE = new BooleanConstant(primitives, true);
        return new ConditionManager(FALSE, CausesOfDelay.EMPTY, FALSE, CausesOfDelay.EMPTY,
                new Precondition(FALSE, List.of()), CausesOfDelay.EMPTY, null);
    }

    /*
    adds a new layer (parent this)
     */
    public ConditionManager newAtStartOfNewBlock(Primitives primitives,
                                                 Expression condition,
                                                 CausesOfDelay conditionIsDelayed,
                                                 Precondition precondition,
                                                 CausesOfDelay preconditionIsDelayed) {
        return new ConditionManager(condition, conditionIsDelayed,
                new BooleanConstant(primitives, true), CausesOfDelay.EMPTY,
                precondition, preconditionIsDelayed, this);
    }


    /* does not add a new layer */
    public ConditionManager replaceState(Expression state, CausesOfDelay stateIsDelayed) {
        return new ConditionManager(condition, conditionIsDelayed, state, stateIsDelayed, precondition, preconditionIsDelayed, parent);
    }

    /*
    we guarantee a parent so that the condition counts!
     */
    public ConditionManager withCondition(EvaluationContext evaluationContext, Expression switchCondition, CausesOfDelay switchExpressionIsDelayed) {
        return new ConditionManager(combine(evaluationContext, condition, switchCondition),
                conditionIsDelayed.merge(switchExpressionIsDelayed),
                state, stateIsDelayed, precondition, preconditionIsDelayed, this);
    }

    /*
    adds a new layer (parent this)
    */
    public ConditionManager newAtStartOfNewBlockDoNotChangePrecondition(Primitives primitives, Expression condition, CausesOfDelay conditionIsDelayed) {
        return new ConditionManager(condition,
                conditionIsDelayed.merge(this.conditionIsDelayed),
                new BooleanConstant(primitives, true),
                stateIsDelayed, precondition, preconditionIsDelayed, this);
    }

    /*
    adds a new layer (parent this)
    */
    public ConditionManager addState(Expression state, CausesOfDelay stateIsDelayed) {
        return new ConditionManager(condition, conditionIsDelayed, state, stateIsDelayed,
                precondition, preconditionIsDelayed, this);
    }

    /*
    stays at the same level (parent parent)
     */
    public ConditionManager withPrecondition(Precondition combinedPrecondition, CausesOfDelay combinedPreconditionIsDelayed) {
        return new ConditionManager(condition, conditionIsDelayed, state, stateIsDelayed, combinedPrecondition,
                combinedPreconditionIsDelayed, parent);
    }

    /*
    stays at the same level
     */
    public ConditionManager withoutState(Primitives primitives) {
        return new ConditionManager(condition, conditionIsDelayed, new BooleanConstant(primitives, true),
                CausesOfDelay.EMPTY, precondition, preconditionIsDelayed, parent);
    }

    /*
    stays at the same level (parent parent)
     */
    public ConditionManager newForNextStatementDoNotChangePrecondition(EvaluationContext evaluationContext,
                                                                       Expression addToState) {
        Objects.requireNonNull(addToState);
        if (addToState.isBoolValueTrue()) return this;
        Expression newState = combine(evaluationContext, state, addToState);
        return new ConditionManager(condition, conditionIsDelayed, newState,
                newState.causesOfDelay().merge(stateIsDelayed),
                precondition, preconditionIsDelayed, parent);
    }

    public Expression absoluteState(EvaluationContext evaluationContext) {
        Expression[] expressions;
        if (parent == null) {
            expressions = new Expression[]{state};
        } else {
            expressions = new Expression[]{condition, state, parent.absoluteState(evaluationContext)};
        }
        return And.and(evaluationContext, expressions);
    }

    public Expression stateUpTo(EvaluationContext evaluationContext, int recursions) {
        Expression[] expressions;
        if (parent == null) {
            expressions = new Expression[]{state};
        } else if (recursions == 0) {
            expressions = new Expression[]{condition};
        } else {
            expressions = new Expression[]{condition, state, parent.stateUpTo(evaluationContext, recursions - 1)};
        }
        return And.and(evaluationContext, expressions);
    }

    /**
     * computes a value in the context of the current condition manager.
     *
     * @return a value without the precondition attached
     */
    public Expression evaluate(EvaluationContext evaluationContext, Expression value) {
        return evaluate(evaluationContext, value, false);
    }

    public Expression evaluate(EvaluationContext evaluationContext, Expression value, boolean negate) {
        Expression absoluteState = absoluteState(evaluationContext);
        if (absoluteState.isUnknown() || value.isUnknown()) throw new UnsupportedOperationException();
        /*
        check on true: no state, so don't do anything
         */
        boolean reallyNegate = negate && !absoluteState.isBoolValueTrue();
        Expression negated = reallyNegate
                ? Negation.negate(evaluationContext, absoluteState)
                : absoluteState;

        Expression combinedWithPrecondition;
        if (precondition.isEmpty()) {
            combinedWithPrecondition = negated;
        } else {
            combinedWithPrecondition = And.and(evaluationContext, negated, precondition.expression());
        }

        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Expression resultWithPrecondition = And.and(evaluationContext, combinedWithPrecondition, value);
        if (resultWithPrecondition.equals(combinedWithPrecondition)) {
            // constant true: adding the value has no effect at all
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }
        // return the result without precondition
        return reallyNegate ? Or.or(evaluationContext, negated, value) : And.and(evaluationContext, negated, value);
    }


    private static Expression combine(EvaluationContext evaluationContext, Expression e1, Expression e2) {
        Objects.requireNonNull(e2);
        if (e1.isUnknown() || e2.isUnknown()) throw new UnsupportedOperationException();
        return And.and(evaluationContext, e1, e2);
    }

    /**
     * Extract NOT_NULL properties from the current absolute state, seen as a disjunction (filter mode REJECT)
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullInCondition(EvaluationContext evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(condition, evaluationContext, Filter.FilterMode.REJECT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current absolute state, seen as a conjunction (filter mode ACCEPT)
     *
     * @return individual variables that appear in the conjunction as variable == null
     */
    public Set<Variable> findIndividualNullInState(EvaluationContext evaluationContext, boolean requireEqualsNull) {
        Expression absoluteState = absoluteState(evaluationContext);
        return findIndividualNull(absoluteState, evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);

    }

    public Set<Variable> findIndividualNullInPrecondition(EvaluationContext evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(precondition.expression(), evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level conjunction or disjunction as variable == null
     */
    private static Set<Variable> findIndividualNull(Expression value,
                                                    EvaluationContext evaluationContext,
                                                    Filter.FilterMode filterMode,
                                                    boolean requireEqualsNull) {
        if (value.isUnknown()) {
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
    public Expression precondition(EvaluationContext evaluationContext) {
        Expression absoluteState = absoluteState(evaluationContext);
        if (absoluteState.isUnknown()) throw new UnsupportedOperationException();
        Expression negated = Negation.negate(evaluationContext, absoluteState);

        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<ParameterInfo> filterResult = filter.filter(negated, filter.individualNullOrNotNullClauseOnParameter());
        // those parts that have nothing to do with individual clauses
        return filterResult.rest();
    }

    private static Filter.FilterResult<Variable> obtainVariableFilter(Expression defaultRest, Variable variable, Expression value) {
        List<Variable> variables = value.variables();
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Filter.FilterResult<>(Map.of(variable, value), defaultRest);
        }
        return null;
    }

    /*
    any info there is about this variable
     */
    public Expression individualStateInfo(EvaluationContext evaluationContext, Variable variable) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Expression absoluteState = absoluteState(evaluationContext);
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
        return isDelayed() || parent != null && parent.isDelayed();
    }

    public CausesOfDelay stateDelayedOrPreconditionDelayed() {
        return stateIsDelayed.merge(preconditionIsDelayed);
    }

    public CausesOfDelay causesOfDelay() {
        return conditionIsDelayed.merge(stateIsDelayed).merge(preconditionIsDelayed);
    }

    public static record EvaluationContextImpl(AnalyserContext analyserContext) implements EvaluationContext {

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Primitives getPrimitives() {
            return analyserContext.getPrimitives();
        }

        @Override
        public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
            return false;
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable);
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            return LinkedVariables.EMPTY;
        }
    }
}

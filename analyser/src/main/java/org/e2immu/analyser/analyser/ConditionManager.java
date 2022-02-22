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

import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
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
                               Expression state,
                               Precondition precondition,
                               ConditionManager parent) {

    private static final ConditionManager SPECIAL = new ConditionManager();

    public static final int LIMIT_ON_COMPLEXITY = 200;

    private ConditionManager() {
        this(UnknownExpression.forSpecial(), UnknownExpression.forSpecial(), new Precondition(UnknownExpression.forSpecial(), List.of()), null);
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
        return new ConditionManager(TRUE, TRUE,
                Precondition.empty(TRUE), null);
    }

    public static ConditionManager impossibleConditionManager(Primitives primitives) {
        BooleanConstant FALSE = new BooleanConstant(primitives, true);
        return new ConditionManager(FALSE, FALSE,
                new Precondition(FALSE, List.of()), null);
    }

    /*
    adds a new layer (parent this)
     */
    public ConditionManager newAtStartOfNewBlock(Primitives primitives,
                                                 Expression condition,
                                                 Precondition precondition) {
        return new ConditionManager(condition, new BooleanConstant(primitives, true), precondition, this);
    }


    /* does not add a new layer */
    public ConditionManager replaceState(Expression state) {
        return new ConditionManager(condition, state, precondition, parent);
    }

    /*
    we guarantee a parent so that the condition counts!
     */
    public ConditionManager withCondition(EvaluationResult context, Expression switchCondition) {
        return new ConditionManager(combine(context, condition, switchCondition),
                state, precondition, this);
    }

    /*
    adds a new layer (parent this)
    */
    public ConditionManager newAtStartOfNewBlockDoNotChangePrecondition(Primitives primitives, Expression condition) {
        return new ConditionManager(condition,
                new BooleanConstant(primitives, true),
                precondition, this);
    }

    /*
    adds a new layer (parent this)
    */
    public ConditionManager addState(Expression state) {
        return new ConditionManager(condition, state, precondition, this);
    }

    /*
    stays at the same level (parent parent)
     */
    public ConditionManager withPrecondition(Precondition combinedPrecondition) {
        return new ConditionManager(condition, state, combinedPrecondition, parent);
    }

    /*
    stays at the same level
     */
    public ConditionManager withoutState(Primitives primitives) {
        return new ConditionManager(condition, new BooleanConstant(primitives, true), precondition, parent);
    }

    /*
    stays at the same level (parent parent)
     */
    public ConditionManager newForNextStatementDoNotChangePrecondition(EvaluationResult evaluationContext,
                                                                       Expression addToState) {
        Objects.requireNonNull(addToState);
        if (addToState.isBoolValueTrue()) return this;
        Expression newState = combine(evaluationContext, state, addToState);
        return new ConditionManager(condition, newState, precondition, parent);
    }

    public Expression absoluteState(EvaluationResult evaluationContext) {
        Expression[] expressions;
        int complexity;
        if (parent == null) {
            expressions = new Expression[]{state};
            complexity = state.getComplexity();
        } else {
            Expression parentAbsolute = parent.absoluteState(evaluationContext);
            expressions = new Expression[]{condition, state, parentAbsolute};
            complexity = condition.getComplexity() + state.getComplexity() + parentAbsolute.getComplexity();
        }
        if (complexity > LIMIT_ON_COMPLEXITY) {
            return Instance.forTooComplex(Identifier.generate("too complex CM 1"), evaluationContext.getPrimitives().booleanParameterizedType());
        }
        return And.and(evaluationContext, expressions);
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

    /**
     * computes a value in the context of the current condition manager.
     *
     * @return a value without the precondition attached
     */
    public Expression evaluate(EvaluationResult context, Expression value) {
        return evaluate(context, value, false);
    }

    public Expression evaluate(EvaluationResult context, Expression value, boolean negate) {
        assert value.returnType().isBooleanOrBoxedBoolean() : "Got " + value.getClass() + ", type " + value.returnType();

        Expression absoluteState = absoluteState(context);
        if (absoluteState.isEmpty() || value.isEmpty()) throw new UnsupportedOperationException();
        /*
        check on true: no state, so don't do anything
         */
        boolean reallyNegate = negate && !absoluteState.isBoolValueTrue();
        Expression negated = reallyNegate
                ? Negation.negate(context, absoluteState)
                : absoluteState;

        Expression combinedWithPrecondition;
        if (precondition.isEmpty()) {
            combinedWithPrecondition = negated;
        } else {
            combinedWithPrecondition = And.and(context, negated, precondition.expression());
        }
        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Expression resultWithPrecondition = And.and(context, combinedWithPrecondition, value);
        if (resultWithPrecondition.equals(combinedWithPrecondition)) {
            // constant true: adding the value has no effect at all
            return new BooleanConstant(context.getPrimitives(), true);
        }
        // return the result without precondition
        return reallyNegate ? Or.or(context, negated, value) : And.and(context, negated, value);
    }


    private static Expression combine(EvaluationResult evaluationContext, Expression e1, Expression e2) {
        Objects.requireNonNull(e2);
        if (e1.isEmpty() || e2.isEmpty()) throw new UnsupportedOperationException();
        int complexity = e1.getComplexity() + e2.getComplexity();
        if (complexity > LIMIT_ON_COMPLEXITY) {
            return Instance.forTooComplex(Identifier.generate("too complex CM 2"),
                    evaluationContext.getPrimitives().booleanParameterizedType());
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
            state = absoluteState(context);
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
        Expression absoluteState = absoluteState(evaluationContext);
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

    public ConditionManager removeDelaysOn(Primitives primitives, Set<FieldInfo> fieldsWithBreakDelay) {
        Expression c = removeDelaysOn(primitives, fieldsWithBreakDelay, condition);
        Expression s = removeDelaysOn(primitives, fieldsWithBreakDelay, state);
        Expression pc = removeDelaysOn(primitives, fieldsWithBreakDelay, precondition.expression());
        return new ConditionManager(c, s, pc.equals(precondition.expression()) ? precondition : new Precondition(pc, precondition.causes()), SPECIAL);
    }

    private Expression removeDelaysOn(Primitives primitives, Set<FieldInfo> fieldsWithBreakDelay, Expression expression) {
        if (expression.isDelayed()) {
            CausesOfDelay causes = expression.causesOfDelay();
            if (causes.causesStream().anyMatch(cause -> cause instanceof VariableCause vc && vc.variable() instanceof FieldReference fr && fieldsWithBreakDelay.contains(fr.fieldInfo))) {
                return new BooleanConstant(primitives, true);
            }
        }
        return expression;
    }

    public ConditionManager removeFromState(EvaluationResult evaluationContext, Set<Variable> variablesAssigned) {
        Primitives primitives = evaluationContext.getPrimitives();
        Expression withoutNegation = state instanceof Negation negation ? negation.expression : state;
        if (withoutNegation instanceof Equals equals && !Collections.disjoint(equals.variables(true), variablesAssigned)) {
            return new ConditionManager(condition, new BooleanConstant(primitives, true), precondition, parent);
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

    public record EvaluationContextImpl(AnalyserContext analyserContext) implements EvaluationContext {

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
        public Expression currentValue(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable);
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            return LinkedVariables.EMPTY;
        }
    }
}

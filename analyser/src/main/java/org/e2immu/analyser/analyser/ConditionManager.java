package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Collectors;

public class ConditionManager {

    public static final ConditionManager INITIAL = new ConditionManager(EmptyExpression.EMPTY_EXPRESSION, EmptyExpression.EMPTY_EXPRESSION);
    public static final ConditionManager DELAYED = new ConditionManager(EmptyExpression.NO_VALUE, EmptyExpression.NO_VALUE);

    public final Expression condition;
    public final Expression state;

    public ConditionManager() {
        this(EmptyExpression.EMPTY_EXPRESSION, EmptyExpression.EMPTY_EXPRESSION);
    }

    public ConditionManager(Expression condition, Expression state) {
        this.condition = checkBoolean(Objects.requireNonNull(condition));
        this.state = checkBoolean(Objects.requireNonNull(state));
    }

    private static Expression checkBoolean(Expression v) {
        if (v != EmptyExpression.EMPTY_EXPRESSION && v != EmptyExpression.NO_VALUE
                && (v.returnType() == null || Primitives.isNotBooleanOrBoxedBoolean(v.returnType()))) {
            throw new UnsupportedOperationException("Need a boolean value in the condition manager; got " + v);
        }
        return v;
    }

    // adding a condition always adds to the state as well (testing only)
    public ConditionManager addCondition(EvaluationContext evaluationContext, Expression value) {
        if (value == null || value == EmptyExpression.EMPTY_EXPRESSION) return this;
        if (value.isBoolValueTrue()) return this;
        if (value.isBoolValueFalse()) return new ConditionManager(value, value);
        return new ConditionManager(combineWithCondition(evaluationContext, value), combineWithState(evaluationContext, value));
    }

    public ConditionManager addToState(EvaluationContext evaluationContext, Expression value) {
        if (value.isBoolValueTrue()) return this;
        if (value.isBoolValueFalse()) return new ConditionManager(value, value);
        return new ConditionManager(condition, combineWithState(evaluationContext, value));
    }

    /**
     * Used in evaluation of the `if` statement's expression, to obtain the 'real' restriction.
     *
     * @param value the restriction given by the program
     * @return the computed, real restriction
     */
    public Expression evaluateWithCondition(EvaluationContext evaluationContext, Expression value) {
        return evaluateWith(evaluationContext, condition, value);
    }

    public Expression evaluateWithState(EvaluationContext evaluationContext, Expression value) {
        return evaluateWith(evaluationContext, state, value);
    }

    private static Expression evaluateWith(EvaluationContext evaluationContext, Expression condition, Expression value) {
        if (condition == EmptyExpression.EMPTY_EXPRESSION) return value; // allow to go delayed
        // one delayed, all delayed
        if (isDelayed(condition) || value == EmptyExpression.NO_VALUE) return EmptyExpression.NO_VALUE;

        // we take the condition as a given, and see if the value agrees

        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Expression result = new And(evaluationContext.getPrimitives(), value.getObjectFlow())
                .append(evaluationContext, condition, value);
        if (result.equals(condition)) {
            // constant true: adding the value has no effect at all
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }
        return result;
    }

    public Expression combineWithCondition(EvaluationContext evaluationContext, Expression value) {
        return combineWith(evaluationContext, condition, value);
    }

    public Expression combineWithState(EvaluationContext evaluationContext, Expression value) {
        return combineWith(evaluationContext, state, value);
    }

    public static Expression combineWith(EvaluationContext evaluationContext, Expression condition, Expression value) {
        Objects.requireNonNull(value);
        if (condition == EmptyExpression.EMPTY_EXPRESSION) return value;
        if (value == EmptyExpression.EMPTY_EXPRESSION) return condition;
        if (isDelayed(condition) || isDelayed(value)) return EmptyExpression.NO_VALUE;
        return new And(evaluationContext.getPrimitives(), value.getObjectFlow())
                .append(evaluationContext, condition, value);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullInCondition(EvaluationContext evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(condition, evaluationContext, Filter.FilterMode.REJECT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullInState(EvaluationContext evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(state, evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);

    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    private static Set<Variable> findIndividualNull(Expression value, EvaluationContext evaluationContext, Filter.FilterMode filterMode, boolean requireEqualsNull) {
        if (value == EmptyExpression.EMPTY_EXPRESSION || isDelayed(value)) {
            return Set.of();
        }
        Map<Variable, Expression> individualNullClauses = Filter.filter(evaluationContext, value, filterMode, Filter.INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE).accepted();
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> requireEqualsNull == (e.getValue() == NullConstant.NULL_CONSTANT))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public boolean haveNonEmptyState() {
        return state != EmptyExpression.EMPTY_EXPRESSION;
    }

    // we need a system to be able to delay conditionals, when they are based on the value of fields
    public boolean delayedCondition() {
        return isDelayed(condition);
    }

    public boolean notInDelayedState() {
        return !isDelayed(state);
    }

    static boolean isDelayed(Expression value) {
        return value == EmptyExpression.NO_VALUE;
    }

    public boolean inErrorState(Primitives primitives) {
        return new BooleanConstant(primitives, false).equals(state);
    }

    // used in assignments (it gets a new value, so whatever was known, must go)
    public ConditionManager variableReassigned(EvaluationContext evaluationContext, Variable variable) {
        return new ConditionManager(condition, removeClausesInvolving(evaluationContext, state, variable, true));
    }

    // after a modifying method call, we lose whatever we know about this variable; except assignment!
    public ConditionManager modifyingMethodAccess(EvaluationContext evaluationContext, Variable variable) {
        return new ConditionManager(condition, removeClausesInvolving(evaluationContext, state, variable, false));
    }

    private static Filter.FilterResult<Variable> removeVariableFilter(Variable variable, Expression value, boolean removeEqualityOnVariable) {
        VariableExpression variableValue;
        if ((variableValue = value.asInstanceOf(VariableExpression.class)) != null && variable.equals(variableValue.variable())) {
            return new Filter.FilterResult<>(Map.of(variable, value), EmptyExpression.EMPTY_EXPRESSION);
        }
        Equals equalsValue;
        if (removeEqualityOnVariable && (equalsValue = value.asInstanceOf(Equals.class)) != null) {
            VariableExpression lhs;
            if ((lhs = equalsValue.lhs.asInstanceOf(VariableExpression.class)) != null && variable.equals(lhs.variable())) {
                return new Filter.FilterResult<>(Map.of(lhs.variable(), value), EmptyExpression.EMPTY_EXPRESSION);
            }
            VariableExpression rhs;
            if ((rhs = equalsValue.rhs.asInstanceOf(VariableExpression.class)) != null && variable.equals(rhs.variable())) {
                return new Filter.FilterResult<>(Map.of(rhs.variable(), value), EmptyExpression.EMPTY_EXPRESSION);
            }
        }
        if (value.isInstanceOf(MethodCall.class) && value.variables().contains(variable)) {
            return new Filter.FilterResult<>(Map.of(variable, value), EmptyExpression.EMPTY_EXPRESSION);
        }
        return null;
    }

    /**
     * null-clauses like if(a==null) a = ... (then the null-clause on a should go)
     * same applies to size()... if(a.isEmpty()) a = ...
     *
     * @param conditional              the conditional from which we need to remove clauses
     * @param variable                 the variable to be removed
     * @param removeEqualityOnVariable in the case of modifying method access, clauses with equality should STAY rather than be removed
     */
    public static Expression removeClausesInvolving(EvaluationContext evaluationContext,
                                                    Expression conditional, Variable variable, boolean removeEqualityOnVariable) {
        Filter.FilterResult<Variable> filterResult = Filter.filter(evaluationContext, conditional, Filter.FilterMode.ALL,
                value -> removeVariableFilter(variable, value, removeEqualityOnVariable));
        return filterResult.rest();
    }

    // return that part of the conditional that is NOT covered by @NotNull (individual not null clauses) or @Size (individual size clauses)
    public EvaluationResult escapeCondition(EvaluationContext evaluationContext) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        if (condition == EmptyExpression.EMPTY_EXPRESSION || delayedCondition()) {
            return builder.setExpression(EmptyExpression.EMPTY_EXPRESSION).build();
        }

        // TRUE: parameters only FALSE: preconditionSide; OR of 2 filters
        Filter.FilterResult<ParameterInfo> filterResult = Filter.filter(evaluationContext, condition,
                Filter.FilterMode.REJECT, Filter.INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE_ON_PARAMETER);
        // those parts that have nothing to do with individual clauses
        if (filterResult.rest() == EmptyExpression.EMPTY_EXPRESSION) {
            return builder.setExpression(EmptyExpression.EMPTY_EXPRESSION).build();
        }
        // replace all VariableValues in the rest by VVPlaceHolders
        Map<Expression, Expression> translation = new HashMap<>();
        filterResult.rest().visit(v -> {
            VariableExpression variableValue;
            if ((variableValue = v.asInstanceOf(VariableExpression.class)) != null) {
                // null evalContext -> do not copy properties (the condition+state may hold a not null, which can
                // be copied in the property, which can reEvaluate later to constant true/false
                Variable variable = variableValue.variable();
                translation.put(v, new VariableExpression(variable, ObjectFlow.NO_FLOW));
            }
            return true;
        });

        // and negate. This will become the precondition or "initial state"
        EvaluationResult reRest = filterResult.rest().reEvaluate(evaluationContext, translation);
        return builder.compose(reRest).setExpression(Negation.negate(evaluationContext, reRest.value)).build();
    }

    private static Filter.FilterResult<Variable> obtainVariableFilter(Variable variable, Expression value) {
        List<Variable> variables = value.variables();
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Filter.FilterResult<>(Map.of(variable, value), EmptyExpression.EMPTY_EXPRESSION);
        }
        return null;
    }

    // note: very similar to remove, except that here we're interested in the actual value
    public Expression individualStateInfo(EvaluationContext evaluationContext, Variable variable) {
        Filter.FilterResult<Variable> filterResult = Filter.filter(evaluationContext, state, Filter.FilterMode.ACCEPT,
                value -> obtainVariableFilter(variable, value));
        return filterResult.accepted().getOrDefault(variable, EmptyExpression.EMPTY_EXPRESSION);
    }
}

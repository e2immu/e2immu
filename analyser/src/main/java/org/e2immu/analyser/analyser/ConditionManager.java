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

In a recursion of inline conditionals, the state remains true, and the condition equals the condition of each inline.

 */
public record ConditionManager(Expression condition, Expression state, ConditionManager parent) {

    public ConditionManager {
        checkBooleanOrUnknown(Objects.requireNonNull(condition));
        checkBooleanOrUnknown(Objects.requireNonNull(state));
    }

    /*
    EMPTY -> some value, no clue which one, we'll never know
    NO_VALUE -> delay
     */
    private static void checkBooleanOrUnknown(Expression v) {
        if (!v.isUnknown() && Primitives.isNotBooleanOrBoxedBoolean(v.returnType())) {
            throw new UnsupportedOperationException("Need an unknown or boolean value in the condition manager; got " + v);
        }
    }

    public static final ConditionManager DELAYED = new ConditionManager(EmptyExpression.NO_VALUE, EmptyExpression.NO_VALUE, null);

    public static ConditionManager initialConditionManager(Primitives primitives) {
        return new ConditionManager(new BooleanConstant(primitives, true),
                new BooleanConstant(primitives, true), null);
    }

    public ConditionManager newAtStartOfNewBlock(Primitives primitives, Expression condition) {
        return new ConditionManager(condition, new BooleanConstant(primitives, true), this);
    }

    public ConditionManager newForNextStatement(EvaluationContext evaluationContext, Expression addToState) {
        Objects.requireNonNull(addToState);
        if (addToState.isBoolValueTrue()) return this;
        return new ConditionManager(condition, combine(evaluationContext, state, addToState), parent);
    }

    public Expression absoluteState(EvaluationContext evaluationContext) {
        Expression[] expressions;
        if (parent == null) {
            expressions = new Expression[]{state};
        } else {
            expressions = new Expression[]{condition, state, parent.absoluteState(evaluationContext)};
        }
        return new And(evaluationContext.getPrimitives()).append(evaluationContext, expressions);
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
        return new And(evaluationContext.getPrimitives()).append(evaluationContext, expressions);
    }

    /**
     * Evaluate an expression in the context of the absolute state.
     *
     * @param value the expression to be evaluated
     * @return The boolean constant 'true' if adding the expression to the state does not change the state; an unknown
     * value if either is unknown, or the combined expression if the expression is not true given the state. The
     * latter can be the boolean constant 'false' if an inconsistency is reported.
     */
    public Expression evaluate(EvaluationContext evaluationContext, Expression value) {
        Expression absoluteState = absoluteState(evaluationContext);
        if (absoluteState.isUnknown() || value.isUnknown())
            return absoluteState.combineUnknown(value); // allow to go delayed

        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Expression result = new And(evaluationContext.getPrimitives(), value.getObjectFlow())
                .append(evaluationContext, absoluteState, value);
        if (result.equals(absoluteState)) {
            // constant true: adding the value has no effect at all
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }
        return result;
    }

    private static Expression combine(EvaluationContext evaluationContext, Expression e1, Expression e2) {
        Objects.requireNonNull(e2);
        if (e1.isUnknown() || e2.isUnknown()) return e1.combineUnknown(e2); // allow to go delayed
        return new And(evaluationContext.getPrimitives(), e2.getObjectFlow()).append(evaluationContext, e1, e2);
    }

    /**
     * Extract NOT_NULL properties from the current absolute state, seen as a disjunction (filter mode REJECT)
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullInCondition(EvaluationContext evaluationContext, boolean requireEqualsNull) {
        Expression absoluteState = absoluteState(evaluationContext);
        return findIndividualNull(absoluteState, evaluationContext, Filter.FilterMode.REJECT, requireEqualsNull);
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

    public boolean isDelayed() {
        if (condition == EmptyExpression.NO_VALUE || state == EmptyExpression.NO_VALUE) return true;
        if (parent == null) return false;
        return parent.isDelayed();
    }

    private static Filter.FilterResult<Variable> removeVariableFilter(Expression defaultRest,
                                                                      Variable variable,
                                                                      Expression value,
                                                                      boolean removeEqualityOnVariable) {
        VariableExpression variableValue;
        if ((variableValue = value.asInstanceOf(VariableExpression.class)) != null && variable.equals(variableValue.variable())) {
            return new Filter.FilterResult<>(Map.of(variable, value), defaultRest);
        }
        Equals equalsValue;
        if (removeEqualityOnVariable && (equalsValue = value.asInstanceOf(Equals.class)) != null) {
            VariableExpression lhs;
            if ((lhs = equalsValue.lhs.asInstanceOf(VariableExpression.class)) != null && variable.equals(lhs.variable())) {
                return new Filter.FilterResult<>(Map.of(lhs.variable(), value), defaultRest);
            }
            VariableExpression rhs;
            if ((rhs = equalsValue.rhs.asInstanceOf(VariableExpression.class)) != null && variable.equals(rhs.variable())) {
                return new Filter.FilterResult<>(Map.of(rhs.variable(), value), defaultRest);
            }
        }
        if (value.isInstanceOf(MethodCall.class) && value.variables().contains(variable)) {
            return new Filter.FilterResult<>(Map.of(variable, value), defaultRest);
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
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ALL);
        Filter.FilterResult<Variable> filterResult = filter.filter(conditional,
                value -> removeVariableFilter(filter.getDefaultRest(), variable, value, removeEqualityOnVariable));
        return filterResult.rest();
    }

    // return that part of the conditional that is NOT covered by @NotNull (individual not null clauses)
    public EvaluationResult escapeCondition(EvaluationContext evaluationContext) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        Expression absoluteState = absoluteState(evaluationContext);

        if (absoluteState.isUnknown()) {
            return builder.setExpression(absoluteState).build();
        }

        // TRUE: parameters only FALSE: preconditionSide; OR of 2 filters
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.REJECT);
        Filter.FilterResult<ParameterInfo> filterResult = filter.filter(absoluteState, filter.individualNullOrNotNullClauseOnParameter());
        // those parts that have nothing to do with individual clauses
        if (filterResult.rest().isBoolValueFalse()) {
            return builder.setExpression(filterResult.rest()).build();
        }
        Expression rest = filterResult.rest();
        Expression negatedRest = Negation.negate(new EvaluationContextImpl(evaluationContext.getPrimitives()), rest);
        return builder.setExpression(negatedRest).build();
    }

    private static Filter.FilterResult<Variable> obtainVariableFilter(Expression defaultRest, Variable variable, Expression value) {
        List<Variable> variables = value.variables();
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Filter.FilterResult<>(Map.of(variable, value), defaultRest);
        }
        return null;
    }

    // note: very similar to remove, except that here we're interested in the actual value
    public Expression individualStateInfo(EvaluationContext evaluationContext, Variable variable) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Expression absoluteState = absoluteState(evaluationContext);
        Filter.FilterResult<Variable> filterResult = filter.filter(absoluteState,
                value -> obtainVariableFilter(filter.getDefaultRest(), variable, value));
        return filterResult.accepted().getOrDefault(variable, filter.getDefaultRest());
    }


    public static record EvaluationContextImpl(Primitives primitives) implements EvaluationContext {

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public boolean isNotNull0(Expression value) {
            return false;
        }
    }
}

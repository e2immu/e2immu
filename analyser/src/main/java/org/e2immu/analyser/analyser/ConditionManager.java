package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConditionManager {

    public static final ConditionManager INITIAL = new ConditionManager(UnknownValue.EMPTY, UnknownValue.EMPTY);
    public static final ConditionManager DELAYED = new ConditionManager(UnknownValue.NO_VALUE, UnknownValue.NO_VALUE);

    public final Value condition;
    public final Value state;

    public ConditionManager() {
        this(UnknownValue.EMPTY, UnknownValue.EMPTY);
    }

    public ConditionManager(Value condition, Value state) {
        this.condition = Objects.requireNonNull(condition);
        this.state = Objects.requireNonNull(state);
    }

    // adding a condition always adds to the state as well (testing only)
    public ConditionManager addCondition(EvaluationContext evaluationContext, Value value) {
        if (value == null || value == UnknownValue.EMPTY) return this;
        if (value != BoolValue.createTrue(evaluationContext.getPrimitives())) {
            return new ConditionManager(combineWithCondition(evaluationContext, value), combineWithState(evaluationContext, value));
        }
        return this;
    }

    public ConditionManager addToState(EvaluationContext evaluationContext, Value value) {
        if (!(value.isInstanceOf(BoolValue.class))) {
            return new ConditionManager(condition, combineWithState(evaluationContext, value));
        }
        return this;
    }

    /**
     * Used in evaluation of the `if` statement's expression, to obtain the 'real' restriction.
     *
     * @param value the restriction given by the program
     * @return the computed, real restriction
     */
    public Value evaluateWithCondition(EvaluationContext evaluationContext, Value value) {
        return evaluateWith(evaluationContext, condition, value);
    }

    public Value evaluateWithState(EvaluationContext evaluationContext, Value value) {
        return evaluateWith(evaluationContext, state, value);
    }

    private static Value evaluateWith(EvaluationContext evaluationContext, Value condition, Value value) {
        if (condition == UnknownValue.EMPTY) return value; // allow to go delayed
        // one delayed, all delayed
        if (isDelayed(condition) || value == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;

        // we take the condition as a given, and see if the value agrees

        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Value result = new AndValue(evaluationContext.getPrimitives(), value.getObjectFlow())
                .append(evaluationContext, condition, value);
        if (result.equals(condition)) {
            // constant true: adding the value has no effect at all
            return BoolValue.createTrue(evaluationContext.getPrimitives());
        }
        return result;
    }

    public Value combineWithCondition(EvaluationContext evaluationContext, Value value) {
        return combineWith(evaluationContext, condition, value);
    }

    public Value combineWithState(EvaluationContext evaluationContext, Value value) {
        return combineWith(evaluationContext, state, value);
    }

    public static Value combineWith(EvaluationContext evaluationContext, Value condition, Value value) {
        Objects.requireNonNull(value);
        if (condition == UnknownValue.EMPTY) return value;
        if (value == UnknownValue.EMPTY) return condition;
        if (isDelayed(condition) || isDelayed(value)) return UnknownValue.NO_VALUE;
        return new AndValue(evaluationContext.getPrimitives(), value.getObjectFlow())
                .append(evaluationContext, condition, value);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullConditions(EvaluationContext evaluationContext) {
        if (condition == UnknownValue.EMPTY || delayedCondition()) {
            return Set.of();
        }
        Map<Variable, Value> individualNullClauses = condition.filter(evaluationContext, Value.FilterMode.REJECT,
                Value::isIndividualNotNullClauseOnParameter).accepted;
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> e.getValue().isNotNull())
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findNotNullConditionsOnVariables(EvaluationContext evaluationContext) {
        if (condition == UnknownValue.EMPTY || delayedCondition()) {
            return Set.of();
        }
        Map<Variable, Value> individualNullClauses = condition.filter(evaluationContext, Value.FilterMode.REJECT,
                Value::isIndividualNotNullClauseOnParameter).accepted;
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> e.getValue().isInstanceOf(NullValue.class))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public Map<Variable, Value> findIndividualSizeRestrictionsInCondition(EvaluationContext evaluationContext) {
        return getIndividualSizeRestrictions(evaluationContext, condition, Value.FilterMode.REJECT, true);
    }

    public Map<Variable, Value> individualSizeRestrictions(EvaluationContext evaluationContext) {
        return getIndividualSizeRestrictions(evaluationContext, state, Value.FilterMode.ACCEPT, false);
    }

    private static Map<Variable, Value> getIndividualSizeRestrictions(
            EvaluationContext evaluationContext,
            Value base, Value.FilterMode filterMode, boolean parametersOnly) {
        if (base == null || isDelayed(base)) {
            return Map.of();
        }
        Map<Variable, Value> map = base.filter(evaluationContext, filterMode,
                parametersOnly ? val -> val.isIndividualSizeRestrictionOnParameter(evaluationContext) :
                        val -> val.isIndividualSizeRestriction(evaluationContext)).accepted;
        if (parametersOnly) {
            return map.entrySet().stream()
                    .filter(e -> e.getKey() instanceof ParameterInfo)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return map;
    }


    public boolean haveNonEmptyState() {
        return state != UnknownValue.EMPTY;
    }

    // we need a system to be able to delay conditionals, when they are based on the value of fields
    public boolean delayedCondition() {
        return isDelayed(condition);
    }

    public boolean notInDelayedState() {
        return !isDelayed(state);
    }

    static boolean isDelayed(Value value) {
        return value == UnknownValue.NO_VALUE;
    }

    public boolean inErrorState(Primitives primitives) {
        return BoolValue.createFalse(primitives).equals(state);
    }

    // used in assignments (it gets a new value, so whatever was known, must go)
    public ConditionManager variableReassigned(EvaluationContext evaluationContext, Variable variable) {
        return new ConditionManager(condition, removeClausesInvolving(evaluationContext, state, variable, true));
    }

    // after a modifying method call, we lose whatever we know about this variable; except assignment!
    public ConditionManager modifyingMethodAccess(EvaluationContext evaluationContext, Variable variable) {
        return new ConditionManager(condition, removeClausesInvolving(evaluationContext, state, variable, false));
    }

    private static Value.FilterResult removeVariableFilter(Variable variable, Value value, boolean removeEqualityOnVariable) {
        VariableValue variableValue;
        if ((variableValue = value.asInstanceOf(VariableValue.class)) != null && variable.equals(variableValue.variable)) {
            return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
        }
        EqualsValue equalsValue;
        if (removeEqualityOnVariable && (equalsValue = value.asInstanceOf(EqualsValue.class)) != null) {
            VariableValue lhs;
            if ((lhs = equalsValue.lhs.asInstanceOf(VariableValue.class)) != null && variable.equals(lhs.variable)) {
                return new Value.FilterResult(Map.of(lhs.variable, value), UnknownValue.EMPTY);
            }
            VariableValue rhs;
            if ((rhs = equalsValue.rhs.asInstanceOf(VariableValue.class)) != null && variable.equals(rhs.variable)) {
                return new Value.FilterResult(Map.of(rhs.variable, value), UnknownValue.EMPTY);
            }
        }
        if (value.isInstanceOf(MethodValue.class) && value.variables().contains(variable)) {
            return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
        }
        return new Value.FilterResult(Map.of(), value);
    }

    /**
     * null-clauses like if(a==null) a = ... (then the null-clause on a should go)
     * same applies to size()... if(a.isEmpty()) a = ...
     *
     * @param conditional              the conditional from which we need to remove clauses
     * @param variable                 the variable to be removed
     * @param removeEqualityOnVariable in the case of modifying method access, clauses with equality should STAY rather than be removed
     */
    public static Value removeClausesInvolving(EvaluationContext evaluationContext,
                                               Value conditional, Variable variable, boolean removeEqualityOnVariable) {
        Value.FilterResult filterResult = conditional.filter(evaluationContext, Value.FilterMode.ALL,
                value -> removeVariableFilter(variable, value, removeEqualityOnVariable));
        return filterResult.rest;
    }

    // return that part of the conditional that is NOT covered by @NotNull (individual not null clauses) or @Size (individual size clauses)
    public EvaluationResult escapeCondition(EvaluationContext evaluationContext) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        if (condition == UnknownValue.EMPTY || delayedCondition()) {
            return builder.setValue(UnknownValue.EMPTY).build();
        }

        // TRUE: parameters only FALSE: preconditionSide; OR of 2 filters
        Value.FilterResult filterResult = condition.filter(evaluationContext, Value.FilterMode.REJECT, Value::isIndividualNotNullClauseOnParameter,
                val -> val.isIndividualSizeRestrictionOnParameter(evaluationContext)); // those parts that have nothing to do with individual clauses
        if (filterResult.rest == UnknownValue.EMPTY) {
            return builder.setValue(UnknownValue.EMPTY).build();
        }
        // replace all VariableValues in the rest by VVPlaceHolders
        Map<Value, Value> translation = new HashMap<>();
        filterResult.rest.visit(v -> {
            VariableValue variableValue;
            if ((variableValue = v.asInstanceOf(VariableValue.class)) != null) {
                // null evalContext -> do not copy properties (the condition+state may hold a not null, which can
                // be copied in the property, which can reEvaluate later to constant true/false
                Variable variable = variableValue.variable;
                translation.put(v, new VariableValue(variable, ObjectFlow.NO_FLOW));
            }
        });

        // and negate. This will become the precondition or "initial state"
        EvaluationResult reRest = filterResult.rest.reEvaluate(evaluationContext, translation);
        return builder.compose(reRest).setValue(NegatedValue.negate(evaluationContext, reRest.value)).build();
    }

    private static Value.FilterResult obtainVariableFilter(Variable variable, Value value) {
        Set<Variable> variables = value.variables();
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
        }
        return new Value.FilterResult(Map.of(), value);
    }

    // note: very similar to remove, except that here we're interested in the actual value
    public Value individualStateInfo(EvaluationContext evaluationContext, Variable variable) {
        Value.FilterResult filterResult = state.filter(evaluationContext, Value.FilterMode.ACCEPT,
                value -> obtainVariableFilter(variable, value));
        return filterResult.accepted.getOrDefault(variable, UnknownValue.EMPTY);
    }
}

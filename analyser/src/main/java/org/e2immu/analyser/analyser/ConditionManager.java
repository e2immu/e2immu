package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConditionManager {

    public final Value condition;
    public final Value state;

    public ConditionManager(Value condition, Value state) {
        this.condition = Objects.requireNonNull(condition);
        this.state = Objects.requireNonNull(state);
    }

    // adding a condition always adds to the state as well (testing only)
    public ConditionManager addCondition(Value value) {
        if (value != BoolValue.TRUE) {
            return new ConditionManager(combineWithCondition(value), combineWithState(value));
        }
        return this;
    }

    public ConditionManager addToState(Value value) {
        if (!(value.isInstanceOf(BoolValue.class))) {
            return new ConditionManager(condition, combineWithState(value));
        }
        return this;
    }

    /**
     * Used in evaluation of the `if` statement's expression, to obtain the 'real' restriction.
     *
     * @param value the restriction given by the program
     * @return the computed, real restriction
     */
    public Value evaluateWithCondition(Value value) {
        return evaluateWith(condition, value);
    }

    public Value evaluateWithState(Value value) {
        return evaluateWith(state, value);
    }

    private static Value evaluateWith(Value condition, Value value) {
        if (condition == UnknownValue.EMPTY) return value; // allow to go delayed
        // one delayed, all delayed
        if (isDelayed(condition) || value == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;

        // we take the condition as a given, and see if the value agrees

        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Value result = new AndValue(value.getObjectFlow()).append(condition, value);
        if (result.equals(condition)) {
            // constant true: adding the value has no effect at all
            return BoolValue.TRUE;
        }
        return result;
    }

    public Value combineWithCondition(Value value) {
        return combineWith(condition, value);
    }

    public Value combineWithState(Value value) {
        return combineWith(state, value);
    }

    public static Value combineWith(Value condition, Value value) {
        Objects.requireNonNull(value);
        if (condition == UnknownValue.EMPTY) return value;
        if (value == UnknownValue.EMPTY) return condition;
        if (isDelayed(condition) || isDelayed(value)) return UnknownValue.NO_VALUE;
        return new AndValue(value.getObjectFlow()).append(condition, value);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullConditions() {
        if (condition == UnknownValue.EMPTY || delayedCondition()) {
            return Set.of();
        }
        Map<Variable, Value> individualNullClauses = condition.filter(Value.FilterMode.REJECT,
                Value::isIndividualNotNullClauseOnParameter).accepted;
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> e.getValue().isInstanceOf(NullValue.class))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * Conversion from state to notNull numeric property value, combined with the existing state
     *
     * @param value any non-constant, non-variable value, to be combined with the current state
     * @return the numeric VariableProperty.NOT_NULL value
     */
    public int notNull(Value value) {
        if (state == UnknownValue.EMPTY || isDelayed(state)) return Level.DELAY;

        // action: if we add value == null, and nothing changes, we know it is true, we rely on value.getProperty
        // if the whole thing becomes false, we know it is false, which means we can return Level.TRUE
        Value equalsNull = EqualsValue.equals(NullValue.NULL_VALUE, value, ObjectFlow.NO_FLOW);
        if (equalsNull == BoolValue.FALSE) return MultiLevel.EFFECTIVELY_NOT_NULL;
        Value withCondition = combineWithState(equalsNull);
        if (withCondition == BoolValue.FALSE) return MultiLevel.EFFECTIVELY_NOT_NULL; // we know != null
        if (withCondition.equals(equalsNull)) return MultiLevel.NULLABLE; // we know == null was already there
        return Level.DELAY;
    }

    public boolean isNotNull(Variable variable) {
        if (state == UnknownValue.EMPTY || isDelayed(state)) return false;

        VariableValue vv = new VariableValue(variable, variable.name(), Map.of(), Set.of(), ObjectFlow.NO_FLOW, false);
        return MultiLevel.isEffectivelyNotNull(notNull(vv));
    }

    public Map<Variable, Value> findIndividualSizeRestrictionsInCondition() {
        return getIndividualSizeRestrictions(condition, Value.FilterMode.REJECT, true);
    }

    public Map<Variable, Value> individualSizeRestrictions() {
        return getIndividualSizeRestrictions(state, Value.FilterMode.ACCEPT, false);
    }

    private static Map<Variable, Value> getIndividualSizeRestrictions(Value base, Value.FilterMode filterMode, boolean parametersOnly) {
        if (base == null || isDelayed(base)) {
            return Map.of();
        }
        Map<Variable, Value> map = base.filter(filterMode,
                parametersOnly ? Value::isIndividualSizeRestrictionOnParameter : Value::isIndividualSizeRestriction).accepted;
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

    public boolean delayedState() {
        return isDelayed(state);
    }

    private static boolean isDelayed(Value value) {
        return value == UnknownValue.NO_VALUE;
    }

    public boolean inErrorState() {
        return BoolValue.FALSE == state;
    }

    // used in assignments (it gets a new value, so whatever was known, must go)
    public ConditionManager variableReassigned(Variable variable) {
        return new ConditionManager(condition, removeClausesInvolving(state, variable, true));
    }

    // after a modifying method call, we lose whatever we know about this variable; except assignment!
    public ConditionManager modifyingMethodAccess(Variable variable) {
        return new ConditionManager(condition, removeClausesInvolving(state, variable, false));
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
    public static Value removeClausesInvolving(Value conditional, Variable variable, boolean removeEqualityOnVariable) {
        Value.FilterResult filterResult = conditional.filter(Value.FilterMode.ALL,
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
        Value.FilterResult filterResult = condition.filter(Value.FilterMode.REJECT, Value::isIndividualNotNullClauseOnParameter,
                Value::isIndividualSizeRestrictionOnParameter); // those parts that have nothing to do with individual clauses
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
                // TODO why do we make a fresh copy?
                translation.put(v, new VariableValue(variable, variable.name(), Map.of(), Set.of(), v.getObjectFlow(), false));
            }
        });

        // and negate. This will become the precondition or "initial state"
        EvaluationResult reRest = filterResult.rest.reEvaluate(evaluationContext, translation);
        return builder.compose(reRest).setValue(NegatedValue.negate(reRest.value)).build();
    }

    private static Value.FilterResult obtainVariableFilter(Variable variable, Value value) {
        Set<Variable> variables = value.variables();
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
        }
        return new Value.FilterResult(Map.of(), value);
    }

    // note: very similar to remove, except that here we're interested in the actual value
    Value individualStateInfo(Variable variable) {
        Value.FilterResult filterResult = state.filter(Value.FilterMode.ACCEPT,
                value -> obtainVariableFilter(variable, value));
        return filterResult.accepted.getOrDefault(variable, UnknownValue.EMPTY);
    }
}

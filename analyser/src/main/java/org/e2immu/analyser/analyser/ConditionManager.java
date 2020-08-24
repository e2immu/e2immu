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

    private Value condition;
    private Value state;

    public ConditionManager(Value condition, Value state) {
        this.condition = Objects.requireNonNull(condition);
        this.state = Objects.requireNonNull(state);
    }

    public Value getCondition() {
        return condition;
    }

    public Value getState() {
        return state;
    }

    private void setState(Value state) {
        this.state = state;
    }

    // adding a condition always adds to the state as well (testing only)
    public void addCondition(Value value) {
        if (value != BoolValue.TRUE) {
            condition = combineWithCondition(value);
            setState(combineWithState(value));
        }
    }

    public void addToState(Value value) {
        if (value instanceof BoolValue) throw new UnsupportedOperationException();
        setState(combineWithState(value));
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

    private static Value combineWith(Value condition, Value value) {
        Objects.requireNonNull(value);
        if (condition == UnknownValue.EMPTY) return value;
        if (isDelayed(condition) || isDelayed(value)) return UnknownValue.NO_VALUE;
        return new AndValue(value.getObjectFlow()).append(condition, value);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level disjunction as variable == null
     */
    public Set<Variable> findIndividualNullConditions() {
        if (condition == null || delayedCondition()) {
            return Set.of();
        }
        Map<Variable, Value> individualNullClauses = condition.filter(Value.FilterMode.REJECT,
                Value::isIndividualNotNullClauseOnParameter).accepted;
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> e.getValue() instanceof NullValue)
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
        if (state == null || isDelayed(state)) return false;

        VariableValue vv = new VariableValue(null, variable, variable.name());
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
    public void variableReassigned(Variable variable) {
        setState(removeClausesInvolving(state, variable, true));
    }

    // after a modifying method call, we lose whatever we know about this variable; except assignment!
    public void modifyingMethodAccess(Variable variable) {
        setState(removeClausesInvolving(state, variable, false));
    }

    /**
     * null-clauses like if(a==null) a = ... (then the null-clause on a should go)
     * same applies to size()... if(a.isEmpty()) a = ...
     *
     * @param conditional              the conditional from which we need to remove clauses
     * @param variable                 the variable to be removed
     * @param removeEqualityOnVariable in the case of modifying method access, clauses with equality should STAY rather than be removed
     */
    private static Value removeClausesInvolving(Value conditional, Variable variable, boolean removeEqualityOnVariable) {
        Value.FilterResult filterResult = conditional.filter(Value.FilterMode.ALL, value -> {
            if (value instanceof ValueWithVariable && variable.equals(((ValueWithVariable) value).variable)) {
                return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
            }
            if (removeEqualityOnVariable && value instanceof EqualsValue) {
                EqualsValue equalsValue = (EqualsValue) value;
                if (equalsValue.lhs instanceof ValueWithVariable && variable.equals(((ValueWithVariable) equalsValue.lhs).variable)) {
                    return new Value.FilterResult(Map.of(((ValueWithVariable) equalsValue.lhs).variable, value), UnknownValue.EMPTY);
                }
                if (equalsValue.rhs instanceof ValueWithVariable && variable.equals(((ValueWithVariable) equalsValue.rhs).variable)) {
                    return new Value.FilterResult(Map.of(((ValueWithVariable) equalsValue.rhs).variable, value), UnknownValue.EMPTY);
                }
            }
            if (value instanceof MethodValue && value.variables().contains(variable)) {
                return new Value.FilterResult(Map.of(variable, value), UnknownValue.EMPTY);
            }
            return new Value.FilterResult(Map.of(), value);
        });
        return filterResult.rest;
    }

    // return that part of the conditional that is NOT covered by @NotNull (individual not null clauses) or @Size (individual size clauses)
    public Value escapeCondition(EvaluationContext evaluationContext) {
        if (condition == UnknownValue.EMPTY || delayedCondition()) return UnknownValue.EMPTY;

        // TRUE: parameters only FALSE: preconditionSide; OR of 2 filters
        Value.FilterResult filterResult = condition.filter(Value.FilterMode.REJECT, Value::isIndividualNotNullClauseOnParameter,
                Value::isIndividualSizeRestrictionOnParameter); // those parts that have nothing to do with individual clauses
        if (filterResult.rest == UnknownValue.EMPTY) return UnknownValue.EMPTY;

        // replace all VariableValues in the rest by VVPlaceHolders
        Map<Value, Value> translation = new HashMap<>();
        filterResult.rest.visit(v -> {
            if (v instanceof VariableValue) {
                translation.put(v, new VariableValuePlaceholder((VariableValue) v, evaluationContext, v.getObjectFlow()));
            }
        });

        // and negate. This will become the precondition or "initial state"
        return NegatedValue.negate(filterResult.rest.reEvaluate(evaluationContext, translation));
    }

}

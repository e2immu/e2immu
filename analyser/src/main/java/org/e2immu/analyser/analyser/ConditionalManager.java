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

public class ConditionalManager {

    private Value conditional;

    public ConditionalManager(Value conditional) {
        this.conditional = conditional;
    }

    public Value getConditional() {
        return conditional;
    }

    public void addToConditional(Value value) {
        if (value != BoolValue.TRUE) {
            conditional = combineWithConditional(value);
        }
    }

    public Value evaluateWithConditional(Value value) {
        if (conditional == null) return value; // allow to go delayed
        // one delayed, all delayed
        if (delayedConditional() || value == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;

        // we take the conditional as a given, and see if the value agrees

        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the conditional
        Value result = new AndValue(value.getObjectFlow()).append(conditional, value);
        if (result.equals(conditional)) {
            // constant true: adding the value has no effect at all
            return BoolValue.TRUE;
        }
        return result;
    }

    public Value combineWithConditional(Value value) {
        Objects.requireNonNull(value);
        if (conditional == null) return value;
        if (delayedConditional() || value == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;

        if (conditional instanceof AndValue) {
            return ((AndValue) conditional).append(value);
        }
        return new AndValue(value.getObjectFlow()).append(conditional, value);
    }

    public Set<Variable> getNullConditionals(boolean preconditionSide, boolean equalToNull, boolean parametersOnly) {
        if (conditional == null || delayedConditional()) {
            return Set.of();
        }
        Map<Variable, Boolean> individualNullClauses = conditional.individualNullClauses(preconditionSide);
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> (!parametersOnly || (e.getKey() instanceof ParameterInfo)) && e.getValue() == equalToNull)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public Map<Variable, Value> getSizeRestrictions(boolean preconditionSide, boolean parametersOnly) {
        if (conditional == null || delayedConditional()) return Map.of();
        Map<Variable, Value> map = conditional.individualSizeRestrictions(preconditionSide);
        if (parametersOnly) {
            return map.entrySet().stream()
                    .filter(e -> e.getKey() instanceof ParameterInfo)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return map;
    }

    public int notNull(Value value) {
        if (conditional == null || delayedConditional()) return Level.DELAY;

        // action: if we add value == null, and nothing changes, we know it is true, we rely on value.getProperty
        // if the whole thing becomes false, we know it is false, which means we can return Level.TRUE
        Value equalsNull = EqualsValue.equals(NullValue.NULL_VALUE, value, ObjectFlow.NO_FLOW);
        if (equalsNull == BoolValue.FALSE) return MultiLevel.EFFECTIVELY_NOT_NULL;
        Value withConditional = combineWithConditional(equalsNull);
        if (withConditional == BoolValue.FALSE) return MultiLevel.EFFECTIVELY_NOT_NULL; // we know != null
        if (withConditional.equals(equalsNull)) return MultiLevel.NULLABLE; // we know == null was already there
        return Level.DELAY;
    }

    public boolean isNotNull(Variable variable) {
        if (conditional == null || delayedConditional()) return false;

        VariableValue vv = new VariableValue(null, variable, variable.name());
        return MultiLevel.isEffectivelyNotNull(notNull(vv));
    }

    public boolean haveConditional() {
        return conditional != null;
    }

    // we need a system to be able to delay conditionals, when they are based on the value of fields
    public boolean delayedConditional() {
        return conditional == UnknownValue.NO_VALUE;
    }

    public boolean conditionalInErrorState() {
        return BoolValue.TRUE == conditional || BoolValue.FALSE == conditional;
    }

    // used in assignments (it gets a new value, so whatever was known, must go)
    // and in escapes (forget about the null condition, we have stored it in the property)
    public void variableReassigned(Variable variable) {
        if (conditional != null) conditional = removeClausesInvolving(conditional, variable, true);
    }

    public void modifyingMethodAccess(Variable variable) {
        if (conditional != null) conditional = removeClausesInvolving(conditional, variable, false);
    }

    // null-clauses like if(a==null) a = ... (then the null-clause on a should go)
    // same applies to size()... if(a.isEmpty()) a = ...

    private static Value removeClausesInvolving(Value conditional, Variable variable, boolean includeEqualityOnVariable) {
        Value toTest = conditional instanceof NegatedValue ? ((NegatedValue) conditional).value : conditional;

        // method call on the variable (a.isEmpty())
        if (toTest instanceof MethodValue && toTest.variables().contains(variable)) {
            return null;
        }
        //
        if (includeEqualityOnVariable && toTest instanceof EqualsValue && toTest.variables().contains(variable)) {
            return null;
        }
        if (conditional instanceof AndValue) {
            return ((AndValue) conditional).removeClausesInvolving(variable, includeEqualityOnVariable);
        }
        return conditional;
    }

    // return that part of the conditional that is NOT covered by @NotNull (individual not null clauses) or @Size (individual size clauses)
    public Value escapeCondition(EvaluationContext evaluationContext) {
        if (conditional == null || delayedConditional()) return null;
        Value pre = conditional.nonIndividualCondition(false, true); // those parts that have nothing to do with individual clauses
        if (pre == null) return null;
        Map<Value, Value> translation = new HashMap<>();
        pre.visit(v -> {
            if (v instanceof VariableValue) {
                translation.put(v, new VariableValuePlaceholder((VariableValue) v, evaluationContext, v.getObjectFlow()));
            }
        });
        return NegatedValue.negate(pre.reEvaluate(evaluationContext, translation));
    }

}

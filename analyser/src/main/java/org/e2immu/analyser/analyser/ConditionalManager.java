package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.EqualsValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
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
        if (conditional == null || conditional.isUnknown()) return value;
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
        if (value == null || value.isUnknown()) return conditional;
        if (conditional == null || conditional.isUnknown()) return value;

        if (conditional instanceof AndValue) {
            return ((AndValue) conditional).append(value);
        }
        return new AndValue(value.getObjectFlow()).append(conditional, value);
    }

    public Set<Variable> getNullConditionals(boolean equalToNull) {
        if (conditional == null) {
            return Set.of();
        }
        Map<Variable, Boolean> individualNullClauses = conditional.individualNullClauses();
        return individualNullClauses.entrySet()
                .stream().filter(e -> e.getValue() == equalToNull)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public Map<Variable, Value> getSizeRestrictions() {
        if (conditional == null) return Map.of();
        return conditional.individualSizeRestrictions();
    }

    public int notNull(Value value) {

        // TODO to which extent is this correct ?


        // action: if we add value == null, and nothing changes, we know it is true, we rely on value.getProperty
        // if the whole thing becomes false, we know it is false, which means we can return Level.TRUE
        Value equalsNull = EqualsValue.equals(NullValue.NULL_VALUE, value, null);
        if (equalsNull == BoolValue.FALSE) return Level.TRUE;
        Value withConditional = combineWithConditional(equalsNull);
        if (withConditional == BoolValue.FALSE) return Level.TRUE; // we know != null
        if (withConditional.equals(equalsNull)) return Level.FALSE; // we know == null
        return Level.DELAY;
    }

    // return the size restriction on value represented by the current condition
    public int sizeRestriction(Value value) {
        throw new UnsupportedOperationException();
    }


    public boolean isNotNull(Variable variable) {
        VariableValue vv = new VariableValue(null, variable, variable.name());
        return notNull(vv) == Level.TRUE;
    }

    public boolean haveConditional() {
        return conditional != null;
    }

    public boolean conditionalInErrorState() {
        return BoolValue.TRUE == conditional || BoolValue.FALSE == conditional;
    }

    // used in assignments (it gets a new value, so whatever was known, must go)
    // and in escapes (forget about the null condition, we have stored it in the property)
    public void variableReassigned(Variable at) {
        if (conditional != null) conditional = removeClausesInvolving(conditional, at);
    }

    // null-clauses like if(a==null) a = ... (then the null-clause on a should go)
    // same applies to size()... if(a.isEmpty()) a = ...
    private static Value removeClausesInvolving(Value conditional, Variable variable) {
        Value toTest = conditional instanceof NegatedValue ? ((NegatedValue) conditional).value : conditional;
        if (toTest instanceof EqualsValue && toTest.variables().contains(variable)) {
            return null;
        }
        if (conditional instanceof AndValue) {
            return ((AndValue) conditional).removeClausesInvolving(variable);
        }
        return conditional;
    }

    // return that part of the conditional that is NOT covered by @NotNull (individual not null clauses) or @Size (individual size clauses)
    public Value escapeCondition() {
        if (conditional == null) return null;
        Value pre = conditional.nonIndividualCondition(); // those parts that have nothing to do with individual clauses
        if (pre == null) return null;
        return NegatedValue.negate(pre);
    }
}

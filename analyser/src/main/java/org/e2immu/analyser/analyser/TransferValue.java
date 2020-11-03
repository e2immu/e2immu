package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnce;

import java.util.Set;

/**
 * an object purely used to transfer information between analysers
 * <p>
 * Will NOT be returned in evaluations.
 * It may be linked to a variable, but not inside the class itself.
 * It optionally holds a value, but properties are NOT taken from that value.
 */
public class TransferValue {

    public final SetOnce<Value> value = new SetOnce<>();
    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

    // two fields related to the computation of @Mark, @Only: we like to know if there is a current
    // conditional on the value; e.g. for field j, j > 0 when assigning this.j = j; so that we can compare with the eventual precondition.
    // we also need to know if this value is guaranteed to be executed (and does not sit inside an if statement, for example).

    public final SetOnce<Value> stateOnAssignment = new SetOnce<>();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    public final SetOnce<Set<Variable>> linkedVariables = new SetOnce<>();

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    public boolean isDelayed(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY) == Level.DELAY;
    }

    @Override
    public String toString() {
        return "TransferValue{" +
                "value=" + value +
                ", properties=" + properties +
                ", stateOnAssignment=" + stateOnAssignment +
                ", linkedVariables=" + linkedVariables +
                '}';
    }

    public TransferValue copy() {
        TransferValue copy = new TransferValue();
        copy.value.copy(value);
        copy.properties.putAll(properties);
        copy.stateOnAssignment.copy(stateOnAssignment);
        copy.linkedVariables.copy(linkedVariables);
        return copy;
    }

    public void merge(TransferValue other) {
        value.copyIfNotSet(other.value);
        stateOnAssignment.copyIfNotSet(other.stateOnAssignment);
        linkedVariables.copyIfNotSet(other.linkedVariables);
        properties.putAll(other.properties);
    }
}

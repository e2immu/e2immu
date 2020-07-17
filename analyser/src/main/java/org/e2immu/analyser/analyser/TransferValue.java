package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

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


    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    public final SetOnce<Set<Variable>> linkedVariables = new SetOnce<>();

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, Level.DELAY);
    }

    public boolean isDelayed(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, Level.DELAY) == Level.DELAY;
    }
}

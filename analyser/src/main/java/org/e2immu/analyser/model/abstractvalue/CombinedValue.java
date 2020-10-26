package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// a combined value is NOT a variable value. that means it should not be assigned to a variable
public class CombinedValue implements Value {

    public final List<Value> values;

    private CombinedValue(List<Value> values) {
        this.values = values;
    }

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    public static Value create(@Size(min = 1) List<Value> values) {
        if (values.isEmpty()) throw new UnsupportedOperationException();
        return new CombinedValue(ImmutableList.copyOf(values));
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return values.stream().mapToInt(value -> evaluationContext.getProperty(value, variableProperty)).min().orElse(Level.DELAY);
    }

    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public int order() {
        return ORDER_COMBINED;
    }

    @Override
    public int internalCompareTo(Value v) {
        return ListUtil.compare(values, ((CombinedValue) v).values);
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        Set<Variable> result = new HashSet<>();
        for (Value value : values) {
            Set<Variable> sub = evaluationContext.linkedVariables(value);
            if (sub == null) return null; // DELAY
            result.addAll(sub);
        }
        return result;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        values.forEach(v -> v.visit(consumer));
        consumer.accept(this);
    }
}

package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.annotation.Size;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (values.size() == 1) {
            Value value = values.get(0);
            if (value == UnknownValue.NO_VALUE) throw new UnsupportedOperationException();
            if (value.hasConstantProperties() || value instanceof CombinedValue) return value;
        }
        return new CombinedValue(ImmutableList.copyOf(values));
    }

    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public int compareTo(Value o) {
        return 0;
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return values.stream().flatMap(value -> value.linkedVariables(bestCase, evaluationContext).stream()).collect(Collectors.toSet());
    }
}

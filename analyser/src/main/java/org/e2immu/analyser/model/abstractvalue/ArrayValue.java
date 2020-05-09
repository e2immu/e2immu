package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;

import java.util.List;
import java.util.stream.Collectors;

public class ArrayValue implements Value {

    public final List<Value> values;

    public ArrayValue(List<Value> values) {
        this.values = ImmutableList.copyOf(values);
    }

    @Override
    public String asString() {
        return "{" + values.stream().map(Value::asString).collect(Collectors.joining(",")) + "}";
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof ArrayValue) {
            // TODO
        }
        return 0;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int leastOfValues = values.stream().mapToInt(v -> v.getPropertyOutsideContext(variableProperty))
                    .min().orElse(Level.UNDEFINED);
            int levelOfValues = Level.level(leastOfValues);
            return Level.compose(Level.TRUE, levelOfValues + 1); // default = @NotNull level 0
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int leastOfValues = values.stream().mapToInt(v -> v.getProperty(evaluationContext, variableProperty))
                    .min().orElse(Level.UNDEFINED);
            int levelOfValues = Level.level(leastOfValues);
            return Level.compose(Level.TRUE, levelOfValues + 1); // default = @NotNull level 0
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }
}
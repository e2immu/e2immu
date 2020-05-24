package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ArrayValue implements Value {

    public final Value combinedValue; // NO_VALUE when no values
    public final List<Value> values;

    public ArrayValue(List<Value> values) {
        this.values = ImmutableList.copyOf(values);
        combinedValue = values.isEmpty() ? UnknownValue.NO_VALUE : CombinedValue.create(values);
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
            int notNull = combinedValue.getPropertyOutsideContext(variableProperty);
            int levelOfValues = Level.level(notNull);
            return Level.compose(Level.TRUE, levelOfValues + 1); // default = @NotNull level 0
        }
        if (VariableProperty.SIZE == variableProperty) {
            return Analysis.encodeSizeEquals(values.size());
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNull = combinedValue.getProperty(evaluationContext, variableProperty);
            int levelOfValues = Level.level(notNull);
            return Level.compose(Level.TRUE, levelOfValues + 1); // default = @NotNull level 0
        }
        if (VariableProperty.SIZE == variableProperty) {
            return Analysis.encodeSizeEquals(values.size());
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    @Override
    public Set<Variable> variables() {
        return combinedValue.variables();
    }
}
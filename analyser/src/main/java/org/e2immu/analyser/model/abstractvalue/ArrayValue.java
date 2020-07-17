package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ArrayValue implements Value {

    public final Value combinedValue; // NO_VALUE when no values
    public final List<Value> values;
    public final ObjectFlow objectFlow;

    public ArrayValue(ObjectFlow objectFlow, List<Value> values) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.values = ImmutableList.copyOf(values);
        combinedValue = values.isEmpty() ? UnknownValue.NO_VALUE : CombinedValue.create(values);
    }

    @Override
    public String toString() {
        return "{" + values.stream().map(Value::toString).collect(Collectors.joining(",")) + "}";
    }

    @Override
    public int order() {
        return ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Value v) {
        return ListUtil.compare(values, ((ArrayValue) v).values);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNull = combinedValue.getPropertyOutsideContext(variableProperty);
            int levelOfValues = MultiLevel.level(notNull);
            int valueAtLevel = MultiLevel.value(notNull, levelOfValues);
            return MultiLevel.compose(valueAtLevel, levelOfValues + 1); // default = @NotNull level 0
        }
        if (VariableProperty.SIZE == variableProperty) {
            return Analysis.encodeSizeEquals(values.size());
        }
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNull = evaluationContext.getProperty(combinedValue, variableProperty);
            int levelOfValues = MultiLevel.level(notNull);
            int valueAtLevel = MultiLevel.value(notNull, levelOfValues);
            return MultiLevel.compose(valueAtLevel, levelOfValues + 1); // default = @NotNull level 0
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

    @Override
    public boolean isExpressionOfParameters() {
        return values.stream().allMatch(Value::isExpressionOfParameters);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayValue that = (ArrayValue) o;
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        values.forEach(v -> v.visit(consumer));
        consumer.accept(this);
    }
}
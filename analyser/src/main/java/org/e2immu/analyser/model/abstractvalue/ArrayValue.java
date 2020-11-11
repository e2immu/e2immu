package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// {1,2,3}, {a, b, {1,3,3}}, ...
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
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        List<EvaluationResult> reClauseERs = values.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        List<Value> reValues = reClauseERs.stream().map(er -> er.value).collect(Collectors.toList());
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setValue(new ArrayValue(objectFlow, reValues))
                .build();
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "{" + values.stream().map(v -> v.print(printMode)).collect(Collectors.joining(",")) + "}";
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
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNull = evaluationContext.getProperty(combinedValue, variableProperty);
            return MultiLevel.shift(MultiLevel.EFFECTIVE, notNull); // default = @NotNull level 0
        }
        if (VariableProperty.SIZE == variableProperty) {
            return Level.encodeSizeEquals(values.size());
        }
        // default is to refer to each of the components
        return evaluationContext.getProperty(combinedValue, variableProperty);
    }

    @Override
    public Set<Variable> variables() {
        return combinedValue.variables();
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
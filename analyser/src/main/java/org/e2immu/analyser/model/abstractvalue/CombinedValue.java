package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.Size;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CombinedValue implements Value {

    public final List<Value> values;
    public final Map<VariableProperty, List<Value>> alternativeList;

    private CombinedValue(List<Value> values, Map<VariableProperty, List<Value>> alternativeList) {
        this.values = values;
        this.alternativeList = alternativeList;
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
        return new CombinedValue(ImmutableList.copyOf(values), Map.of());
    }

    public static Value createNotNull(@Size(min = 1) List<Value> values, List<Value> notNullList) {
        if (values.isEmpty()) throw new UnsupportedOperationException();
        return new CombinedValue(ImmutableList.copyOf(values), Map.of(VariableProperty.NOT_NULL, notNullList));
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return values(variableProperty).stream().mapToInt(value -> value.getPropertyOutsideContext(variableProperty)).min().orElse(Level.DELAY);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return values(variableProperty).stream().mapToInt(value -> evaluationContext.getProperty(value, variableProperty)).min().orElse(Level.DELAY);
    }

    private List<Value> values(VariableProperty variableProperty) {
        List<Value> inMap = alternativeList.get(variableProperty);
        return inMap == null ? values : inMap;
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
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return values.stream().flatMap(value -> value.linkedVariables(bestCase, evaluationContext).stream()).collect(Collectors.toSet());
    }
}

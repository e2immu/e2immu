package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Map;
import java.util.Objects;

public abstract class ConstantValue implements Value {

    public final ObjectFlow objectFlow;

    public ConstantValue(ObjectFlow objectFlow) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    // executed without context, default for all constant types
    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.CONTAINER == variableProperty) return Level.TRUE;
        if (VariableProperty.IMMUTABLE == variableProperty) return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        if (VariableProperty.NOT_NULL == variableProperty) return MultiLevel.EFFECTIVELY_NOT_NULL;
        if (VariableProperty.SIZE == variableProperty) return Level.DELAY;
        if (VariableProperty.SIZE_COPY == variableProperty) return Level.DELAY;
        if (VariableProperty.MODIFIED == variableProperty) return Level.FALSE;
        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        return this; // minor speedup + security, we're not allowed to mess with constants :-)
    }

    @Override
    public boolean isExpressionOfParameters() {
        return true;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }
}

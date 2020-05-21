package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;

public abstract class PrimitiveValue implements Value {

    // executed without context, default for all constant types
    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.DYNAMIC_TYPE_PROPERTY.contains(variableProperty)) return variableProperty.best;
        if (VariableProperty.NOT_NULL == variableProperty) return Level.TRUE; // primitives are not null
        if (VariableProperty.FIELD_AND_METHOD_PROPERTIES.contains(variableProperty)) return Level.DELAY;

        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

}

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Objects;

public abstract class PrimitiveValue implements Value {

    public final ObjectFlow objectFlow;

    public PrimitiveValue(ObjectFlow objectFlow) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    // executed without context, default for all constant types
    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER:
                return Level.TRUE;
            case NOT_NULL:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case SIZE:
            case SIZE_COPY:
                return Level.DELAY;
            case MODIFIED:
                return Level.FALSE;
        }
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

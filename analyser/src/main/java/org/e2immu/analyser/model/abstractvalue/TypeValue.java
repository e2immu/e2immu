package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;

import java.util.StringJoiner;

/**
 * the thing that, for now, makes TypeValue different from UnknownValue is that it is not null.
 */
public class TypeValue implements Value {
    public final ParameterizedType parameterizedType;

    public TypeValue(ParameterizedType parameterizedType) {
        this.parameterizedType = parameterizedType;
    }

    @Override
    public String asString() {
        return parameterizedType.detailedString();
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public int compareTo(Value o) {
        if (this == o) return 0;
        return 1;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) return Level.TRUE;
        return Level.FALSE;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }
}

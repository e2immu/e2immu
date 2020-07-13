package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.origin.StaticOrigin;
import org.e2immu.analyser.parser.Primitives;

/**
 * the thing that, for now, makes TypeValue different from UnknownValue is that it is not null.
 *
 * for object flows: TypeValue is used as the scope for static methods.
 */
public class TypeValue implements Value {
    public final ParameterizedType parameterizedType;
    public final ObjectFlow objectFlow;

    public TypeValue(ParameterizedType parameterizedType, Location location) {
        this.parameterizedType = parameterizedType;
        objectFlow = new ObjectFlow(location, Primitives.PRIMITIVES.classTypeInfo.asParameterizedType(), StaticOrigin.LITERAL);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public ParameterizedType type() {
        return parameterizedType;
    }

    @Override
    public String toString() {
        return parameterizedType.detailedString();
    }

    @Override
    public int order() {
        return ORDER_TYPE;
    }

    @Override
    public int internalCompareTo(Value v) {
        return parameterizedType.detailedString().compareTo(((TypeValue) v).parameterizedType.detailedString());
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

    @Override
    public boolean isExpressionOfParameters() {
        return true;
    }
}

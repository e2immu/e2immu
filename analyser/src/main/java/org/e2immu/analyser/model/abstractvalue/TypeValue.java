package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;

/**
 * the thing that, for now, makes TypeValue different from UnknownValue is that it is not null.
 * <p>
 * for object flows: TypeValue is used as the scope for static methods.
 */
public class TypeValue implements Value {
    public final ParameterizedType parameterizedType;
    public final ObjectFlow objectFlow;

    public TypeValue(ParameterizedType parameterizedType, ObjectFlow objectFlow) {
        this.parameterizedType = parameterizedType;
        this.objectFlow = objectFlow;
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
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return parameterizedType.print(printMode);
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
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) return MultiLevel.EFFECTIVELY_NOT_NULL;
        return Level.FALSE;
    }
}

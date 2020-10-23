package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

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
        switch (variableProperty) {
            case CONTAINER:
                return Level.TRUE;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case NOT_NULL:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case SIZE:
            case SIZE_COPY:
                return Level.DELAY;
            case MODIFIED:
            case NOT_MODIFIED_1:
            case IDENTITY:
            case METHOD_DELAY:
            case IGNORE_MODIFICATIONS:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        return new EvaluationResult.Builder().setValue(this).build();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    public static Value nullValue(ParameterizedType type) {
        if (type.isPrimitive()) {
            if (Primitives.PRIMITIVES.integerTypeInfo == type.typeInfo) return new IntValue(0);
            if (Primitives.PRIMITIVES.booleanTypeInfo == type.typeInfo) return new BoolValue(false);
            if (Primitives.PRIMITIVES.charTypeInfo == type.typeInfo) return new CharValue('\0');
            if (Primitives.PRIMITIVES.shortTypeInfo == type.typeInfo) return new ShortValue((short) 0);
            if (Primitives.PRIMITIVES.longTypeInfo == type.typeInfo) return new LongValue(0L);
            if (Primitives.PRIMITIVES.byteTypeInfo == type.typeInfo) return new ByteValue((byte) 0);
            if (Primitives.PRIMITIVES.doubleTypeInfo == type.typeInfo) return new DoubleValue(0.0d);
            if (Primitives.PRIMITIVES.floatTypeInfo == type.typeInfo) return new FloatValue(0.0f);

            throw new UnsupportedOperationException("Need to implement null value for " + type);
        }
        return NullValue.NULL_VALUE;
    }

}

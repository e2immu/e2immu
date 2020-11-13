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


    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case CONTAINER:
                return Level.TRUE;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case NOT_NULL:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case MODIFIED:
            case NOT_MODIFIED_1:
            case METHOD_DELAY:
            case IGNORE_MODIFICATIONS:
            case IDENTITY:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        return new EvaluationResult.Builder().setValue(this).build();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    public static Value nullValue(Primitives primitives, TypeInfo typeInfo) {
        if (typeInfo != null) {
            if (Primitives.isBoolean(typeInfo)) return new BoolValue(primitives, false, ObjectFlow.NO_FLOW);
            if (Primitives.isInt(typeInfo)) return new IntValue(primitives, 0, ObjectFlow.NO_FLOW);
            if (Primitives.isLong(typeInfo)) return new LongValue(primitives, 0L, ObjectFlow.NO_FLOW);
            if (Primitives.isShort(typeInfo)) return new ShortValue(primitives, (short) 0, ObjectFlow.NO_FLOW);
            if (Primitives.isByte(typeInfo)) return new ByteValue(primitives, (byte) 0, ObjectFlow.NO_FLOW);
            if (Primitives.isFloat(typeInfo)) return new FloatValue(primitives, 0, ObjectFlow.NO_FLOW);
            if (Primitives.isDouble(typeInfo)) return new DoubleValue(primitives, 0, ObjectFlow.NO_FLOW);
            if (Primitives.isChar(typeInfo)) return new CharValue(primitives, '\0', ObjectFlow.NO_FLOW);
        }
        return NullValue.NULL_VALUE;
    }

}

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeContext;

public class ThisValue implements Value {
    public final This thisVariable;

    public ThisValue(This thisVariable) {
        this.thisVariable = thisVariable;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return getProperty(thisVariable.typeInfo, variableProperty);
    }

    public static int getProperty(TypeInfo typeInfo, VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) {
            return Level.TRUE;
        }
        return typeInfo.typeAnalysis.getProperty(variableProperty);
    }

    @Override
    public ParameterizedType type() {
        return thisVariable.parameterizedType();
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof This) {
            return thisVariable.typeInfo.fullyQualifiedName.compareTo(((This) o).typeInfo.fullyQualifiedName);
        }
        return -1;
    }
}

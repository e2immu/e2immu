package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.Value;

public class ParameterValue implements Value {
    public final ParameterInfo parameterInfo;

    public ParameterValue(ParameterInfo parameterInfo) {
        this.parameterInfo = parameterInfo;
    }

    @Override
    public int compareTo(Value o) {
        return 0;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return parameterInfo.parameterAnalysis.get().getProperty(variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    @Override
    public String asString() {
        return "Link to parameter " + parameterInfo.detailedString();
    }
}

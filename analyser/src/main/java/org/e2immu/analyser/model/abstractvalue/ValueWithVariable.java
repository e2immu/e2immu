package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class ValueWithVariable implements Value {

    @NotNull
    public final Variable variable;

    protected ValueWithVariable(@NotNull Variable variable) {
        this.variable = Objects.requireNonNull(variable);
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        int immutable = getProperty(evaluationContext, VariableProperty.IMMUTABLE);
        boolean selfReferencing = typeInfo == evaluationContext.getCurrentType();
        if (immutable == Level.TRUE || immutable == Level.DELAY && (bestCase || selfReferencing)) return Set.of();
        return Set.of(variable);
    }

    @Override
    public ParameterizedType type() {
        return variable.concreteReturnType();
    }

    @Override
    public Set<Variable> variables() {
        return Set.of(variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public String toString() {
        return variable.name();
    }

    @Override
    public int order() {
        return ORDER_VARIABLE_VALUE;
    }

    @Override
    public int internalCompareTo(Value v) {
        return variable.name().compareTo(((ValueWithVariable) v).variable.name());
    }

    @Override
    public boolean isExpressionOfParameters() {
        return variable instanceof ParameterInfo;
    }
}

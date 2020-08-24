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

    protected ValueWithVariable(@NotNull Variable variable, EvaluationContext evaluationContext) {
        this.variable = Objects.requireNonNull(variable);
        this.evaluationContext = evaluationContext;
    }

    protected final EvaluationContext evaluationContext;

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return typeInfo.isNumericPrimitive() || typeInfo.isNumericPrimitiveBoxed();
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        int immutable = getProperty(evaluationContext, VariableProperty.IMMUTABLE);
        boolean selfReferencing = typeInfo == evaluationContext.getCurrentType();
        if (MultiLevel.isE2Immutable(immutable) || (immutable == Level.DELAY && (bestCase || selfReferencing)))
            return Set.of();
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValueWithVariable)) return false;
        ValueWithVariable that = (ValueWithVariable) o;
        // special for MULTI_COPY fields!!
        // VariableValePlaceHolders are excluded here
        if (evaluationContext != null && this instanceof VariableValue && that instanceof VariableValue) {
            return evaluationContext.equals(variable, that.variable);
        }
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public String toString() {
        if (variable instanceof FieldReference) {
            return "this." + variable.name();
        }
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

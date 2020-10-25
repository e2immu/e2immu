package org.e2immu.analyser.model;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.Objects;
import java.util.Set;

// variable representing a[b] (a field within an array)
public class DependentVariable extends VariableWithConcreteReturnType {
    public final ParameterizedType parameterizedType;
    public final Set<Variable> dependencies;
    public final String name;
    public final Variable arrayVariable;

    public DependentVariable(@NotNull Value array,
                             @NotNull Value index,
                             @NotNull ParameterizedType parameterizedType,  // the formal type
                             @NotNull1 Set<Variable> dependencies,         // all variables on which this one depends
                             Variable arrayVariable) {     // can be null!
        super(parameterizedType);
        this.parameterizedType = parameterizedType;
        this.dependencies = dependencies;
        this.name = dependentVariableName(array, index);
        this.arrayVariable = arrayVariable;
    }

    // array access
    public static String dependentVariableName(Value array, Value index) {
        return array.toString() + "[" + index.toString() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependentVariable that = (DependentVariable) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    @Override
    public String simpleName() {
        return name;
    }

    @Override
    public String fullyQualifiedName() {
        return name;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.LOCAL;
    }
}

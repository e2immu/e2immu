package org.e2immu.analyser.model;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.List;
import java.util.Objects;

/**
 * variable representing a complex expression by name.
 * we store it because w
 * <p>
 * array variable with known index a[0] (either as constant, or known value of variable)
 * array variable with unknown index a[i], with dependent i
 * <p>
 * method(a, b)[i], with null arrayVariable, and dependent variables a, b, i
 */
public class DependentVariable extends VariableWithConcreteReturnType {
    public final ParameterizedType parameterizedType;
    public final String name;
    public final Variable arrayVariable;
    public final List<Variable> dependencies; // idea: a change to these will invalidate the variable

    public DependentVariable(String name,
                             @NotNull ParameterizedType parameterizedType,  // the formal type
                             @NotNull1 List<Variable> dependencies,         // all variables on which this one depends
                             Variable arrayVariable) {     // can be null!
        super(parameterizedType);
        this.parameterizedType = parameterizedType;
        this.name = name;
        this.arrayVariable = arrayVariable;
        this.dependencies = dependencies;
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

    @Override
    public boolean isLocal() {
        return arrayVariable != null && arrayVariable.isLocal();
    }
}

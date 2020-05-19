package org.e2immu.analyser.model;

import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DependentVariable extends VariableWithConcreteReturnType {
    public final ParameterizedType parameterizedType;
    public final Set<Variable> dependencies;
    public final String name;
    public final String arrayName;

    public DependentVariable(@NotNull ParameterizedType parameterizedType,  // the formal type
                             @NotNull1 Set<Variable> dependencies,         // all variables on which this one depends
                             @NotNull String name,
                             String arrayName) {     // can be null!
        super(parameterizedType);
        this.parameterizedType = parameterizedType;
        this.dependencies = dependencies;
        this.name = name;
        this.arrayName = arrayName;
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
    public String name() {
        return name;
    }

    @Override
    public String detailedString() {
        return name;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return SideEffect.LOCAL;
    }
}

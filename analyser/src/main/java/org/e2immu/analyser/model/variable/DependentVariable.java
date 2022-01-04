/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
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
    public final TypeInfo owningType;
    public final String name;
    public final String simpleName;
    public final Variable arrayVariable;
    public final List<Variable> dependencies; // idea: a change to these will invalidate the variable

    public DependentVariable(String name,
                             String simpleName,
                             TypeInfo owningType,
                             @NotNull ParameterizedType parameterizedType,  // the formal type
                             @NotNull1 List<Variable> dependencies,         // all variables on which this one depends
                             Variable arrayVariable) {     // can be null!
        super(parameterizedType);
        this.name = name;
        this.simpleName = simpleName;
        this.arrayVariable = arrayVariable;
        this.dependencies = dependencies;
        this.owningType = owningType;
    }

    @Override
    public TypeInfo getOwningType() {
        return owningType;
    }

    // array access
    public static String fullDependentVariableName(Expression array, Expression index) {
        return array.toString() + "[" + index.toString() + "]";
    }
    public static String simpleDependentVariableName(Expression array, Expression index) {
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
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(name)); // TODO this can/should be more complex
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String simpleName() {
        return simpleName;
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
    public boolean isLocal() {
        return arrayVariable != null && arrayVariable.isLocal();
    }

    @Override
    public boolean needsNewVariableWithoutValueCall() {
        return true;
    }
}

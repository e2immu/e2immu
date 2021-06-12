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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.QualifiedName;

import java.util.Objects;

public class LocalVariableReference extends VariableWithConcreteReturnType {
    public final LocalVariable variable;
    public final Expression assignmentExpression;

    public LocalVariableReference(LocalVariable localVariable) {
        this(localVariable, EmptyExpression.EMPTY_EXPRESSION);
    }

    public LocalVariableReference(LocalVariable localVariable,
                                  Expression assignmentExpression) {
        super(assignmentExpression == EmptyExpression.EMPTY_EXPRESSION
                ? localVariable.parameterizedType() : assignmentExpression.returnType());
        this.variable = Objects.requireNonNull(localVariable);
        this.assignmentExpression = Objects.requireNonNull(assignmentExpression);
    }

    @Override
    public TypeInfo getOwningType() {
        return variable.owningType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariableReference that = (LocalVariableReference) o;
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return variable.parameterizedType();
    }

    @Override
    public String simpleName() {
        return variable.simpleName();
    }

    @Override
    public String fullyQualifiedName() {
        return variable.name();
    }

    @Override
    public String toString() {
        return output(Qualification.EMPTY).toString();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String name = qualification == Qualification.FULLY_QUALIFIED_NAME ? fullyQualifiedName() : simpleName();
        return new OutputBuilder().add(new QualifiedName(name, null, QualifiedName.Required.NEVER));
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean needsNewVariableWithoutValueCall() {
        return true;
    }

    @Override
    public VariableNature variableNature() {
        return variable.nature();
    }
}

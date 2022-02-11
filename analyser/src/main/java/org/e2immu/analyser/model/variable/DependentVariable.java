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
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;

import java.util.Objects;

/**
 * variable representing a complex expression by name, concretely, used to store array access variables.
 * two situations: the array is a variable, and, more complex, the array is an expression.
 * <p>
 * the former is much more stable than the latter.
 * in the latter, we store the unevaluated expression to be re-evaluated at initialisation time.
 * <p>
 * In either case StatementAnalysisImpl.initializeLocalOrDependentVariable needs to run on every iteration.
 */
public class DependentVariable extends VariableWithConcreteReturnType {


    public record NonVariable(Expression value, Identifier identifier) {
    }

    public final TypeInfo owningType;
    public final String name;
    public final String simpleName;
    public final Either<NonVariable, Variable> expressionOrArrayVariable;

    public DependentVariable(Identifier identifier,
                             Expression arrayExpression,
                             Expression indexExpression,
                             @NotNull ParameterizedType parameterizedType) {  // the formal type
        super(parameterizedType);
        Variable arrayVariable = singleVariable(arrayExpression);
        this.expressionOrArrayVariable = arrayVariable == null
                ? Either.left(new NonVariable(arrayExpression, identifier))
                : Either.right(arrayVariable);
        Variable indexVariable = singleVariable(indexExpression);
        String indexString = (indexVariable == null ? indexExpression.minimalOutput() : indexVariable.fullyQualifiedName());
        String indexSimple = (indexVariable == null ? indexExpression.minimalOutput() : indexVariable.simpleName());
        if (arrayVariable != null) {
            name = arrayVariable.fullyQualifiedName() + "[" + indexString + "]";
            simpleName = arrayVariable.simpleName() + "[" + indexSimple + "]";
        } else {
            name = "AV$" + expressionOrArrayVariable.getLeft().identifier + "[" + indexString + "]";
            simpleName = "AV$[" + indexSimple + "]";
        }
        this.owningType = arrayVariable != null ? arrayVariable.getOwningType() : null;
    }

    public static Variable singleVariable(Expression expression) {
        IsVariableExpression ve;
        if ((ve = expression.asInstanceOf(IsVariableExpression.class)) != null) {
            return ve.variable();
        }
        return null;
    }


    @Override
    public TypeInfo getOwningType() {
        return owningType;
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
        return new OutputBuilder().add(new Text(name));
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
        if (expressionOrArrayVariable.isLeft()) return false;
        Variable arrayVariable = expressionOrArrayVariable.getRight();
        return arrayVariable != null && arrayVariable.isLocal();
    }

    public boolean hasArrayVariable() {
        return expressionOrArrayVariable.isRight();
    }

    public Variable arrayVariable() {
        return expressionOrArrayVariable.getRight();
    }
}

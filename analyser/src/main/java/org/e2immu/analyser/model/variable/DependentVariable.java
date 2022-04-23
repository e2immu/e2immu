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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * variable representing a complex expression by name, concretely, used to store array access variables.
 * two situations: the array is a variable, and, more complex, the array is an expression.
 * <p>
 * if the array or the index is a variable, we refer to this variable when evaluating a variable expression.
 * <p>
 * In either case StatementAnalysisImpl.initializeLocalOrDependentVariable needs to run on every iteration.
 */
public class DependentVariable extends VariableWithConcreteReturnType {

    public final String name;
    public final String simpleName;
    private final Expression arrayExpression;
    private final Variable arrayVariable;
    private final Expression indexExpression;
    private final Variable indexVariable;
    public final String statementIndex;
    private final Identifier identifier;

    public DependentVariable(Identifier identifier,
                             Expression arrayExpression,
                             Variable arrayVariable,
                             Expression indexExpression,
                             Variable indexVariable,
                             ParameterizedType parameterizedType,//formal type
                             String statementIndex) {
        super(parameterizedType);
        this.identifier = identifier;
        this.statementIndex = statementIndex; // not-"" when created during analysis
        this.arrayExpression = arrayExpression;
        this.arrayVariable = arrayVariable;
        this.indexExpression = indexExpression;
        this.indexVariable = indexVariable; // can be null, in case of a constant
        String indexFqn = indexVariable == null ? indexExpression.minimalOutput() : indexVariable.fullyQualifiedName();
        name = arrayVariable.fullyQualifiedName() + "[" + indexFqn + "]";
        String indexSimple = indexVariable == null ? indexExpression.minimalOutput() : indexVariable.simpleName();
        simpleName = arrayVariable.simpleName() + "[" + indexSimple + "]";
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public Expression indexExpression() {
        return indexExpression;
    }

    public Variable indexVariable() {
        return indexVariable;
    }

    public Expression arrayExpression() {
        return arrayExpression;
    }

    public Variable arrayVariable() {
        return arrayVariable;
    }

    @Override
    public TypeInfo getOwningType() {
        return arrayVariable.getOwningType();
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
        return arrayVariable.isLocal() && (indexVariable == null || indexVariable.isLocal());
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        arrayExpression.visit(predicate);
        indexExpression.visit(predicate);
    }

    public Variable arrayBaseVariable() {
        if (arrayVariable instanceof DependentVariable dv) return dv.arrayBaseVariable();
        return arrayVariable;
    }

    @Override
    public boolean hasScopeVariableCreatedAt(String index) {
        return arrayVariable.hasScopeVariableCreatedAt(index) || indexVariable != null && indexVariable.hasScopeVariableCreatedAt(index);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        CausesOfDelay c1 = arrayExpression.causesOfDelay().merge(arrayVariable == null ? CausesOfDelay.EMPTY : arrayVariable.causesOfDelay());
        CausesOfDelay c2 = indexExpression.causesOfDelay().merge(indexVariable == null ? CausesOfDelay.EMPTY : indexVariable.causesOfDelay());
        return c1.merge(c2);
    }
}

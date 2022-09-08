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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class ArrayAccess extends BaseExpression implements Expression {

    public static final String ARRAY_VARIABLE = "av-";
    public static final String INDEX_VARIABLE = "iv-";
    public final Expression expression;
    public final Expression index;
    public final DependentVariable dependentVariable;
    public final ParameterizedType returnType;

    /*
    indexIdentifier separately, because when the index expression is a constant, this expression does not have
    a positional identifier.
     */
    public ArrayAccess(Identifier identifier,
                       @NotNull Expression expression,
                       @NotNull Expression index,
                       Identifier indexIdentifier,
                       TypeInfo owningType) {
        super(identifier);
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
        this.returnType = expression.returnType().copyWithOneFewerArrays();
        Variable arrayVariable = makeVariable(expression, expression.getIdentifier(), ARRAY_VARIABLE, owningType);
        Variable indexVariable = makeVariable(index, indexIdentifier, INDEX_VARIABLE, owningType);
        assert arrayVariable != null; // an array initializer (such as {3,5,6}) is not a constant
        dependentVariable = new DependentVariable(identifier, expression, arrayVariable, index, indexVariable, returnType, "");
    }

    public static Variable makeVariable(Expression expression, Identifier identifier, String variablePrefix, TypeInfo owningType) {
        if (expression.isConstant()) return null;
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            return ve.variable();
        }
        assert !identifier.unstableIdentifier() : "cannot have unstable identifiers here!";
        String name = variablePrefix + identifier.compact();
        VariableNature vn = new VariableNature.ScopeVariable();
        LocalVariable lv = new LocalVariable(Set.of(LocalVariableModifier.FINAL), name, expression.returnType(),
                List.of(), owningType, vn);
        return new LocalVariableReference(lv, expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayAccess that = (ArrayAccess) o;
        return expression.equals(that.expression) &&
                index.equals(that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, index);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
            index.visit(predicate);
        }
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translatedExpression = expression.translate(inspectionProvider, translationMap);
        Expression translatedIndex = index.translate(inspectionProvider, translationMap);
        if (translatedIndex == this.index && translatedExpression == this.expression) return this;
        return new ArrayAccess(identifier, translatedExpression, translatedIndex,
                dependentVariable.indexExpression().getIdentifier(), dependentVariable.getOwningType());
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION) {
            DV nneArray = context.evaluationContext().getProperty(expression, Property.NOT_NULL_EXPRESSION, duringEvaluation, false);
            if (nneArray.isDelayed()) return nneArray.causesOfDelay();
            return MultiLevel.composeOneLevelLessNotNull(nneArray);
        }
        throw new UnsupportedOperationException("Not yet evaluated");
    }

    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), expression))
                .add(Symbol.LEFT_BRACKET).add(index.output(qualification)).add(Symbol.RIGHT_BRACKET);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression, index);
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        VariableExpression ve = new VariableExpression(dependentVariable.getIdentifier(),
                dependentVariable, VariableExpression.NO_SUFFIX, expression, index);
        return ve.evaluate(context, forwardEvaluationInfo);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return VariableExpression.internalLinkedVariables(dependentVariable, LinkedVariables.LINK_STATICALLY_ASSIGNED);
    }
}

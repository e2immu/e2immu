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
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class ExpandedVariable extends BaseExpression {

    private final Variable variable;
    private final Properties properties;

    public ExpandedVariable(Identifier identifier, Variable variable, Properties properties) {
        super(identifier);
        assert variable.causesOfDelay().isDone();
        this.variable = Objects.requireNonNull(variable);
        this.properties = Objects.requireNonNull(properties);
    }

    public Variable getVariable() {
        return variable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpandedVariable that = (ExpandedVariable) o;
        return variable.equals(that.variable) && identifier.equals(that.identifier);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return translationMap.translateExpression(this);
    }

    @Override
    public int hashCode() {
        return variable.hashCode() + 37 * identifier.hashCode();
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return properties.get(property);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder()
                .add(Symbol.LEFT_BACKTICK)
                .add(variable.output(qualification))
                .add(Symbol.RIGHT_BACKTICK);
    }

    @Override
    public boolean isNumeric() {
        return variable.parameterizedType().isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(context).setExpression(this).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_EXPANDED_VARIABLE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlineConditional ic;
        Expression e;
        if ((ic = v.asInstanceOf(InlineConditional.class)) != null) {
            e = ic.condition;
        } else e = v;
        if(e instanceof ExpandedVariable ev) {
            return variable.compareTo(ev.variable);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        predicate.test(this);
    }

    @Override
    public String minimalOutput() {
        // the back quotes signal the expansion: this is not a variable!
        return "`" + variable.minimalOutput() + "`";
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return List.of(); // explicitly present here to help remember it should not return its variable!
    }
}

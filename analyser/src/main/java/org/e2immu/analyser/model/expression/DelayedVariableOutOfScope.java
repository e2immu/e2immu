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
import org.e2immu.analyser.output.Text;

import java.util.List;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL_DV;

/*
differs from DelayedExpression in equality; this one is based on identifier.
The identifier will be tied to the field reference, so that only one delayed scope, with different
causes of delay, will exist.

Primary example: InstanceOf_11
 */
public class DelayedVariableOutOfScope extends BaseExpression implements Expression {

    private final ParameterizedType parameterizedType;
    private final CausesOfDelay causesOfDelay;
    private final LinkedVariables linkedVariables;
    private final String msg;

    public DelayedVariableOutOfScope(Identifier identifier, ParameterizedType parameterizedType,
                                     LinkedVariables linkedVariables,
                                     CausesOfDelay causesOfDelay) {
        super(identifier);
        if (identifier instanceof Identifier.VariableOutOfScopeIdentifier i) {
            msg = "<out of scope:" + i.fqn() + ":" + i.index() + ">";
        } else {
            throw new UnsupportedOperationException();
        }
        this.causesOfDelay = causesOfDelay;
        this.parameterizedType = parameterizedType;
        this.linkedVariables = linkedVariables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DelayedVariableOutOfScope that = (DelayedVariableOutOfScope) o;
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(msg));
    }

    @Override
    public String toString() {
        return msg;
    }


    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION &&
                parameterizedType.isPrimitiveExcludingVoid()) return EFFECTIVELY_NOT_NULL_DV;
        return causesOfDelay;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        if (linkedVariables.isEmpty()) return this;
        return new DelayedVariableOutOfScope(identifier, translationMap.translateType(parameterizedType),
                linkedVariables.translate(translationMap), causesOfDelay);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return List.copyOf(linkedVariables.variables().keySet());
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return linkedVariables;
    }

}
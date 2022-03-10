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
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.E2Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/*
Purpose: when facing an infinite loop in determining the values of a field, in special cases an InlineConditional
is replaced by this delayed expression, effectively eliminating, for one iteration, a delayed field value.
See ConditionalInitialization_1.
 */
@E2Container
public final class DelayedWrappedExpression extends BaseExpression implements Expression {
    private static final Logger LOGGER = LoggerFactory.getLogger(DelayedWrappedExpression.class);

    private final VariableInfo variableInfo;
    private final CausesOfDelay causesOfDelay;
    private final Expression expression;

    public DelayedWrappedExpression(Identifier identifier,
                                    Expression expression,
                                    VariableInfo variableInfo,
                                    CausesOfDelay causesOfDelay) {
        super(identifier);
        this.variableInfo = variableInfo;
        this.causesOfDelay = causesOfDelay;
        this.expression = expression;
        assert expression.isDone();
        assert causesOfDelay.isDelayed();
    }

    /*
    variable fields have different values according to statement time, but then, at this point we cannot know yet
    whether the field will be variable or not.
    Basics7 shows a case where the local condition manager goes from true to false depending on this equality.
     */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableInfo, causesOfDelay);
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public String toString() {
        return msg();
    }

    private String msg() {
        return "<wrapped:" + variableInfo.variable().simpleName() + ">";
    }

    @Override
    public ParameterizedType returnType() {
        return variableInfo.variable().parameterizedType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String msg = msg();
        return new OutputBuilder().add(new Text(msg, msg));
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
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
       // return variableInfo.getProperty(property);
        //return causesOfDelay;
        return expression.getProperty(context, property, duringEvaluation);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        if (translationMap.expandDelayedWrappedExpressions()) {
            return expression;
        }
        return this;
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return variableInfo.getLinkedVariables();
    }

    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    public VariableInfo getVariableInfo() {
        return variableInfo;
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new DelayedWrappedExpression(identifier, expression, variableInfo, this.causesOfDelay.merge(causesOfDelay));
    }

    public static Expression moveDelayedWrappedExpressionToFront(InspectionProvider inspectionProvider, Expression value) {
        if (value.isDelayed() && !(value instanceof DelayedWrappedExpression)) {
            List<DelayedWrappedExpression> x = value.collect(DelayedWrappedExpression.class);
            if (!x.isEmpty()) {
                if (x.size() > 1) {
                    LOGGER.warn("Multiple occurrences of DWE? Taking the first one");
                }
                TranslationMap tm = new TranslationMapImpl.Builder().setExpandDelayedWrapperExpressions(true).build();
                Expression translated = value.translate(inspectionProvider, tm);
                if(translated.isDelayed()) {
                    return x.get(0); // no need to proceed, will not be picked up by FieldAnalyserImpl.values
                }
                DelayedWrappedExpression dwe = x.get(0);
                DelayedWrappedExpression newDwe = new DelayedWrappedExpression(dwe.getIdentifier(),
                        translated, dwe.variableInfo, dwe.causesOfDelay());
                assert newDwe.isDelayed();
                return newDwe;
            }
        }
        return value;
    }

    public Expression getExpression() {
        return expression;
    }
}

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
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.Objects;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL;

@E2Container
public record DelayedExpression(String msg,
                                String debug,
                                ParameterizedType parameterizedType,
                                int notNull) implements Expression {

    public static DelayedExpression forMethod(MethodInfo methodInfo, int notNull) {
        return new DelayedExpression("<m:" + methodInfo.name + ">",
                "<method:" + methodInfo.fullyQualifiedName + ">", methodInfo.returnType(), notNull);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(ParameterizedType parameterizedType, int notNull) {
        return new DelayedExpression("<s:" + parameterizedType.printSimple() + ">",
                "<state:" + parameterizedType.detailedString() + ">", parameterizedType, notNull);
    }

    public static Expression forNewObject(ParameterizedType parameterizedType, int notNull) {
        assert notNull >= EFFECTIVELY_NOT_NULL;
        return new DelayedExpression("<new:" + parameterizedType.printSimple() + ">",
                "<new:" + parameterizedType.detailedString() + ">", parameterizedType, notNull);
    }

    public static Expression forArrayLength(Primitives primitives) {
        return new DelayedExpression("<delayed array length>",
                "<delayed array length>", primitives.intParameterizedType, EFFECTIVELY_NOT_NULL);
    }

    public static Expression forPrecondition(Primitives primitives) {
        return new DelayedExpression("<precondition>", "<precondition>", primitives.booleanParameterizedType,
                EFFECTIVELY_NOT_NULL);
    }

    public static Expression forInstanceOf(Primitives primitives, ParameterizedType parameterizedType) {
        return new DelayedExpression("<instanceOf:" + parameterizedType.printSimple() + ">",
                "<instanceOf:" + parameterizedType.detailedString() + ">", primitives.booleanParameterizedType,
                EFFECTIVELY_NOT_NULL);
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
        return Objects.hash(msg, parameterizedType);
    }

    @Override
    public boolean isNumeric() {
        return Primitives.isNumeric(parameterizedType.typeInfo);
    }

    @Override
    public String toString() {
        return msg;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(msg, debug));
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
    public boolean isDelayed(EvaluationContext evaluationContext) {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (VariableProperty.NOT_NULL_EXPRESSION == variableProperty) {
            assert !Primitives.isPrimitiveExcludingVoid(parameterizedType) || notNull == EFFECTIVELY_NOT_NULL;
            return notNull;
        }
        return Level.DELAY;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return this;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.DELAY;
    }
}

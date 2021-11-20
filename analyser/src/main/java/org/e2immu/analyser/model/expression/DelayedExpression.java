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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL_DV;

@E2Container
public record DelayedExpression(String msg,
                                String debug,
                                ParameterizedType parameterizedType,
                                LinkedVariables linkedVariables,
                                CausesOfDelay causesOfDelay) implements Expression {

    public static final String PRECONDITION = "<precondition>";

    public DelayedExpression {
        assert PRECONDITION.equals(msg) ||
                linkedVariables.isDelayed() : "Linked variables of " + debug + " is not delayed!";
    }

    public static DelayedExpression forMethod(MethodInfo methodInfo, ParameterizedType concreteReturnType,
                                              LinkedVariables linkedVariables,
                                              CausesOfDelay causesOfDelay) {
        return new DelayedExpression("<m:" + methodInfo.name + ">",
                "<method:" + methodInfo.fullyQualifiedName + ">", concreteReturnType, linkedVariables,
                causesOfDelay);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(ParameterizedType parameterizedType, LinkedVariables linkedVariables,
                                      CausesOfDelay causes) {
        return new DelayedExpression("<s:" + parameterizedType.printSimple() + ">",
                "<state:" + parameterizedType.detailedString() + ">", parameterizedType, linkedVariables,
                causes);
    }

    public static Expression forNewObject(ParameterizedType parameterizedType, DV notNull, LinkedVariables linkedVariables,
                                          CausesOfDelay causes) {
        assert notNull.ge(EFFECTIVELY_NOT_NULL_DV);
        return new DelayedExpression("<new:" + parameterizedType.printSimple() + ">",
                "<new:" + parameterizedType.detailedString() + ">", parameterizedType, linkedVariables, causes);
    }

    public static Expression forReplacementObject(ParameterizedType parameterizedType, LinkedVariables linkedVariables,
                                                  CausesOfDelay causes) {
        return new DelayedExpression("<replace:" + parameterizedType.printSimple() + ">",
                "<replace:" + parameterizedType.detailedString() + ">", parameterizedType, linkedVariables, causes);
    }

    public static Expression forArrayLength(Primitives primitives, CausesOfDelay causes) {
        return new DelayedExpression("<delayed array length>",
                "<delayed array length>", primitives.intParameterizedType, LinkedVariables.delayedEmpty(causes), causes);
        // result is an int, so no linked variables
    }

    public static Expression forPrecondition(Primitives primitives, CausesOfDelay causes) {
        return new DelayedExpression(PRECONDITION, PRECONDITION, primitives.booleanParameterizedType,
                LinkedVariables.EMPTY, causes); // no need for linked variables
    }

    public static Expression forInstanceOf(Primitives primitives, ParameterizedType parameterizedType,
                                           LinkedVariables linkedVariables,
                                           CausesOfDelay causes) {
        return new DelayedExpression("<instanceOf:" + parameterizedType.printSimple() + ">",
                "<instanceOf:" + parameterizedType.detailedString() + ">", primitives.booleanParameterizedType,
                linkedVariables, causes);
    }

    public static Expression forMerge(ParameterizedType parameterizedType, LinkedVariables linkedVariables, CausesOfDelay causes) {
        return new DelayedExpression("<merge:" + parameterizedType.printSimple() + ">",
                "<merge:" + parameterizedType.detailedString() + ">", parameterizedType, linkedVariables, causes);
    }

    public static Expression forUnspecifiedLoopCondition(ParameterizedType booleanParameterizedType, LinkedVariables linkedVariables, CausesOfDelay causes) {
        String name = "<loopIsNotEmptyCondition>";
        return new DelayedExpression(name, name, booleanParameterizedType, linkedVariables, causes);
    }

    public static Expression forLocalVariableInLoop(ParameterizedType parameterizedType, LinkedVariables linkedVariables, CausesOfDelay causesOfDelay) {
        String name = "<localVariableInLoop:" + parameterizedType.detailedString() + ">";
        return new DelayedExpression(name, name, parameterizedType, linkedVariables, causesOfDelay);
    }

    public static Expression forValueOf(ParameterizedType parameterizedType, CausesOfDelay causesOfDelay) {
        String name = "<valueOf:" + parameterizedType.detailedString() + ">";
        return new DelayedExpression(name, name, parameterizedType, LinkedVariables.delayedEmpty(causesOfDelay), causesOfDelay);
    }

    public static Expression forDelayedValueProperties(ParameterizedType parameterizedType, LinkedVariables linkedVariables, CausesOfDelay causes) {
        String name = "<vp:" + parameterizedType.detailedString() + ">";
        return new DelayedExpression(name, name, parameterizedType, linkedVariables, causes);
    }

    public static Expression forInitialFieldValue(FieldInfo fieldInfo, LinkedVariables linkedVariables, CausesOfDelay causesOfDelay) {
        return new DelayedExpression(fieldInfo.name, fieldInfo.fullyQualifiedName(), fieldInfo.type, linkedVariables, causesOfDelay);
    }

    /* temporary, in field analyser */
    public static Expression forFieldProperty(FieldInfo fieldInfo, CausesOfDelay causesOfDelay) {
        return new DelayedExpression(fieldInfo.name, fieldInfo.fullyQualifiedName(), fieldInfo.type,
                LinkedVariables.delayedEmpty(causesOfDelay), causesOfDelay);
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
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION &&
                Primitives.isPrimitiveExcludingVoid(parameterizedType)) return EFFECTIVELY_NOT_NULL_DV;
        return causesOfDelay;
    }

    // See Loops_19: during merging, local loop variables are replaced. The variables in the DelayedExpression.variables
    // list need to be replaced as well.
    @Override
    public Expression translate(TranslationMap translationMap) {
        if (linkedVariables.isEmpty()) return this;
        return new DelayedExpression(msg, debug, translationMap.translateType(parameterizedType),
                linkedVariables.translate(translationMap), causesOfDelay);
    }

    @Override
    public List<Variable> variables() {
        return List.copyOf(linkedVariables.variables().keySet());
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return linkedVariables;
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT; // not important in final equality
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }
}

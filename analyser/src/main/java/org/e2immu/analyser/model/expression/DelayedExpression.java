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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL_DV;

@E2Container
public final class DelayedExpression extends BaseExpression implements Expression {
    private final String msg;
    private final ParameterizedType parameterizedType;
    private final LinkedVariables linkedVariables;
    private final CausesOfDelay causesOfDelay;
    private final Properties priorityProperties;

    public DelayedExpression(String msg,
                             ParameterizedType parameterizedType,
                             LinkedVariables linkedVariables,
                             CausesOfDelay causesOfDelay) {
        this(msg, parameterizedType, linkedVariables, causesOfDelay, Properties.EMPTY);
    }

    public DelayedExpression(String msg,
                             ParameterizedType parameterizedType,
                             LinkedVariables linkedVariables,
                             CausesOfDelay causesOfDelay,
                             Properties properties) {
        super(Identifier.CONSTANT);
        this.msg = msg;
        this.parameterizedType = parameterizedType;
        this.linkedVariables = linkedVariables;
        this.causesOfDelay = causesOfDelay;
        this.priorityProperties = properties;
    }

    private static String brackets(String msg) {
        return "<" + msg + ">";
    }

    public static DelayedExpression forMethod(MethodInfo methodInfo,
                                              ParameterizedType concreteReturnType,
                                              LinkedVariables linkedVariables,
                                              CausesOfDelay causesOfDelay) {
        String msg = brackets("m:" + methodInfo.name);
        return new DelayedExpression(msg, concreteReturnType, linkedVariables, causesOfDelay);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(ParameterizedType parameterizedType,
                                      LinkedVariables linkedVariables,
                                      CausesOfDelay causes) {
        String msg = brackets("s:" + parameterizedType.printSimple());
        return new DelayedExpression(msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forNewObject(ParameterizedType parameterizedType,
                                          DV notNull,
                                          LinkedVariables linkedVariables,
                                          CausesOfDelay causes) {
        assert notNull.ge(EFFECTIVELY_NOT_NULL_DV);
        String msg = brackets("new:" + parameterizedType.printSimple());
        return new DelayedExpression(msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forReplacementObject(ParameterizedType parameterizedType,
                                                  LinkedVariables linkedVariables,
                                                  CausesOfDelay causes) {
        String msg = brackets("replace:" + parameterizedType.printSimple());
        return new DelayedExpression(msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forArrayLength(Primitives primitives, CausesOfDelay causes) {
        String msg = brackets("delayed array length");
        return new DelayedExpression(msg, primitives.intParameterizedType(), LinkedVariables.delayedEmpty(causes), causes);
        // result is an int, so no linked variables
    }

    public static Expression forPrecondition(Primitives primitives, CausesOfDelay causes) {
        String msg = brackets("precondition");
        return new DelayedExpression(msg, primitives.booleanParameterizedType(), LinkedVariables.EMPTY, causes);
    }

    public static Expression forInstanceOf(Primitives primitives,
                                           ParameterizedType parameterizedType,
                                           LinkedVariables linkedVariables,
                                           CausesOfDelay causes) {
        String msg = brackets("instanceOf:" + parameterizedType.printSimple());
        return new DelayedExpression(msg, primitives.booleanParameterizedType(), linkedVariables, causes);
    }

    public static Expression forMerge(ParameterizedType parameterizedType,
                                      LinkedVariables linkedVariables,
                                      CausesOfDelay causes) {
        String msg = brackets("merge:" + parameterizedType.printSimple());
        return new DelayedExpression(msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forUnspecifiedLoopCondition(ParameterizedType booleanParameterizedType, LinkedVariables linkedVariables, CausesOfDelay causes) {
        String msg = brackets("loopIsNotEmptyCondition");
        return new DelayedExpression(msg, booleanParameterizedType, linkedVariables, causes);
    }

    public static Expression forLocalVariableInLoop(ParameterizedType parameterizedType,
                                                    LinkedVariables linkedVariables,
                                                    CausesOfDelay causesOfDelay) {
        String msg = brackets("localVariableInLoop:" + parameterizedType.detailedString());
        return new DelayedExpression(msg, parameterizedType, linkedVariables, causesOfDelay);
    }

    public static Expression forValueOf(ParameterizedType parameterizedType, CausesOfDelay causesOfDelay) {
        String msg = brackets("valueOf:" + parameterizedType.detailedString());
        return new DelayedExpression(msg, parameterizedType, LinkedVariables.delayedEmpty(causesOfDelay), causesOfDelay);
    }

    public static Expression forDelayedValueProperties(ParameterizedType parameterizedType,
                                                       LinkedVariables linkedVariables,
                                                       CausesOfDelay causes,
                                                       Properties priorityProperties) {
        String msg = brackets("vp:" + parameterizedType.detailedString() + ":" + causes);
        return new DelayedExpression(msg, parameterizedType, linkedVariables, causes, priorityProperties);
    }

    public static Expression forInitialFieldValue(FieldInfo fieldInfo,
                                                  LinkedVariables linkedVariables,
                                                  CausesOfDelay causesOfDelay) {
        String msg = brackets("f:" + fieldInfo.name);
        return new DelayedExpression(msg, fieldInfo.type, linkedVariables, causesOfDelay);
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
        return parameterizedType.typeInfo != null && parameterizedType.typeInfo.isNumeric();
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
        return new OutputBuilder().add(new Text(msg, msg));
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
        DV priority = priorityProperties.getOrDefaultNull(property);
        if (priority != null) return priority;
        return causesOfDelay;
    }

    // See Loops_19: during merging, local loop variables are replaced. The variables in the DelayedExpression.variables
    // list need to be replaced as well.
    @Override
    public Expression translate(TranslationMap translationMap) {
        if (linkedVariables.isEmpty()) return this;
        return new DelayedExpression(msg, translationMap.translateType(parameterizedType),
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

    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    public String msg() {
        return msg;
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public LinkedVariables linkedVariables() {
        return linkedVariables;
    }

}

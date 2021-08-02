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
import java.util.Set;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL;

@E2Container
public record DelayedExpression(String msg,
                                String debug,
                                ParameterizedType parameterizedType,
                                List<Variable> variables) implements Expression {

    public static DelayedExpression forMethod(MethodInfo methodInfo, ParameterizedType concreteReturnType,
                                              List<Variable> variables) {
        return new DelayedExpression("<m:" + methodInfo.name + ">",
                "<method:" + methodInfo.fullyQualifiedName + ">", concreteReturnType, variables);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(ParameterizedType parameterizedType, List<Variable> variables) {
        return new DelayedExpression("<s:" + parameterizedType.printSimple() + ">",
                "<state:" + parameterizedType.detailedString() + ">", parameterizedType, variables);
    }

    public static Expression forNewObject(ParameterizedType parameterizedType, int notNull, List<Variable> variables) {
        assert notNull >= EFFECTIVELY_NOT_NULL;
        return new DelayedExpression("<new:" + parameterizedType.printSimple() + ">",
                "<new:" + parameterizedType.detailedString() + ">", parameterizedType, variables);
    }

    public static Expression forReplacementObject(ParameterizedType parameterizedType, List<Variable> variables) {
        return new DelayedExpression("<replace:" + parameterizedType.printSimple() + ">",
                "<replace:" + parameterizedType.detailedString() + ">", parameterizedType, variables);
    }

    public static Expression forArrayLength(Primitives primitives) {
        return new DelayedExpression("<delayed array length>",
                "<delayed array length>", primitives.intParameterizedType, List.of());
        // result is an int, so no linked variables
    }

    public static Expression forPrecondition(Primitives primitives) {
        return new DelayedExpression("<precondition>", "<precondition>", primitives.booleanParameterizedType,
                List.of()); // no need for linked variables
    }

    public static Expression forInstanceOf(Primitives primitives, ParameterizedType parameterizedType,
                                           List<Variable> variables) {
        return new DelayedExpression("<instanceOf:" + parameterizedType.printSimple() + ">",
                "<instanceOf:" + parameterizedType.detailedString() + ">", primitives.booleanParameterizedType,
                variables);
    }

    public static Expression forMerge(ParameterizedType parameterizedType, List<Variable> variables) {
        return new DelayedExpression("<merge:" + parameterizedType.printSimple() + ">",
                "<merge:" + parameterizedType.detailedString() + ">", parameterizedType, variables);
    }

    public static Expression forUnspecifiedLoopCondition(ParameterizedType booleanParameterizedType, List<Variable> variables) {
        String name = "<loopIsNotEmptyCondition>";
        return new DelayedExpression(name, name, booleanParameterizedType, variables);
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
        if (variableProperty == VariableProperty.NOT_NULL_EXPRESSION &&
                Primitives.isPrimitiveExcludingVoid(parameterizedType)) return EFFECTIVELY_NOT_NULL;
        return Level.DELAY;
    }

    // See Loops_19: during merging, local loop variables are replaced. The variables in the DelayedExpression.variables
    // list need to be replaced as well.
    @Override
    public Expression translate(TranslationMap translationMap) {
        if (variables.isEmpty()) return this;
        return new DelayedExpression(msg, debug, translationMap.translateType(parameterizedType),
                variables.stream()
                        .map(v -> {
                            Variable translated = translationMap.translateVariable(v);
                            if(translated != v) return translated;
                            // the variable has not been translated. Check variable expressions
                            VariableExpression ve = new VariableExpression(v);
                            Expression translatedVe = translationMap.translateExpression(ve);
                            if(!translatedVe.variables().contains(v)) {
                                return null; // this one can disappear, replaced by NewObject
                            }
                            return v; // no effect
                        })
                        .filter(Objects::nonNull)
                        .toList());
    }

    @Override
    public List<Variable> variables() {
        return variables;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return new LinkedVariables(Set.copyOf(variables), true);
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT; // not important in final equality
    }
}

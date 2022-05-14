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
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL_DV;

@E2Container
public final class DelayedExpression extends BaseExpression implements Expression {
    private final String msg;
    private final ParameterizedType parameterizedType;
    private final LinkedVariables linkedVariables;
    private final CausesOfDelay causesOfDelay;
    private final Properties priorityProperties;
    private final Map<Variable, DV> cnnMap;

    private DelayedExpression(Identifier identifier,
                              String msg,
                              ParameterizedType parameterizedType,
                              LinkedVariables linkedVariables,
                              CausesOfDelay causesOfDelay) {
        this(identifier, msg, parameterizedType, linkedVariables, causesOfDelay, Properties.EMPTY, Map.of());
    }

    private DelayedExpression(Identifier identifier,
                              String msg,
                              ParameterizedType parameterizedType,
                              LinkedVariables linkedVariables,
                              CausesOfDelay causesOfDelay,
                              Properties properties,
                              Map<Variable, DV> cnnMap) {
        super(identifier);
        this.msg = msg;
        this.parameterizedType = parameterizedType;
        this.linkedVariables = linkedVariables;
        this.causesOfDelay = causesOfDelay;
        assert causesOfDelay.isDelayed();
        this.priorityProperties = properties;
        this.cnnMap = Objects.requireNonNull(cnnMap);
    }

    private static String brackets(String msg) {
        return "<" + msg + ">";
    }

    public static DelayedExpression forMethod(Identifier identifier,
                                              MethodInfo methodInfo,
                                              ParameterizedType concreteReturnType,
                                              LinkedVariables linkedVariables,
                                              CausesOfDelay causesOfDelay,
                                              Map<Variable, DV> cnnMap) {
        String msg = brackets("m:" + methodInfo.name);
        return new DelayedExpression(identifier, msg, concreteReturnType, linkedVariables, causesOfDelay,
                Properties.EMPTY, cnnMap);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(Identifier identifier,
                                      ParameterizedType parameterizedType,
                                      LinkedVariables linkedVariables,
                                      CausesOfDelay causes) {
        assert linkedVariables != LinkedVariables.NOT_YET_SET : "There are always variables!";
        String msg = brackets("s:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forCondition(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          LinkedVariables linkedVariables,
                                          CausesOfDelay causes) {
        assert linkedVariables != LinkedVariables.NOT_YET_SET : "There are always variables!";
        String msg = brackets("c:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forNewObject(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          DV notNull,
                                          LinkedVariables linkedVariables,
                                          CausesOfDelay causes) {
        assert notNull.ge(EFFECTIVELY_NOT_NULL_DV);
        String msg = brackets("new:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forArrayAccessValue(Identifier identifier,
                                                 ParameterizedType parameterizedType,
                                                 LinkedVariables linkedVariables,
                                                 CausesOfDelay causes) {
        String msg = brackets("array-access:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forReplacementObject(ParameterizedType parameterizedType,
                                                  LinkedVariables linkedVariables,
                                                  CausesOfDelay causes) {
        String msg = brackets("replace:" + parameterizedType.printSimple());
        return new DelayedExpression(Identifier.generate("replacement"),
                msg, parameterizedType, linkedVariables, causes);
    }

    public static Expression forArrayLength(Identifier identifier,
                                            Primitives primitives,
                                            LinkedVariables linkedVariables,
                                            CausesOfDelay causes) {
        String msg = brackets("delayed array length");
        return new DelayedExpression(identifier, msg, primitives.intParameterizedType(), linkedVariables, causes);
        // result is an int, so no linked variables
    }

    public static Expression forPrecondition(Identifier identifier, Primitives primitives, CausesOfDelay causes) {
        String msg = brackets("precondition");
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), LinkedVariables.EMPTY, causes);
    }

    public static Expression forSwitchSelector(Identifier identifier, Primitives primitives, CausesOfDelay causes) {
        String msg = brackets("switch-selector");
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), LinkedVariables.EMPTY, causes);
    }

    public static Expression forInstanceOf(Identifier identifier,
                                           Primitives primitives,
                                           ParameterizedType parameterizedType,
                                           LinkedVariables linkedVariables,
                                           CausesOfDelay causes) {
        String msg = brackets("instanceOf:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), linkedVariables, causes);
    }

    public static Expression forUnspecifiedLoopCondition(Identifier identifier,
                                                         ParameterizedType booleanParameterizedType,
                                                         LinkedVariables linkedVariables,
                                                         CausesOfDelay causes) {
        String msg = brackets("loopIsNotEmptyCondition");
        return new DelayedExpression(identifier, msg, booleanParameterizedType, linkedVariables, causes);
    }

    public static Expression forValueOf(ParameterizedType parameterizedType,
                                        LinkedVariables linkedVariables,
                                        CausesOfDelay causesOfDelay) {
        String msg = brackets("valueOf:" + parameterizedType.printSimple());
        return new DelayedExpression(Identifier.generate("valueOf"), msg, parameterizedType, linkedVariables,
                causesOfDelay);
    }

    public static Expression forDelayedValueProperties(Identifier identifier,
                                                       ParameterizedType parameterizedType,
                                                       LinkedVariables linkedVariables,
                                                       CausesOfDelay causes,
                                                       Properties priorityProperties) {
        String msg = brackets("vp:" + parameterizedType.printSimple() + ":" + causes);
        return new DelayedExpression(identifier, msg, parameterizedType, linkedVariables, causes, priorityProperties,
                Map.of());
    }

    public static Expression forInlinedMethod(Identifier identifier,
                                              ParameterizedType parameterizedType,
                                              CausesOfDelay causes) {
        String msg = brackets("inline");
        return new DelayedExpression(identifier, msg, parameterizedType, LinkedVariables.EMPTY, causes);
    }

    public static Expression forTooComplex(Identifier identifier, ParameterizedType parameterizedType, CausesOfDelay causes) {
        assert parameterizedType.isPrimitiveExcludingVoid();
        String msg = brackets("too complex");
        return new DelayedExpression(identifier, msg, parameterizedType, LinkedVariables.EMPTY, causes);
        // result is an int, so no linked variables
    }

    // explicit constructor invocation is delayed
    public static Expression forECI(Identifier identifier, CausesOfDelay eciDelay) {
        return new DelayedExpression(identifier, "<eci>", ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR,
                LinkedVariables.NOT_YET_SET, eciDelay);
    }

    public static Expression forSimplification(Identifier identifier,
                                               ParameterizedType returnType,
                                               CausesOfDelay causes) {
        // linked variables are empty, because the return type must be primitive
        assert returnType.isPrimitiveExcludingVoid() : "Simplification must represent primitives";
        return new DelayedExpression(identifier, "<simplification>", returnType, LinkedVariables.EMPTY, causes);
    }

    public static Expression forNullCheck(Identifier identifier, Primitives primitives, CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<null-check>", primitives.booleanParameterizedType(),
                LinkedVariables.EMPTY, causes);
    }

    public static Expression forOutOfScope(Identifier identifier,
                                           String variableName,
                                           ParameterizedType parameterizedType,
                                           CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<oos:" + variableName + ">", parameterizedType,
                LinkedVariables.EMPTY, causes);
    }

    /*
    variable fields have different values according to statement time, but then, at this point we cannot know yet
    whether the field will be variable or not.
    Basics7 shows a case where the local condition manager goes from true to false depending on this equality.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DelayedExpression de && identifier.equals(de.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
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
    public void visit(Predicate<Element> predicate) {
        predicate.test(this);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String text;
        if (qualification == Qualification.FULLY_QUALIFIED_NAME) {
            // for use in the scope of field references
            text = identifier.compact();
        } else {
            text = msg;
        }
        return new OutputBuilder().add(new Text(text));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        cnnMap.forEach((v, dv) -> builder.setProperty(v, Property.CONTEXT_NOT_NULL, dv));
        return builder.setExpression(this).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        DV priority = priorityProperties.getOrDefaultNull(property);
        if (priority != null) return priority;
        return causesOfDelay;
    }

    // See Loops_19: during merging, local loop variables are replaced. The variables in the DelayedExpression.variables
    // list need to be replaced as well.
    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        if (linkedVariables.isEmpty()) return this;
        LinkedVariables translated = linkedVariables.translate(translationMap);
        if (translated == linkedVariables) return this;
        return new DelayedExpression(identifier, msg, translationMap.translateType(parameterizedType),
                translated, causesOfDelay);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return List.copyOf(linkedVariables.variables().keySet());
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
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

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new DelayedExpression(identifier, msg, parameterizedType, linkedVariables, this.causesOfDelay.merge(causesOfDelay));
    }
}

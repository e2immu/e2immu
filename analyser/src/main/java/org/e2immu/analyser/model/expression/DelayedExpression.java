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
import org.e2immu.analyser.model.variable.FieldReference;
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
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL_DV;

@E2Container
public final class DelayedExpression extends BaseExpression implements Expression {
    private final String msg;
    private final ParameterizedType parameterizedType;
    private final List<Variable> variables;
    private final CausesOfDelay causesOfDelay;
    private final Properties priorityProperties;
    private final Map<Variable, DV> cnnMap;
    private final Map<FieldInfo, Expression> shortCutMap;
    private final boolean concreteImplementationForthcoming; // see e.g. Lambda_6

    private DelayedExpression(Identifier identifier,
                              String msg,
                              ParameterizedType parameterizedType,
                              List<Variable> variables,
                              CausesOfDelay causesOfDelay) {
        this(identifier, msg, parameterizedType, variables, causesOfDelay, Properties.EMPTY, Map.of(),
                null, false);
    }

    private DelayedExpression(Identifier identifier,
                              String msg,
                              ParameterizedType parameterizedType,
                              List<Variable> variables,
                              CausesOfDelay causesOfDelay,
                              Properties properties,
                              Map<Variable, DV> cnnMap,
                              Map<FieldInfo, Expression> shortCutMap,
                              boolean concreteImplementationForthcoming) {
        super(identifier);
        this.msg = msg;
        this.parameterizedType = parameterizedType;
        this.causesOfDelay = causesOfDelay;
        assert causesOfDelay.isDelayed();
        this.priorityProperties = properties;
        this.cnnMap = Objects.requireNonNull(cnnMap);
        this.variables = variables;
        this.shortCutMap = shortCutMap;
        this.concreteImplementationForthcoming = concreteImplementationForthcoming;
    }

    private static String brackets(String msg) {
        return "<" + msg + ">";
    }

    public static DelayedExpression forMethod(Identifier identifier,
                                              MethodInfo methodInfo,
                                              ParameterizedType concreteReturnType,
                                              List<Variable> variables,
                                              CausesOfDelay causesOfDelay,
                                              Map<Variable, DV> cnnMap,
                                              boolean concreteImplementationForthcoming) {
        String msg = brackets("m:" + methodInfo.name);
        return new DelayedExpression(identifier, msg, concreteReturnType, variables, causesOfDelay,
                Properties.EMPTY, cnnMap, null, concreteImplementationForthcoming);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(Identifier identifier,
                                      ParameterizedType parameterizedType,
                                      List<Variable> variables,
                                      CausesOfDelay causes) {
        String msg = brackets("s:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes);
    }

    public static Expression forCondition(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          List<Variable> variables,
                                          CausesOfDelay causes) {
        String msg = brackets("c:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes);
    }

    public static Expression forNewObject(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          DV notNull,
                                          List<Variable> variables,
                                          CausesOfDelay causes,
                                          Map<FieldInfo, Expression> shortCutMap) {
        assert notNull.ge(EFFECTIVELY_NOT_NULL_DV);
        String msg = brackets("new:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes, Properties.EMPTY,
                Map.of(), shortCutMap, false);
    }

    public static Expression forArrayAccessValue(Identifier identifier,
                                                 ParameterizedType parameterizedType,
                                                 List<Variable> variables,
                                                 CausesOfDelay causes) {
        String msg = brackets("array-access:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes);
    }

    public static Expression forReplacementObject(ParameterizedType parameterizedType,
                                                  List<Variable> variables,
                                                  CausesOfDelay causes) {
        String msg = brackets("replace:" + parameterizedType.printSimple());
        return new DelayedExpression(Identifier.generate("replacement"), msg, parameterizedType, variables, causes);
    }

    public static Expression forArrayLength(Identifier identifier,
                                            Primitives primitives,
                                            List<Variable> variables,
                                            CausesOfDelay causes) {
        String msg = brackets("delayed array length");
        return new DelayedExpression(identifier, msg, primitives.intParameterizedType(), variables, causes);
        // result is an int, so no linked variables
    }

    public static Expression forPrecondition(Identifier identifier,
                                             Primitives primitives,
                                             List<Variable> variables,
                                             CausesOfDelay causes) {
        String msg = brackets("precondition");
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), variables, causes);
    }

    public static Expression forSwitchSelector(Identifier identifier,
                                               Primitives primitives,
                                               List<Variable> variables,
                                               CausesOfDelay causes) {
        String msg = brackets("switch-selector");
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), variables, causes);
    }

    public static Expression forInstanceOf(Identifier identifier,
                                           Primitives primitives,
                                           ParameterizedType parameterizedType,
                                           List<Variable> variables,
                                           CausesOfDelay causes) {
        String msg = brackets("instanceOf:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), variables, causes);
    }

    public static Expression forUnspecifiedLoopCondition(Identifier identifier,
                                                         ParameterizedType booleanParameterizedType,
                                                         List<Variable> variables,
                                                         CausesOfDelay causes) {
        String msg = brackets("loopIsNotEmptyCondition");
        return new DelayedExpression(identifier, msg, booleanParameterizedType, variables, causes);
    }

    public static Expression forValueOf(ParameterizedType parameterizedType,
                                        List<Variable> variables,
                                        CausesOfDelay causesOfDelay) {
        String msg = brackets("valueOf:" + parameterizedType.printSimple());
        return new DelayedExpression(Identifier.generate("valueOf"), msg, parameterizedType, variables, causesOfDelay);
    }

    public static Expression forDelayedValueProperties(Identifier identifier,
                                                       ParameterizedType parameterizedType,
                                                       List<Variable> variables,
                                                       CausesOfDelay causes,
                                                       Properties priorityProperties) {
        String msg = brackets("vp:" + parameterizedType.printSimple() + ":" + causes);
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes, priorityProperties,
                Map.of(), null, false);
    }

    public static Expression forInlinedMethod(Identifier identifier,
                                              ParameterizedType parameterizedType,
                                              List<Variable> variables,
                                              CausesOfDelay causes) {
        String msg = brackets("inline");
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes);
    }

    public static Expression forTooComplex(Identifier identifier,
                                           ParameterizedType parameterizedType,
                                           List<Variable> variables,
                                           CausesOfDelay causes) {
        assert parameterizedType.isPrimitiveExcludingVoid();
        String msg = brackets("too complex");
        return new DelayedExpression(identifier, msg, parameterizedType, variables, causes);
    }

    // explicit constructor invocation is delayed
    public static Expression forECI(Identifier identifier,
                                    List<Variable> variables,
                                    CausesOfDelay eciDelay) {
        return new DelayedExpression(identifier, "<eci>", ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR, variables, eciDelay);
    }

    public static Expression forSimplification(Identifier identifier,
                                               ParameterizedType returnType,
                                               List<Variable> variables,
                                               CausesOfDelay causes) {
        // linked variables are empty, because the return type must be primitive
        assert returnType.isPrimitiveExcludingVoid() : "Simplification must represent primitives";
        return new DelayedExpression(identifier, "<simplification>", returnType, variables, causes);
    }

    public static Expression forNullCheck(Identifier identifier,
                                          Primitives primitives,
                                          List<Variable> variables,
                                          CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<null-check>", primitives.booleanParameterizedType(), variables, causes);
    }

    public static Expression forOutOfScope(Identifier identifier,
                                           String variableName,
                                           ParameterizedType parameterizedType,
                                           List<Variable> variables,
                                           CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<oos:" + variableName + ">", parameterizedType, variables, causes);
    }

    public static Expression forConstructorCallExpansion(Identifier identifier,
                                                         String typeName,
                                                         ParameterizedType parameterizedType,
                                                         List<Variable> variables,
                                                         CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<cc-exp:" + typeName + ">", parameterizedType, variables, causes);
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
        if (variables.isEmpty()) return this;
        List<Variable> translated = variables.stream().map(translationMap::translateVariable).toList();
        if (translated.equals(variables)) return this;
        return new DelayedExpression(identifier, msg, translationMap.translateType(parameterizedType),
                translated, causesOfDelay);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return variables;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return LinkedVariables.EMPTY;
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

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new DelayedExpression(identifier, msg, parameterizedType, variables, this.causesOfDelay.merge(causesOfDelay));
    }

    // see VariableExpression.tryShortCut, ConstructorCall.evaluate
    public Expression shortCutDelay(FieldInfo fieldInfo) {
        if (shortCutMap != null) {
            return shortCutMap.get(fieldInfo);
        }
        return null;
    }

    public Map<Variable, Expression> shortCutVariables(TypeInfo currentType, Expression scope) {
        if (shortCutMap == null) return Map.of();
        return shortCutMap.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> new FieldReference(InspectionProvider.DEFAULT, e.getKey(),
                                scope, currentType),
                        Map.Entry::getValue));
    }

    @Override
    public boolean concreteImplementationForthcoming() {
        return concreteImplementationForthcoming;
    }
}

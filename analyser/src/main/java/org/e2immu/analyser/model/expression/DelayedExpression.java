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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.MultiLevel.EFFECTIVELY_NOT_NULL_DV;

/*
A delayed expression stores the original expression, rather than the variables needed to generate (delayed) linked variables,
because the translation needs to work for ExplicitConstructorInvocations. See e.g. ECI_7 where there are explicit tests.
 */
public final class DelayedExpression extends BaseExpression implements Expression {
    public static final String ECI = "<eci>";
    public static final DelayedExpression NO_POST_CONDITION_INFO = new DelayedExpression(Identifier.CONSTANT,
            "post-condition",
            ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR, EmptyExpression.EMPTY_EXPRESSION,
            DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.NO_POST_CONDITION_INFO)));
    public static final DelayedExpression NO_STATIC_SIDE_EFFECT_INFO = new DelayedExpression(Identifier.CONSTANT,
            "static-side-effect", ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR, EmptyExpression.EMPTY_EXPRESSION,
            DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.NO_SSE_INFO)));
    public static final String NULL_CHECK = "<null-check>";
    private final String msg;
    private final ParameterizedType parameterizedType;
    private final Expression original;
    private final CausesOfDelay causesOfDelay;
    private final Properties priorityProperties;
    private final Map<Variable, DV> cnnMap;
    private final Map<FieldInfo, Expression> shortCutMap;

    private DelayedExpression(Identifier identifier,
                              String msg,
                              ParameterizedType parameterizedType,
                              Expression original,
                              CausesOfDelay causesOfDelay) {
        this(identifier, msg, parameterizedType, original, causesOfDelay, Properties.EMPTY, Map.of(), null);
    }

    private DelayedExpression(Identifier identifier,
                              String msg,
                              ParameterizedType parameterizedType,
                              Expression original,
                              CausesOfDelay causesOfDelay,
                              Properties properties,
                              Map<Variable, DV> cnnMap,
                              Map<FieldInfo, Expression> shortCutMap) {
        super(identifier, original.getComplexity());
        this.msg = msg;
        this.parameterizedType = parameterizedType;
        this.causesOfDelay = causesOfDelay;
        assert causesOfDelay.isDelayed();
        this.priorityProperties = properties;
        this.cnnMap = Objects.requireNonNull(cnnMap);
        this.original = Objects.requireNonNull(original);
        this.shortCutMap = shortCutMap;
    }

    private static String brackets(String msg) {
        return "<" + msg + ">";
    }

    public static DelayedExpression forMethod(Identifier identifier,
                                              MethodInfo methodInfo,
                                              ParameterizedType concreteReturnType,
                                              Expression original,
                                              CausesOfDelay causesOfDelay,
                                              Map<Variable, DV> cnnMap) {
        String msg = brackets("m:" + methodInfo.name);
        return new DelayedExpression(identifier, msg, concreteReturnType, original, causesOfDelay,
                Properties.EMPTY, cnnMap, null);
    }

    /*
    expression with delayed state
     */
    public static Expression forState(Identifier identifier,
                                      ParameterizedType parameterizedType,
                                      Expression original,
                                      CausesOfDelay causes) {
        String msg = brackets("s:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes);
    }

    public static Expression forCondition(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          Expression original,
                                          CausesOfDelay causes) {
        String msg = brackets("c:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes);
    }

    public static Expression forNewObject(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          DV notNull,
                                          Expression original,
                                          CausesOfDelay causes,
                                          Map<FieldInfo, Expression> shortCutMap) {
        assert notNull.ge(EFFECTIVELY_NOT_NULL_DV);
        String msg = brackets("new:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes, Properties.EMPTY,
                Map.of(), shortCutMap);
    }

    public static Expression forArrayAccessValue(Identifier identifier,
                                                 ParameterizedType parameterizedType,
                                                 Expression original,
                                                 CausesOfDelay causes) {
        String msg = brackets("array-access:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes);
    }

    public static Expression forReplacementObject(ParameterizedType parameterizedType,
                                                  Expression original,
                                                  CausesOfDelay causes) {
        String msg = brackets("replace:" + parameterizedType.printSimple());
        return new DelayedExpression(Identifier.generate("replacement"), msg, parameterizedType, original, causes);
    }

    public static Expression forModification(Expression original,
                                             CausesOfDelay causes) {
        String msg = brackets("mod:" + original.returnType().printSimple());
        return new DelayedExpression(original.getIdentifier(), msg, original.returnType(), original, causes);
    }

    public static Expression forArrayLength(Identifier identifier,
                                            Primitives primitives,
                                            Expression original,
                                            CausesOfDelay causes) {
        String msg = brackets("delayed array length");
        return new DelayedExpression(identifier, msg, primitives.intParameterizedType(), original, causes);
        // result is an int, so no linked variables
    }

    public static Expression forPrecondition(Identifier identifier,
                                             Primitives primitives,
                                             Expression original,
                                             CausesOfDelay causes) {
        String msg = brackets("precondition");
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), original, causes);
    }

    public static Expression forSwitchSelector(Identifier identifier,
                                               Primitives primitives,
                                               Expression original,
                                               CausesOfDelay causes) {
        String msg = brackets("switch-selector");
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), original, causes);
    }

    public static Expression forInstanceOf(Identifier identifier,
                                           Primitives primitives,
                                           ParameterizedType parameterizedType,
                                           Expression original,
                                           CausesOfDelay causes) {
        String msg = brackets("instanceOf:" + parameterizedType.printSimple());
        return new DelayedExpression(identifier, msg, primitives.booleanParameterizedType(), original, causes);
    }


    public static Expression forStaticSideEffects(Identifier identifier,
                                                  Primitives primitives,
                                                  Expression original,
                                                  CausesOfDelay causes) {
        return new DelayedExpression(identifier, "sse", primitives.voidParameterizedType(), original, causes);
    }

    public static Expression forUnspecifiedLoopCondition(Identifier identifier,
                                                         ParameterizedType booleanParameterizedType,
                                                         Expression original,
                                                         CausesOfDelay causes) {
        String msg = brackets("loopIsNotEmptyCondition");
        return new DelayedExpression(identifier, msg, booleanParameterizedType, original, causes);
    }

    public static Expression forValueOf(ParameterizedType parameterizedType,
                                        Expression original,
                                        CausesOfDelay causesOfDelay) {
        String msg = brackets("valueOf:" + parameterizedType.printSimple());
        return new DelayedExpression(Identifier.generate("valueOf"), msg, parameterizedType, original, causesOfDelay);
    }

    public static Expression forDelayedValueProperties(Identifier identifier,
                                                       ParameterizedType parameterizedType,
                                                       Expression original,
                                                       CausesOfDelay causes,
                                                       Properties priorityProperties) {
        String msg = brackets("vp:" + parameterizedType.printSimple() + ":" + causes);
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes, priorityProperties,
                Map.of(), null);
    }

    public static Expression forInlinedMethod(Identifier identifier,
                                              ParameterizedType parameterizedType,
                                              Expression original,
                                              CausesOfDelay causes) {
        String msg = brackets("inline");
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes);
    }

    public static Expression forTooComplex(Identifier identifier,
                                           ParameterizedType parameterizedType,
                                           Expression original,
                                           CausesOfDelay causes) {
        assert parameterizedType.isPrimitiveExcludingVoid();
        String msg = brackets("too complex");
        return new DelayedExpression(identifier, msg, parameterizedType, original, causes);
    }

    // explicit constructor invocation is delayed
    public static Expression forECI(Identifier identifier,
                                    Expression original,
                                    CausesOfDelay eciDelay) {
        return new DelayedExpression(identifier, ECI, ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR, original,
                eciDelay);
    }

    public static Expression forSimplification(Identifier identifier,
                                               ParameterizedType returnType,
                                               Expression original,
                                               CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<simplification>", returnType, original, causes);
    }

    public static Expression forNullCheck(Identifier identifier,
                                          Primitives primitives,
                                          Expression original,
                                          CausesOfDelay causes) {
        return new DelayedExpression(identifier, NULL_CHECK, primitives.booleanParameterizedType(), original,
                causes);
    }

    public static Expression forOutOfScope(Identifier identifier,
                                           String variableName,
                                           ParameterizedType parameterizedType,
                                           Expression original,
                                           CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<oos:" + variableName + ">", parameterizedType, original, causes);
    }

    public static Expression forConstructorCallExpansion(Identifier identifier,
                                                         String typeName,
                                                         ParameterizedType parameterizedType,
                                                         Expression original,
                                                         CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<cc-exp:" + typeName + ">", parameterizedType, original, causes);
    }

    public static Expression forDelayedCompanionAnalysis(Identifier identifier,
                                                         String name,
                                                         ParameterizedType parameterizedType,
                                                         Expression original,
                                                         CausesOfDelay causes) {
        return new DelayedExpression(identifier, "<companion:" + name + ">", parameterizedType, original, causes);
    }

    /*
    variable fields have different values according to statement time, but then, at this point we cannot know yet
    whether the field will be variable or not.
    Basics7 shows a case where the local condition manager goes from true to false depending on this equality.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DelayedExpression de && original.equals(de.original);
    }

    @Override
    public int hashCode() {
        return original.hashCode();
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
    public void visit(Visitor visitor) {
        visitor.beforeExpression(this);
        visitor.afterExpression(this);
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
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        cnnMap.forEach((v, dv) -> builder.setProperty(v, Property.CONTEXT_NOT_NULL, dv));

        // see InstanceOf_16 as an example on why we should add these...
        // essentially, the return expression may expand, and cause context changes
        for (Variable variable : variables()) {
            if (context.evaluationContext().isPresent(variable)) {
                builder.setProperty(variable, Property.CONTEXT_MODIFIED, causesOfDelay);
                builder.setProperty(variable, Property.CONTEXT_NOT_NULL, causesOfDelay);
                builder.setProperty(variable, Property.CONTEXT_CONTAINER, causesOfDelay);
                builder.setProperty(variable, Property.CONTEXT_IMMUTABLE, causesOfDelay);
            }
        }
        return builder.setExpression(this).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_DELAYED_EXPRESSION;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        if (v instanceof DelayedExpression de) {
            return original.compareTo(de.original);
        }
        throw new ExpressionComparator.InternalError();
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        DV priority = priorityProperties.getOrDefaultNull(property);
        if (priority != null) return priority;
        return causesOfDelay;
    }

    /*
     See Loops_19: during merging, local loop variables are replaced. The variables in the DelayedExpression.variables
     list need to be replaced as well. We also need these replacements in merging (See e.g. FormatterSimplified_2, where
     a pattern variable has to be replaced by an out of scope delayed variable).
     */
    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = original.translate(inspectionProvider, translationMap);
        ParameterizedType translatedType = translationMap.translateType(parameterizedType);
        if (translated == original && translatedType == parameterizedType) return this;
        return new DelayedExpression(identifier, msg, translatedType, translated, causesOfDelay);
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        return original.variables(descendIntoFieldReferences);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        // we descend into "this", see e.g. Loops_17
        Set<Variable> set = new HashSet<>(variables(DescendMode.YES_INCLUDE_THIS));
        return LinkedVariables.of(set.stream().collect(Collectors.toUnmodifiableMap(v -> v, v -> causesOfDelay)));
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

    // for testing, recursive
    public Expression getDoneOriginal() {
        if (original instanceof DelayedExpression de) {
            return de.getDoneOriginal();
        }
        return original;
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new DelayedExpression(identifier, msg, parameterizedType, original, this.causesOfDelay.merge(causesOfDelay),
                priorityProperties, cnnMap, shortCutMap);
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
                .collect(Collectors.toUnmodifiableMap(e ->
                                new FieldReferenceImpl(InspectionProvider.DEFAULT, e.getKey(), scope, currentType),
                        Map.Entry::getValue));
    }

    public Expression getOriginal() {
        return original;
    }
}

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
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public final class Instance extends BaseExpression implements Expression {
    private final ParameterizedType parameterizedType;
    private final Properties valueProperties;

    public static Expression forUnspecifiedLoopCondition(String index, Primitives primitives) {
        return new Instance(Identifier.loopCondition(index), primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Instance forInlinedMethod(Identifier identifier,
                                            ParameterizedType parameterizedType) {
        return new Instance(identifier, parameterizedType,
                Properties.of(Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        Property.IMMUTABLE, MultiLevel.MUTABLE_DV,
                        Property.INDEPENDENT, MultiLevel.DEPENDENT_DV,
                        Property.CONTAINER, MultiLevel.NOT_CONTAINER_DV,
                        Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv,
                        Property.IDENTITY, Property.IDENTITY.falseDv)));
    }

    public Expression copyWithImmutable(DV immutable) {
        Properties p = valueProperties.writableCopy().overwrite(Property.IMMUTABLE, immutable);
        return new Instance(identifier, parameterizedType, p);
    }

    public static Expression forMethodResult(Identifier identifier,
                                             ParameterizedType parameterizedType,
                                             Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Expression forSelfAssignmentBreakInit(Identifier identifier,
                                                        ParameterizedType parameterizedType,
                                                        Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Expression forMerge(Identifier identifier,
                                      ParameterizedType parameterizedType,
                                      Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Expression forUnspecifiedCatchCondition(String index, Primitives primitives) {
        return new Instance(Identifier.catchCondition(index),
                primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Instance forTesting(ParameterizedType parameterizedType) {
        return new Instance(Identifier.generate("test instance"), parameterizedType,
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    // never null, never more interesting.
    public static Instance forCatchOrThis(String index, Variable variable, Properties properties) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(VariableIdentifier.variable(variable, index), parameterizedType, properties);
    }

    public static Instance forLoopVariable(String index, Variable variable, Properties valueProperties) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(VariableIdentifier.variable(variable, index),
                parameterizedType, valueProperties);
    }

    /*
    not-null always in properties
     */
    public static Instance initialValueOfParameter(ParameterInfo parameterInfo, Properties valueProperties) {
        return new Instance(VariableIdentifier.variable(parameterInfo), parameterInfo.parameterizedType,
                valueProperties);
    }

    // null-status derived from variable in evaluation context
    public static Instance genericMergeResult(String index, Variable variable, Properties valueProperties) {
        return new Instance(VariableIdentifier.variable(variable, index), variable.parameterizedType(),
                valueProperties);
    }

    public static Expression genericArrayAccess(Identifier identifier,
                                                EvaluationResult context,
                                                Expression array,
                                                Variable variable) {
        DV notNull = context.evaluationContext().getProperty(array, Property.NOT_NULL_EXPRESSION, true, false);
        ParameterizedType baseType = array.returnType().copyWithOneFewerArrays();
        DV notNullOfElement;
        if (baseType.isPrimitiveExcludingVoid()) {
            notNullOfElement = MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        } else {
            notNullOfElement = MultiLevel.composeOneLevelLessNotNull(notNull);
        }

        // we need to go the base type of the array

        Properties properties = context.getAnalyserContext().defaultValueProperties(baseType, notNullOfElement);
        CausesOfDelay delays = properties.delays();
        if (delays.isDelayed()) {
            Stream<Variable> variableStream = Stream.concat(Stream.of(variable), array.variables(true).stream());
            return DelayedExpression.forArrayAccessValue(variable.parameterizedType(),
                    LinkedVariables.sameValue(variableStream, delays), delays);
        }
        return new Instance(identifier, variable.parameterizedType(), properties);
    }

    /*
   getInstance is used by MethodCall to enrich an instance with state.

   cannot be null, we're applying a method on it.
    */
    public static Instance forGetInstance(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Instance forVariableInLoopDefinedOutside(Identifier identifier,
                                                           ParameterizedType parameterizedType,
                                                           Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Instance forTooComplex(Identifier identifier,
                                         ParameterizedType parameterizedType) {
        return new Instance(identifier, parameterizedType, EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public Instance(Identifier identifier,
                    ParameterizedType parameterizedType,
                    Properties valueProperties) {
        super(identifier);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.valueProperties = valueProperties;
        assert internalChecks();
    }

    public static Instance forField(FieldInfo fieldInfo,
                                    ParameterizedType type,
                                    Properties properties) {
        return new Instance(fieldInfo.getIdentifier(), type == null ? fieldInfo.type : type, properties);
    }

    private boolean internalChecks() {
        assert EvaluationContext.VALUE_PROPERTIES.stream().allMatch(valueProperties::containsKey) :
                "Value properties missing! " + valueProperties;
        assert valueProperties.stream()
                .filter(e -> EvaluationContext.VALUE_PROPERTIES.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .noneMatch(DV::isDelayed) : "Properties: " + valueProperties;
        assert !parameterizedType.isJavaLangString() || valueProperties.get(Property.CONTAINER).equals(MultiLevel.CONTAINER_DV);
        assert !parameterizedType.isPrimitiveExcludingVoid() || valueProperties.get(Property.NOT_NULL_EXPRESSION).equals(MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        return identifier.equals(instance.identifier) && parameterizedType.equals(instance.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, parameterizedType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        ParameterizedType translated = translationMap.translateType(this.parameterizedType);
        if (translated == this.parameterizedType) return this;
        return new Instance(identifier, translated, valueProperties);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE;
    }


    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return LinkedVariables.EMPTY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if (!(v instanceof Instance)) {
            return 1; // we're at the back; Instance is used as "too complex" in boolean expressions
        }
        return parameterizedType.detailedString()
                .compareTo(((Instance) v).parameterizedType.detailedString());
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return switch (property) {
            case IGNORE_MODIFICATIONS, IDENTITY, IMMUTABLE, NOT_NULL_EXPRESSION, CONTAINER, INDEPENDENT -> valueProperties.get(property);
            case CONTEXT_MODIFIED -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("NewObject has no value for " + property);
        };
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        predicate.test(this);
    }

    @Override
    public boolean isNumeric() {
        return parameterizedType.isType() && parameterizedType.typeInfo.isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();

        Text text = new Text(text() + "instance type " + parameterizedType.printSimple());
        outputBuilder.add(text);

        if (qualification == Qualification.FULLY_QUALIFIED_NAME) {
            outputBuilder.add(Space.ONE).add(new Text(identifier.compact()));
        } else {
            // not consistent, but hack after changing 10s of tests, don't want to change back again
            if (valueProperties.getOrDefault(Property.IDENTITY, DV.FALSE_DV).valueIsTrue()) {
                outputBuilder.add(new Text("/*@Identity*/"));
            }
            DV ignoreMods = valueProperties.getOrDefault(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
            if (ignoreMods.equals(MultiLevel.IGNORE_MODS_DV)) {
                outputBuilder.add(new Text("/*@IgnoreMods*/"));
            }
        }
        return outputBuilder;
    }

    private String text() {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType != null && bestType.isPrimitiveExcludingVoid()) return "";
        DV minimalNotNull = valueProperties.getOrDefault(Property.NOT_NULL_EXPRESSION, MultiLevel.NULLABLE_DV);
        if (minimalNotNull.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return "nullable ";
        return "";
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(parameterizedType.typesReferenced(true));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of();
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation) {
        return new EvaluationResult.Builder(context).setExpression(this).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(context).setExpression(this).build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public Identifier identifier() {
        return identifier;
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public Properties valueProperties() {
        return valueProperties;
    }

}

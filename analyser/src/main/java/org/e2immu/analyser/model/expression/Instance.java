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
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public final class Instance extends BaseExpression implements Expression {
    private final ParameterizedType parameterizedType;
    private final Properties valueProperties;
    private final String index;

    public static Expression forArrayAccess(Identifier identifier,
                                            String index,
                                            ParameterizedType parameterizedType,
                                            Properties properties) {
        return new Instance(identifier, index, parameterizedType, properties);
    }

    public static Expression forUnspecifiedLoopCondition(String index, Primitives primitives) {
        return new Instance(Identifier.loopCondition(index), index, primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Expression forUnspecifiedCondition(Identifier identifier, String index, Primitives primitives) {
        return new Instance(identifier, index, primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Expression forMethodResult(Identifier identifier,
                                             String index,
                                             ParameterizedType parameterizedType,
                                             Properties valueProperties) {
        return new Instance(identifier, index, parameterizedType, valueProperties);
    }

    public static Expression forSelfAssignmentBreakInit(Identifier identifier,
                                                        String index,
                                                        ParameterizedType parameterizedType,
                                                        Properties valueProperties) {
        return new Instance(identifier, index, parameterizedType, valueProperties);
    }

    public static Expression forMerge(Identifier identifier,
                                      String index,
                                      ParameterizedType parameterizedType,
                                      Properties valueProperties) {
        return new Instance(identifier, index, parameterizedType, valueProperties);
    }

    public static Expression forUnspecifiedCatchCondition(Primitives primitives, Identifier identifier, String index) {
        return new Instance(identifier, index, primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Instance forTesting(ParameterizedType parameterizedType) {
        return new Instance(Identifier.generate("test instance"), "0", parameterizedType,
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    // never null, never more interesting.
    public static Instance forCatchOrThis(String index, Variable variable, Properties properties) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(VariableIdentifier.variable(variable, index), index, parameterizedType, properties);
    }

    public static Instance forLoopVariable(Identifier identifier, String index, Variable variable, Properties valueProperties) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(identifier, index, parameterizedType, valueProperties);
    }

    /*
    not-null always in properties
     */
    public static Instance initialValueOfParameter(ParameterInfo parameterInfo, Properties valueProperties) {
        return new Instance(VariableIdentifier.variable(parameterInfo), VariableInfoContainer.NOT_YET_READ,
                parameterInfo.parameterizedType, valueProperties);
    }

    // null-status derived from variable in evaluation context
    public static Instance genericMergeResult(String index, Variable variable, Properties valueProperties) {
        return new Instance(VariableIdentifier.variable(variable, index), index, variable.parameterizedType(),
                valueProperties);
    }

    public static Expression genericArrayAccess(Identifier identifier,
                                                EvaluationResult context,
                                                Expression array,
                                                Variable variable) {
        DV notNull = context.evaluationContext().getProperty(array, Property.NOT_NULL_EXPRESSION,
                true, false);
        ParameterizedType baseType = array.returnType().copyWithOneFewerArrays();
        DV notNullOfElement;
        if (baseType.isPrimitiveExcludingVoid()) {
            notNullOfElement = MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        } else {
            notNullOfElement = MultiLevel.composeOneLevelLessNotNull(notNull);
        }

        // we need to go the base type of the array

        Properties properties = context.evaluationContext().defaultValueProperties(baseType, notNullOfElement);
        CausesOfDelay delays = properties.delays();
        if (delays.isDelayed()) {
            return DelayedExpression.forArrayAccessValue(identifier, variable.parameterizedType(),
                    new VariableExpression(identifier, variable), delays);
        }
        return new Instance(identifier, context.evaluationContext().statementIndex(), variable.parameterizedType(),
                properties);
    }

    /*
   getInstance is used by MethodCall to enrich an instance with state.

   cannot be null, we're applying a method on it.
    */
    public static Instance forGetInstance(Identifier identifier,
                                          String index,
                                          ParameterizedType parameterizedType,
                                          Properties valueProperties) {
        return new Instance(identifier, index, parameterizedType, valueProperties);
    }

    public static Instance forVariableInLoopDefinedOutside(Identifier identifier,
                                                           String index,
                                                           ParameterizedType parameterizedType,
                                                           Properties valueProperties) {
        return new Instance(identifier, index, parameterizedType, valueProperties);
    }

    // must only be used by ExpressionCanBeTooComplex.reducedComplexity
    public static Instance forTooComplex(Identifier identifier,
                                         String index,
                                         ParameterizedType parameterizedType) {
        return new Instance(identifier, index, parameterizedType, EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Instance forTooComplex(Identifier identifier,
                                         String index,
                                         ParameterizedType parameterizedType,
                                         Properties properties) {
        return new Instance(identifier, index, parameterizedType, properties);
    }

    public Instance(Identifier identifier,
                    String index,
                    ParameterizedType parameterizedType,
                    Properties valueProperties) {
        super(identifier, 3);
        this.index = index;
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.valueProperties = valueProperties;
        assert internalChecks();
    }

    public static Instance forField(FieldInfo fieldInfo,
                                    ParameterizedType type,
                                    Properties properties) {
        return new Instance(fieldInfo.getIdentifier(), VariableInfoContainer.NOT_YET_READ,
                type == null ? fieldInfo.type : type, properties);
    }

    private boolean internalChecks() {
        assert EvaluationContext.VALUE_PROPERTIES.stream().allMatch(valueProperties::containsKey) :
                "Value properties missing! " + valueProperties;
        assert valueProperties.stream()
                .filter(e -> e.getKey().propertyType == Property.PropertyType.VALUE)
                .map(Map.Entry::getValue)
                .noneMatch(DV::isDelayed) : "Properties: " + valueProperties;
        assert !parameterizedType.isJavaLangString() || valueProperties.get(Property.CONTAINER).equals(MultiLevel.CONTAINER_DV);
        // FIXME currently gone for InstanceOf_11 -- need to put back
        // assert !parameterizedType.isPrimitiveExcludingVoid() || valueProperties.get(Property.NOT_NULL_EXPRESSION).equals(MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        return true;
    }

    private static final Logger EQUALS = LoggerFactory.getLogger(Configuration.EQUALS);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        if (EQUALS.isDebugEnabled()) {
            boolean e1 = identifier.equals(instance.identifier);
            boolean e2 = parameterizedType.equals(instance.parameterizedType);
            EQUALS.debug("Instance: {}={} && {}: identifier {} vs {}, pt {} vs {}",
                    e1 && e2, e1, e2,
                    identifier.compact(), instance.identifier.compact(),
                    parameterizedType, instance.parameterizedType);
        }
        return identifier.equals(instance.identifier) && parameterizedType.equals(instance.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, parameterizedType);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        ParameterizedType translatedType = translationMap.translateType(this.parameterizedType);
        if (translatedType == this.parameterizedType) return this;
        return new Instance(identifier, index, translatedType, valueProperties);
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
            case IGNORE_MODIFICATIONS, IDENTITY, IMMUTABLE, NOT_NULL_EXPRESSION, CONTAINER, INDEPENDENT ->
                    valueProperties.get(property);
            case CONTEXT_MODIFIED -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("NewObject has no value for " + property);
        };
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

        // To help debug problems of overwriting values, replace "instance" (or add) the identifier.compact() string
        Text text = new Text(text() + "instance" + modifiedText() + "type " + parameterizedType.printSimple());
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

    private String modifiedText() {
        if (index != null && !index.equals(VariableInfoContainer.NOT_YET_READ)) {
            return " " + index + " ";
        }
        return " ";
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
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResultImpl.Builder(context).setExpression(this).build();
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public Properties valueProperties() {
        return valueProperties;
    }

    public String getIndex() {
        return index;
    }

    @Override
    public DV hardCodedPropertyOrNull(Property property) {
        return valueProperties.getOrDefaultNull(property);
    }
}

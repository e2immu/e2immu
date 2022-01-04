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
    private final Diamond diamond;
    private final Map<Property, DV> valueProperties;

    public static Map<Property, DV> primitiveValueProperties() {
        return Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                Property.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
                Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV,
                Property.CONTAINER, DV.TRUE_DV,
                Property.IDENTITY, DV.FALSE_DV);
    }

    public static Expression forUnspecifiedLoopCondition(String index, Primitives primitives) {
        return new Instance(Identifier.loopCondition(index), primitives.booleanParameterizedType(), Diamond.NO,
                primitiveValueProperties());
    }

    public static Expression genericFieldAccess(InspectionProvider inspectionProvider, FieldInfo fieldInfo,
                                                Map<Property, DV> valueProperties) {
        return new Instance(Identifier.generate(),
                fieldInfo.owner.asParameterizedType(inspectionProvider), Diamond.NO,
                valueProperties);
    }

    // IMPROVE should this not be delayed?
    public static Instance forInlinedMethod(Identifier identifier,
                                            ParameterizedType parameterizedType) {
        return new Instance(identifier, parameterizedType, Diamond.SHOW_ALL,
                Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        Property.IMMUTABLE, MultiLevel.MUTABLE_DV,
                        Property.INDEPENDENT, MultiLevel.DEPENDENT_DV,
                        Property.CONTAINER, DV.FALSE_DV,
                        Property.IDENTITY, DV.FALSE_DV));
    }

    public static Expression forMethodResult(Identifier identifier,
                                             ParameterizedType parameterizedType,
                                             Map<Property, DV> valueProperties) {
        return new Instance(identifier, parameterizedType, Diamond.SHOW_ALL, valueProperties);
    }

    public static Expression forUnspecifiedCatchCondition(String index, Primitives primitives) {
        return new Instance(Identifier.catchCondition(index),
                primitives.booleanParameterizedType(), Diamond.NO,
                primitiveValueProperties());
    }

    public static Instance forTesting(ParameterizedType parameterizedType) {
        return new Instance(Identifier.generate(), parameterizedType, Diamond.SHOW_ALL,
                primitiveValueProperties());
    }

    // never null, never more interesting.
    public static Instance forCatchOrThis(String index, Variable variable, AnalysisProvider analysisProvider) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new Instance(VariableIdentifier.variable(variable, index),
                parameterizedType, diamond,
                Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        Property.IMMUTABLE, defaultImmutable(parameterizedType, analysisProvider),
                        Property.INDEPENDENT, defaultIndependent(parameterizedType, analysisProvider),
                        Property.CONTAINER, defaultContainer(parameterizedType, analysisProvider),
                        Property.IDENTITY, DV.FALSE_DV));
    }

    private static DV defaultIndependent(ParameterizedType parameterizedType, AnalysisProvider analysisProvider) {
        DV v = analysisProvider.defaultIndependent(parameterizedType);
        return v.replaceDelayBy(MultiLevel.DEPENDENT_DV);
    }

    private static DV defaultImmutable(ParameterizedType parameterizedType, AnalysisProvider analysisProvider) {
        DV v = analysisProvider.defaultImmutable(parameterizedType, false);
        return v.replaceDelayBy(MultiLevel.MUTABLE_DV);
    }

    private static DV defaultContainer(ParameterizedType parameterizedType, AnalysisProvider analysisProvider) {
        DV v = analysisProvider.defaultContainer(parameterizedType);
        return v.replaceDelayBy(DV.FALSE_DV);
    }

    public static Instance forLoopVariable(String index, Variable variable, Map<Property, DV> valueProperties) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new Instance(VariableIdentifier.variable(variable, index),
                parameterizedType, diamond, valueProperties);
    }
    /*
     local variable, defined outside a loop, will be assigned inside the loop
     don't assume that this instance is non-null straight away; state is also generic at this point
     */

    public static Expression localVariableInLoop(String index, Variable variable, AnalysisProvider analysisProvider) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        DV immutable = defaultImmutable(parameterizedType, analysisProvider);
        DV independent = defaultIndependent(parameterizedType, analysisProvider);
        DV container = defaultContainer(parameterizedType, analysisProvider);
        if (independent.isDelayed() || immutable.isDelayed() || container.isDelayed()) {
            CausesOfDelay causes = independent.causesOfDelay().merge(immutable.causesOfDelay()).merge(container.causesOfDelay());
            return DelayedExpression.forLocalVariableInLoop(parameterizedType, LinkedVariables.delayedEmpty(causes),
                    causes);
        }
        return new Instance(VariableIdentifier.variable(variable, index), parameterizedType, Diamond.SHOW_ALL,
                Map.of(Property.NOT_NULL_EXPRESSION, AnalysisProvider.defaultNotNull(parameterizedType),
                        Property.IMMUTABLE, defaultImmutable(parameterizedType, analysisProvider),
                        Property.INDEPENDENT, defaultIndependent(parameterizedType, analysisProvider),
                        Property.CONTAINER, defaultContainer(parameterizedType, analysisProvider),
                        Property.IDENTITY, DV.FALSE_DV));
    }

    public static Instance localCopyOfVariableField(String index, Variable variable, AnalysisProvider analysisProvider) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(VariableIdentifier.variable(variable, index), parameterizedType, Diamond.SHOW_ALL,
                Map.of(Property.NOT_NULL_EXPRESSION, AnalysisProvider.defaultNotNull(parameterizedType),
                        Property.IMMUTABLE, defaultImmutable(parameterizedType, analysisProvider),
                        Property.INDEPENDENT, defaultIndependent(parameterizedType, analysisProvider),
                        Property.CONTAINER, defaultContainer(parameterizedType, analysisProvider),
                        Property.IDENTITY, DV.FALSE_DV));
    }

    /*
    not-null always in properties
     */
    public static Instance initialValueOfParameter(ParameterInfo parameterInfo,
                                                   DV contractNotNull,
                                                   DV immutable,
                                                   DV independent,
                                                   DV container,
                                                   boolean identity) {
        return new Instance(VariableIdentifier.variable(parameterInfo), parameterInfo.parameterizedType,
                Diamond.SHOW_ALL, Map.of(Property.NOT_NULL_EXPRESSION, contractNotNull,
                Property.IMMUTABLE, immutable,
                Property.INDEPENDENT, independent,
                Property.CONTAINER, container,
                Property.IDENTITY, DV.fromBoolDv(identity)));
    }

    public static Instance initialValueOfFieldPartOfConstruction(String index,
                                                                 EvaluationContext evaluationContext,
                                                                 FieldReference fieldReference) {
        DV notNull = evaluationContext.getProperty(fieldReference, Property.NOT_NULL_EXPRESSION);
        return new Instance(VariableIdentifier.variable(fieldReference, index),
                fieldReference.parameterizedType(), Diamond.SHOW_ALL,
                Map.of(Property.NOT_NULL_EXPRESSION, notNull,
                        // TODO
                        Property.IDENTITY, DV.FALSE_DV));
    }

    public static Instance initialValueOfExternalVariableField(FieldReference fieldReference,
                                                               String index,
                                                               DV minimalNotNull,
                                                               AnalysisProvider analysisProvider) {
        ParameterizedType parameterizedType = fieldReference.parameterizedType();
        return new Instance(VariableIdentifier.variable(fieldReference, index),
                fieldReference.parameterizedType(), Diamond.SHOW_ALL,
                Map.of(Property.NOT_NULL_EXPRESSION, minimalNotNull,
                        Property.IMMUTABLE, defaultImmutable(parameterizedType, analysisProvider),
                        Property.INDEPENDENT, defaultIndependent(parameterizedType, analysisProvider),
                        Property.CONTAINER, defaultContainer(parameterizedType, analysisProvider),
                        Property.IDENTITY, DV.FALSE_DV));
    }

    // null-status derived from variable in evaluation context
    public static Instance genericMergeResult(String index,
                                              Variable variable,
                                              Map<Property, DV> valueProperties) {
        return new Instance(VariableIdentifier.variable(variable, index), variable.parameterizedType(),
                Diamond.SHOW_ALL, valueProperties);
    }

    public static Expression genericArrayAccess(Identifier identifier,
                                                EvaluationContext evaluationContext,
                                                Expression array,
                                                Variable variable) {
        DV notNull = evaluationContext.getProperty(array, Property.NOT_NULL_EXPRESSION, true, false);
        if (notNull.isDelayed()) {
            // FIXME notNull
            return DelayedExpression.forNewObject(variable.parameterizedType(), notNull,
                    LinkedVariables.sameValue(Stream.concat(Stream.of(variable), array.variables(true).stream()), notNull),
                    notNull.causesOfDelay());
        }
        DV notNullOfElement = MultiLevel.composeOneLevelLessNotNull(notNull);
        return new Instance(identifier, variable.parameterizedType(), Diamond.SHOW_ALL,
                Map.of(Property.NOT_NULL_EXPRESSION, notNullOfElement,
                        Property.IDENTITY, DV.FALSE_DV));
    }

    /*
   getInstance is used by MethodCall to enrich an instance with state.

   cannot be null, we're applying a method on it.
    */
    public static Instance forGetInstance(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          Map<Property, DV> valueProperties) {
        return new Instance(identifier, parameterizedType, Diamond.SHOW_ALL, valueProperties);
    }

    public Instance(Identifier identifier,
                    ParameterizedType parameterizedType,
                    Diamond diamond,
                    Map<Property, DV> valueProperties) {
        super(identifier);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : diamond;
        this.valueProperties = valueProperties;
        assert internalChecks();
    }

    public static Instance forField(FieldInfo fieldInfo, DV notNull, DV immutable, DV container, DV independent) {
        return new Instance(fieldInfo.getIdentifier(), fieldInfo.type, Diamond.NO, Map.of(
                Property.NOT_NULL_EXPRESSION, notNull,
                Property.IMMUTABLE, immutable,
                Property.CONTAINER, container,
                Property.INDEPENDENT, independent,
                Property.IDENTITY, DV.FALSE_DV));
    }

    private boolean internalChecks() {
        assert EvaluationContext.VALUE_PROPERTIES.stream().allMatch(valueProperties::containsKey) :
                "Value properties missing! " + valueProperties;
        assert valueProperties.values().stream().noneMatch(DV::isDelayed) : "Properties: " + valueProperties;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        return identifier.equals(instance.identifier) &&
                parameterizedType.equals(instance.parameterizedType) &&
                valueProperties.equals(instance.valueProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, valueProperties);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Instance(identifier,
                translationMap.translateType(parameterizedType), diamond, valueProperties);
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
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.EMPTY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((Instance) v).parameterizedType.detailedString());
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return switch (property) {
            /*
            // value properties
            case NOT_NULL_EXPRESSION -> {
                Integer inMap = valueProperties.getOrDefault(variableProperty, null);
                if (inMap != null) yield inMap;
                throw new UnsupportedOperationException("Need a value for NOT_NULL_EXPRESSION");
            }
            case INDEPENDENT -> {
                Integer inMap = valueProperties.getOrDefault(variableProperty, null);
                if (inMap != null) yield inMap;
                if (evaluationContext.isMyself(parameterizedType)) yield MultiLevel.DEPENDENT;
                int defaultValue = parameterizedType.defaultIndependent(evaluationContext.getAnalyserContext());
                if (defaultValue < 0) {
                    throw new UnsupportedOperationException("Need a value for INDEPENDENT");
                }
                yield defaultValue;
            }
            case IDENTITY -> valueProperties.getOrDefault(variableProperty, Level.FALSE);
            case IMMUTABLE -> {
                Integer inMap = valueProperties.getOrDefault(variableProperty, null);
                if (inMap != null) yield inMap;
                if (evaluationContext.isMyself(parameterizedType)) yield MultiLevel.MUTABLE;
                int defaultValue = parameterizedType.defaultImmutable(evaluationContext.getAnalyserContext(), false);
                if (defaultValue < 0) {
                    throw new UnsupportedOperationException("Need a value for IMMUTABLE");
                }
                yield defaultValue;

            }
            case CONTAINER -> {
                Integer inMap = valueProperties.getOrDefault(variableProperty, null);
                if (inMap != null) yield inMap;
                if (evaluationContext.isMyself(parameterizedType)) yield Level.FALSE;
                int defaultValue = parameterizedType.defaultContainer(evaluationContext.getAnalyserContext());
                if (defaultValue < 0) {
                    throw new UnsupportedOperationException("Need a value for CONTAINER");
                }
                yield defaultValue;
            }*/
            case IDENTITY, IMMUTABLE, NOT_NULL_EXPRESSION, CONTAINER, INDEPENDENT -> valueProperties.get(property);
            case CONTEXT_MODIFIED, IGNORE_MODIFICATIONS -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("NewObject has no value for " + property);
        };
        /*
        switch (variableProperty) {
            case NOT_NULL_EXPRESSION: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (Primitives.isPrimitiveExcludingVoid(bestType))
                    return MultiLevel.EFFECTIVELY_NOT_NULL;
                return minimalNotNull;
            }
            case IDENTITY:
                return Level.fromBool(identity);

            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case PROPAGATE_MODIFICATION_DELAY:
            case IGNORE_MODIFICATIONS:
                return Level.FALSE;

            case INDEPENDENT:
                return parameterizedType.defaultIndependent(evaluationContext.getAnalyserContext());

            case CONTAINER: { // must be pretty similar to the code in ParameterAnalysis, because every parameter will be of this type
                Boolean transparent = parameterizedType.isTransparent(evaluationContext.getAnalyserContext(),
                        evaluationContext.getCurrentType());
                if (transparent == Boolean.TRUE) return Level.TRUE;
                // if implicit is null, we cannot return FALSE, we'll have to wait!
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                int withoutDelay;
                if (bestType != null) {
                    withoutDelay = evaluationContext.getAnalyserContext()
                            .getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);
                } else {
                    withoutDelay = Level.FALSE;
                }
                return transparent == null && withoutDelay != Level.TRUE ? Level.DELAY : withoutDelay;
            }
            case IMMUTABLE: {
                int immutable = parameterizedType.defaultImmutable(evaluationContext.getAnalyserContext(), false);
                if (constructor != null) {
                    if (immutable == MultiLevel.EVENTUALLY_E1IMMUTABLE)
                        return MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
                    if (immutable == MultiLevel.EVENTUALLY_E2IMMUTABLE)
                        return MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
                }
                assert immutable != Level.DELAY || evaluationContext.translatedDelay(this.toString(),
                        parameterizedType.bestTypeInfo().fullyQualifiedName + DelayDebugger.D_IMMUTABLE,
                        this + "@" + evaluationContext.statementIndex() + DelayDebugger.D_IMMUTABLE);
                return immutable;
            }
            default:
        }*/
        // @NotModified should not be asked here
        //throw new UnsupportedOperationException("Asking for " + variableProperty);
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

        // TODO not consistent, but hack after changing 10s of tests, don't want to change back again
        if (valueProperties.getOrDefault(Property.IDENTITY, DV.FALSE_DV).valueIsTrue()) {
            outputBuilder.add(new Text("/*@Identity*/"));
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
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public DV getValueProperty(Property property) {
        return valueProperties.get(property);
    }

    public Identifier identifier() {
        return identifier;
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public Diamond diamond() {
        return diamond;
    }

    public Map<Property, DV> valueProperties() {
        return valueProperties;
    }

}

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
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public record ConstructorCall(
        Identifier identifier, // variable FQN + assignment ID
        MethodInfo constructor,
        ParameterizedType parameterizedType,
        Diamond diamond,
        List<Expression> parameterExpressions,
        Map<VariableProperty, Integer> valueProperties,
        TypeInfo anonymousClass,
        ArrayInitializer arrayInitializer) implements HasParameterExpressions {

    private static Map<VariableProperty, Integer> ensureNotNull(Map<VariableProperty, Integer> in) {
        return in.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        e -> e.getValue() < MultiLevel.EFFECTIVELY_NOT_NULL && e.getKey() == VariableProperty.NOT_NULL_EXPRESSION
                                ? MultiLevel.EFFECTIVELY_NOT_NULL : e.getValue()));
    }

    // specific construction and copy methods: we explicitly name construction
    /*
    specific situation, new X[] { 0, 1, 2 } array initialiser
     */

    public static Expression withArrayInitialiser(MethodInfo arrayCreationConstructor,
                                                  ParameterizedType parameterizedType,
                                                  List<Expression> parameterExpressions,
                                                  ArrayInitializer arrayInitializer) {
        return new ConstructorCall(Identifier.generate(), arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, Map.of(),
                null, arrayInitializer);
    }

    public static Expression instanceFromSam(MethodInfo sam, ParameterizedType parameterizedType) {
        return new ConstructorCall(sam.getIdentifier(), null, parameterizedType, Diamond.NO, List.of(),
                Map.of(), sam.typeInfo, null);
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static ConstructorCall withAnonymousClass(@NotNull ParameterizedType parameterizedType,
                                                     @NotNull TypeInfo anonymousClass,
                                                     Diamond diamond) {
        return new ConstructorCall(Identifier.generate(), null, parameterizedType, diamond,
                List.of(), Map.of(), anonymousClass, null);
    }

    /*
    Result of actual object creation expressions (new XX, xx::new, ...)
    This call is made in the inspection phase, before analysis.
    So there are no values yet, except obviously not identity, and effectively not null.
    Others
     */
    public static ConstructorCall objectCreation(Identifier identifier,
                                                 MethodInfo constructor,
                                                 ParameterizedType parameterizedType,
                                                 Diamond diamond,
                                                 List<Expression> parameterExpressions) {
        return new ConstructorCall(identifier, constructor, parameterizedType, diamond, parameterExpressions,
                Map.of(), null, null);
    }


    public Expression removeConstructor() {
        assert arrayInitializer == null;
        return new Instance(identifier, parameterizedType, diamond, valueProperties);
    }

    public ConstructorCall(Identifier identifier,
                           MethodInfo constructor,
                           ParameterizedType parameterizedType,
                           Diamond diamond,
                           List<Expression> parameterExpressions,
                           Map<VariableProperty, Integer> valueProperties,
                           TypeInfo anonymousClass,
                           ArrayInitializer arrayInitializer) {
        assert constructor != null || arrayInitializer != null; // but not both
        this.identifier = identifier;
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor;
        this.anonymousClass = anonymousClass;
        this.arrayInitializer = arrayInitializer;
        this.diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : diamond;
        this.valueProperties = valueProperties;
    }

    @Override
    public TypeInfo definesType() {
        return anonymousClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstructorCall newObject = (ConstructorCall) o;
        return identifier.equals(newObject.identifier) &&
                parameterizedType.equals(newObject.parameterizedType) &&
                parameterExpressions.equals(newObject.parameterExpressions) &&
                Objects.equals(anonymousClass, newObject.anonymousClass) &&
                Objects.equals(constructor, newObject.constructor) &&
                Objects.equals(arrayInitializer, newObject.arrayInitializer) &&
                valueProperties.equals(newObject.valueProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, anonymousClass, constructor,
                arrayInitializer, valueProperties);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ConstructorCall(identifier,
                constructor,
                translationMap.translateType(parameterizedType),
                diamond,
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                valueProperties,
                anonymousClass, // not translating this yet!
                arrayInitializer == null ? null : TranslationMapImpl.ensureExpressionType(arrayInitializer, ArrayInitializer.class));
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
        // instance, no constructor parameter expressions
        if (constructor == null) return LinkedVariables.EMPTY;

        return linkedVariablesFromParameters(evaluationContext, constructor.methodInspection.get(),
                parameterExpressions);
    }

    static LinkedVariables linkedVariablesFromParameters(EvaluationContext evaluationContext,
                                                         MethodInspection methodInspection,
                                                         List<Expression> parameterExpressions) {
        // quick shortcut
        if (parameterExpressions.isEmpty()) {
            return LinkedVariables.EMPTY;
        }

        LinkedVariables result = LinkedVariables.EMPTY;
        int i = 0;
        for (Expression value : parameterExpressions) {
            ParameterInfo parameterInfo;
            if (i < methodInspection.getParameters().size()) {
                parameterInfo = methodInspection.getParameters().get(i);
            } else {
                parameterInfo = methodInspection.getParameters().get(methodInspection.getParameters().size() - 1);
                assert parameterInfo.parameterInspection.get().isVarArgs();
            }
            ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);
            int independentOnParameter = parameterAnalysis.getProperty(VariableProperty.INDEPENDENT);
            LinkedVariables sub = value.linkedVariables(evaluationContext);
            if (independentOnParameter == Level.DELAY) {
                result = result.mergeDelay(sub);
            } else if (independentOnParameter >= MultiLevel.DEPENDENT && independentOnParameter < MultiLevel.INDEPENDENT) {
                result = result.merge(sub, MultiLevel.fromIndependentToLinkedVariableLevel(independentOnParameter));
            }
            i++;
        }
        return result;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((ConstructorCall) v).parameterizedType.detailedString());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return switch (variableProperty) {
            /*
             value properties; errors should appear only when the object is unevaluated
             (because it was created in the inspection phase)
             */
            case NOT_NULL_EXPRESSION, INDEPENDENT, IDENTITY, IMMUTABLE, CONTAINER -> {
                Integer inMap = valueProperties.getOrDefault(variableProperty, null);
                if (inMap != null) yield inMap;
                throw new UnsupportedOperationException("Need a value for " + variableProperty);
            }
            case CONTEXT_MODIFIED, CONTEXT_MODIFIED_DELAY, PROPAGATE_MODIFICATION_DELAY, IGNORE_MODIFICATIONS -> Level.FALSE;
            default -> throw new UnsupportedOperationException("NewObject has no value for " + variableProperty);
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
        if (predicate.test(this)) {
            parameterExpressions.forEach(predicate::test);
        }
    }

    @Override
    public boolean isNumeric() {
        return parameterizedType.isType() && Primitives.isNumeric(parameterizedType.typeInfo);
    }

    @Override
    public MethodInfo getMethodInfo() {
        return constructor;
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (constructor != null || anonymousClass != null) {
            outputBuilder.add(new Text("new")).add(Space.ONE)
                    .add(parameterizedType.output(qualification, false, diamond));
            if (arrayInitializer == null) {
                if (parameterExpressions.isEmpty()) {
                    outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
                } else {
                    outputBuilder
                            .add(Symbol.LEFT_PARENTHESIS)
                            .add(parameterExpressions.stream().map(expression -> expression.output(qualification))
                                    .collect(OutputBuilder.joining(Symbol.COMMA)))
                            .add(Symbol.RIGHT_PARENTHESIS);
                }
            }
        } else {
            Text text = new Text(text() + "instance type " + parameterizedType.printSimple());
            outputBuilder.add(text);
        }
        if (anonymousClass != null) {
            outputBuilder.add(anonymousClass.output(qualification, false));
        }
        if (arrayInitializer != null) {
            outputBuilder.add(arrayInitializer.output(qualification));
        }
        // TODO not consistent, but hack after changing 10s of tests, don't want to change back again
        if (valueProperties.getOrDefault(VariableProperty.IDENTITY, Level.FALSE) == Level.TRUE) {
            outputBuilder.add(new Text("/*@Identity*/"));
        }
        return outputBuilder;
    }

    private String text() {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (Primitives.isPrimitiveExcludingVoid(bestType)) return "";
        int minimalNotNull = valueProperties.getOrDefault(VariableProperty.NOT_NULL_EXPRESSION, VariableProperty.NOT_NULL_EXPRESSION.falseValue);
        if (minimalNotNull < MultiLevel.EFFECTIVELY_NOT_NULL) return "nullable ";
        return "";
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parameterizedType.typesReferenced(true),
                parameterExpressions.stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public List<? extends Element> subElements() {
        return parameterExpressions;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reParams = parameterExpressions.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        List<Expression> reParamValues = reParams.stream().map(EvaluationResult::value).collect(Collectors.toList());
        ConstructorCall newObject = new ConstructorCall(identifier, constructor, parameterizedType,
                diamond, reParamValues, valueProperties, anonymousClass, arrayInitializer);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reParams);
        return builder.setExpression(newObject).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {

        // arrayInitializer variant

        if (arrayInitializer != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            List<EvaluationResult> results = arrayInitializer.multiExpression.stream()
                    .map(e -> e.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT))
                    .collect(Collectors.toList());
            builder.compose(results);
            List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());
            builder.setExpression(new ArrayInitializer(evaluationContext.getAnalyserContext(), values, arrayInitializer.returnType()));
            return builder.build();
        }

        // "normal"

        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                evaluationContext, forwardEvaluationInfo, constructor, false, null);

        ParameterizedType pt;
        if (anonymousClass != null) {
            pt = anonymousClass.asParameterizedType(evaluationContext.getAnalyserContext());
        } else {
            pt = parameterizedType;
        }
        int nne = MultiLevel.EFFECTIVELY_NOT_NULL;
        int immutable = pt.defaultImmutable(evaluationContext.getAnalyserContext(), false);
        int independent = pt.defaultIndependent(evaluationContext.getAnalyserContext());
        int container = pt.defaultContainer(evaluationContext.getAnalyserContext());
        if (nne == Level.DELAY || immutable == Level.DELAY || independent == Level.DELAY || container == Level.DELAY) {
            LinkedVariables linkedVariables = linkedVariables(evaluationContext).changeAllToDelay();
            Expression delayedExpression = DelayedExpression.forNewObject(pt, nne, linkedVariables);
            return res.k.setExpression(delayedExpression).build();
        }

        Map<VariableProperty, Integer> newValueProperties = Map.of(VariableProperty.NOT_NULL_EXPRESSION, nne,
                VariableProperty.IMMUTABLE, immutable, VariableProperty.INDEPENDENT, independent, VariableProperty.CONTAINER, container,
                VariableProperty.IDENTITY, Level.FALSE);

        ConstructorCall initialInstance = new ConstructorCall(identifier, constructor, pt, diamond, res.v,
                newValueProperties, anonymousClass, null);

        // check state changes of companion methods
        MethodAnalysis constructorAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(constructor);
        Expression modifiedInstance = MethodCall.checkCompanionMethodsModifying(res.k, evaluationContext,
                constructor, constructorAnalysis, null, initialInstance, res.v);
        Expression instance = modifiedInstance == null
                ? DelayedExpression.forNewObject(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL,
                initialInstance.linkedVariables(evaluationContext).changeAllToDelay())
                : modifiedInstance;

        res.k.setExpression(instance);

        if ((!constructor.methodResolution.isSet() || constructor.methodResolution.get().allowsInterrupts())) {
            res.k.incrementStatementTime();
        }

        if (anonymousClass != null) {
            evaluationContext.getLocalPrimaryTypeAnalysers().stream()
                    .filter(pta -> pta.primaryTypes.contains(anonymousClass))
                    .forEach(res.k::markVariablesFromPrimaryTypeAnalyser);
        }

        int cImm = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
        if (MultiLevel.isAfterThrowWhenNotEventual(cImm)) {
            res.k.raiseError(getIdentifier(), Message.Label.EVENTUAL_AFTER_REQUIRED);
        }
        return res.k.build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }
}

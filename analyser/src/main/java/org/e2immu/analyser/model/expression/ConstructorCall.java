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
        TypeInfo anonymousClass,
        ArrayInitializer arrayInitializer) implements HasParameterExpressions {

    // specific construction and copy methods: we explicitly name construction
    /*
    specific situation, new X[] { 0, 1, 2 } array initialiser
     */

    public static Expression withArrayInitialiser(MethodInfo arrayCreationConstructor,
                                                  ParameterizedType parameterizedType,
                                                  List<Expression> parameterExpressions,
                                                  ArrayInitializer arrayInitializer) {
        return new ConstructorCall(Identifier.generate(), arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, null, arrayInitializer);
    }

    public static Expression instanceFromSam(MethodInfo sam, ParameterizedType parameterizedType) {
        return new ConstructorCall(sam.getIdentifier(), null, parameterizedType, Diamond.NO, List.of(),
                sam.typeInfo, null);
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static ConstructorCall withAnonymousClass(@NotNull ParameterizedType parameterizedType,
                                                     @NotNull TypeInfo anonymousClass,
                                                     Diamond diamond) {
        return new ConstructorCall(Identifier.generate(), null, parameterizedType, diamond,
                List.of(), anonymousClass, null);
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
                null, null);
    }


    public Expression removeConstructor(Map<Property, DV> valueProperties) {
        assert arrayInitializer == null;
        return new Instance(identifier, parameterizedType, diamond, valueProperties);
    }

    public ConstructorCall(Identifier identifier,
                           MethodInfo constructor,
                           ParameterizedType parameterizedType,
                           Diamond diamond,
                           List<Expression> parameterExpressions,
                           TypeInfo anonymousClass,
                           ArrayInitializer arrayInitializer) {
        this.identifier = identifier;
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor;
        this.anonymousClass = anonymousClass;
        this.arrayInitializer = arrayInitializer;
        this.diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : diamond;
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
                Objects.equals(arrayInitializer, newObject.arrayInitializer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, anonymousClass, constructor,
                arrayInitializer);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ConstructorCall(identifier,
                constructor,
                translationMap.translateType(parameterizedType),
                diamond,
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
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
            DV independentOnParameter = parameterAnalysis.getProperty(Property.INDEPENDENT);
            LinkedVariables sub = value.linkedVariables(evaluationContext);
            if (independentOnParameter.isDelayed()) {
                result = result.mergeDelay(sub, independentOnParameter);
            } else if (independentOnParameter.ge(MultiLevel.DEPENDENT_DV) &&
                    independentOnParameter.lt(MultiLevel.INDEPENDENT_DV)) {
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
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        ParameterizedType pt;
        if (anonymousClass != null) {
            pt = anonymousClass.asParameterizedType(evaluationContext.getAnalyserContext());
        } else {
            pt = parameterizedType;
        }
        return switch (property) {
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case INDEPENDENT -> pt.defaultIndependent(evaluationContext.getAnalyserContext());
            case IDENTITY -> Level.FALSE_DV;
            case IMMUTABLE -> pt.defaultImmutable(evaluationContext.getAnalyserContext(), false);
            case CONTAINER -> pt.defaultContainer(evaluationContext.getAnalyserContext());
            case CONTEXT_MODIFIED, IGNORE_MODIFICATIONS -> Level.FALSE_DV;
            default -> throw new UnsupportedOperationException("NewObject has no value for " + property);
        };
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            parameterExpressions.forEach(predicate::test);
        }
    }

    @Override
    public boolean isNumeric() {
        return parameterizedType.isType() && parameterizedType.typeInfo.isNumeric();
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
        }
        if (anonymousClass != null) {
            outputBuilder.add(anonymousClass.output(qualification, false));
        }
        if (arrayInitializer != null) {
            outputBuilder.add(arrayInitializer.output(qualification));
        }
        return outputBuilder;
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
                diamond, reParamValues, anonymousClass, arrayInitializer);
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


        // check state changes of companion methods
        Expression instance;
        if (constructor != null) {
            MethodAnalysis constructorAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(constructor);
            Expression modifiedInstance = MethodCall.checkCompanionMethodsModifying(res.k, evaluationContext,
                    constructor, constructorAnalysis, null, this, res.v);
            if (modifiedInstance == null) {
                instance = this;
            } else {
                instance = modifiedInstance.isDelayed()
                        ? DelayedExpression.forNewObject(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        linkedVariables(evaluationContext).changeAllToDelay(modifiedInstance.causesOfDelay()),
                        modifiedInstance.causesOfDelay())
                        : modifiedInstance;
            }
        } else {
            instance = this;
        }
        res.k.setExpression(instance);

        if (constructor != null &&
                (!constructor.methodResolution.isSet() || constructor.methodResolution.get().allowsInterrupts())) {
            res.k.incrementStatementTime();
        }

        if (anonymousClass != null) {
            evaluationContext.getLocalPrimaryTypeAnalysers().stream()
                    .filter(pta -> pta.primaryTypes.contains(anonymousClass))
                    .forEach(res.k::markVariablesFromPrimaryTypeAnalyser);
        }

        DV cImm = forwardEvaluationInfo.getProperty(Property.CONTEXT_IMMUTABLE);
        if (MultiLevel.isAfterThrowWhenNotEventual(cImm)) {
            res.k.raiseError(getIdentifier(), Message.Label.EVENTUAL_AFTER_REQUIRED);
        }
        return res.k.build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public Expression createDelayedValue(EvaluationContext evaluationContext, CausesOfDelay causes) {
        return DelayedExpression.forNewObject(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                linkedVariables(evaluationContext).changeAllToDelay(causes), causes);
    }
}

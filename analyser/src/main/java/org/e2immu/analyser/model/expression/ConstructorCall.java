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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
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
public class ConstructorCall extends BaseExpression implements HasParameterExpressions {

    private final MethodInfo constructor;
    private final ParameterizedType parameterizedType;
    private final Diamond diamond;
    private final List<Expression> parameterExpressions;
    private final TypeInfo anonymousClass;
    private final ArrayInitializer arrayInitializer;

    // specific construction and copy methods: we explicitly name construction
    /*
    specific situation, new X[] { 0, 1, 2 } array initialiser
     */

    public static Expression withArrayInitialiser(MethodInfo arrayCreationConstructor,
                                                  ParameterizedType parameterizedType,
                                                  List<Expression> parameterExpressions,
                                                  ArrayInitializer arrayInitializer,
                                                  Identifier identifier) {
        return new ConstructorCall(identifier, arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, null, arrayInitializer);
    }

    public static Expression instanceFromSam(MethodInfo sam, ParameterizedType parameterizedType) {
        return new ConstructorCall(sam.getIdentifier(), null, parameterizedType, Diamond.NO, List.of(),
                sam.typeInfo, null);
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static ConstructorCall withAnonymousClass(Identifier identifier,
                                                     @NotNull ParameterizedType parameterizedType,
                                                     @NotNull TypeInfo anonymousClass,
                                                     Diamond diamond) {
        return new ConstructorCall(identifier, null, parameterizedType, diamond,
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


    public Expression removeConstructor(Properties valueProperties, Primitives primitives) {
        assert arrayInitializer == null;
        CausesOfDelay causesOfDelay = valueProperties.delays();
        if (causesOfDelay.isDelayed()) {
            return DelayedExpression.forInstanceOf(primitives, parameterizedType, LinkedVariables.delayedEmpty(causesOfDelay), causesOfDelay);
        }
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public ConstructorCall(Identifier identifier,
                           MethodInfo constructor,
                           ParameterizedType parameterizedType,
                           Diamond diamond,
                           List<Expression> parameterExpressions,
                           TypeInfo anonymousClass,
                           ArrayInitializer arrayInitializer) {
        super(identifier, 1 + parameterExpressions.stream().mapToInt(Expression::getComplexity).sum());
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        assert parameterExpressions.stream().noneMatch(Expression::isDelayed) : "Creating a constructor call with delayed arguments";
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        ParameterizedType translatedType = translationMap.translateType(this.parameterizedType);
        List<Expression> translatedParameterExpressions = parameterExpressions.isEmpty() ? parameterExpressions
                : parameterExpressions.stream().map(e -> e.translate(inspectionProvider, translationMap))
                .collect(TranslationCollectors.toList(parameterExpressions));
        if (translatedType == this.parameterizedType && translatedParameterExpressions == this.parameterExpressions) {
            return this;
        }
        CausesOfDelay causesOfDelay = translatedParameterExpressions.stream()
                .map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (causesOfDelay.isDelayed()) {
            return DelayedExpression.forNewObject(translatedType,
                    MultiLevel.EFFECTIVELY_NOT_NULL_DV, LinkedVariables.delayedEmpty(causesOfDelay), causesOfDelay);
        }
        return new ConstructorCall(identifier,
                constructor,
                translatedType,
                diamond,
                translatedParameterExpressions,
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
    public LinkedVariables linkedVariables(EvaluationResult context) {
        // instance, no constructor parameter expressions
        if (constructor == null) return LinkedVariables.EMPTY;

        return linkedVariablesFromParameters(context, constructor.methodInspection.get(),
                parameterExpressions);
    }

    static LinkedVariables linkedVariablesFromParameters(EvaluationResult evaluationContext,
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
                result = result.merge(sub, LinkedVariables.fromIndependentToLinkedVariableLevel(independentOnParameter));
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
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.CONTAINER && context.getCurrentType().isMyself(returnType(), context.getAnalyserContext())) {
            return MultiLevel.NOT_CONTAINER_DV; // ALWAYS, regardless of the actual value
        }
        ParameterizedType pt;
        AnalyserContext analyserContext = context.getAnalyserContext();
        if (anonymousClass != null) {
            pt = anonymousClass.asParameterizedType(analyserContext);
        } else {
            pt = parameterizedType;
        }
        return switch (property) {
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case IDENTITY, IGNORE_MODIFICATIONS -> analyserContext.defaultValueProperty(property, pt);
            case IMMUTABLE, IMMUTABLE_BREAK -> immutableValue(pt, analyserContext);
            case CONTAINER -> containerValue(pt, analyserContext);
            case INDEPENDENT -> independentValue(pt, analyserContext);
            case CONTEXT_MODIFIED -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("ConstructorCall has no value for " + property);
        };
    }

    private DV containerValue(ParameterizedType pt, AnalyserContext analyserContext) {
        DV dv = analyserContext.defaultContainer(pt);
        if (dv.isDelayed() && anonymousClass != null) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(anonymousClass);
            return typeAnalysis.getProperty(Property.PARTIAL_CONTAINER);
        }
        return dv;
    }

    private DV independentValue(ParameterizedType pt, AnalyserContext analyserContext) {
        if (anonymousClass != null) {
            DV immutable = immutableValue(pt, analyserContext);
            if (MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
                return MultiLevel.independentCorrespondingToImmutableLevelDv(MultiLevel.level(immutable));
            }
            if (immutable.isDelayed()) return immutable;
            return MultiLevel.DEPENDENT_DV;
        }
        return analyserContext.defaultValueProperty(Property.INDEPENDENT, pt);
    }

    private DV immutableValue(ParameterizedType pt, AnalyserContext analyserContext) {
        DV dv = analyserContext.defaultImmutable(pt, false);
        if (dv.isDone() && MultiLevel.effective(dv) == MultiLevel.Effective.EVENTUAL) {
            return MultiLevel.beforeImmutableDv(MultiLevel.level(dv));
        }
        // this is the value for use in the statement analyser, for inner classes (non-static nested classes)
        if (dv.isDelayed() && anonymousClass != null) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(anonymousClass);
            return typeAnalysis.getProperty(Property.PARTIAL_IMMUTABLE);
        }
        return dv;
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            parameterExpressions.forEach(p -> p.visit(predicate));
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
    public Expression generify(EvaluationContext evaluationContext) {
        if (anonymousClass == null && constructor != null) {
            Properties valueProperties = evaluationContext.getValueProperties(this);
            return removeConstructor(valueProperties, evaluationContext.getPrimitives());
        }
        return this;
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
    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation) {
        List<EvaluationResult> reParams = parameterExpressions.stream().map(v -> v.reEvaluate(context, translation)).collect(Collectors.toList());
        List<Expression> reParamValues = reParams.stream().map(EvaluationResult::value).collect(Collectors.toList());
        Expression expression;
        CausesOfDelay causesOfDelay = reParamValues.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (causesOfDelay.isDelayed()) {
            expression = createDelayedValue(context, causesOfDelay);
        } else {
            expression = new ConstructorCall(identifier, constructor, parameterizedType,
                    diamond, reParamValues, anonymousClass, arrayInitializer);
        }
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(reParams);
        return builder.setExpression(expression).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {

        // arrayInitializer variant

        if (arrayInitializer != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
            List<EvaluationResult> results = arrayInitializer.multiExpression.stream()
                    .map(e -> e.evaluate(context, ForwardEvaluationInfo.DEFAULT))
                    .collect(Collectors.toList());
            builder.compose(results);
            List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());
            builder.setExpression(new ArrayInitializer(identifier, context.getAnalyserContext(),
                    values, arrayInitializer.returnType()));
            return builder.build();
        }

        // "normal"

        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                context, forwardEvaluationInfo, constructor, false, null);
        CausesOfDelay parameterDelays = res.v.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (parameterDelays.isDelayed()) {
            return delayedConstructorCall(context, res.k, parameterDelays);
        }

        // check state changes of companion methods
        Expression instance;
        if (constructor != null) {
            MethodAnalysis constructorAnalysis = context.getAnalyserContext().getMethodAnalysis(constructor);
            Expression modifiedInstance = MethodCall.checkCompanionMethodsModifying(res.k, context,
                    constructor, constructorAnalysis, null, this, res.v);
            if (modifiedInstance == null) {
                instance = this;
            } else {
                instance = modifiedInstance.isDelayed()
                        ? DelayedExpression.forNewObject(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        linkedVariables(context).changeAllToDelay(modifiedInstance.causesOfDelay()),
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

        DV cImm = forwardEvaluationInfo.getProperty(Property.CONTEXT_IMMUTABLE);
        if (MultiLevel.isAfterThrowWhenNotEventual(cImm)) {
            res.k.raiseError(getIdentifier(), Message.Label.EVENTUAL_AFTER_REQUIRED);
        }
        return res.k.build();
    }

    private EvaluationResult delayedConstructorCall(EvaluationResult context,
                                                    EvaluationResult.Builder builder,
                                                    CausesOfDelay causesOfDelay) {
        assert causesOfDelay.isDelayed();
        builder.setExpression(createDelayedValue(context, causesOfDelay));
        // set scope delay
        return builder.build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public Expression createDelayedValue(EvaluationResult context, CausesOfDelay causes) {
        return DelayedExpression.forNewObject(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                linkedVariables(context).changeAllToDelay(causes), causes);
    }

    public MethodInfo constructor() {
        return constructor;
    }

    public TypeInfo anonymousClass() {
        return anonymousClass;
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public List<Expression> parameterExpressions() {
        return parameterExpressions;
    }
}

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
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.MultiExpression;
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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.MultiLevel.INDEPENDENT_DV;

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
            return DelayedExpression.forInstanceOf(identifier, primitives, parameterizedType, this, causesOfDelay);
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
            return createDelayedValue(identifier, null, translatedType, causesOfDelay);
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
        return ExpressionComparator.ORDER_NEW_INSTANCE;
    }


    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        // instance, no constructor parameter expressions
        if (constructor == null) return LinkedVariables.EMPTY;
        List<LinkedVariables> linkedVariables = computeLinkedVariablesOfParameters(context, parameterExpressions,
                // FIXME!!!
                parameterExpressions);
        return linkedVariablesFromParameters(context, constructor.methodInspection.get(),
                parameterExpressions, linkedVariables);
    }

    public static List<LinkedVariables> computeLinkedVariablesOfParameters(EvaluationResult context,
                                                                           List<Expression> parameterExpressions,
                                                                           List<Expression> parameterValues) {
        assert parameterExpressions.size() == parameterValues.size();
        int i = 0;
        List<LinkedVariables> result = new ArrayList<>(parameterExpressions.size());
        for (Expression expression : parameterExpressions) {
            LinkedVariables lvExpression = expression.linkedVariables(context);
            Expression value = parameterValues.get(i++);
            LinkedVariables lvValue = value.linkedVariables(context);
            LinkedVariables merged = lvExpression.merge(lvValue);
            result.add(merged);
        }
        return result;
    }

    /*
    Compute the links between the newly created object (constructor call) or the object (method call) and the
    parameters of the constructor/method.

     */
    static LinkedVariables linkedVariablesFromParameters(EvaluationResult evaluationContext,
                                                         MethodInspection methodInspection,
                                                         List<Expression> parameterExpressions,
                                                         List<LinkedVariables> linkedVariables) {
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
            DV independent = computeIndependentFromComponents(evaluationContext, value, parameterInfo);
            LinkedVariables sub = linkedVariables.get(i);
            if (independent.isDelayed()) {
                result = result.mergeDelay(sub, independent);
            } else if (independent.ge(MultiLevel.DEPENDENT_DV) && independent.lt(INDEPENDENT_DV)) {
                result = result.merge(sub, LinkedVariables.fromIndependentToLinkedVariableLevel(independent));
            }

            i++;
        }
        return result.minimum(LinkedVariables.LINK_ASSIGNED);
    }

    /*
    important: the result has to be independence with respect to the fields!!

    Example 1: parameterInfo = java.util.List.add(E):0:e, which is @Independent1.
    This means that the argument will be part of the list's hidden content.
    If the argument is MUTABLE, result should be Independent1, if it is E2_IMMUTABLE, the result should be Independent_2, etc.
    See e.g. Modification_16

    Example 2: parameterInfo = java.util.function.Consumer.accept(T):0:t, which is @Dependent
    See e.g. E2ImmutableComposition_0.ExposedArrayOfHasSize
    If we feed in an array of recursively immutable elements, like HasSize[], we want @Dependent as an outcome.
    If we feed in the recursively immutable element HasSize, we remain independent

    Code similar to computeIndependent in ComputingMethodAnalyser; see also analyseIndependentNoAssignment
    in ComputingParameterAnalyser.
     */
    private static DV computeIndependentFromComponents(EvaluationResult context,
                                                       Expression value,
                                                       ParameterInfo parameterInfo) {
        ParameterAnalysis parameterAnalysis = context.getAnalyserContext().getParameterAnalysis(parameterInfo);
        DV independentOnParameter = parameterAnalysis.getProperty(Property.INDEPENDENT);
        DV immutableOfValue = context.getProperty(value, Property.IMMUTABLE);

        // shortcut: either is at max value, then there is no discussion
        if (independentOnParameter.equals(INDEPENDENT_DV)
                || MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableOfValue)) {
            return INDEPENDENT_DV;
        }

        // any delay: wait!
        CausesOfDelay causes = immutableOfValue.causesOfDelay().merge(independentOnParameter.causesOfDelay());
        if (causes.isDelayed()) return causes;

        int immutableLevel = MultiLevel.level(immutableOfValue);

        if (independentOnParameter.ge(MultiLevel.INDEPENDENT_1_DV)
                && immutableLevel < MultiLevel.Level.IMMUTABLE_2.level) {
            // mutable, but linked content-wise
            return MultiLevel.INDEPENDENT_1_DV;
        }
        return MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((ConstructorCall) v).parameterizedType.detailedString());
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.CONTAINER && context.getCurrentType().isMyself(returnType(), context.getAnalyserContext())) {
            return parameterizedType.arrays > 0 ? MultiLevel.CONTAINER_DV : MultiLevel.NOT_CONTAINER_DV;
            // ALWAYS, regardless of the actual value
        }
        ParameterizedType pt;
        AnalyserContext analyserContext = context.getAnalyserContext();
        if (anonymousClass != null) {
            pt = anonymousClass.asParameterizedType(analyserContext);
        } else {
            pt = parameterizedType;
        }
        return switch (property) {
            case NOT_NULL_EXPRESSION -> notNullValue();
            case IDENTITY, IGNORE_MODIFICATIONS -> analyserContext.defaultValueProperty(property, pt);
            case IMMUTABLE, IMMUTABLE_BREAK -> immutableValue(pt, analyserContext, context.getCurrentType());
            case CONTAINER -> analyserContext.defaultContainer(pt);
            case INDEPENDENT -> independentValue(pt, analyserContext, context.getCurrentType());
            case CONTEXT_MODIFIED -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("ConstructorCall has no value for " + property);
        };
    }

    private DV notNullValue() {
        if (parameterizedType.arrays <= 1) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        if (parameterizedType.arrays == 2) return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV;
        return MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV;
    }

    private DV independentValue(ParameterizedType pt, AnalyserContext analyserContext, TypeInfo currentType) {
        if (anonymousClass != null) {
            DV immutable = immutableValue(pt, analyserContext, currentType);
            if (MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
                return MultiLevel.independentCorrespondingToImmutableLevelDv(MultiLevel.level(immutable));
            }
            if (immutable.isDelayed()) return immutable;
            return MultiLevel.DEPENDENT_DV;
        }
        return analyserContext.defaultValueProperty(Property.INDEPENDENT, pt);
    }

    private DV immutableValue(ParameterizedType pt, AnalyserContext analyserContext, TypeInfo currentType) {
        DV dv = analyserContext.defaultImmutable(pt);
        if (dv.isDone() && MultiLevel.effective(dv) == MultiLevel.Effective.EVENTUAL) {
            return MultiLevel.beforeImmutableDv(MultiLevel.level(dv));
        }
        // this is the value for use in the statement analyser, for inner classes (non-static nested classes)
        if (anonymousClass != null) {
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(anonymousClass);
            return typeAnalysis.getProperty(Property.PARTIAL_IMMUTABLE);
        }
        return dv;
    }

    @Override
    public void visit(Predicate<Element> predicate) {
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
                context, forwardEvaluationInfo, constructor, false, null,
                false);
        CausesOfDelay parameterDelays = res.v.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (parameterDelays.isDelayed()) {
            return delayedConstructorCall(context, res.k, parameterDelays);
        }

        // check state changes of companion methods
        Expression instance;
        if (constructor != null) {
            MethodCall.ModReturn modReturn = MethodCall.checkCompanionMethodsModifying(identifier, res.k, context,
                    constructor, null, this, res.v, this, DV.TRUE_DV);
            if (modReturn == null) {
                instance = this;
            } else if (modReturn.expression() != null) {
                instance = modReturn.expression().isDelayed()
                        ? createDelayedValue(identifier, context, modReturn.expression().causesOfDelay())
                        : modReturn.expression();
            } else {
                instance = createDelayedValue(identifier, context, modReturn.causes());
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
        builder.setExpression(createDelayedValue(identifier, context, causesOfDelay));
        // set scope delay
        return builder.build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public Expression createDelayedValue(Identifier identifier, EvaluationResult context, CausesOfDelay causes) {
        return createDelayedValue(identifier, context, parameterizedType, causes);
    }

    private Expression createDelayedValue(Identifier identifier,
                                          EvaluationResult context,
                                          ParameterizedType parameterizedType,
                                          CausesOfDelay causes) {
        Map<FieldInfo, Expression> shortCutMap;
        if (context == null || constructor == null || parameterExpressions.isEmpty()) {
            shortCutMap = null;
        } else {
            boolean haveNoVariables = parameterExpressions.stream()
                    .allMatch(e -> e.variables(true).isEmpty());
            if (haveNoVariables) {
                shortCutMap = null;
            } else {
            /*
            We have an explicit call new X(a, b, c);
            The parameters a, b, c may be linked to the fields of x, x.f, x.g, ...
            The shortcut will expand x.f to b when x.f turns out to be FINAL and linked to the parameter b.
            This map anticipates this, by concocting special delay expressions for the field access operations x.f, x.g...
            which are intercepted in SAEvaluationContext.makeVariableExpression().
            Instead of expanding x.f into a DelayedVariableExpression, they'll be expanded into a DelayedExpression
            containing the relevant variables:
            1. if we don't know about FINAL, or assignment, all possible variables of all parameters
            2. if we know about the assignment from field to parameter: only the variables of the relevant parameter
            for each of the fields.
            */
                List<FieldInfo> fields = constructor.typeInfo.typeInspection.get().fields();
                DV fieldsFinal = fields.stream()
                        .map(fieldInfo -> context.getAnalyserContext().getFieldAnalysis(fieldInfo).getProperty(Property.FINAL))
                        .reduce(DV.MIN_INT_DV, DV::min);
                if (fieldsFinal.isDelayed()) {
                    // we must be in iteration 0, and the type has not been analysed yet...
                    String delayName = constructor.typeInfo.simpleName;
                    Expression originalForDelay = new MultiExpressions(identifier, context.getAnalyserContext(),
                            MultiExpression.create(parameterExpressions));
                    shortCutMap = fields.stream().collect(Collectors.toUnmodifiableMap(f -> f, f ->
                            DelayedExpression.forConstructorCallExpansion(identifier, delayName, f.type,
                                    originalForDelay, fieldsFinal.causesOfDelay().merge(causes))));
                } else {
                    shortCutMap = new HashMap<>();
                    for (FieldInfo fieldInfo : fields) {
                        FieldAnalysis fieldAnalysis = context.getAnalyserContext().getFieldAnalysis(fieldInfo);
                        boolean isFinal = fieldAnalysis.getProperty(Property.FINAL).valueIsTrue();
                        String delayName = constructor.typeInfo.simpleName + ":" + fieldInfo.name;
                        if (isFinal) {
                            CausesOfDelay linked = fieldAnalysis.getLinkedVariables().causesOfDelay();
                            if (linked.isDone()) {
                                fieldAnalysis.getLinkedVariables().variables().forEach((v, lv) -> {
                                    if (v instanceof ParameterInfo pi
                                            && pi.owner == constructor
                                            && lv.equals(LinkedVariables.LINK_STATICALLY_ASSIGNED)) {
                                        // the field has been statically assigned to pi
                                        Expression original = parameterExpressions.get(pi.index);
                                        Expression de = DelayedExpression.forConstructorCallExpansion(identifier, delayName,
                                                fieldInfo.type, original, causes);
                                        shortCutMap.put(fieldInfo, de);
                                    }
                                });
                            } else {
                                // linking not done yet
                                CausesOfDelay merged = fieldsFinal.causesOfDelay().merge(causes);
                                Expression originalForDelay = new MultiExpressions(identifier, context.getAnalyserContext(),
                                        MultiExpression.create(parameterExpressions));
                                shortCutMap.put(fieldInfo, DelayedExpression.forConstructorCallExpansion(identifier,
                                        delayName, fieldInfo.type, originalForDelay, merged));
                            }
                        } // else: definitely nothing for this field
                    }
                }
            }
        }
        return DelayedExpression.forNewObject(identifier, parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                this, causes, shortCutMap);
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

    public boolean hasConstructor() {
        return constructor != null;
    }
}

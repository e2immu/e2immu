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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.Keyword;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.*;
import org.e2immu.annotation.NotNull;
import org.e2immu.graph.analyser.PackedInt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public class ConstructorCall extends BaseExpression implements HasParameterExpressions {

    private final Expression scope;
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
        return new ConstructorCall(identifier, null, arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, null, arrayInitializer);
    }

    public static Expression instanceFromSam(MethodInfo sam, ParameterizedType parameterizedType) {
        return new ConstructorCall(sam.getIdentifier(), null, null, parameterizedType, Diamond.NO, List.of(),
                sam.typeInfo, null);
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static ConstructorCall withAnonymousClass(Identifier identifier,
                                                     Expression scope,
                                                     @NotNull ParameterizedType parameterizedType,
                                                     @NotNull TypeInfo anonymousClass,
                                                     Diamond diamond) {
        return new ConstructorCall(identifier, scope, null, parameterizedType, diamond,
                List.of(), anonymousClass, null);
    }

    /*
    Result of actual object creation expressions (new XX, xx::new, ...)
    This call is made in the inspection phase, before analysis.
    So there are no values yet, except obviously not identity, and effectively not null.
    Others
     */
    public static ConstructorCall objectCreation(Identifier identifier,
                                                 Expression scope,
                                                 MethodInfo constructor,
                                                 ParameterizedType parameterizedType,
                                                 Diamond diamond,
                                                 List<Expression> parameterExpressions) {
        return new ConstructorCall(identifier, scope, constructor, parameterizedType, diamond, parameterExpressions,
                null, null);
    }

    public ConstructorCall withParameterExpressions(List<Expression> newExpressions) {
        return new ConstructorCall(identifier, scope, constructor, parameterizedType, diamond, newExpressions,
                anonymousClass, arrayInitializer);
    }

    public Expression removeConstructor(Properties valueProperties, Primitives primitives) {
        assert arrayInitializer == null;
        CausesOfDelay causesOfDelay = valueProperties.delays();
        if (causesOfDelay.isDelayed()) {
            return DelayedExpression.forInstanceOf(identifier, primitives, parameterizedType, this, causesOfDelay);
        }
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public ConstructorCall copy(List<Expression> newParameterExpressions) {
        return new ConstructorCall(identifier, scope, constructor, parameterizedType, diamond, newParameterExpressions,
                anonymousClass, arrayInitializer);
    }

    // to erase identifier equality
    public ConstructorCall copy(Identifier identifier) {
        return new ConstructorCall(identifier, scope, constructor, parameterizedType, diamond, parameterExpressions,
                anonymousClass, arrayInitializer);
    }

    public ConstructorCall(Identifier identifier,
                           Expression scope,
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
        this.scope = scope;
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
                Objects.equals(scope, newObject.scope) &&
                Objects.equals(anonymousClass, newObject.anonymousClass) &&
                Objects.equals(constructor, newObject.constructor) &&
                Objects.equals(arrayInitializer, newObject.arrayInitializer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, scope, anonymousClass, constructor,
                arrayInitializer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Expression> T ensureExpressionType(Expression expression, Class<T> clazz) {
        if (clazz.isAssignableFrom(expression.getClass())) return (T) expression;
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedScope = scope == null ? null : translationMap.translateExpression(scope);
        ParameterizedType translatedType = translationMap.translateType(this.parameterizedType);
        List<Expression> translatedParameterExpressions = parameterExpressions.isEmpty() ? parameterExpressions
                : parameterExpressions.stream()
                .map(e -> e.translate(inspectionProvider, translationMap))
                .filter(e -> !e.isEmpty())
                .collect(TranslationCollectors.toList(parameterExpressions));
        ArrayInitializer translatedInitializer = arrayInitializer == null ? null :
                ensureExpressionType(
                        arrayInitializer.translate(inspectionProvider, translationMap), ArrayInitializer.class);
        if (translatedScope == scope
                && translatedType == this.parameterizedType
                && translatedParameterExpressions == this.parameterExpressions
                && translatedInitializer == arrayInitializer) {
            return this;
        }
        CausesOfDelay causesOfDelay = translatedParameterExpressions.stream()
                .map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (causesOfDelay.isDelayed()) {
            return createDelayedValue(identifier, null, translatedType, causesOfDelay);
        }
        return new ConstructorCall(identifier,
                translatedScope,
                constructor,
                translatedType,
                diamond,
                translatedParameterExpressions,
                anonymousClass, // not translating this yet!
                translatedInitializer);
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
        // TODO link to scope if not null
        // instance, no constructor parameter expressions
        if (constructor == null || parameterExpressions.isEmpty()) return LinkedVariables.EMPTY;
        List<Expression> parameterValues = parameterExpressions.stream()
                .map(pe -> pe.evaluate(context, ForwardEvaluationInfo.DEFAULT).value())
                .toList();
        List<LinkedVariables> linkedVariables = LinkParameters.computeLinkedVariablesOfParameters(context,
                parameterExpressions, parameterValues);
        return LinkParameters.linkFromObjectToParameters(context, constructor.methodInspection.get(), linkedVariables,
                returnType());
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        if (v instanceof ConstructorCall cc) {
            int c = getMethodInfo().fullyQualifiedName.compareTo(cc.getMethodInfo().fullyQualifiedName);
            if (c != 0) return c;
            int d = ListUtil.compare(parameterExpressions, cc.parameterExpressions);
            if (d != 0) return d;
            if (scope == null && cc.scope != null) return -1;
            if (scope != null && cc.scope == null) return 1;
            if (scope != null) {
                return scope.compareTo(cc.scope);
            }
            return 0;
        }
        throw new ExpressionComparator.InternalError();
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
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
            case IMMUTABLE, IMMUTABLE_BREAK -> immutableValue(pt, context);
            case CONTAINER -> containerValue(pt, context);
            case INDEPENDENT -> independentValue(pt, context);
            case CONTEXT_MODIFIED -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("ConstructorCall has no value for " + property);
        };
    }

    private DV containerValue(ParameterizedType pt, EvaluationResult context) {
        if (context.getCurrentType().isMyself(returnType(), context.getAnalyserContext()).toFalse(Property.CONTAINER)) {
            return parameterizedType.arrays > 0 ? MultiLevel.CONTAINER_DV : MultiLevel.NOT_CONTAINER_DV;
            // ALWAYS, regardless of the actual value
        }
        return context.getAnalyserContext().typeContainer(pt);
    }

    /*
    new String[3][2] = content not null
    new String[3][] = not null
    new String[] = illegal
     */
    private DV notNullValue() {
        if (parameterizedType.arrays > 0) {
            int countSize = 0;
            while (countSize < parameterExpressions.size() && !(parameterExpressions.get(countSize) instanceof UnknownExpression)) {
                countSize++;
            }
            assert countSize > 0;
            if (countSize == 1) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV;
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
    }

    private DV independentValue(ParameterizedType pt, EvaluationResult context) {
        if (constructor != null && constructor.typeInfo.isMyself(context.getCurrentType(), context.getAnalyserContext())
                .toFalse(Property.INDEPENDENT)) {
            return MultiLevel.DEPENDENT_DV;
        }
        if (anonymousClass != null) {
            DV immutable = immutableValue(pt, context);
            if (MultiLevel.isAtLeastEventuallyImmutableHC(immutable)) {
                return MultiLevel.independentCorrespondingToImmutableLevelDv(MultiLevel.level(immutable));
            }
            if (immutable.isDelayed()) return immutable;
            return MultiLevel.DEPENDENT_DV;
        }
        return context.getAnalyserContext().defaultValueProperty(Property.INDEPENDENT, pt);
    }

    private DV immutableValue(ParameterizedType pt, EvaluationResult context) {
        if (constructor != null && constructor.typeInfo.isMyself(context.getCurrentType(), context.getAnalyserContext())
                .toFalse(Property.IMMUTABLE)) {
            return MultiLevel.MUTABLE_DV;
        }
        DV dv = context.getAnalyserContext().typeImmutable(pt);
        if (dv.isDone() && MultiLevel.effective(dv) == MultiLevel.Effective.EVENTUAL) {
            return MultiLevel.beforeImmutableDv(MultiLevel.level(dv));
        }
        // this is the value for use in the statement analyser, for inner classes (non-static nested classes)
        if (anonymousClass != null) {
            TypeAnalysis typeAnalysis = context.getAnalyserContext().getTypeAnalysis(anonymousClass);
            return typeAnalysis.getProperty(Property.PARTIAL_IMMUTABLE);
        }
        if (parameterizedType.arrays > 0) {
            return MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV;
        }
        return new DynamicImmutableOfConstructor(context, constructor, parameterExpressions, returnType())
                .compute(dv);
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
        if (scope != null) {
            outputBuilder.add(outputInParenthesis(qualification, precedence(), scope));
            outputBuilder.add(Symbol.DOT);
        }
        if (constructor != null || anonymousClass != null) {
            outputBuilder.add(Keyword.NEW).add(Space.ONE)
                    .add(parameterizedType.copyWithoutArrays().output(qualification, false, diamond));
            //      if (arrayInitializer == null) {
            if (parameterizedType.arrays > 0) {
                for (int i = 0; i < parameterizedType.arrays; i++) {
                    if (i < parameterExpressions.size()) {
                        outputBuilder.add(Symbol.LEFT_BRACKET);
                        Expression size = parameterExpressions.get(i);
                        if (!(size instanceof UnknownExpression)) {
                            outputBuilder.add(size.output(qualification));
                        }
                        outputBuilder.add(Symbol.RIGHT_BRACKET);
                    } else {
                        outputBuilder.add(Symbol.OPEN_CLOSE_BRACKETS);
                    }
                }
            } else {
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
            //    }
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
                scope == null ? UpgradableBooleanMap.of() : scope.typesReferenced(),
                parameterizedType.typesReferenced(true),
                parameterExpressions.stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        return PackedIntMap.of(scope == null ? PackedIntMap.of() : scope.typesReferenced2(weight),
                parameterizedType.typesReferenced2(weight),
                parameterExpressions.stream().flatMap(e -> e.typesReferenced2(weight).stream())
                        .collect(PackedIntMap.collector()));
    }

    @Override
    public List<? extends Element> subElements() {
        if (scope != null) {
            return Stream.concat(Stream.of(scope), parameterExpressions.stream()).toList();
        }
        return parameterExpressions;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.isOnlySort()) {
            return evaluateComponents(context, forwardEvaluationInfo);
        }

        EvaluationResult scopeResult;
        if (scope != null) {
            scopeResult = scope.evaluate(context, forwardEvaluationInfo);
        } else {
            scopeResult = null;
        }

        // arrayInitializer variant

        if (arrayInitializer != null) {
            EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
            List<EvaluationResult> results = arrayInitializer.multiExpression.stream()
                    .map(e -> e.evaluate(context, forwardEvaluationInfo))
                    .collect(Collectors.toList());
            builder.compose(results);
            List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());
            builder.setExpression(new ArrayInitializer(identifier, context.getAnalyserContext(),
                    values, arrayInitializer.returnType()));
            return builder.build();
        }

        // "normal"

        Pair<EvaluationResultImpl.Builder, List<Expression>> res = EvaluateParameters.go(parameterExpressions,
                context, forwardEvaluationInfo, constructor, false, null,
                false);
        CausesOfDelay parameterDelays = res.v.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (parameterDelays.isDelayed()) {
            return delayedConstructorCall(context, res.k, parameterDelays);
        }

        // check state changes of companion methods
        Expression instance;
        if (constructor != null) {
            int sumComplexity = res.v.stream().mapToInt(Expression::getComplexity).sum();
            ConstructorCall withEvalParam = sumComplexity >= Expression.CONSTRUCTOR_CALL_EXPANSION_LIMIT
                    ? this : withParameterExpressions(res.v);
            MethodCall.ModReturn modReturn = MethodCall.checkCompanionMethodsModifying(identifier, res.k, context,
                    constructor, null, withEvalParam, res.v, this, DV.TRUE_DV);
            if (modReturn == null) {
                instance = withEvalParam;
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

        // links from object into the parameters  new List(x), which may render x --3--> new object
        // but that's not a variable... how do we solve that? with a reverse link, that gets translated in assignment?
        // IMPROVE implement!!

        DV cImm = forwardEvaluationInfo.getProperty(Property.CONTEXT_IMMUTABLE);
        if (MultiLevel.isAfterThrowWhenNotEventual(cImm)) {
            res.k.raiseError(getIdentifier(), Message.Label.EVENTUAL_AFTER_REQUIRED);
        }
        return res.k.build();
    }

    private EvaluationResult evaluateComponents(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        ArrayInitializer evaluatedArrayInitializer = arrayInitializer == null ? null :
                (ArrayInitializer) arrayInitializer.evaluate(context, forwardEvaluationInfo).getExpression();
        List<Expression> evaluatedParams = parameterExpressions.stream()
                .map(e -> e.evaluate(context, forwardEvaluationInfo).getExpression()).toList();
        MethodAnalysis methodAnalysis = constructor == null ? null
                : context.getAnalyserContext().getMethodAnalysisNullWhenAbsent(constructor);
        List<Expression> sortedParameters;
        if (methodAnalysis != null && methodAnalysis.hasParallelGroups()) {
            sortedParameters = methodAnalysis.sortAccordingToParallelGroupsAndNaturalOrder(parameterExpressions);
        } else {
            sortedParameters = evaluatedParams;
        }
        Expression mc = new ConstructorCall(identifier, scope, constructor, parameterizedType, diamond, sortedParameters,
                anonymousClass, evaluatedArrayInitializer);
        return new EvaluationResultImpl.Builder(context).setExpression(mc).build();
    }

    private EvaluationResult delayedConstructorCall(EvaluationResult context,
                                                    EvaluationResultImpl.Builder builder,
                                                    CausesOfDelay causesOfDelay) {
        assert causesOfDelay.isDelayed();
        builder.setExpression(createDelayedValue(identifier, context, causesOfDelay));
        // set scope delay
        return builder.build();
    }

    @Override
    public Expression createDelayedValue(Identifier identifier,
                                         EvaluationResult context,
                                         CausesOfDelay causes) {
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
                    .allMatch(e -> e.variables().isEmpty());
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
                        .reduce(DelayFactory.initialDelay(), DV::min);
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

    public Expression scope() {
        return scope;
    }

    @SuppressWarnings("unused")
    public boolean hasConstructor() {
        return constructor != null;
    }
}

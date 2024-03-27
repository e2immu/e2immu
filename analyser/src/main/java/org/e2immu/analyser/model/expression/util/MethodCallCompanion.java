package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MethodCallCompanion {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodCallCompanion.class);

    public record ModReturn(Expression expression, CausesOfDelay causes) {
    }

    public static ModReturn checkCompanionMethodsModifying(
            Identifier identifier,
            EvaluationResultImpl.Builder builder,
            EvaluationResult context,
            MethodInfo methodInfo,
            Expression object,
            Expression objectValue,
            LinkedVariables linkedVariablesOfObject,
            List<Expression> parameterValues,
            Expression original,
            DV modified) {
        if (modified.valueIsFalse()) return null;

        CausesOfDelay delayMarker = DelayFactory.createDelay(new SimpleCause(context.evaluationContext().getLocation(Stage.EVALUATION),
                CauseOfDelay.Cause.CONSTRUCTOR_TO_INSTANCE));
        if (objectValue.isDelayed() || modified.isDelayed()) {
            return new ModReturn(null, delayMarker);
        }

        IsVariableExpression ive = object == null ? null : object.asInstanceOf(IsVariableExpression.class);
        IsVariableExpression iveValue = objectValue.asInstanceOf(IsVariableExpression.class);

        Expression newInstance = createNewInstance(context, methodInfo, objectValue, iveValue, identifier, original);
        boolean inLoop = iveValue instanceof VariableExpression ve && ve.getSuffix() instanceof VariableExpression.VariableInLoop;

        Expression newState = computeNewState(context, builder, methodInfo, objectValue, linkedVariablesOfObject, parameterValues);
        if (newState.isDelayed()) {
            return new ModReturn(null, delayMarker.merge(newState.causesOfDelay()));
        }
        Expression modifiedInstance;
        if (newState.isBoolValueTrue() || inLoop) {
            modifiedInstance = newInstance;
        } else {
            modifiedInstance = PropertyWrapper.addState(newInstance, newState);
        }

        if (object != null) {
            // ideally we change the underlying variable, otherwise the static one
            if (iveValue != null && !(iveValue.variable() instanceof This)) {
                builder.modifyingMethodAccess(iveValue.variable(), modifiedInstance, null);
            } else if (ive != null && !(ive.variable() instanceof This)) {
                builder.modifyingMethodAccess(ive.variable(), modifiedInstance, null);
            }

            for (Map.Entry<Variable, LV> e : linkedVariablesOfObject.variables().entrySet()) {
                LV lv = e.getValue();
                Variable variable = e.getKey();
                if (ive == null || !variable.equals(ive.variable())) {
                    if (lv.isDone()) {
                        if (lv.le(LV.LINK_DEPENDENT)) {
                            ConstructorCall cc;
                            Expression i;
                            Expression varVal = context.currentValue(variable);
                            if ((cc = varVal.asInstanceOf(ConstructorCall.class)) != null && cc.constructor() != null) {
                                Properties valueProperties = context.evaluationContext().getValueProperties(cc);
                                i = Instance.forMethodResult(cc.identifier, context.evaluationContext().statementIndex(),
                                        cc.returnType(), valueProperties);
                            } else if (varVal instanceof PropertyWrapper pw && pw.hasState()) {
                                // drop this state -- IMPROVE we won't do any companion code here at the moment
                                i = pw.unwrapState();
                            } else {
                                i = null;
                            }
                            if (i != null) {
                                // keep existing linked variables!!!
                                builder.modifyingMethodAccess(variable, i, null);
                            }
                        }
                    } else {
                        // delay
                        Expression delayed = DelayedExpression.forModification(object, delayMarker);
                        builder.modifyingMethodAccess(variable, delayed, linkedVariablesOfObject);
                    }
                } // else: already done: modifiedInstance
            }
        }

        return new ModReturn(modifiedInstance, null);
    }

    private static Expression createNewInstance(EvaluationResult context,
                                                MethodInfo methodInfo,
                                                Expression objectValue,
                                                IsVariableExpression ive,
                                                Identifier identifier,
                                                Expression original) {
        Expression createInstanceBasedOn;
        Expression newInstance;
        String statementIndex = context.evaluationContext().statementIndex();

        if (objectValue.isInstanceOf(ConstructorCall.class) && methodInfo.isConstructor()) {
            newInstance = unwrap(objectValue);
            createInstanceBasedOn = null;
        } else if (objectValue.isInstanceOf(Instance.class)) {
            Expression unwrapped = unwrap(objectValue);
            newInstance = unwrapped instanceof Instance unwrappedInstance
                          && statementIndex.equals(unwrappedInstance.getIndex()) ? unwrapped : null;
            createInstanceBasedOn = objectValue;
        } else if (ive != null) {
            Expression current = context.currentValue(ive.variable());
            if (current.isInstanceOf(ConstructorCall.class) && methodInfo.isConstructor()) {
                newInstance = unwrap(current);
                createInstanceBasedOn = null;
            } else if (current.isInstanceOf(Instance.class)) {
                Expression unwrapped = unwrap(current);
                newInstance = unwrapped instanceof Instance unwrappedInstance
                              && statementIndex.equals(unwrappedInstance.getIndex()) ? unwrapped : null;
                createInstanceBasedOn = current;
            } else {
                createInstanceBasedOn = current;
                newInstance = null;
            }
        } else {
            createInstanceBasedOn = objectValue;
            newInstance = null;
        }
        if (createInstanceBasedOn != null) {
            ParameterizedType returnType = createInstanceBasedOn.returnType();
            Properties defaultValueProperties = context.evaluationContext().defaultValueProperties(returnType,
                    MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            CausesOfDelay causesOfDelay = defaultValueProperties.delays();
            Properties valueProperties;
            Instance instance;
            if (causesOfDelay.isDelayed()) {
                if (context.evaluationContext().isMyself(returnType).toFalse(Property.IMMUTABLE)) {
                    valueProperties = context.evaluationContext().valuePropertiesOfFormalType(returnType,
                            MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                } else {
                    return DelayedExpression.forMethod(identifier, methodInfo, objectValue.returnType(),
                            original, causesOfDelay, Map.of());
                }
            } else if ((instance = createInstanceBasedOn.asInstanceOf(Instance.class)) != null) {
                DV identity = instance.valueProperties().getOrDefault(Property.IDENTITY, DV.FALSE_DV);
                valueProperties = defaultValueProperties.combineSafely(Properties.of(Map.of(Property.IDENTITY, identity)));
            } else {
                valueProperties = defaultValueProperties;
            }
            newInstance = Instance.forGetInstance(objectValue.getIdentifier(), statementIndex, objectValue.returnType(),
                    valueProperties);
        }
        return newInstance;
    }

    private static Expression computeNewState(EvaluationResult context,
                                              EvaluationResultImpl.Builder builder,
                                              MethodInfo methodInfo,
                                              Expression objectValue,
                                              LinkedVariables linkedVariablesOfObject,
                                              List<Expression> parameterValues) {
        Expression state;
        BooleanConstant TRUE = new BooleanConstant(context.getPrimitives(), true);
        if (context.evaluationContext().hasState(objectValue)) {
            state = context.evaluationContext().state(objectValue);
        } else {
            state = TRUE;
        }
        assert state.isDone();

        AtomicReference<Expression> newState = new AtomicReference<>(state);
        // NOTE: because of the code that selects concrete implementations, we must go up in hierarchy, to collect all companions
        Map<CompanionMethodName, CompanionAnalysis> cMap = methodInfo.collectCompanionMethods(context.getAnalyserContext());
        if (cMap.isEmpty()) {
            // modifying method, without instructions on how to change the state... we simply clear it!
            newState.set(TRUE);
        } else {
            EvaluationResult temporaryContext = builder.build();
            String objectModificationTimes = temporaryContext.modificationTimesOf(linkedVariablesOfObject);
            cMap.keySet().stream()
                    .filter(cmn -> CompanionMethodName.MODIFYING_METHOD_OR_CONSTRUCTOR.contains(cmn.action()))
                    .sorted()
                    .forEach(cmn -> companionMethod(context, temporaryContext, methodInfo, objectModificationTimes,
                            parameterValues, newState, cmn, cMap.get(cmn)));
            if (containsEmptyExpression(newState.get())) {
                newState.set(TRUE);
            }
        }
        return newState.get();
    }

    private static void companionMethod(EvaluationResult context,
                                        EvaluationResult currentContext,
                                        MethodInfo methodInfo,
                                        String objectModificationTimes,
                                        List<Expression> parameterValues,
                                        AtomicReference<Expression> newState,
                                        CompanionMethodName companionMethodName,
                                        CompanionAnalysis companionAnalysis) {
        assert companionAnalysis != null;
        if (companionAnalysis.causesOfDelay().isDelayed()) {
            newState.set(DelayedExpression.forDelayedCompanionAnalysis(methodInfo.getIdentifier(),
                    companionMethodName.composeMethodName(), context.getPrimitives().booleanParameterizedType(),
                    new BooleanConstant(context.getPrimitives(), true),
                    companionAnalysis.causesOfDelay()));
            LOGGER.debug("Delaying companionMethod {}, not yet analysed", companionMethodName);
            return;
        }
        MethodInfo aspectMethod;
        if (companionMethodName.aspect() != null) {
            TypeAnalysis typeAnalysis = context.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo);
            aspectMethod = typeAnalysis.getAspects().get(companionMethodName.aspect());
            assert aspectMethod != null : "Expect aspect method in " + companionMethodName +
                                          " to be known to " + methodInfo.typeInfo.fullyQualifiedName;
        } else {
            aspectMethod = null;
        }

        Filter.FilterResult<MethodCall> filterResult;

        if (companionMethodName.action() == CompanionMethodName.Action.CLEAR) {
            newState.set(new BooleanConstant(context.getPrimitives(), true));
            filterResult = null; // there is no "pre"
        } else {
            // in the case of java.util.List.add(), the aspect is Size, there are 3+ "parameters":
            // pre, post, and the parameter(s) of the add method.
            // post is already OK (it is the new value of the aspect method)
            // pre is the "old" value, which has to be obtained. If that's impossible, we bail out.
            // the parameters are available

            if (aspectMethod != null && !methodInfo.isConstructor()) {
                // first: pre (POST CONDITION, MODIFICATION)
                filterResult = EvaluateMethodCall.filter(context, aspectMethod, newState.get(), List.of());
            } else {
                filterResult = null;
            }
        }

        // we're not adding originals here  TODO would that be possible, necessary?
        Set<Variable> newStateVariables = newState.get().variableStream()
                .collect(Collectors.toUnmodifiableSet());
        Expression companionValueTranslated = translateCompanionValue(context, currentContext, companionAnalysis,
                filterResult, newState.get(), newStateVariables, objectModificationTimes, parameterValues);

        boolean remove = companionMethodName.action() == CompanionMethodName.Action.REMOVE;
        if (remove) {
            Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
            Filter.FilterResult<Expression> res = filter.filter(newState.get(),
                    new Filter.ExactValue(filter.getDefaultRest(), companionValueTranslated));
            newState.set(res.rest());
        } else {
            Expression startFrom = filterResult != null ? filterResult.rest() : newState.get();
            newState.set(And.and(context, startFrom, companionValueTranslated));
        }
    }

    // IMPROVE we're assuming at the moment that the wrapper is used for the companion data
    // but it could, in theory, be used for e.g. a @NotNull or so
    private static Expression unwrap(Expression expression) {
        if (expression instanceof PropertyWrapper pw) {
            return unwrap(pw.expression());
        }
        return expression;
    }


    private static boolean containsEmptyExpression(Expression expression) {
        AtomicBoolean result = new AtomicBoolean();
        expression.visit(e -> {
            if (e == EmptyExpression.EMPTY_EXPRESSION) result.set(true);
            return true;
        });
        return result.get();
    }
    /*

    Examples
    Modification_0; set1.add(v)
    instanceState: "true"

    companionValue:
    AnnotatedAPI.isFact(this.contains(e))?this.contains(e)?this.size()==pre:1==this.size()-pre:AnnotatedAPI.isKnown(true)?1==this.size()-pre:1-this.size()+pre>=0&&this.size()>=pre

    translated:
    AnnotatedAPI.isFact(this.contains(v))?this.contains(v)?this.size()==0:1==this.size()-0:AnnotatedAPI.isKnown(true)?1==this.size()-0:this.size()>=1
    --false:true when in instanceState ---   ....                                          -false: true when in state---   ...    -- : this.size()>=1

    result:
    this.size>=1

    -----------
    BasicCompanionMethods_5, statement 01; set.add("a")
    instanceState: AnnotatedAPI.isKnown(true)&&0==this.size()   -> there's a new, empty set

    companionValue:
    AnnotatedAPI.isFact(this.contains(e))?this.contains(e)?this.size()==pre:1==this.size()-pre:AnnotatedAPI.isKnown(true)?1==this.size()-pre:this.size()>=1

    translated:
    AnnotatedAPI.isFact(this.contains("a"))?this.contains("a")?this.size()==0:1==this.size()-0:AnnotatedAPI.isKnown(true)?1==this.size()-0:this.size()>=1
    --false: this.contains("a") not in state --- ... :--- true --- ? 1==this.size() : ----
     */

    private static Expression translateCompanionValue(EvaluationResult context,
                                                      EvaluationResult currentContext,
                                                      CompanionAnalysis companionAnalysis,
                                                      Filter.FilterResult<MethodCall> filterResult,
                                                      Expression instanceState,
                                                      Set<Variable> instanceStateVariables,
                                                      String objectModificationTimes,
                                                      List<Expression> parameterValues) {
        TranslationMapImpl.Builder translationMap = new TranslationMapImpl.Builder();
        if (filterResult != null) {
            Expression preAspectVariableValue = companionAnalysis.getPreAspectVariableValue();
            if (preAspectVariableValue != null) {
                Expression pre = filterResult.accepted().values().stream().findFirst().orElse(NullConstant.NULL_CONSTANT);
                translationMap.put(preAspectVariableValue, pre);
            }
        }
        // parameters
        ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues).forEach(pair -> translationMap.put(pair.k, pair.v));

        translationMap.setModificationTimesHandler((beforeTranslation, translatedObject, translatedParameters) -> {
            String params = "XX";// currentContext.modificationTimesOf(translatedParameters.toArray(Expression[]::new));
            // FIXME this needs serious attention
            return objectModificationTimes + (params.isEmpty() ? "" : ("," + params));
        });

        Expression companionValue = companionAnalysis.getValue();
        Expression translated = companionValue.translate(context.getAnalyserContext(), translationMap.build());
        EvaluationResult child = context.child(instanceState, instanceStateVariables, true);
        ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().doNotReevaluateVariableExpressionsDoNotComplain()
                .setInCompanionExpression().build();
        EvaluationResult companionValueTranslationResult = translated.evaluate(child, fwd);
        // no need to compose: this is a separate operation. builder.compose(companionValueTranslationResult);
        return companionValueTranslationResult.value();
    }

}

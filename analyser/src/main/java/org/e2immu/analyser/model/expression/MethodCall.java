/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateMethodCall;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Pair;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.output.QualifiedName.Required.NO_METHOD;
import static org.e2immu.analyser.output.QualifiedName.Required.YES;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;


public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MethodCall.class);

    public final boolean objectIsImplicit; // irrelevant after evaluation
    public final Expression object;
    public final List<Expression> parameterExpressions;
    public final ObjectFlow objectFlow;

    public MethodCall(Expression object,
                      MethodInfo methodInfo,
                      List<Expression> parameterExpressions,
                      ObjectFlow objectFlow) {
        this(false, object, methodInfo, methodInfo.returnType(), parameterExpressions, objectFlow);
    }

    public MethodCall(boolean objectIsImplicit,
                      Expression object,
                      MethodInfo methodInfo,
                      ParameterizedType returnType,
                      List<Expression> parameterExpressions,
                      ObjectFlow objectFlow) {
        super(methodInfo, returnType);
        this.object = Objects.requireNonNull(object);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.objectIsImplicit = objectIsImplicit;
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MethodCall(objectIsImplicit,
                translationMap.translateExpression(object),
                methodInfo,
                concreteReturnType,
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                objectFlow);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_METHOD;
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        if (Primitives.isPrimitiveExcludingVoid(returnType())) return null;
        return NewObject.forGetInstance(evaluationResult.evaluationContext().newObjectIdentifier(),
                evaluationResult.evaluationContext().getPrimitives(), returnType(), objectFlow);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            object.visit(predicate);
            parameterExpressions.forEach(p -> p.visit(predicate));
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodCall that = (MethodCall) o;
        boolean sameMethod = methodInfo.equals(that.methodInfo) ||
                checkSpecialCasesWhereDifferentMethodsAreEqual(methodInfo, that.methodInfo);
        return sameMethod &&
                parameterExpressions.equals(that.parameterExpressions) &&
                object.equals(that.object);
        // IMPROVE does modification play a role here?
    }

    /*
     the interface and the implementation, or the interface and sub-interface
     */
    private boolean checkSpecialCasesWhereDifferentMethodsAreEqual(MethodInfo m1, MethodInfo m2) {
        Set<MethodInfo> overrides1 = m1.methodResolution.get().overrides();
        if (m2.typeInfo.isInterface() && overrides1.contains(m2)) return true;
        Set<MethodInfo> overrides2 = m2.methodResolution.get().overrides();
        return m1.typeInfo.isInterface() && overrides2.contains(m1);

        // any other?
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, parameterExpressions, methodInfo);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return output(qualification, null);
    }

    // will come directly here only from this method (chaining of method calls produces a guide)
    public OutputBuilder output(Qualification qualification, Guide.GuideGenerator guideGenerator) {
        OutputBuilder outputBuilder = new OutputBuilder();
        boolean last = false;
        boolean start = false;
        Guide.GuideGenerator gg = null;
        if (object != null) {
            if (object instanceof MethodCall methodCall) {
                // chaining!
                if (guideGenerator == null) {
                    gg = Guide.defaultGuideGenerator();
                    last = true;
                } else {
                    gg = guideGenerator;
                }
                outputBuilder.add(methodCall.output(qualification, gg)); // recursive call
                outputBuilder.add(gg.mid());
                outputBuilder.add(Symbol.DOT);
                outputBuilder.add(new Text(methodInfo.name));
            } else if (object instanceof TypeExpression typeExpression) {
                /*
                we may or may not need to write the type here.
                 */
                assert methodInfo.methodInspection.get().isStatic();
                TypeInfo typeInfo = typeExpression.parameterizedType.typeInfo;
                TypeName typeName = new TypeName(typeInfo, qualification.qualifierRequired(typeInfo));
                outputBuilder.add(new QualifiedName(methodInfo.name, typeName,
                        qualification.qualifierRequired(methodInfo) ? YES : NO_METHOD));
                if (guideGenerator != null) start = true;
            } else if (object instanceof VariableExpression ve && ve.variable() instanceof This thisVar) {
                assert !methodInfo.methodInspection.get().isStatic();
                TypeName typeName = new TypeName(thisVar.typeInfo, qualification.qualifierRequired(thisVar.typeInfo));
                ThisName thisName = new ThisName(thisVar.writeSuper, typeName, qualification.qualifierRequired(thisVar));
                outputBuilder.add(new QualifiedName(methodInfo.name, thisName,
                        qualification.qualifierRequired(methodInfo) ? YES : NO_METHOD));
                if (guideGenerator != null) start = true;
            } else {
                // next level is NOT a gg; if gg != null we're at the start of the chain
                outputBuilder.add(outputInParenthesis(qualification, precedence(), object));
                if (guideGenerator != null) outputBuilder.add(guideGenerator.start());
                outputBuilder.add(Symbol.DOT);
                outputBuilder.add(new Text(methodInfo.name));
            }
        }

        if (parameterExpressions.isEmpty()) {
            outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
        } else {
            outputBuilder
                    .add(Symbol.LEFT_PARENTHESIS)
                    .add(parameterExpressions.stream()
                            .map(expression -> expression.output(qualification))
                            .collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        if (start) {
            outputBuilder.add(guideGenerator.start());
        }
        if (last) {
            outputBuilder.add(gg.end());
        }
        return outputBuilder;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reParams = parameterExpressions.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        EvaluationResult reObject = object.reEvaluate(evaluationContext, translation);
        List<Expression> reParamValues = reParams.stream().map(EvaluationResult::value).collect(Collectors.toList());
        int modified = evaluationContext.getAnalyserContext()
                .getMethodAnalysis(methodInfo).getProperty(VariableProperty.MODIFIED_METHOD);
        EvaluationResult mv = EvaluateMethodCall.methodValue(modified, evaluationContext, methodInfo,
                evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo), reObject.value(), reParamValues, getObjectFlow());
        return new EvaluationResult.Builder(evaluationContext).compose(reParams).compose(reObject, mv)
                .setExpression(mv.value()).build();
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        boolean alwaysModifying;
        boolean neverModifying;
        boolean delayUndeclared;
        boolean recursiveCall;

        if (evaluationContext.getCurrentMethod() != null) {
            TypeInfo currentPrimaryType = evaluationContext.getCurrentType().primaryType();
            TypeInfo methodPrimaryType = methodInfo.typeInfo.primaryType();

            boolean circularCallOutsidePrimaryType = methodPrimaryType != currentPrimaryType &&
                    currentPrimaryType.typeResolution.get().circularDependencies().contains(methodPrimaryType) &&
                    !ShallowTypeAnalyser.IS_FACT_FQN.equals(methodInfo.fullyQualifiedName());

            // internal circular dependency (as opposed to one outside the primary type)
            neverModifying = methodInfo.partOfCallCycle();

            boolean undeclaredFunctionalInterface;
            if (methodInfo.isSingleAbstractMethod()) {
                Boolean b = EvaluateParameters.tryToDetectUndeclared(evaluationContext, builder.getStatementTime(), object);
                undeclaredFunctionalInterface = b != null && b;
                delayUndeclared = b == null;
            } else {
                undeclaredFunctionalInterface = false;
                delayUndeclared = false;
            }
            if ((circularCallOutsidePrimaryType || undeclaredFunctionalInterface)) {
                builder.addCircularCallOrUndeclaredFunctionalInterface();
            }
            alwaysModifying = circularCallOutsidePrimaryType || undeclaredFunctionalInterface;
            recursiveCall = evaluationContext.getCurrentMethod().methodInfo == this.methodInfo; // recursive call
        } else {
            alwaysModifying = false;
            delayUndeclared = false;
            neverModifying = false;
            recursiveCall = false;
        }

        MethodAnalysis methodAnalysis;
        try {
            methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("Error obtaining method analysis for {}", methodInfo.fullyQualifiedName());
            throw e;
        }
        // is the method modifying, do we need to wait?
        int modified = alwaysModifying ? Level.TRUE : recursiveCall || neverModifying ? Level.FALSE
                : methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
        int contextModifiedDelay = Level.fromBool(modified == Level.DELAY || delayUndeclared);

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        int notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL));
        boolean contentNotNullRequired = notNullForward == MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;

        // scope
        EvaluationResult objectResult = object.evaluate(evaluationContext, new ForwardEvaluationInfo(Map.of(
                VariableProperty.CONTEXT_NOT_NULL, notNullForward,
                VariableProperty.METHOD_CALLED, Level.TRUE,
                VariableProperty.CONTEXT_MODIFIED_DELAY, contextModifiedDelay,
                VariableProperty.CONTEXT_MODIFIED, modified), true));

        // null scope
        Expression objectValue = objectResult.value();
        if (objectValue.isInstanceOf(NullConstant.class)) {
            builder.raiseError(Message.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        int notModified1Scope = evaluationContext.getProperty(objectValue, VariableProperty.NOT_MODIFIED_1, true);
        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions, evaluationContext, methodInfo, notModified1Scope, objectValue);
        List<Expression> parameterValues = res.v;
        builder.compose(objectResult, res.k.build());

        // before we return, increment the time, irrespective of NO_VALUE
        if (!recursiveCall) {
            boolean increment;
            if (methodAnalysis.isBeingAnalysed()) {
                StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                if (lastStatement == null) {
                    increment = false;
                } else if (!lastStatement.flowData.initialTimeIsSet()) {
                    return delayedMethod(builder, objectValue, parameterValues);
                } else {
                    increment = lastStatement.flowData.getTimeAfterSubBlocks() > 0;
                }
            } else {
                increment = !methodInfo.methodResolution.isSet() || methodInfo.methodResolution.get().allowsInterrupts();
            }
            if (increment) builder.incrementStatementTime();
        }

        if (parameterValues.stream().anyMatch(evaluationContext::isDelayed)) {
            return delayedMethod(builder, objectValue, parameterValues);
        }

        // access
        ObjectFlow objectFlow = objectValue.getObjectFlow();
        if (objectFlow != ObjectFlow.NO_FLOW) {
            if (modified == Level.DELAY) {
                Logger.log(DELAYED, "Delaying flow access registration because modification status of {} not known",
                        methodInfo.fullyQualifiedName());
                objectFlow.delay();
            } else {
                List<ObjectFlow> flowsOfArguments = parameterValues.stream().map(Expression::getObjectFlow).collect(Collectors.toList());
                MethodAccess methodAccess = new MethodAccess(methodInfo, flowsOfArguments);
                builder.addAccess(modified == Level.TRUE, methodAccess, objectValue);
            }
        }

        // companion methods
        NewObject modifiedInstance;
        if (modified == Level.TRUE) {
            modifiedInstance = checkCompanionMethodsModifying(builder, evaluationContext, methodInfo,
                    methodAnalysis, object, objectValue, parameterValues);
        } else {
            modifiedInstance = null;
        }

        // @Only check
        checkOnly(evaluationContext, builder, objectValue, objectFlow);

        // return value
        Location location = evaluationContext.getLocation(this);
        ObjectFlow objectFlowOfResult;
        if (!Primitives.isVoid(methodInfo.returnType())) {
            ObjectFlow returnedFlow = methodAnalysis.getObjectFlow();

            objectFlowOfResult = builder.createInternalObjectFlow(location, methodInfo.returnType(), Origin.RESULT_OF_METHOD);
            objectFlowOfResult.addPrevious(returnedFlow);
            // cross-link, possible because returnedFlow is already permanent
            // TODO ObjectFlow check cross-link
            returnedFlow.addNext(objectFlowOfResult);
        } else {
            objectFlowOfResult = ObjectFlow.NO_FLOW;
        }

        Expression result;
        boolean resultIsDelayed;
        if (!methodInfo.isVoid()) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            complianceWithForwardRequirements(builder, methodAnalysis, methodInspection, forwardEvaluationInfo, contentNotNullRequired);

            EvaluationResult mv = EvaluateMethodCall.methodValue(modified, evaluationContext, methodInfo,
                    methodAnalysis, objectValue, parameterValues, objectFlowOfResult);
            builder.compose(mv);
            if (mv.value() == objectValue && mv.value() instanceof NewObject && modifiedInstance != null) {
                result = modifiedInstance;
                resultIsDelayed = false;
            } else {
                result = mv.value();
                resultIsDelayed = mv.someValueWasDelayed();
            }
        } else {
            result = EmptyExpression.NO_RETURN_VALUE;
            resultIsDelayed = false;
        }
        builder.setExpression(result);

        // scope delay
        if (resultIsDelayed || contextModifiedDelay == Level.TRUE) {
            objectValue.variables().forEach(variable -> builder.setProperty(variable, VariableProperty.SCOPE_DELAY, Level.TRUE));
            object.variables().forEach(variable -> builder.setProperty(variable, VariableProperty.SCOPE_DELAY, Level.TRUE));
        }

        checkCommonErrors(builder, evaluationContext, objectValue);
        return builder.build();
    }

    private EvaluationResult delayedMethod(EvaluationResult.Builder builder, Expression objectValue, List<Expression> parameterValues) {
        Logger.log(DELAYED, "Delayed method call because one of the parameter values of {} is delayed: {}",
                methodInfo.name, parameterValues);
        builder.setExpression(DelayedExpression.forMethod(methodInfo));
        // set scope delay
        objectValue.variables().forEach(variable -> builder.setProperty(variable, VariableProperty.SCOPE_DELAY, Level.TRUE));
        object.variables().forEach(variable -> builder.setProperty(variable, VariableProperty.SCOPE_DELAY, Level.TRUE));

        return builder.build();
    }

    static NewObject checkCompanionMethodsModifying(
            EvaluationResult.Builder builder,
            EvaluationContext evaluationContext,
            MethodInfo methodInfo,
            MethodAnalysis methodAnalysis,
            Expression object,
            Expression objectValue,
            List<Expression> parameterValues) {
        if (evaluationContext.isDelayed(objectValue)) return null; // don't even try

        NewObject newObject;
        VariableExpression variableExpression;
        if ((variableExpression = objectValue.asInstanceOf(VariableExpression.class)) != null) {
            newObject = builder.currentInstance(variableExpression.variable());
        } else if (objectValue instanceof TypeExpression) {
            assert methodInfo.methodInspection.get().isStatic();
            return null; // static method
        } else {
            newObject = objectValue.getInstance(builder.build());
        }
        Objects.requireNonNull(newObject, "Modifying method on constant or primitive? Impossible: " + objectValue.getClass());

        if (evaluationContext.isDelayed(newObject.state())) return null; // DELAY

        AtomicReference<Expression> newState = new AtomicReference<>(newObject.state());
        methodInfo.methodInspection.get().getCompanionMethods().keySet().stream()
                .filter(e -> CompanionMethodName.MODIFYING_METHOD_OR_CONSTRUCTOR.contains(e.action()))
                .sorted()
                .forEach(companionMethodName -> {
                    CompanionAnalysis companionAnalysis = methodAnalysis.getCompanionAnalyses().get(companionMethodName);
                    MethodInfo aspectMethod;
                    if (companionMethodName.aspect() != null) {
                        aspectMethod = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo).getAspects().get(companionMethodName.aspect());
                        assert aspectMethod != null : "Expect aspect method to be known";
                    } else {
                        aspectMethod = null;
                    }

                    Filter.FilterResult<MethodCall> filterResult;

                    if (companionMethodName.action() == CompanionMethodName.Action.CLEAR) {
                        newState.set(new BooleanConstant(evaluationContext.getPrimitives(), true));
                        filterResult = null; // there is no "pre"
                    } else {
                        // in the case of java.util.List.add(), the aspect is Size, there are 3+ "parameters":
                        // pre, post, and the parameter(s) of the add method.
                        // post is already OK (it is the new value of the aspect method)
                        // pre is the "old" value, which has to be obtained. If that's impossible, we bail out.
                        // the parameters are available

                        if (aspectMethod != null && !methodInfo.isConstructor) {
                            // first: pre (POSTCONDITION, MODIFICATION)
                            filterResult = EvaluateMethodCall.filter(evaluationContext, aspectMethod, newState.get(), List.of());
                        } else {
                            filterResult = null;
                        }
                    }

                    Expression companionValueTranslated = translateCompanionValue(evaluationContext, companionAnalysis,
                            filterResult, newState.get(), parameterValues);

                    boolean remove = companionMethodName.action() == CompanionMethodName.Action.REMOVE;
                    if (remove) {
                        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
                        Filter.FilterResult<Expression> res = filter.filter(newState.get(),
                                new Filter.ExactValue(filter.getDefaultRest(), companionValueTranslated));
                        newState.set(res.rest());
                    } else {
                        Expression startFrom = filterResult != null ? filterResult.rest() : newState.get();
                        newState.set(new And(evaluationContext.getPrimitives()).append(evaluationContext, startFrom,
                                companionValueTranslated));
                    }
                });
        if (containsEmptyExpression(newState.get())) {
            newState.set(new BooleanConstant(evaluationContext.getPrimitives(), true));
        }
        NewObject modifiedInstance = methodInfo.isConstructor ? newObject.copyWithNewState(newState.get()) :
                // we clear the constructor and its arguments after calling a modifying method on the object
                newObject.copyAfterModifyingMethodOnConstructor(newState.get());

        LinkedVariables linkedVariables = variablesLinkedToScopeVariableInModifyingMethod(evaluationContext, methodInfo, parameterValues);
        if (object instanceof VariableExpression variableValue && !(variableValue.variable() instanceof This)) {
            builder.modifyingMethodAccess(variableValue.variable(), modifiedInstance, linkedVariables);
        } else if (object instanceof FieldAccess fieldAccess) {
            builder.modifyingMethodAccess(fieldAccess.variable(), modifiedInstance, linkedVariables);
        }
        return modifiedInstance;
    }

    private static boolean containsEmptyExpression(Expression expression) {
        AtomicBoolean result = new AtomicBoolean();
        expression.visit(e -> {
            if (e == EmptyExpression.EMPTY_EXPRESSION) result.set(true);
            return true;
        });
        return result.get();
    }

    private static Expression translateCompanionValue(EvaluationContext evaluationContext,
                                                      CompanionAnalysis companionAnalysis,
                                                      Filter.FilterResult<MethodCall> filterResult,
                                                      Expression instanceState,
                                                      List<Expression> parameterValues) {
        Map<Expression, Expression> translationMap = new HashMap<>();
        if (filterResult != null) {
            Expression preAspectVariableValue = companionAnalysis.getPreAspectVariableValue();
            if (preAspectVariableValue != null) {
                translationMap.put(preAspectVariableValue, filterResult.accepted().values().stream()
                        .findFirst()
                        // it is possible that no pre- information can be found... that's OK as long as it isn't used
                        // the empty expression will travel all the way
                        .orElse(EmptyExpression.EMPTY_EXPRESSION));
            }
        }
        // parameters
        ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues).forEach(pair -> translationMap.put(pair.k, pair.v));

        Expression companionValue = companionAnalysis.getValue();
        EvaluationContext child = evaluationContext.child(instanceState, true);
        EvaluationResult companionValueTranslationResult = companionValue.reEvaluate(child, translationMap);
        // no need to compose: this is a separate operation. builder.compose(companionValueTranslationResult);
        return companionValueTranslationResult.value();
    }

    /*
    Modifying method, b.method(c,d)

    After this operation, the generic instance b can be linked to c and d, but only when the parameters are @Modified

    Null value means delays, as per convention.
     */
    private static LinkedVariables variablesLinkedToScopeVariableInModifyingMethod(EvaluationContext evaluationContext,
                                                                                   MethodInfo methodInfo,
                                                                                   List<Expression> parameterValues) {
        Set<Variable> result = new HashSet<>();
        int i = 0;
        int n = methodInfo.methodInspection.get().getParameters().size();
        for (Expression p : parameterValues) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(Math.min(n - 1, i));
            int modified = evaluationContext.getAnalyserContext()
                    .getParameterAnalysis(parameterInfo).getProperty(VariableProperty.MODIFIED_VARIABLE);
            if (modified == Level.DELAY) return LinkedVariables.DELAY;
            if (modified == Level.TRUE) {
                LinkedVariables cd = evaluationContext.linkedVariables(p);
                if (cd == LinkedVariables.DELAY) return LinkedVariables.DELAY;
                result.addAll(cd.variables());
            }
            i++;
        }
        return new LinkedVariables(result);
    }

    private int notNullRequirementOnScope(int notNullRequirement) {
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL;
    }

    private void checkOnly(EvaluationContext evaluationContext, EvaluationResult.Builder builder,
                           Expression objectValue,
                           ObjectFlow objectFlow) {
        Set<String> marks = objectFlow.marks();
        if (marks != null && !marks.isEmpty()
                && objectValue instanceof VariableExpression ve && ve.variable() instanceof This) {
            MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
            if (methodAnalysis.markAndOnlyIsSet()) {
                MethodAnalysis.MarkAndOnly markAndOnly = methodAnalysis.getMarkAndOnly();
                if(markAndOnly != null) {
                    if (markAndOnly.after() == Boolean.FALSE) {
                        String before = markAndOnly.markLabel();
                        if (marks.contains(before)) {
                            builder.raiseError(Message.ONLY_BEFORE, methodInfo.fullyQualifiedName() +
                                    ", mark \"" + before + "\"");
                        }
                    } else if (markAndOnly.after() == Boolean.TRUE) {
                        String after = markAndOnly.markLabel();
                        if (!marks.contains(after)) {
                            builder.raiseError(Message.ONLY_AFTER, methodInfo.fullyQualifiedName() +
                                    ", mark \"" + after + "\"");
                        }
                    }
                }
            }
        }
    }

    private void checkCommonErrors(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Expression objectValue) {
        if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
            ParameterizedType type = objectValue.returnType();
            if (type != null && type.typeInfo != null && type.typeInfo ==
                    evaluationContext.getPrimitives().stringTypeInfo) {
                builder.raiseError(Message.UNNECESSARY_METHOD_CALL);
            }
        }

        MethodInfo method;
        if (objectValue instanceof InlinedMethod ico) {
            method = ico.methodInfo();
        } else {
            method = methodInfo;
        }
        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(method);
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
        int immutable = evaluationContext.getProperty(objectValue, VariableProperty.IMMUTABLE, true);
        if (modified == Level.TRUE && immutable >= MultiLevel.EVENTUALLY_E2IMMUTABLE) {
            builder.raiseError(Message.CALLING_MODIFYING_METHOD_ON_E2IMMU,
                    "Method: " + methodInfo.distinguishingName() + ", Type: " + objectValue.returnType());
        }
    }

    private static void complianceWithForwardRequirements(EvaluationResult.Builder builder,
                                                          MethodAnalysis methodAnalysis,
                                                          MethodInspection methodInspection,
                                                          ForwardEvaluationInfo forwardEvaluationInfo,
                                                          boolean contentNotNullRequired) {
        if (!contentNotNullRequired) {
            int requiredNotNull = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL);
            if (MultiLevel.isEffectivelyNotNull(requiredNotNull)) {
                int methodNotNull = methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
                if (methodNotNull != Level.DELAY) {
                    boolean isNotNull = MultiLevel.isEffectivelyNotNull(methodNotNull);
                    if (!isNotNull) {
                        builder.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                                "Result of method call " + methodInspection.getFullyQualifiedName());
                    }
                } // else: delaying is fine
            }
        } // else: we've already requested this from the scope (functional interface)
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(parameterExpressions, List.of(object));
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        Objects.requireNonNull(evaluationContext);

        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        // look at the object... if it is static, we're in the same boat
        if (object instanceof FieldAccess fieldAccess) {
            if (fieldAccess.variable().isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object instanceof VariableExpression variableExpression) {
            if (variableExpression.variable().isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object != null) {
            SideEffect sideEffect = object.sideEffect(evaluationContext);
            if (sideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
        }

        SideEffect methodsSideEffect = sideEffectNotTakingEventualIntoAccount(evaluationContext);
        if (methodsSideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
            return SideEffect.STATIC_ONLY;
        }
        return methodsSideEffect.combine(params);
    }

    private SideEffect sideEffectNotTakingEventualIntoAccount(EvaluationContext evaluationContext) {
        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);

        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo);
        int immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);

        boolean effectivelyE2Immutable = immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        if (!effectivelyE2Immutable && modified == Level.DELAY) return SideEffect.DELAYED;
        if (effectivelyE2Immutable || modified == Level.FALSE) {
            if (methodInfo.methodInspection.get().isStatic()) {
                if (methodInfo.isVoid()) {
                    return SideEffect.STATIC_ONLY;
                }
                return SideEffect.NONE_PURE;
            }
            return SideEffect.NONE_CONTEXT;
        }
        return SideEffect.SIDE_EFFECT;
    }


    @Override
    public int internalCompareTo(Expression v) {
        MethodCall mv = (MethodCall) v;
        int c = methodInfo.fullyQualifiedName().compareTo(mv.methodInfo.fullyQualifiedName());
        if (c != 0) return c;
        int i = 0;
        while (i < parameterExpressions.size()) {
            if (i >= mv.parameterExpressions.size()) return 1;
            c = parameterExpressions.get(i).compareTo(mv.parameterExpressions.get(i));
            if (c != 0) return c;
            i++;
        }
        return object.compareTo(mv.object);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        boolean recursiveCall = evaluationContext.getCurrentMethod() != null && methodInfo == evaluationContext.getCurrentMethod().methodInfo;
        if (recursiveCall) {
            return variableProperty.best;
        }
        if (variableProperty == VariableProperty.NOT_NULL_EXPRESSION) {
            int fluent = evaluationContext.getAnalyserContext()
                    .getMethodAnalysis(methodInfo).getProperty(VariableProperty.FLUENT);
            if (fluent == Level.TRUE) return Level.best(MultiLevel.EFFECTIVELY_NOT_NULL,
                    evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo)
                            .getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }
        return evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getProperty(variableProperty);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {

        // RULE 0: void method cannot link
        ParameterizedType returnType = methodInfo.returnType();
        if (Primitives.isVoid(returnType)) return LinkedVariables.EMPTY; // no assignment

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 1: if the return type is E2IMMU, then no links at all
        boolean notSelf = returnType.typeInfo != evaluationContext.getCurrentType();
        if (notSelf) {
            int immutable = MultiLevel.value(methodAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
            if (immutable == MultiLevel.DELAY) return LinkedVariables.DELAY;
            if (immutable >= MultiLevel.EVENTUAL) {
                return LinkedVariables.EMPTY;
            }
        }

        // RULE 2: @Identity links to the 1st parameter, TODO generalised @Identity(param=3) to the 4th...

        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.TRUE) return evaluationContext.linkedVariables(parameterExpressions.get(0));

        // rule 2 bis: TODO additional @Linked({ multiple parameters })

        // RULE 3: E2IMMU object cannot link, neither can implicitly immutable types

        int objectE2Immutable = MultiLevel.value(evaluationContext.getProperty(object, VariableProperty.IMMUTABLE, true), MultiLevel.E2IMMUTABLE);
        if (objectE2Immutable >= MultiLevel.EVENTUAL_AFTER) {
            return LinkedVariables.EMPTY;
        }

        // ignore object if return type is implicitly immutable in the type of the method
        // in Map<K,V>, V get(K key) has a return type V which is implicitly immutable in Map
        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo);
        Set<ParameterizedType> implicitlyImmutable = typeAnalysis.getImplicitlyImmutableDataTypes();
        if (implicitlyImmutable != null && implicitlyImmutable.contains(methodInfo.returnType())) {
            return LinkedVariables.EMPTY;
        }

        // RULE 4: independent method: no link to object

        int independent = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
        if (independent == MultiLevel.EFFECTIVE) {
            return LinkedVariables.EMPTY;
        }


        // TODO objects of the same type
        if (independent == Level.DELAY || objectE2Immutable == MultiLevel.DELAY || identity == Level.DELAY ||
                typeAnalysis.getImplicitlyImmutableDataTypes() == null) return LinkedVariables.DELAY;
        LinkedVariables b = evaluationContext.linkedVariables(object);
        if (b == LinkedVariables.DELAY) return LinkedVariables.DELAY;
        return new LinkedVariables(b.variables());
    }

    @Override
    public boolean isNumeric() {
        return Primitives.isNumeric(methodInfo.returnType().bestTypeInfo());
    }

    @Override
    public List<Variable> variables() {
        return object.variables();
    }

    public boolean objectIsThisOrSuper(InspectionProvider inspectionProvider) {
        if (object instanceof VariableExpression ve && ve.variable() instanceof This) return true;
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        return !methodInspection.isStatic() && objectIsImplicit;
    }

}

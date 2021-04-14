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
import org.e2immu.analyser.model.expression.util.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
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


public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions, OneVariable {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MethodCall.class);

    public final boolean objectIsImplicit; // irrelevant after evaluation
    public final Expression object;
    public final List<Expression> parameterExpressions;

    public MethodCall(Expression object,
                      MethodInfo methodInfo,
                      List<Expression> parameterExpressions) {
        this(false, object, methodInfo, methodInfo.returnType(), parameterExpressions);
    }

    public MethodCall(boolean objectIsImplicit,
                      Expression object,
                      MethodInfo methodInfo,
                      ParameterizedType returnType,
                      List<Expression> parameterExpressions) {
        super(methodInfo, returnType);
        this.object = Objects.requireNonNull(object);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.objectIsImplicit = objectIsImplicit;
    }

    // only used in the inequality system
    @Override
    public Variable variable() {
        List<Variable> variables = object.variables();
        return variables.size() == 1 ? variables.get(0) : null;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression asExpression = translationMap.directExpression(this);
        if (asExpression != null) return asExpression;
        MethodInfo translatedMethod = translationMap.translateMethod(methodInfo);
        return new MethodCall(objectIsImplicit,
                translationMap.translateExpression(object),
                translatedMethod,
                translatedMethod.returnType(),
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()));
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_METHOD;
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        if (Primitives.isPrimitiveExcludingVoid(returnType())) return null;
        return NewObject.forGetInstance(evaluationResult.evaluationContext().newObjectIdentifier(),
                evaluationResult.evaluationContext().getPrimitives(), returnType());
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
                assert !methodInfo.methodInspection.get().isStatic() : "Have a static method with scope 'this'? "
                        + methodInfo.fullyQualifiedName + "; this " + thisVar.typeInfo.fullyQualifiedName;
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
                evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo), reObject.value(), reParamValues);
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
        boolean partOfCallCycle;
        boolean abstractMethod;
        boolean recursiveCall;

        if (evaluationContext.getCurrentMethod() != null) {
            TypeInfo currentPrimaryType = evaluationContext.getCurrentType().primaryType();
            TypeInfo methodPrimaryType = methodInfo.typeInfo.primaryType();

            boolean circularCallOutsidePrimaryType = methodPrimaryType != currentPrimaryType &&
                    currentPrimaryType.typeResolution.get().circularDependencies().contains(methodPrimaryType) &&
                    !ShallowTypeAnalyser.IS_FACT_FQN.equals(methodInfo.fullyQualifiedName());

            // internal circular dependency (as opposed to one outside the primary type)
            partOfCallCycle = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();

            abstractMethod = methodInfo.isAbstract();

            if (circularCallOutsidePrimaryType) {
                builder.addCircularCall();
            }
            alwaysModifying = circularCallOutsidePrimaryType;
            recursiveCall = evaluationContext.getCurrentMethod().methodInfo == this.methodInfo; // recursive call
        } else {
            alwaysModifying = false;
            abstractMethod = false;
            partOfCallCycle = false;
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
        int modifiedMethod = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
        boolean propagateModification = abstractMethod && modifiedMethod == Level.DELAY;
        int modified = alwaysModifying ? Level.TRUE : recursiveCall || partOfCallCycle ||
                propagateModification ? Level.FALSE : modifiedMethod;
        int contextModifiedDelay = Level.fromBool(modified == Level.DELAY);

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        int notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL));
        boolean contentNotNullRequired = notNullForward == MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;

        ImmutableData immutableData = recursiveCall || partOfCallCycle ? NOT_EVENTUAL :
                computeContextImmutable(evaluationContext);

        // scope
        EvaluationResult objectResult = object.evaluate(evaluationContext, new ForwardEvaluationInfo(Map.of(
                VariableProperty.CONTEXT_NOT_NULL, notNullForward,
                VariableProperty.METHOD_CALLED, Level.TRUE,
                VariableProperty.CONTEXT_MODIFIED_DELAY, contextModifiedDelay,
                VariableProperty.CONTEXT_MODIFIED, modified,
                VariableProperty.CONTEXT_IMMUTABLE_DELAY, immutableData.delay,
                VariableProperty.CONTEXT_IMMUTABLE, immutableData.required,
                VariableProperty.NEXT_CONTEXT_IMMUTABLE, immutableData.next), true));

        // null scope
        Expression objectValue = objectResult.value();
        if (objectValue.isInstanceOf(NullConstant.class)) {
            builder.raiseError(Message.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        int notModified1Scope = evaluationContext.getProperty(objectValue, VariableProperty.NOT_MODIFIED_1, true, false);
        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                evaluationContext, methodInfo, notModified1Scope, recursiveCall || partOfCallCycle, objectValue);
        List<Expression> parameterValues = res.v;
        builder.compose(objectResult, res.k.build());

        // revisit abstract method, check if object value pointed to a concrete, modifying method
        if (abstractMethod && objectValue instanceof IsVariableExpression ve) {
            MethodInfo pointsToConcreteMethod = evaluationContext.concreteMethod(variable(), methodInfo);
            if (pointsToConcreteMethod != null) {
                MethodAnalysis concreteMethodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(pointsToConcreteMethod);
                int modifyingConcreteMethod = concreteMethodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                if (modifyingConcreteMethod != Level.DELAY) {
                    builder.markContextModified(ve.variable(), modifyingConcreteMethod);
                } else {
                    builder.markContextModifiedDelay(ve.variable());
                    builder.eraseContextModified(ve.variable());
                }
            } else if (propagateModification) {
                builder.markPropagateModification(ve.variable());
            }
        }

        // precondition
        EvaluatePreconditionFromMethod.evaluate(evaluationContext, builder, methodInfo, objectValue, parameterValues);

        // before we return, increment the time, irrespective of NO_VALUE
        if (!recursiveCall) {
            boolean increment;
            if (methodAnalysis.isBeingAnalysed()) {
                StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                if (lastStatement == null) {
                    increment = false;
                } else if (lastStatement.flowData.initialTimeNotYetSet()) {
                    return delayedMethod(evaluationContext, builder, objectValue,
                            contextModifiedDelay == Level.TRUE, parameterValues);
                } else {
                    increment = lastStatement.flowData.getTimeAfterSubBlocks() > 0;
                }
            } else {
                increment = !methodInfo.methodResolution.isSet() || methodInfo.methodResolution.get().allowsInterrupts();
            }
            if (increment) builder.incrementStatementTime();
        }

        boolean delayedFinalizer = checkFinalizer(evaluationContext, builder, methodAnalysis, objectValue);

        if (parameterValues.stream().anyMatch(evaluationContext::isDelayed) || delayedFinalizer) {
            return delayedMethod(evaluationContext, builder, objectValue,
                    contextModifiedDelay == Level.TRUE, parameterValues);
        }

        // companion methods
        NewObject modifiedInstance;
        if (modified == Level.TRUE) {
            modifiedInstance = checkCompanionMethodsModifying(builder, evaluationContext, methodInfo,
                    methodAnalysis, object, objectValue, parameterValues);
        } else {
            modifiedInstance = null;
        }

        Expression result;
        boolean resultIsDelayed;
        if (!methodInfo.isVoid()) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            complianceWithForwardRequirements(builder, methodAnalysis, methodInspection, forwardEvaluationInfo, contentNotNullRequired);

            EvaluationResult mv = EvaluateMethodCall.methodValue(modified, evaluationContext, methodInfo,
                    methodAnalysis, objectValue, parameterValues);
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
        Boolean objectValueIsLinkedToField = evaluationContext.isCurrentlyLinkedToField(objectValue);
        int independent = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);

        if (objectValueIsLinkedToField == Boolean.TRUE &&
                (independent == MultiLevel.DEPENDENT_1 || independent == MultiLevel.DEPENDENT_2)) {
            Expression wrappedResult = PropertyWrapper.propertyWrapper(result, Map.of(VariableProperty.INDEPENDENT, independent));
            builder.setExpression(wrappedResult);
        /* IMPROVE we'll have to have some delay detection? but is rather sensitive
           if (objectValueIsLinkedToField == null || independent == Level.DELAY) {
            return delayedMethod(evaluationContext, builder, result, contextModifiedDelay==Level.TRUE, parameterValues);
         */
        } else {
            builder.setExpression(result);
        }

        // scope delay
        if (resultIsDelayed || contextModifiedDelay == Level.TRUE) {
            delay(evaluationContext, builder, objectValue, contextModifiedDelay == Level.TRUE);
        }

        checkCommonErrors(builder, evaluationContext, objectValue);
        return builder.build();
    }

    private boolean checkFinalizer(EvaluationContext evaluationContext,
                                   EvaluationResult.Builder builder,
                                   MethodAnalysis methodAnalysis,
                                   Expression objectValue) {
        if (methodAnalysis.getProperty(VariableProperty.FINALIZER) == Level.TRUE) {
            if (objectValue instanceof IsVariableExpression ve) {
                if (raiseErrorForFinalizer(evaluationContext, builder, ve.variable())) return false;
                // check links of this variable
                LinkedVariables linked = evaluationContext.linkedVariables(ve.variable());
                if (linked == LinkedVariables.DELAY) {
                    // we'll have to come back, we need to know the linked variables
                    return true;
                }
                return linked.variables().stream().anyMatch(v -> raiseErrorForFinalizer(evaluationContext, builder, v));
            }
        }
        return false;
    }

    private boolean raiseErrorForFinalizer(EvaluationContext evaluationContext,
                                           EvaluationResult.Builder builder, Variable variable) {
        if (variable instanceof FieldReference && (evaluationContext.getCurrentMethod() == null ||
                evaluationContext.getCurrentMethod().methodAnalysis.getProperty(VariableProperty.FINALIZER) != Level.TRUE)) {
            // ensure that the current method has been marked @Finalizer
            builder.raiseError(Message.FINALIZER_METHOD_CALLED_ON_FIELD_NOT_IN_FINALIZER);
            return true;
        }
        if (variable instanceof ParameterInfo) {
            builder.raiseError(Message.FINALIZER_METHOD_CALLED_ON_PARAMETER);
            return true;
        }
        return false;
    }

    private EvaluationResult delayedMethod(EvaluationContext evaluationContext,
                                           EvaluationResult.Builder builder,
                                           Expression objectValue,
                                           boolean contextModifiedDelay,
                                           List<Expression> parameterValues) {
        Logger.log(DELAYED, "Delayed method call because the object value or one of the parameter values of {} is delayed: {}",
                methodInfo.name, parameterValues);
        builder.setExpression(DelayedExpression.forMethod(methodInfo));
        // set scope delay
        delay(evaluationContext, builder, objectValue, contextModifiedDelay);
        return builder.build();
    }

    private void delay(EvaluationContext evaluationContext,
                       EvaluationResult.Builder builder,
                       Expression objectValue,
                       boolean contextModifiedDelay) {
        objectValue.variables().forEach(variable -> {
            builder.setProperty(variable, VariableProperty.SCOPE_DELAY, Level.TRUE);
            if (variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                This currentThis = evaluationContext.currentThis();
                builder.setProperty(currentThis, VariableProperty.SCOPE_DELAY, Level.TRUE);
                if (contextModifiedDelay) {
                    builder.setProperty(currentThis, VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE);
                }
            }
        });
    }

    /*
    next => after the call; required => before the call.
    @Mark goes from BEFORE to AFTER
    @Only(before) goes from BEFORE to BEFORE
    @Only(after) goes from AFTER to AFTER
     */
    private record ImmutableData(int delay, int required, int next) {
    }

    private static final ImmutableData NOT_EVENTUAL = new ImmutableData(Level.DELAY, Level.DELAY, Level.DELAY);
    private static final ImmutableData IMMUTABLE_DELAYED = new ImmutableData(Level.TRUE, Level.DELAY, Level.DELAY);

    private ImmutableData computeContextImmutable(EvaluationContext evaluationContext) {
        int formalTypeImmutable = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo)
                .getProperty(VariableProperty.IMMUTABLE);
        if (formalTypeImmutable == Level.DELAY) {
            return IMMUTABLE_DELAYED;
        }
        int formalLevel = MultiLevel.level(formalTypeImmutable);
        int formalValue = MultiLevel.value(formalTypeImmutable, formalLevel);
        if (formalValue != MultiLevel.EVENTUAL) {
            return NOT_EVENTUAL;
        }
        MethodAnalysis.Eventual eventual = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getEventual();
        if (eventual == null) {
            return IMMUTABLE_DELAYED;
        }
        if (eventual.mark()) {
            return new ImmutableData(Level.DELAY, MultiLevel.before(formalLevel), MultiLevel.after(formalLevel));
        }
        if (eventual.after() != null) {
            if (eventual.after()) {
                return new ImmutableData(Level.DELAY, MultiLevel.after(formalLevel), MultiLevel.after(formalLevel));
            }
            return new ImmutableData(Level.DELAY, MultiLevel.before(formalLevel), MultiLevel.before(formalLevel));
        }
        return NOT_EVENTUAL;
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
        if (modified == Level.TRUE && evaluationContext.cannotBeModified(objectValue)) {
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

    /*
    In general, the method result a, in a = b.method(c, d), can link to b, c and/or d.
    Independence and level 2 immutability restrict the ability to link.

    The current implementation is heavily focused on understanding links towards the fields of a type,
    i.e., in sub = list.subList(0, 10), we want to link sub to list.

    links from the parameters to the result (from c to a, from d to a) have currently only
    been implemented for @Identity methods (i.e., between a and c).

    So we implement
    1/ void methods cannot link
    2/ if the method is @Identity, the result is linked to the 1st parameter c

    all other rules now determine whether we return an empty set, or the set {a}.

    3/ if the return type of the method is level 2 immutable, there is no linking.
    4/ if the return type of the method is implicitly immutable in the type, there is no linking.
       (there may be a @Dependent1 or @Dependent2, but that's not relevant here)
    5/ if a (the object) is @E2Immutable, the method must be @Independent, so it cannot link
    6/ if the method is @Independent, then it does not link to the fields -> empty.
       Note that in the *current* implementation, all modifying methods are @Dependent
       (independence is implemented only to compute level 2 immutability)

     */

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {

        // RULE 1: void method cannot link
        ParameterizedType returnType = methodInfo.returnType();
        if (Primitives.isVoid(returnType)) return LinkedVariables.EMPTY; // no assignment

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.TRUE) return evaluationContext.linkedVariables(parameterExpressions.get(0));

        // RULE 3: if the return type is E2IMMU, then no links at all
        boolean notSelf = returnType.typeInfo != evaluationContext.getCurrentType();
        if (notSelf) {
            int immutable = MultiLevel.value(methodAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
            if (immutable == MultiLevel.DELAY) return LinkedVariables.DELAY;
            if (immutable >= MultiLevel.EVENTUAL_AFTER) {
                return LinkedVariables.EMPTY;
            }
        }

        // RULE 4: neither can implicitly immutable types
        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo);
        Set<ParameterizedType> implicitlyImmutable = typeAnalysis.getImplicitlyImmutableDataTypes();
        if (implicitlyImmutable != null && implicitlyImmutable.contains(methodInfo.returnType())) {
            return LinkedVariables.EMPTY;
        }

        // RULE 5: level 2 immutable object cannot link
        int objectImmutable = evaluationContext.getProperty(object, VariableProperty.IMMUTABLE, true, false);
        int objectE2Immutable = MultiLevel.value(objectImmutable, MultiLevel.E2IMMUTABLE);
        if (objectE2Immutable >= MultiLevel.EVENTUAL_AFTER) {
            return LinkedVariables.EMPTY;
        }

        // RULE 6: independent method: no link to object
        int independent = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
        if (independent == MultiLevel.EFFECTIVE) {
            return LinkedVariables.EMPTY;
        }

        // delays
        if (independent == Level.DELAY || objectE2Immutable == MultiLevel.DELAY ||
                identity == Level.DELAY || implicitlyImmutable == null) {
            return LinkedVariables.DELAY;
        }

        // link to the object
        return evaluationContext.linkedVariables(object);
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

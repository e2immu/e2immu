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
import java.util.stream.Stream;

import static org.e2immu.analyser.output.QualifiedName.Required.NO_METHOD;
import static org.e2immu.analyser.output.QualifiedName.Required.YES;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;


public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions, OneVariable {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MethodCall.class);

    public final boolean objectIsImplicit; // irrelevant after evaluation
    public final Expression object;
    public final List<Expression> parameterExpressions;

    public MethodCall(Identifier identifier,
                      Expression object,
                      MethodInfo methodInfo,
                      List<Expression> parameterExpressions) {
        this(identifier, false, object, methodInfo, methodInfo.returnType(), parameterExpressions);
    }

    public MethodCall(Identifier identifier,
                      boolean objectIsImplicit,
                      Expression object,
                      MethodInfo methodInfo,
                      ParameterizedType returnType,
                      List<Expression> parameterExpressions) {
        super(identifier, methodInfo, returnType);
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
        return new MethodCall(identifier,
                objectIsImplicit,
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
            VariableExpression ve;
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
            } else if ((ve = object.asInstanceOf(VariableExpression.class)) != null &&
                    ve.variable() instanceof This thisVar) {
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
        EvaluationResult mv = new EvaluateMethodCall(evaluationContext, this).methodValue(modified,
                evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo),
                objectIsImplicit, reObject.value(), concreteReturnType,
                reParamValues);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reParams)
                .compose(reObject, mv);
        if (reObject.value() instanceof IsVariableExpression ve) {
            EvaluationResult forwarded = ve.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
            builder.compose(forwarded);
        }
        return builder.setExpression(mv.value()).build();
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
                    !methodInfo.shallowAnalysis();

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
        int modified = alwaysModifying ? Level.TRUE : recursiveCall || partOfCallCycle ? Level.FALSE : modifiedMethod;
        int contextModifiedDelay = Level.fromBool(modified == Level.DELAY);
        builder.causeOfContextModificationDelay(methodInfo, modified == Level.DELAY);

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        int notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL));
        boolean contentNotNullRequired = notNullForward == MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;

        ImmutableData immutableData = recursiveCall || partOfCallCycle ? NOT_EVENTUAL :
                computeContextImmutable(evaluationContext);

        // modification on a type expression -> make sure that this gets modified too!
        if (object instanceof TypeExpression) {
            /* static method, not on a variable (not System.out.println, e.g.), with modification information
            Translate the modification to a 'this' variable
             */
            This thisType = new This(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentType());
            builder.setProperty(thisType, VariableProperty.CONTEXT_MODIFIED, modified); // without being "read"
            builder.setProperty(thisType, VariableProperty.CONTEXT_MODIFIED_DELAY, contextModifiedDelay);
            builder.setProperty(thisType, VariableProperty.METHOD_CALLED, Level.TRUE);
        }

        // scope
        EvaluationResult objectResult = object.evaluate(evaluationContext, new ForwardEvaluationInfo(Map.of(
                VariableProperty.CONTEXT_NOT_NULL, notNullForward,
                VariableProperty.METHOD_CALLED, Level.TRUE,
                VariableProperty.CONTEXT_MODIFIED_DELAY, contextModifiedDelay,
                VariableProperty.CONTEXT_MODIFIED, modified,
                VariableProperty.CONTEXT_IMMUTABLE_DELAY, immutableData.delay,
                VariableProperty.CONTEXT_IMMUTABLE, immutableData.required,
                VariableProperty.NEXT_CONTEXT_IMMUTABLE, immutableData.next), true,
                forwardEvaluationInfo.assignmentTarget()));

        // null scope
        Expression objectValue = objectResult.value();
        if (objectValue.isInstanceOf(NullConstant.class)) {
            builder.raiseError(object.getIdentifier(), Message.Label.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                evaluationContext, forwardEvaluationInfo,
                methodInfo, recursiveCall || partOfCallCycle, objectValue);
        List<Expression> parameterValues = res.v;
        builder.compose(objectResult, res.k.build());

        // revisit abstract method, check if object value pointed to a concrete, modifying method
        if (abstractMethod && objectValue instanceof IsVariableExpression ve) {
            MethodInfo pointsToConcreteMethod = evaluationContext.concreteMethod(ve.variable(), methodInfo);
            if (pointsToConcreteMethod != null) {
                MethodAnalysis concreteMethodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(pointsToConcreteMethod);
                int modifyingConcreteMethod = concreteMethodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
                if (modifyingConcreteMethod != Level.DELAY) {
                    builder.markContextModified(ve.variable(), modifyingConcreteMethod);
                } else {
                    builder.markContextModifiedDelay(ve.variable());
                    builder.eraseContextModified(ve.variable());
                }
            }
            // TODO else propagate modification?
        }

        // precondition
        EvaluatePreconditionFromMethod.evaluate(evaluationContext, builder, methodInfo, objectValue, parameterValues);

        // linked1 towards the scope
        IsVariableExpression scopeVariable;
        boolean objectIsDelayed = evaluationContext.isDelayed(objectValue);
        if ((scopeVariable = objectValue.asInstanceOf(IsVariableExpression.class)) != null) {
            LinkedVariables linked1Scope = linked1VariablesScope(evaluationContext);
            builder.registerLinked1(scopeVariable.variable(), linked1Scope.delay(objectIsDelayed));
        } else {
            LinkedVariables linkedVariables = evaluationContext.linkedVariables(objectValue);
            LinkedVariables linked1Scope = linked1VariablesScope(evaluationContext);
            LinkedVariables combined = linkedVariables.merge(linked1Scope).delay(objectIsDelayed);
            for (Variable variable : linkedVariables.variables()) {
                builder.registerLinked1(variable, combined);
            }
        }

        // FIXME HACK
        IsVariableExpression ve0, ve2;
        if ("arraycopy".equals(methodInfo.name)
                && (ve0 = parameterExpressions.get(0).asInstanceOf(IsVariableExpression.class)) != null
                && (ve2 = parameterExpressions.get(2).asInstanceOf(IsVariableExpression.class)) != null) {
            ParameterizedType pt = ve0.returnType().copyWithOneFewerArrays();
            SetOfTypes transparentTypes = evaluationContext.getAnalyserContext().getTypeAnalysis(evaluationContext.getCurrentType()).getTransparentTypes();
            boolean isDelayed = ve0 instanceof DelayedVariableExpression || ve2 instanceof DelayedVariableExpression || transparentTypes == null;
            if (transparentTypes == null || transparentTypes.contains(pt)) {
                builder.link1(ve2.variable(), ve0.variable(), isDelayed);
            }
            if(transparentTypes == null || !transparentTypes.contains(pt)) {
                builder.link(ve2.variable(), ve0.variable(), isDelayed);
            }
        }


        // before we return, increment the time, irrespective of NO_VALUE
        if (!recursiveCall) {
            boolean increment;
            switch (methodAnalysis.analysisMode()) {
                case COMPUTED -> {
                    StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                    if (lastStatement == null) {
                        increment = false;
                    } else if (lastStatement.flowData.initialTimeNotYetSet()) {
                        return delayedMethod(evaluationContext, builder, objectValue,
                                contextModifiedDelay == Level.TRUE, parameterValues);
                    } else {
                        if (lastStatement.flowData.timeAfterSubBlocksNotYetSet()) {
                            increment = false;
                            // see e.g. Trie.recursivelyVisit: recursive call, but inside a lambda so we don't see this
                        } else {
                            increment = lastStatement.flowData.getTimeAfterSubBlocks() > 0;
                        }
                    }
                }
                // TODO aggregated needs its specific code
                case CONTRACTED, AGGREGATED -> increment = !methodInfo.methodResolution.isSet() ||
                        methodInfo.methodResolution.get().allowsInterrupts();
                default -> throw new IllegalStateException("Unexpected value: " + methodAnalysis.analysisMode());
            }
            if (increment) builder.incrementStatementTime();
        }

        boolean delayedFinalizer = checkFinalizer(evaluationContext, builder, methodAnalysis, objectValue);

        if (parameterValues.stream().anyMatch(evaluationContext::isDelayed) || delayedFinalizer) {
            return delayedMethod(evaluationContext, builder, objectValue,
                    contextModifiedDelay == Level.TRUE, parameterValues);
        }

        // companion methods
        Expression modifiedInstance;
        if (modified == Level.TRUE) {
            modifiedInstance = checkCompanionMethodsModifying(builder, evaluationContext, this, methodInfo,
                    methodAnalysis, object, objectValue, parameterValues);
        } else {
            modifiedInstance = null;
        }

        Expression result;
        boolean resultIsDelayed;
        if (!methodInfo.isVoid()) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            complianceWithForwardRequirements(builder, methodAnalysis, methodInspection, forwardEvaluationInfo, contentNotNullRequired);

            EvaluationResult mv = new EvaluateMethodCall(evaluationContext, this).methodValue(modified,
                    methodAnalysis, objectIsImplicit, objectValue, concreteReturnType, parameterValues);
            builder.compose(mv);
            if (mv.value() == objectValue && mv.value().isInstanceOf(NewObject.class) && modifiedInstance != null) {
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
                if (linked.isDelayed()) {
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
            builder.raiseError(getIdentifier(), Message.Label.FINALIZER_METHOD_CALLED_ON_FIELD_NOT_IN_FINALIZER);
            return true;
        }
        if (variable instanceof ParameterInfo) {
            builder.raiseError(getIdentifier(), Message.Label.FINALIZER_METHOD_CALLED_ON_PARAMETER);
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
        builder.setExpression(DelayedExpression.forMethod(methodInfo, concreteReturnType,
                evaluationContext.linkedVariables(objectValue).variablesAsList()));
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
        int effective = MultiLevel.effective(formalTypeImmutable);
        if (effective != MultiLevel.EVENTUAL) {
            return NOT_EVENTUAL;
        }
        MethodAnalysis.Eventual eventual = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getEventual();
        if (eventual == null) {
            return IMMUTABLE_DELAYED;
        }

        int formalLevel = MultiLevel.level(formalTypeImmutable);
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

    static Expression checkCompanionMethodsModifying(
            EvaluationResult.Builder builder,
            EvaluationContext evaluationContext,
            Expression currentCall,
            MethodInfo methodInfo,
            MethodAnalysis methodAnalysis,
            Expression object,
            Expression objectValue,
            List<Expression> parameterValues) {
        if (evaluationContext.isDelayed(objectValue)) return null; // don't even try
        if (objectValue.cannotHaveState()) return null; // ditto

        Expression state;
        if (evaluationContext.hasState(objectValue)) {
            state = evaluationContext.state(objectValue);
            if (evaluationContext.isDelayed(state)) return null; // DELAY
        } else {
            state = new BooleanConstant(evaluationContext.getPrimitives(), true);
        }

        AtomicReference<Expression> newState = new AtomicReference<>(state);
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
                            // first: pre (POST CONDITION, MODIFICATION)
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
                        newState.set(And.and(evaluationContext, startFrom, companionValueTranslated));
                    }
                });
        if (containsEmptyExpression(newState.get())) {
            newState.set(new BooleanConstant(evaluationContext.getPrimitives(), true));
        }
        Expression newInstance;

        IsVariableExpression ive;
        if (currentCall instanceof NewObject) {
            newInstance = currentCall;
        } else if ((ive = objectValue.asInstanceOf(IsVariableExpression.class)) != null) {
            Expression current = evaluationContext.currentValue(ive.variable(), evaluationContext.getInitialStatementTime());
            if (current instanceof NewObject newObject) {
                if (newObject.minimalNotNull() == MultiLevel.NULLABLE) {
                    newInstance = newObject.removeConstructor();
                } else {
                    newInstance = current;
                }
            } else {
                newInstance = NewObject.forGetInstance(current.getIdentifier(), current.returnType());
            }
        } else {
            newInstance = NewObject.forGetInstance(currentCall.getIdentifier(), currentCall.returnType());
        }

        Expression modifiedInstance;
        if (newState.get().isBoolValueTrue()) {
            modifiedInstance = newInstance;
        } else {
            modifiedInstance = PropertyWrapper.addState(newInstance, newState.get());
        }

        LinkedVariables linkedVariables = variablesLinkedToScopeVariableInModifyingMethod(evaluationContext, methodInfo, parameterValues);
        VariableExpression ve;
        if (object != null && (ve = object.asInstanceOf(VariableExpression.class)) != null && !(ve.variable() instanceof This)) {
            builder.modifyingMethodAccess(ve.variable(), modifiedInstance, linkedVariables);
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
        boolean delayed = false;
        for (Expression p : parameterValues) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(Math.min(n - 1, i));
            int modified = evaluationContext.getAnalyserContext()
                    .getParameterAnalysis(parameterInfo).getProperty(VariableProperty.MODIFIED_VARIABLE);
            if (modified != Level.FALSE) {
                if (modified == Level.DELAY) delayed = true;
                LinkedVariables cd = evaluationContext.linkedVariables(p);
                if (cd.isDelayed()) delayed = true;
                result.addAll(cd.variables());
            }
            i++;
        }
        return new LinkedVariables(result, delayed);
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
                builder.raiseError(getIdentifier(), Message.Label.UNNECESSARY_METHOD_CALL, "toString()");
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
            builder.raiseError(getIdentifier(), Message.Label.CALLING_MODIFYING_METHOD_ON_E2IMMU,
                    "Method: " + methodInfo.distinguishingName() + ", Type: " + objectValue.returnType());
        }
    }

    private void complianceWithForwardRequirements(EvaluationResult.Builder builder,
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
                        builder.raiseError(getIdentifier(), Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION,
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
        if (v instanceof InlineConditional inline) {
            return internalCompareTo(inline.condition);
        }
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
        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
        // return the formal value
        int formal = methodAnalysis.getProperty(variableProperty);
        // dynamic value? if the method has a type parameter as part of the result, we could be returning different values
        if (VariableProperty.IMMUTABLE == variableProperty) {
            // IMPROVE but, the formal value could already have been "upgraded" from the immutability of the type (firstEntry() is @E2Container instead of @Container)
            return dynamicImmutable(formal, methodAnalysis, evaluationContext);
        }
        if (VariableProperty.INDEPENDENT == variableProperty) {
            int immutable = getProperty(evaluationContext, VariableProperty.IMMUTABLE, duringEvaluation);
            if (immutable == Level.DELAY) return Level.DELAY;
            int immutableLevel = MultiLevel.level(immutable);
            if (immutableLevel >= MultiLevel.LEVEL_2_IMMUTABLE) {
                return MultiLevel.independentCorrespondingToImmutableLevel(immutableLevel);
            }
        }
        return formal;
    }

    private int dynamicImmutable(int formal, MethodAnalysis methodAnalysis, EvaluationContext evaluationContext) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY) return Level.DELAY;
        if (identity == Level.TRUE) {
            return evaluationContext.getProperty(parameterExpressions.get(0), VariableProperty.IMMUTABLE, true, true);
        }

        if (MultiLevel.isAtLeastEventuallyE2Immutable(formal)) {
            // the independence of the result, and the immutable level of the hidden content, will determine the result
            int methodIndependent = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
            if (methodIndependent == Level.DELAY) return Level.DELAY;
            assert MultiLevel.independentConsistentWithImmutable(methodIndependent, formal) :
                    "formal independent value inconsistent with formal immutable value for method " + methodInfo.fullyQualifiedName;

            // we know the method is formally @Independent1+ < @Independent;
            // looking at the immutable level of linked1 variables looks "through" the recursion that this method provides
            // in the case of factory methods or indeed identity
            // see E2Immutable_11
            TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(evaluationContext.getCurrentType());
            SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();
            MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(methodInfo);
            if (methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
                int minParams = Integer.MAX_VALUE;
                for (Expression expression : parameterExpressions) {
                    ParameterizedType concreteType = expression.returnType();
                    EvaluationContext.HiddenContent concreteHiddenTypes = evaluationContext
                            .extractHiddenContentTypes(concreteType, hiddenContentTypes);
                    if (concreteHiddenTypes.delay()) {
                        minParams = Level.DELAY;
                    } else {
                        int hiddenImmutable = concreteHiddenTypes.hiddenTypes().stream()
                                .mapToInt(pt -> pt.defaultImmutable(evaluationContext.getAnalyserContext(), true))
                                .min().orElse(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE);
                        minParams = Math.min(minParams, hiddenImmutable);
                    }
                }

                if (minParams == Level.DELAY) return Level.DELAY;
                return MultiLevel.sumImmutableLevels(formal, minParams);
            }

            LinkedVariables linked1 = linked1VariablesValue(evaluationContext);
            int linked1Immutable = linked1.variables().stream()
                    .mapToInt(v -> evaluationContext.getProperty(v, VariableProperty.IMMUTABLE))
                    .min().orElse(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE);
            if (linked1Immutable == Level.DELAY) return Level.DELAY;
            return MultiLevel.sumImmutableLevels(formal, linked1Immutable);
        }
        return formal;
    }

    /*
    In general, the method result a, in a = b.method(c, d), can link to b, c and/or d.
    Independence and level 2+ immutability restrict the ability to link.

    The current implementation is heavily focused on understanding links towards the fields of a type,
    i.e., in sub = list.subList(0, 10), we want to link sub to list.

    links from the parameters to the result (from c to a, from d to a) have currently only
    been implemented for @Identity methods (i.e., between a and c).

    So we implement
    1/ void methods cannot link
    2/ if the method is @Identity, the result is linked to the 1st parameter c

    all other rules now determine whether we return an empty set, or the set {a}.

    3/ In case that a == this, we're calling methods of our own type.
       If they are non-modifying, the method result can be substituted, sometimes in terms of fields.
       In our implementation, linking to 'this' is not needed, we catch modifying methods on this directly
    4/ if the return type of the method is dependent1 or higher, there is no linking.
    5/ if the return type of the method is transparent in the type, there is no linking.

     */

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        // RULE 1: void method cannot link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.TRUE) return evaluationContext.linkedVariables(parameterExpressions.get(0));
        boolean delayed = identity == Level.DELAY;

        // RULE 3: the current implementation doesn't link to "this" as object.
        // see the method for other restrictions
        if (ignoreLinkingBecauseOfScope()) {
            return LinkedVariables.EMPTY;
        }

        // RULE 4: if the return type is @Dependent1 or higher, there is no linking
        int independent = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT);
        if (independent >= MultiLevel.INDEPENDENT_1) {
            return LinkedVariables.EMPTY;
        }
        delayed |= independent == Level.DELAY;

        // RULE 5: neither can transparent types
        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo);
        SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();
        if (hiddenContentTypes != null && hiddenContentTypes.contains(methodInfo.returnType())) {
            return LinkedVariables.EMPTY;
        }
        delayed |= hiddenContentTypes == null;

        // link to the object, and all the variables linked to object
        return evaluationContext.linkedVariables(object).merge(new LinkedVariables(Set.of(), delayed));
    }

    private boolean ignoreLinkingBecauseOfScope() {
        VariableExpression ve;
        if ((ve = object.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof This) return true;
        return object instanceof TypeExpression; // static
    }

    /*
    In general, the method result a, in a = b.method(c, d), can content link to b, c and/or d.
    Independence and immutability restrict the ability to link.

    This method is not concerned with links between b and c,d (see linked1VariablesScope) nor
    between c and d (not implemented).

    Content links from the parameters to the result (from c to a, from d to a) have currently only
    been implemented for @Identity methods (i.e., between a and c).

    So we implement
    1/ void methods cannot content link
    2/ if the method is @Identity, the result is content linked to the 1st parameter c

    all other rules now determine whether we return an empty set, or the set {a}.

    3/ Contrary to linking, we allow content linking to 'this' (e.g. in T t = get(index),
       we allow t to be content linked).
    4/ if the return type of the method is dependent, or independent, there is no content linking.
    5/ if the return type of the method is transparent in the type, there is content linking.

    CHAINING:
    T t = list.get(0) will content link t to list.
    T t = list.subList(0, 1).get(0) should also link t to list. Note that list.subList(0, 1) is linked to list.
    T t = new ArrayList(list).get(0) should also link t to list. Note that new ArrayList(list) is content linked to list.
    */

    @Override
    public LinkedVariables linked1VariablesValue(EvaluationContext evaluationContext) {
        // RULE 1: void method cannot content link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity content links to the 1st parameter
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.TRUE) return evaluationContext.linked1Variables(parameterExpressions.get(0));
        boolean delayed = identity == Level.DELAY;

        // RULE 3: not implemented

        // RULE 4: if the return type is @Dependent, or @Independent, there is no content linking
        // we do INDEPENDENT first
        int methodIndependent = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT);
        if (methodIndependent == MultiLevel.INDEPENDENT) {
            return LinkedVariables.EMPTY;
        }
        delayed |= methodIndependent == Level.DELAY;

        // RULE 5: neither can transparent types
        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(evaluationContext.getCurrentType());
        SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();

        ParameterizedType concreteReturnType = returnType();
        boolean isNotTransparent = hiddenContentTypes != null && !hiddenContentTypes.contains(concreteReturnType);
        delayed |= hiddenContentTypes == null;

        if (isNotTransparent && methodIndependent == MultiLevel.DEPENDENT) {
            return LinkedVariables.EMPTY;
        }

        MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(methodInfo);
        if (methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
            // content link to the parameters, and all variables normally linked to them
            return NewObject.linkedVariablesFromParameters(parameterExpressions, methodInspection, evaluationContext);
        }

        // map.firstEntry() is E2Container, with 2x ERContainer type parameters -> ERContainer, independent -> EMPTY
        // however, we must take into account the immutable value on the method as well (@E2Container instead of the normal @Container)
        int immutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
        delayed |= immutable == Level.DELAY;

        int concreteImmutable = concreteReturnType.defaultImmutable(evaluationContext.getAnalyserContext(), true, immutable);
        if (concreteImmutable == MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE) return LinkedVariables.EMPTY;
        delayed |= concreteImmutable == Level.DELAY;

        // content link to the object, and all the variables content+non-content linked to object
        return evaluationContext.linked1Variables(object)
                .merge(evaluationContext.linkedVariables(object))
                .merge(new LinkedVariables(Set.of(), delayed));
    }

    @Override
    public LinkedVariables linked1VariablesScope(EvaluationContext evaluationContext) {
        return NewObject.linkedVariablesFromParameters(parameterExpressions,
                methodInfo.methodInspection.get(), evaluationContext);
    }

    @Override
    public boolean isNumeric() {
        return Primitives.isNumeric(methodInfo.returnType().bestTypeInfo());
    }

    @Override
    public List<Variable> variables() {
        return Stream.concat(object.variables().stream(),
                        parameterExpressions.stream().flatMap(e -> e.variables().stream()))
                .toList();
    }

    public boolean objectIsThisOrSuper(InspectionProvider inspectionProvider) {
        VariableExpression ve;
        if ((ve = object.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof This) return true;
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        return !methodInspection.isStatic() && objectIsImplicit;
    }

}

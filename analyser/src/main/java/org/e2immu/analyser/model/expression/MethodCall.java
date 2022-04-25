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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.output.QualifiedName.Required.NO_METHOD;
import static org.e2immu.analyser.output.QualifiedName.Required.YES;


public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions, OneVariable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodCall.class);

    public final boolean objectIsImplicit; // irrelevant after evaluation
    public final Expression object;
    public final List<Expression> parameterExpressions;

    public MethodCall(Expression object,
                      MethodInfo methodInfo,
                      List<Expression> parameterExpressions) {
        this(Identifier.joined("methodCall", Stream.concat(Stream.of(object.getIdentifier()), parameterExpressions.stream().map(Expression::getIdentifier)).toList()),
                false, object, methodInfo, methodInfo.returnType(), parameterExpressions);
    }

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
        super(identifier,
                object.getComplexity() + 1 + parameterExpressions.stream().mapToInt(Expression::getComplexity).sum(),
                methodInfo, returnType);
        this.object = Objects.requireNonNull(object);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        assert parameterExpressions.stream().noneMatch(Expression::isDelayed) : "Creating a method call with delayed arguments";
        this.objectIsImplicit = objectIsImplicit;
    }

    // only used in the inequality system
    @Override
    public Variable variable() {
        List<Variable> variables = object.variables(true);
        return variables.size() == 1 ? variables.get(0) : null;
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression asExpression = translationMap.translateExpression(this);
        if (asExpression != this) return asExpression;
        MethodInfo translatedMethod = translationMap.translateMethod(methodInfo);
        Expression translatedObject = object.translate(inspectionProvider, translationMap);
        ParameterizedType translatedReturnType = translationMap.translateType(concreteReturnType);
        List<Expression> translatedParameters = parameterExpressions.isEmpty() ? parameterExpressions :
                parameterExpressions.stream().map(e -> e.translate(inspectionProvider, translationMap))
                        .collect(TranslationCollectors.toList(parameterExpressions));
        if (translatedMethod == methodInfo && translatedObject == object
                && translatedReturnType == concreteReturnType
                && translatedParameters == parameterExpressions) {
            return this;
        }
        CausesOfDelay causesOfDelay = translatedParameters.stream()
                .map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge)
                .merge(translatedObject.causesOfDelay());
        if (causesOfDelay.isDelayed()) {
            return DelayedExpression.forMethod(identifier, translatedMethod, translatedMethod.returnType(),
                    LinkedVariables.delayedEmpty(causesOfDelay), causesOfDelay);
        }
        return new MethodCall(identifier,
                objectIsImplicit,
                translatedObject,
                translatedMethod,
                translatedReturnType,
                translatedParameters);
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
                (we check methodInspection is set, because of debugOutput)
                 */
                assert !methodInfo.methodInspection.isSet()
                        || methodInfo.methodInspection.get().isStatic();
                TypeInfo typeInfo = typeExpression.parameterizedType.typeInfo;
                TypeName typeName = typeInfo.typeName(qualification.qualifierRequired(typeInfo));
                outputBuilder.add(new QualifiedName(methodInfo.name, typeName,
                        qualification.qualifierRequired(methodInfo) ? YES : NO_METHOD));
                if (guideGenerator != null) start = true;
            } else if ((ve = object.asInstanceOf(VariableExpression.class)) != null &&
                    ve.variable() instanceof This thisVar) {
                //     (we check methodInspection is set, because of debugOutput)
                assert !methodInfo.methodInspection.isSet() ||
                        !methodInfo.methodInspection.get().isStatic() : "Have a static method with scope 'this'? "
                        + methodInfo.fullyQualifiedName + "; this " + thisVar.typeInfo.fullyQualifiedName;
                TypeName typeName = thisVar.typeInfo.typeName(qualification.qualifierRequired(thisVar.typeInfo));
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
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);

        boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        boolean recursiveCall = recursiveCall(methodInfo, context.evaluationContext());

        // is the method modifying, do we need to wait?
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        DV modifiedMethod = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);

        DV modified = recursiveCall || breakCallCycleDelay ? DV.FALSE_DV : modifiedMethod;

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        DV notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL));
        boolean contentNotNullRequired = notNullForward.equals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);

        ImmutableData immutableData = recursiveCall || breakCallCycleDelay ? NOT_EVENTUAL :
                computeContextImmutable(context);

        // modification on a type expression -> make sure that this gets modified too!
        if (object instanceof TypeExpression) {
            /* static method, not on a variable (not System.out.println, e.g.), with modification information
            Translate the modification to a 'this' variable
             */
            This thisType = new This(context.getAnalyserContext(), context.getCurrentType());
            builder.setProperty(thisType, Property.CONTEXT_MODIFIED, modified); // without being "read"
        }

        // scope
        ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo)
                .clearProperties()
                .addProperty(Property.CONTEXT_NOT_NULL, notNullForward)
                .addProperty(Property.CONTEXT_MODIFIED, modified)
                .addProperty(Property.CONTEXT_IMMUTABLE, immutableData.required)
                .addProperty(Property.NEXT_CONTEXT_IMMUTABLE, immutableData.next)
                .setNotAssignmentTarget().build();
        EvaluationResult objectResult = object.evaluate(context, fwd);

        // null scope
        Expression objectValue = objectResult.value();
        if (objectValue.isInstanceOf(NullConstant.class) && forwardEvaluationInfo.isComplainInlineConditional()) {
            builder.raiseError(object.getIdentifier(), Message.Label.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                context, forwardEvaluationInfo,
                methodInfo, recursiveCall || breakCallCycleDelay, objectValue);
        List<Expression> parameterValues = res.v;
        builder.compose(objectResult, res.k.build());

        // precondition
        Precondition precondition = EvaluatePreconditionFromMethod.evaluate(context, builder, identifier, methodInfo, objectValue,
                parameterValues);
        builder.addPrecondition(precondition);

        LinkedVariables linkedVariables = objectValue.linkedVariables(context);
        if (object instanceof IsVariableExpression ive) {
            linkedVariables = linkedVariables.merge(LinkedVariables.of(ive.variable(), LinkedVariables.STATICALLY_ASSIGNED_DV));
        }
        LinkedVariables linked1Scope = recursiveCall ? LinkedVariables.EMPTY : linked1VariablesScope(context);
        linkedVariables.variables().forEach((v, level) -> linked1Scope.variables().forEach((v2, level2) -> {
            DV combined = object.isDelayed() ? object.causesOfDelay() : level.max(level2);
            builder.link(v, v2, combined);
        }));

        linksBetweenParameters(builder, context);

        // increment the time, irrespective of NO_VALUE
        if (!recursiveCall) {
            EvaluationResult delayedMethod = incrementStatementTime(methodAnalysis, context, builder, modified);
            if (delayedMethod != null) return delayedMethod;
        }

        CausesOfDelay delayedFinalizer = checkFinalizer(context, builder, methodAnalysis, objectValue);

        CausesOfDelay parameterDelays = parameterValues.stream().map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (parameterDelays.isDelayed() || delayedFinalizer.isDelayed() || modified.isDelayed() || objectResult.causesOfDelay().isDelayed()) {
            CausesOfDelay causes = modified.causesOfDelay().merge(parameterDelays).merge(delayedFinalizer)
                    .merge(objectResult.causesOfDelay());
            return delayedMethod(context, builder, causes, modified);
        }

        // companion methods
        Expression modifiedInstance;
        if (modified.valueIsTrue()) {
            Expression unlinkedModifiedInstance = checkCompanionMethodsModifying(identifier, builder, context, methodInfo,
                    methodAnalysis, object, objectValue, parameterValues);
            if (unlinkedModifiedInstance != null) {
                // for now the only test that uses this wrapped linked variables is Finalizer_0; but it is really pertinent.
                modifiedInstance = linkedVariables.isEmpty() ? unlinkedModifiedInstance
                        : PropertyWrapper.propertyWrapper(unlinkedModifiedInstance, linkedVariables);
            } else {
                modifiedInstance = null;
            }
        } else {
            modifiedInstance = null;
        }

        Expression result;
        if (!methodInfo.isVoid()) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();

            EvaluationResult mv = new EvaluateMethodCall(context, this).methodValue(modified,
                    methodAnalysis, objectIsImplicit, objectValue, concreteReturnType, parameterValues,
                    forwardEvaluationInfo, modifiedInstance);
            builder.compose(mv);
            result = mv.value();

            complianceWithForwardRequirements(context, builder, methodAnalysis, methodInspection, forwardEvaluationInfo);
        } else {
            result = EmptyExpression.NO_RETURN_VALUE;
        }

        builder.setExpression(result);

        checkCommonErrors(builder, context, objectValue);
        if (objectValue.isDelayed() || modifiedInstance != null && modifiedInstance.isDelayed()) {
            CausesOfDelay causes = modifiedInstance != null
                    ? objectValue.causesOfDelay().merge(modifiedInstance.causesOfDelay())
                    : objectValue.causesOfDelay();
            return delayedMethod(context, builder, causes, modified);
        }
        return builder.build();
    }

    public static boolean recursiveCall(MethodInfo methodInfo, EvaluationContext evaluationContext) {
        MethodAnalyser currentMethod = evaluationContext.getCurrentMethod();
        if (currentMethod != null && currentMethod.getMethodInfo() == methodInfo) return true;
        if (evaluationContext.getClosure() != null) {
            LOGGER.debug("Going recursive on call to {}, to {} ", methodInfo.fullyQualifiedName,
                    evaluationContext.getClosure().getCurrentType().fullyQualifiedName);
            return recursiveCall(methodInfo, evaluationContext.getClosure());
        }
        return false;
    }

    private EvaluationResult incrementStatementTime(MethodAnalysis methodAnalysis,
                                                    EvaluationResult context,
                                                    EvaluationResult.Builder builder,
                                                    DV modified) {
        boolean increment;
        switch (methodAnalysis.analysisMode()) {
            case COMPUTED -> {
                StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                if (lastStatement == null) {
                    increment = false;
                } else if (lastStatement.flowData().initialTimeNotYetSet()) {
                    CausesOfDelay initialTime = DelayFactory.createDelay(methodAnalysis.location(Stage.INITIAL),
                            CauseOfDelay.Cause.INITIAL_TIME);
                    CausesOfDelay causes = modified.causesOfDelay().merge(initialTime);
                    return delayedMethod(context, builder, causes, modified);
                } else {
                    if (lastStatement.flowData().timeAfterSubBlocksNotYetSet()) {
                        increment = false;
                        // see e.g. Trie.recursivelyVisit: recursive call, but inside a lambda so we don't see this
                    } else {
                        increment = lastStatement.flowData().getTimeAfterSubBlocks() > 0;
                    }
                }
            }
            // TODO aggregated needs its specific code
            case CONTRACTED, AGGREGATED -> increment = !methodInfo.methodResolution.isSet() ||
                    methodInfo.methodResolution.get().allowsInterrupts();
            default -> throw new IllegalStateException("Unexpected value: " + methodAnalysis.analysisMode());
        }
        if (increment) builder.incrementStatementTime();
        return null;
    }

    /*
    not computed, only contracted!
     */
    public void linksBetweenParameters(EvaluationResult.Builder builder, EvaluationResult context) {
        // key is dependent on values, but only if all of them are variable expressions
        Map<Integer, Map<Integer, DV>> crossLinks = methodInfo.crossLinks(context.getAnalyserContext());
        if (crossLinks != null) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            crossLinks.forEach((source, v) -> v.forEach((target, level) -> {
                IsVariableExpression vSource = parameterExpressions.get(source).asInstanceOf(IsVariableExpression.class);
                if (vSource != null) {
                    boolean targetIsVarArgs = target == methodInspection.getParameters().size() - 1 &&
                            methodInspection.getParameters().get(target).parameterInspection.get().isVarArgs();
                    linksBetweenParameters(builder, context, vSource, target, level);
                    if (targetIsVarArgs) {
                        for (int i = target + 1; i < parameterExpressions.size(); i++) {
                            linksBetweenParameters(builder, context, vSource, i, level);
                        }
                    }
                }
            }));
        }
    }

    private void linksBetweenParameters(EvaluationResult.Builder builder,
                                        EvaluationResult context,
                                        IsVariableExpression source,
                                        int target,
                                        DV level) {
        Expression expression = parameterExpressions.get(target);
        LinkedVariables targetLinks = expression.linkedVariables(context);
        CausesOfDelay delays = expression.causesOfDelay().merge(source.causesOfDelay());
        targetLinks.variables().forEach((v, l) ->
                builder.link(source.variable(), v, delays.isDelayed() ? delays : level.max(l)));
    }

    // we raise an error IF a finalizer method is called on a parameter, or on a field inside a finalizer method
    private CausesOfDelay checkFinalizer(EvaluationResult context,
                                         EvaluationResult.Builder builder,
                                         MethodAnalysis methodAnalysis,
                                         Expression objectValue) {
        if (methodAnalysis.getProperty(Property.FINALIZER).valueIsTrue()) {
            IsVariableExpression ive;
            if ((ive = objectValue.asInstanceOf(IsVariableExpression.class)) != null) {
                if (raiseErrorForFinalizer(context, builder, ive.variable())) {
                    return CausesOfDelay.EMPTY;
                }
            }
            // check links of this expression
            LinkedVariables linked = objectValue.linkedVariables(context);
            if (linked.isDelayed()) {
                // we'll have to come back, we need to know the linked variables
                return linked.causesOfDelay();
            }
            linked.variables()
                    .keySet()
                    .forEach(v -> raiseErrorForFinalizer(context, builder, v));
        }
        return CausesOfDelay.EMPTY;
    }

    private boolean raiseErrorForFinalizer(EvaluationResult context,
                                           EvaluationResult.Builder builder, Variable variable) {
        if (variable instanceof FieldReference && (context.getCurrentMethod() == null ||
                !context.getCurrentMethod().getMethodAnalysis().getProperty(Property.FINALIZER).valueIsTrue())) {
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

    private EvaluationResult delayedMethod(EvaluationResult evaluationContext,
                                           EvaluationResult.Builder builder,
                                           CausesOfDelay causesOfDelay,
                                           DV modified) {
        assert causesOfDelay.isDelayed();
        // NOTE: we do not convert the linked variables to blanket delay! this is not necessary and holds back Context Modified
        LinkedVariables linkedVariables = linkedVariables(evaluationContext);
        builder.setExpression(DelayedExpression.forMethod(identifier, methodInfo, concreteReturnType,
                linkedVariables, causesOfDelay));
        if (!modified.valueIsFalse()) {
            // no idea yet whether this method call will change the object from some variable to Instance
            // IMPORTANT: we change the value of the object variable, not the variable the object may be
            // assigned to (object instead of objectValue)
            VariableExpression ve;
            if ((ve = object.asInstanceOf(VariableExpression.class)) != null && !(ve.variable() instanceof This)) {
                Expression delayedObject = DelayedVariableExpression.forDelayedModificationInMethodCall(ve.variable(), causesOfDelay);
                builder.modifyingMethodAccess(ve.variable(), delayedObject, linkedVariables);
            }
        }
        return builder.build();
    }

    /*
    next => after the call; required => before the call.
    @Mark goes from BEFORE to AFTER
    @Only(before) goes from BEFORE to BEFORE
    @Only(after) goes from AFTER to AFTER
     */
    private record ImmutableData(DV required, DV next) {
    }

    private static final ImmutableData NOT_EVENTUAL = new ImmutableData(MultiLevel.NOT_INVOLVED_DV, MultiLevel.NOT_INVOLVED_DV);

    // delays travel to EXTERNAL_IMMUTABLE via variableOccursInEventuallyImmutableContext
    private ImmutableData computeContextImmutable(EvaluationResult evaluationContext) {
        DV formalTypeImmutable = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo)
                .getProperty(Property.IMMUTABLE);
        if (formalTypeImmutable.isDelayed()) {
            return new ImmutableData(formalTypeImmutable.causesOfDelay(), formalTypeImmutable.causesOfDelay());
        }
        MultiLevel.Effective effective = MultiLevel.effective(formalTypeImmutable);
        if (effective != MultiLevel.Effective.EVENTUAL) {
            return NOT_EVENTUAL;
        }
        MethodAnalysis.Eventual eventual = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getEventual();
        if (eventual.causesOfDelay().isDelayed()) {
            return new ImmutableData(eventual.causesOfDelay(), eventual.causesOfDelay());
        }

        int formalLevel = MultiLevel.level(formalTypeImmutable);
        if (eventual.mark()) {
            return new ImmutableData(MultiLevel.beforeImmutableDv(formalLevel), MultiLevel.afterImmutableDv(formalLevel));
        }
        if (eventual.after() != null) {
            if (eventual.after()) {
                return new ImmutableData(MultiLevel.afterImmutableDv(formalLevel), MultiLevel.afterImmutableDv(formalLevel));
            }
            return new ImmutableData(MultiLevel.beforeImmutableDv(formalLevel), MultiLevel.beforeImmutableDv(formalLevel));
        }
        return NOT_EVENTUAL;
    }

    static Expression checkCompanionMethodsModifying(
            Identifier identifier,
            EvaluationResult.Builder builder,
            EvaluationResult context,
            MethodInfo methodInfo,
            MethodAnalysis methodAnalysis,
            Expression object,
            Expression objectValue,
            List<Expression> parameterValues) {
        if (objectValue.isDelayed()) return objectValue; // don't even try
        if (objectValue.cannotHaveState()) return null; // ditto

        Expression state;
        BooleanConstant TRUE = new BooleanConstant(context.getPrimitives(), true);
        if (context.evaluationContext().hasState(objectValue)) {
            state = context.evaluationContext().state(objectValue);
            if (state.isDelayed()) return null; // DELAY
        } else {
            state = TRUE;
        }

        AtomicReference<Expression> newState = new AtomicReference<>(state);
        Set<CompanionMethodName> companionMethodNames = methodInfo.methodInspection.get().getCompanionMethods().keySet();
        if (companionMethodNames.isEmpty()) {
            // modifying method, without instructions on how to change the state... we simply clear it!
            newState.set(TRUE);
        } else {
            companionMethodNames.stream()
                    .filter(e -> CompanionMethodName.MODIFYING_METHOD_OR_CONSTRUCTOR.contains(e.action()))
                    .sorted()
                    .forEach(companionMethodName -> companionMethod(context, methodInfo, methodAnalysis,
                            parameterValues, newState, companionMethodName));

            if (containsEmptyExpression(newState.get())) {
                newState.set(TRUE);
            }
        }
        Expression newInstance;

        IsVariableExpression ive;
        Expression createInstanceBasedOn;
        boolean inLoop = false;
        if (objectValue.isInstanceOf(Instance.class) ||
                objectValue.isInstanceOf(ConstructorCall.class) && methodInfo.isConstructor) {
            newInstance = unwrap(objectValue);
            createInstanceBasedOn = null;
        } else if ((ive = objectValue.asInstanceOf(IsVariableExpression.class)) != null) {
            Expression current = context.currentValue(ive.variable());
            if (current.isInstanceOf(Instance.class) || current.isInstanceOf(ConstructorCall.class) && methodInfo.isConstructor) {
                newInstance = unwrap(current);
                createInstanceBasedOn = null;
            } else {
                createInstanceBasedOn = current;
                newInstance = null;
            }
            inLoop = ive instanceof VariableExpression ve && ve.getSuffix() instanceof VariableExpression.VariableInLoop;
        } else {
            createInstanceBasedOn = objectValue;
            newInstance = null;
        }
        if (createInstanceBasedOn != null) {
            ParameterizedType returnType = createInstanceBasedOn.returnType();
            Properties valueProperties = context.getAnalyserContext().defaultValueProperties(returnType,
                    MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            CausesOfDelay causesOfDelay = valueProperties.delays();
            if (causesOfDelay.isDelayed()) {
                return DelayedExpression.forMethod(identifier, methodInfo, objectValue.returnType(),
                        objectValue.linkedVariables(context).changeAllToDelay(causesOfDelay), causesOfDelay);
            }
            newInstance = Instance.forGetInstance(objectValue.getIdentifier(), objectValue.returnType(), valueProperties);
        }

        Expression modifiedInstance;
        if (newState.get().isBoolValueTrue() || inLoop) {
            modifiedInstance = newInstance;
        } else {
            modifiedInstance = PropertyWrapper.addState(newInstance, newState.get());
        }

        LinkedVariables linkedVariables = objectValue.linked1VariablesScope(context);
        VariableExpression ve;
        if (object != null && (ve = object.asInstanceOf(VariableExpression.class)) != null && !(ve.variable() instanceof This)) {
            builder.modifyingMethodAccess(ve.variable(), modifiedInstance, linkedVariables);
        }
        return modifiedInstance;
    }

    private static void companionMethod(EvaluationResult context,
                                        MethodInfo methodInfo,
                                        MethodAnalysis methodAnalysis,
                                        List<Expression> parameterValues,
                                        AtomicReference<Expression> newState,
                                        CompanionMethodName companionMethodName) {
        CompanionAnalysis companionAnalysis = methodAnalysis.getCompanionAnalyses().get(companionMethodName);
        MethodInfo aspectMethod;
        if (companionMethodName.aspect() != null) {
            aspectMethod = context.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo).getAspects().get(companionMethodName.aspect());
            assert aspectMethod != null : "Expect aspect method to be known";
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

            if (aspectMethod != null && !methodInfo.isConstructor) {
                // first: pre (POST CONDITION, MODIFICATION)
                filterResult = EvaluateMethodCall.filter(context, aspectMethod, newState.get(), List.of());
            } else {
                filterResult = null;
            }
        }

        Expression companionValueTranslated = translateCompanionValue(context, companionAnalysis,
                filterResult, newState.get(), parameterValues);

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
                                                      CompanionAnalysis companionAnalysis,
                                                      Filter.FilterResult<MethodCall> filterResult,
                                                      Expression instanceState,
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

        Expression companionValue = companionAnalysis.getValue();
        Expression translated = companionValue.translate(context.getAnalyserContext(), translationMap.build());
        EvaluationResult child = context.child(instanceState, true);
        ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().doNotReevaluateVariableExpressionsDoNotComplain()
                .setInCompanionExpression().build();
        EvaluationResult companionValueTranslationResult = translated.evaluate(child, fwd);
        // no need to compose: this is a separate operation. builder.compose(companionValueTranslationResult);
        return companionValueTranslationResult.value();
    }

    private DV notNullRequirementOnScope(DV notNullRequirement) {
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
    }

    private void checkCommonErrors(EvaluationResult.Builder builder, EvaluationResult context, Expression objectValue) {
        if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
            ParameterizedType type = objectValue.returnType();
            if (type != null && type.typeInfo != null && type.typeInfo ==
                    context.getPrimitives().stringTypeInfo()) {
                builder.raiseError(getIdentifier(), Message.Label.UNNECESSARY_METHOD_CALL, "toString()");
            }
        }

        MethodInfo method;
        if (objectValue instanceof InlinedMethod ico) {
            method = ico.methodInfo();
        } else {
            method = methodInfo;
        }

        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(method);
        DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
        if (modified.valueIsTrue() && context.evaluationContext().cannotBeModified(objectValue).valueIsTrue()) {
            builder.raiseError(getIdentifier(), Message.Label.CALLING_MODIFYING_METHOD_ON_E2IMMU,
                    "Method: " + methodInfo.distinguishingName() + ", Type: " + objectValue.returnType());
        }
    }

    private void complianceWithForwardRequirements(EvaluationResult context,
                                                   EvaluationResult.Builder builder,
                                                   MethodAnalysis methodAnalysis,
                                                   MethodInspection methodInspection,
                                                   ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.isComplainInlineConditional()) {
            DV requiredNotNull = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
            if (MultiLevel.isEffectivelyNotNull(requiredNotNull)) {
                DV methodNotNull = methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION);
                if (methodNotNull.isDone()) {
                    boolean isNotNull = MultiLevel.isEffectivelyNotNull(methodNotNull);

                    // see FormatterSimplified_1, write.apply( ... ) is the method call, write is a parameter, and write becomes @NotNull1
                    Set<Variable> objectVars = object.directAssignmentVariables();
                    boolean cnnTravelsToFields = context.getAnalyserContext().getConfiguration().analyserConfiguration().computeContextPropertiesOverAllMethods();
                    boolean scopeIsFunctionalInterfaceLinkedToParameter = !objectVars.isEmpty() && objectVars.stream()
                            .allMatch(v -> v.parameterizedType().isFunctionalInterface() && (v instanceof ParameterInfo ||
                                    cnnTravelsToFields && v instanceof FieldReference fr && fr.fieldInfo.owner.primaryType() == context.getCurrentType()));
                    if (!isNotNull && !builder.isNotNull(this).valueIsTrue() && !scopeIsFunctionalInterfaceLinkedToParameter) {
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

    /*
     See Warnings_13.method3 for an example why you're better off with the concreteReturnType rather than the formal type.
     */

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        boolean recursiveCall = context.getCurrentMethod() != null && methodInfo == context.getCurrentMethod().getMethodInfo();
        if (recursiveCall) {
            return property.bestDv;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        // return the formal value
        DV formal = methodAnalysis.getProperty(property);
        if (property.valueProperty) {

            DV adjusted;
            // dynamic value? if the method has a type parameter as part of the result, we could be returning different values
            if (Property.IMMUTABLE == property) {
                adjusted = dynamicImmutable(formal, methodAnalysis, context).max(formal);
            } else if (Property.INDEPENDENT == property) {
                DV immutable = getProperty(context, Property.IMMUTABLE, duringEvaluation);
                if (immutable.isDelayed()) return immutable;
                int immutableLevel = MultiLevel.level(immutable);
                if (immutableLevel >= MultiLevel.Level.IMMUTABLE_2.level) {
                    adjusted = MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
                } else {
                    adjusted = formal;
                }
            } else {
                adjusted = formal;
            }
            // formal can be a @NotNull contracted annotation on the method; we cannot dismiss it
            // problem is that it may have to be computed, which introduces an unresolved delay in the case of cyclic calls.
            DV fromConcrete = context.getAnalyserContext().defaultValueProperty(property, concreteReturnType);
            boolean internalCycle = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
            if (internalCycle) return fromConcrete.maxIgnoreDelay(adjusted).max(property.falseDv);
            return fromConcrete.max(adjusted);
        }
        return formal;
    }

    private DV dynamicImmutable(DV formal, MethodAnalysis methodAnalysis, EvaluationResult context) {
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.isDelayed()) return identity;
        if (identity.valueIsTrue()) {
            return context.evaluationContext().getProperty(parameterExpressions.get(0), Property.IMMUTABLE,
                    true, true);
        }

        if (MultiLevel.isAtLeastEventuallyE2Immutable(formal)) {
            assert formal.isDone();
            // the independence of the result, and the immutable level of the hidden content, will determine the result
            DV methodIndependent = methodAnalysis.getProperty(Property.INDEPENDENT);
            if (methodIndependent.isDelayed()) return methodIndependent;

            assert MultiLevel.independentConsistentWithImmutable(methodIndependent, formal) :
                    "formal independent value inconsistent with formal immutable value for method "
                            + methodInfo.fullyQualifiedName + ": independent " + methodIndependent + ", immutable " + formal;

            // we know the method is formally @Independent1+ < @Independent;
            // looking at the immutable level of linked1 variables looks "through" the recursion that this method provides
            // in the case of factory methods or indeed identity
            // see E2Immutable_11
            AnalyserContext analyserContext = context.getAnalyserContext();
            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(context.getCurrentType());
            MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);

            if (methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
                if (typeAnalysis.hiddenContentTypeStatus().isDelayed()) {
                    return typeAnalysis.hiddenContentTypeStatus();
                }
                SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();
                return factoryMethodDynamicallyImmutable(formal, hiddenContentTypes, context);
            }

            /*
            Formal can be E2Immutable for Map.Entry<K, V>, because the removal method has gone.
            It can still upgrade to ERImmutable when the K and V become ER themselves
             */
            return analyserContext.defaultImmutable(returnType(), true, formal);
        }
        return formal;
    }

    private DV factoryMethodDynamicallyImmutable(DV formal,
                                                 SetOfTypes hiddenContentTypes,
                                                 EvaluationResult context) {
        DV minParams = DV.MAX_INT_DV;
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (Expression expression : parameterExpressions) {
            ParameterizedType concreteType = expression.returnType();
            EvaluationContext.HiddenContent concreteHiddenTypes = context.evaluationContext()
                    .extractHiddenContentTypes(concreteType, hiddenContentTypes);
            if (concreteHiddenTypes.causesOfDelay().isDelayed()) {
                causesOfDelay = causesOfDelay.merge(concreteHiddenTypes.causesOfDelay());
            } else {
                DV hiddenImmutable = concreteHiddenTypes.hiddenTypes().stream()
                        .map(pt -> context.getAnalyserContext().defaultImmutable(pt, true))
                        .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                minParams = minParams.min(hiddenImmutable);
            }
        }
        if (minParams == DV.MAX_INT_DV) return formal;
        if (causesOfDelay.isDelayed()) return causesOfDelay;
        if (minParams.isDelayed()) return minParams;
        return MultiLevel.sumImmutableLevels(formal, minParams);
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
    public LinkedVariables linkedVariables(EvaluationResult context) {
        // RULE 1: void method cannot link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;

        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.valueIsTrue()) {
            return parameterExpressions.get(0).linkedVariables(context);
        }
        if (identity.isDelayed()) {
            // temporarily link to both the object and the parameter, in a delayed way
            if (parameterExpressions.isEmpty()) return LinkedVariables.delayedEmpty(identity.causesOfDelay());
            return object.linkedVariables(context)
                    .merge(parameterExpressions.get(0).linkedVariables(context)).changeAllToDelay(identity);
        }

        // RULE 3: in a factory method, the result links to the parameters, directly
        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodInfo);
        if (methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
            // content link to the parameters, and all variables normally linked to them
            return ConstructorCall.linkedVariablesFromParameters(context, methodInspection, parameterExpressions);
        }

        // RULE 4: otherwise, we link to the scope, even if the scope is 'this'
        LinkedVariables linkedVariablesOfScope = object.linkedVariables(context);

        DV methodIndependent = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        if (methodIndependent.isDelayed()) {
            return linkedVariablesOfScope.changeToDelay(methodIndependent);
        }
        if (methodIndependent.equals(MultiLevel.INDEPENDENT_DV)) return LinkedVariables.EMPTY;
        DV level = LinkedVariables.fromIndependentToLinkedVariableLevel(methodIndependent);
        return LinkedVariables.EMPTY.merge(linkedVariablesOfScope, level);
    }

    @Override
    public LinkedVariables linked1VariablesScope(EvaluationResult context) {
        return ConstructorCall.linkedVariablesFromParameters(context,
                methodInfo.methodInspection.get(), parameterExpressions);
    }

    @Override
    public boolean isNumeric() {
        return methodInfo.returnType().isNumeric();
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return Stream.concat(object.variables(descendIntoFieldReferences).stream(),
                        parameterExpressions.stream().flatMap(e -> e.variables(descendIntoFieldReferences).stream()))
                .toList();
    }

    public boolean objectIsThisOrSuper(InspectionProvider inspectionProvider) {
        VariableExpression ve;
        if ((ve = object.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof This) return true;
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        return !methodInspection.isStatic() && objectIsImplicit;
    }

    @Override
    public Expression removeAllReturnValueParts(Primitives primitives) {
        Expression removedFromObject = object.removeAllReturnValueParts(primitives);
        boolean inParameter = parameterExpressions.stream().anyMatch(e -> !e.equals(e.removeAllReturnValueParts(primitives)));
        if (removedFromObject == null || inParameter) {
            return returnType().isBooleanOrBoxedBoolean() ? new BooleanConstant(primitives, true) : null;
        }
        return this;
    }

    @Override
    public Set<Variable> loopSourceVariables() {
        if (methodInfo.methodInspection.get().getParameters().size() == 0
                && object instanceof VariableExpression ve) {
            return Set.of(ve.variable());
        }
        return Set.of();
    }
}

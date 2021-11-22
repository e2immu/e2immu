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
        DV modified = evaluationContext.getAnalyserContext()
                .getMethodAnalysis(methodInfo).getProperty(Property.MODIFIED_METHOD);
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
        DV modifiedMethod = methodAnalysis.getProperty(Property.MODIFIED_METHOD);
        DV modified = alwaysModifying ? Level.TRUE_DV : recursiveCall || partOfCallCycle ? Level.FALSE_DV : modifiedMethod;
        builder.causeOfContextModificationDelay(methodInfo, modified.isDelayed());

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        DV notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL));
        boolean contentNotNullRequired = notNullForward.equals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);

        ImmutableData immutableData = recursiveCall || partOfCallCycle ? NOT_EVENTUAL :
                computeContextImmutable(evaluationContext);

        // modification on a type expression -> make sure that this gets modified too!
        if (object instanceof TypeExpression) {
            /* static method, not on a variable (not System.out.println, e.g.), with modification information
            Translate the modification to a 'this' variable
             */
            This thisType = new This(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentType());
            builder.setProperty(thisType, Property.CONTEXT_MODIFIED, modified); // without being "read"
        }

        // scope
        EvaluationResult objectResult = object.evaluate(evaluationContext, new ForwardEvaluationInfo(Map.of(
                Property.CONTEXT_NOT_NULL, notNullForward,
                Property.CONTEXT_MODIFIED, modified,
                Property.CONTEXT_IMMUTABLE, immutableData.required,
                Property.NEXT_CONTEXT_IMMUTABLE, immutableData.next), true,
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
        // FIXME should we not do this by default, abstract or not?
        if (abstractMethod && objectValue instanceof IsVariableExpression ve) {
            MethodInfo pointsToConcreteMethod = evaluationContext.concreteMethod(ve.variable(), methodInfo);
            if (pointsToConcreteMethod != null) {
                MethodAnalysis concreteMethodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(pointsToConcreteMethod);
                DV modifyingConcreteMethod = concreteMethodAnalysis.getProperty(Property.MODIFIED_METHOD);
                builder.markContextModified(ve.variable(), modifyingConcreteMethod);
            }
            // TODO else propagate modification?
        }

        // precondition
        EvaluatePreconditionFromMethod.evaluate(evaluationContext, builder, methodInfo, objectValue, parameterValues);


        LinkedVariables linkedVariables = objectValue.linkedVariables(evaluationContext);
        if (object instanceof IsVariableExpression ive) {
            linkedVariables = linkedVariables.merge(LinkedVariables.of(ive.variable(), LinkedVariables.STATICALLY_ASSIGNED_DV));
        }
        LinkedVariables linked1Scope = linked1VariablesScope(evaluationContext);
        linkedVariables.variables().forEach((v, level) -> linked1Scope.variables().forEach((v2, level2) -> {
            DV combined = object.isDelayed() ? object.causesOfDelay() : level.max(level2);
            builder.link(v, v2, combined);
        }));

        linksBetweenParameters(builder, evaluationContext);

        // before we return, increment the time, irrespective of NO_VALUE
        if (!recursiveCall) {
            boolean increment;
            switch (methodAnalysis.analysisMode()) {
                case COMPUTED -> {
                    StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
                    if (lastStatement == null) {
                        increment = false;
                    } else if (lastStatement.flowData.initialTimeNotYetSet()) {
                        CausesOfDelay.SimpleSet initialTime = new CausesOfDelay.SimpleSet(methodAnalysis.location(), CauseOfDelay.Cause.INITIAL_TIME);
                        return delayedMethod(evaluationContext, builder, modified.causesOfDelay().merge(initialTime));
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

        CausesOfDelay parameterDelays = parameterValues.stream().map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (parameterDelays.isDelayed() || delayedFinalizer) {
            return delayedMethod(evaluationContext, builder, modified.causesOfDelay().merge(parameterDelays));
        }

        // companion methods
        Expression modifiedInstance;
        if (modified.valueIsTrue()) {
            modifiedInstance = checkCompanionMethodsModifying(builder, evaluationContext, methodInfo,
                    methodAnalysis, object, objectValue, parameterValues);
        } else {
            modifiedInstance = null;
        }

        Expression result;
        if (!methodInfo.isVoid()) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            complianceWithForwardRequirements(builder, methodAnalysis, methodInspection, forwardEvaluationInfo, contentNotNullRequired);

            EvaluationResult mv = new EvaluateMethodCall(evaluationContext, this).methodValue(modified,
                    methodAnalysis, objectIsImplicit, objectValue, concreteReturnType, parameterValues);
            builder.compose(mv);
            if (mv.value() == objectValue && mv.value().isInstanceOf(Instance.class) && modifiedInstance != null) {
                result = modifiedInstance;
            } else {
                result = mv.value();
            }
        } else {
            result = EmptyExpression.NO_RETURN_VALUE;
        }

        builder.setExpression(result);

        checkCommonErrors(builder, evaluationContext, objectValue);
        return builder.build();
    }

    /*
    not computed, only contracted!
     */
    public void linksBetweenParameters(EvaluationResult.Builder builder, EvaluationContext evaluationContext) {
        // key is dependent on values, but only if all of them are variable expressions
        Map<Integer, Map<Integer, DV>> crossLinks = methodInfo.crossLinks(evaluationContext.getAnalyserContext());
        if (crossLinks != null) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            crossLinks.forEach((source, v) -> v.forEach((target, level) -> {
                IsVariableExpression vSource = parameterExpressions.get(source).asInstanceOf(IsVariableExpression.class);
                if (vSource != null) {
                    boolean targetIsVarArgs = target == methodInspection.getParameters().size() - 1 &&
                            methodInspection.getParameters().get(target).parameterInspection.get().isVarArgs();
                    linksBetweenParameters(builder, evaluationContext, vSource, target, level);
                    if (targetIsVarArgs) {
                        for (int i = target + 1; i < parameterExpressions.size(); i++) {
                            linksBetweenParameters(builder, evaluationContext, vSource, i, level);
                        }
                    }
                }
            }));
        }
    }

    private void linksBetweenParameters(EvaluationResult.Builder builder,
                                        EvaluationContext evaluationContext,
                                        IsVariableExpression source,
                                        int target,
                                        DV level) {
        Expression expression = parameterExpressions.get(target);
        LinkedVariables targetLinks = expression.linkedVariables(evaluationContext);
        CausesOfDelay delays = expression.causesOfDelay().merge(source.causesOfDelay());
        targetLinks.variables().forEach((v, l) ->
                builder.link(source.variable(), v, delays.isDelayed() ? delays : level.max(l)));
    }

    private boolean checkFinalizer(EvaluationContext evaluationContext,
                                   EvaluationResult.Builder builder,
                                   MethodAnalysis methodAnalysis,
                                   Expression objectValue) {
        if (methodAnalysis.getProperty(Property.FINALIZER).valueIsTrue()) {
            if (objectValue instanceof IsVariableExpression ve) {
                if (raiseErrorForFinalizer(evaluationContext, builder, ve.variable())) return false;
                // check links of this variable
                LinkedVariables linked = evaluationContext.linkedVariables(ve.variable());
                if (linked.isDelayed()) {
                    // we'll have to come back, we need to know the linked variables
                    return true;
                }
                return linked.variables()
                        .keySet()
                        .stream().anyMatch(v -> raiseErrorForFinalizer(evaluationContext, builder, v));
            }
        }
        return false;
    }

    private boolean raiseErrorForFinalizer(EvaluationContext evaluationContext,
                                           EvaluationResult.Builder builder, Variable variable) {
        if (variable instanceof FieldReference && (evaluationContext.getCurrentMethod() == null ||
                !evaluationContext.getCurrentMethod().methodAnalysis.getProperty(Property.FINALIZER).valueIsTrue())) {
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
                                           CausesOfDelay causesOfDelay) {
        assert causesOfDelay.isDelayed();
        LinkedVariables delayedLinkedVariables = linkedVariables(evaluationContext).changeAllToDelay(causesOfDelay);
        builder.setExpression(DelayedExpression.forMethod(methodInfo, concreteReturnType,
                delayedLinkedVariables, causesOfDelay));
        // set scope delay
        return builder.build();
    }

    /*
    next => after the call; required => before the call.
    @Mark goes from BEFORE to AFTER
    @Only(before) goes from BEFORE to BEFORE
    @Only(after) goes from AFTER to AFTER
     */
    private record ImmutableData(CausesOfDelay causes, DV required, DV next) {
    }

    private static final ImmutableData NOT_EVENTUAL = new ImmutableData(CausesOfDelay.EMPTY, Level.NOT_INVOLVED_DV, Level.NOT_INVOLVED_DV);

    private ImmutableData computeContextImmutable(EvaluationContext evaluationContext) {
        DV formalTypeImmutable = evaluationContext.getAnalyserContext().getTypeAnalysis(methodInfo.typeInfo)
                .getProperty(Property.IMMUTABLE);
        if (formalTypeImmutable.isDelayed()) {
            return new ImmutableData(formalTypeImmutable.causesOfDelay(), Level.NOT_INVOLVED_DV, Level.NOT_INVOLVED_DV);
        }
        MultiLevel.Effective effective = MultiLevel.effective(formalTypeImmutable);
        if (effective != MultiLevel.Effective.EVENTUAL) {
            return NOT_EVENTUAL;
        }
        MethodAnalysis.Eventual eventual = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getEventual();
        if (eventual.causesOfDelay().isDelayed()) {
            return new ImmutableData(eventual.causesOfDelay(), Level.NOT_INVOLVED_DV, Level.NOT_INVOLVED_DV);
        }

        int formalLevel = MultiLevel.level(formalTypeImmutable);
        if (eventual.mark()) {
            return new ImmutableData(CausesOfDelay.EMPTY, MultiLevel.beforeImmutableDv(formalLevel), MultiLevel.afterImmutableDv(formalLevel));
        }
        if (eventual.after() != null) {
            if (eventual.after()) {
                return new ImmutableData(CausesOfDelay.EMPTY, MultiLevel.afterImmutableDv(formalLevel), MultiLevel.afterImmutableDv(formalLevel));
            }
            return new ImmutableData(CausesOfDelay.EMPTY, MultiLevel.beforeImmutableDv(formalLevel), MultiLevel.beforeImmutableDv(formalLevel));
        }
        return NOT_EVENTUAL;
    }

    static Expression checkCompanionMethodsModifying(
            EvaluationResult.Builder builder,
            EvaluationContext evaluationContext,
            MethodInfo methodInfo,
            MethodAnalysis methodAnalysis,
            Expression object,
            Expression objectValue,
            List<Expression> parameterValues) {
        if (objectValue.isDelayed()) return objectValue; // don't even try
        if (objectValue.cannotHaveState()) return null; // ditto

        Expression state;
        if (evaluationContext.hasState(objectValue)) {
            state = evaluationContext.state(objectValue);
            if (state.isDelayed()) return null; // DELAY
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
        Expression xx;
        if (objectValue instanceof Instance || objectValue instanceof ConstructorCall) {
            newInstance = objectValue;
            xx = null;
        } else if ((ive = objectValue.asInstanceOf(IsVariableExpression.class)) != null) {
            Expression current = evaluationContext.currentValue(ive.variable(), evaluationContext.getInitialStatementTime());
            if (current instanceof Instance || current instanceof ConstructorCall) {
                newInstance = current;
                xx = null;
            } else {
                xx = current;
                newInstance = null;
            }
        } else {
            xx = objectValue;
            newInstance = null;
        }
        if (xx != null) {
            ParameterizedType returnType = xx.returnType();
            AnalysisProvider analysisProvider = evaluationContext.getAnalyserContext();
            DV immutable = returnType.defaultImmutable(analysisProvider, false);
            DV container = returnType.defaultContainer(analysisProvider);
            DV independent = returnType.defaultIndependent(analysisProvider);
            CausesOfDelay causesOfDelay = immutable.causesOfDelay().merge(container.causesOfDelay()).merge(independent.causesOfDelay());
            if (causesOfDelay.isDelayed()) {
                return DelayedExpression.forMethod(methodInfo, objectValue.returnType(),
                        objectValue.linkedVariables(evaluationContext).changeAllToDelay(causesOfDelay), causesOfDelay);
            }
            var valueProperties = Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    Property.IMMUTABLE, immutable,
                    Property.INDEPENDENT, independent,
                    Property.CONTAINER, container,
                    Property.IDENTITY, Level.FALSE_DV);
            newInstance = Instance.forGetInstance(objectValue.getIdentifier(), objectValue.returnType(), valueProperties);
        }

        Expression modifiedInstance;
        if (newState.get().isBoolValueTrue()) {
            modifiedInstance = newInstance;
        } else {
            modifiedInstance = PropertyWrapper.addState(newInstance, newState.get());
        }

        LinkedVariables linkedVariables = objectValue.linked1VariablesScope(evaluationContext);
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

    private DV notNullRequirementOnScope(DV notNullRequirement) {
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
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
        DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD);
        if (modified.valueIsTrue() && evaluationContext.cannotBeModified(objectValue)) {
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
            DV requiredNotNull = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
            if (MultiLevel.isEffectivelyNotNull(requiredNotNull)) {
                DV methodNotNull = methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION);
                if (methodNotNull.isDone()) {
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
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        boolean recursiveCall = evaluationContext.getCurrentMethod() != null && methodInfo == evaluationContext.getCurrentMethod().methodInfo;
        if (recursiveCall) {
            return property.bestDv;
        }
        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
        // return the formal value
        DV formal = methodAnalysis.getProperty(property);
        // dynamic value? if the method has a type parameter as part of the result, we could be returning different values
        if (Property.IMMUTABLE == property) {
            return dynamicImmutable(formal, methodAnalysis, evaluationContext);
        }
        if (Property.INDEPENDENT == property) {
            DV immutable = getProperty(evaluationContext, Property.IMMUTABLE, duringEvaluation);
            if (immutable.isDelayed()) return immutable;
            int immutableLevel = MultiLevel.level(immutable);
            if (immutableLevel >= MultiLevel.Level.IMMUTABLE_2.level) {
                return MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
            }
        }
        return formal;
    }

    private DV dynamicImmutable(DV formal, MethodAnalysis methodAnalysis, EvaluationContext evaluationContext) {
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.isDelayed()) return identity;
        if (identity.valueIsTrue()) {
            return evaluationContext.getProperty(parameterExpressions.get(0), Property.IMMUTABLE,
                    true, true);
        }

        if (MultiLevel.isAtLeastEventuallyE2Immutable(formal)) {
            // the independence of the result, and the immutable level of the hidden content, will determine the result
            DV methodIndependent = methodAnalysis.getProperty(Property.IMMUTABLE);
            if (methodIndependent.isDelayed()) return methodIndependent;

            assert MultiLevel.independentConsistentWithImmutable(methodIndependent, formal) :
                    "formal independent value inconsistent with formal immutable value for method " + methodInfo.fullyQualifiedName;

            // we know the method is formally @Independent1+ < @Independent;
            // looking at the immutable level of linked1 variables looks "through" the recursion that this method provides
            // in the case of factory methods or indeed identity
            // see E2Immutable_11
            TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(evaluationContext.getCurrentType());
            MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(methodInfo);

            if (methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
                if(typeAnalysis.hiddenContentTypeStatus().isDelayed()) {
                    return typeAnalysis.hiddenContentTypeStatus();
                }
                SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();
                return factoryMethodDynamicallyImmutable(formal, hiddenContentTypes, evaluationContext);
            }

            /*
            Formal can be E2Immutable for Map.Entry<K, V>, because the removal method has gone.
            It can still upgrade to ERImmutable when the K and V become ER themselves
             */
            return returnType().defaultImmutable(evaluationContext.getAnalyserContext(), true, formal);
        }
        return formal;
    }

    private DV factoryMethodDynamicallyImmutable(DV formal,
                                                 SetOfTypes hiddenContentTypes,
                                                 EvaluationContext evaluationContext) {
        DV minParams = DV.MAX_INT_DV;
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (Expression expression : parameterExpressions) {
            ParameterizedType concreteType = expression.returnType();
            EvaluationContext.HiddenContent concreteHiddenTypes = evaluationContext
                    .extractHiddenContentTypes(concreteType, hiddenContentTypes);
            if (concreteHiddenTypes.causesOfDelay().isDelayed()) {
                causesOfDelay = causesOfDelay.merge(concreteHiddenTypes.causesOfDelay());
            } else {
                DV hiddenImmutable = concreteHiddenTypes.hiddenTypes().stream()
                        .map(pt -> pt.defaultImmutable(evaluationContext.getAnalyserContext(), true))
                        .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                minParams = minParams.min(hiddenImmutable);
            }
        }
        if (causesOfDelay.isDelayed()) return causesOfDelay;
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
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        // RULE 1: void method cannot link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.valueIsTrue()) {
            return parameterExpressions.get(0).linkedVariables(evaluationContext);
        }
        if (identity.isDelayed()) {
            // temporarily link to both the object and the parameter, in a delayed way
            if (parameterExpressions.isEmpty()) return LinkedVariables.delayedEmpty(identity.causesOfDelay());
            return object.linkedVariables(evaluationContext)
                    .merge(parameterExpressions.get(0).linkedVariables(evaluationContext)).changeAllToDelay(identity);
        }

        // RULE 3: in a factory method, the result links to the parameters, directly
        MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(methodInfo);
        if (methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
            // content link to the parameters, and all variables normally linked to them
            return ConstructorCall.linkedVariablesFromParameters(evaluationContext, methodInspection, parameterExpressions);
        }

        // RULE 4: otherwise, we link to the scope, even if the scope is 'this'
        LinkedVariables linkedVariablesOfScope = object.linkedVariables(evaluationContext);

        DV methodIndependent = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        if (methodIndependent.isDelayed()) {
            return linkedVariablesOfScope.changeToDelay(methodIndependent);
        }
        if (methodIndependent.equals(MultiLevel.INDEPENDENT_DV)) return LinkedVariables.EMPTY;
        DV level = MultiLevel.fromIndependentToLinkedVariableLevel(methodIndependent);
        return LinkedVariables.EMPTY.merge(linkedVariablesOfScope, level);
    }

    @Override
    public LinkedVariables linked1VariablesScope(EvaluationContext evaluationContext) {
        return ConstructorCall.linkedVariablesFromParameters(evaluationContext,
                methodInfo.methodInspection.get(), parameterExpressions);
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

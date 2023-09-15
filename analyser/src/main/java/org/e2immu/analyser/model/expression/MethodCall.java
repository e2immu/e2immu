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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.util.ComputeIndependent;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
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
import org.e2immu.support.Either;
import org.slf4j.Logger;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodCall.class);
    public static final String NO_MODIFICATION_TIMES = "";

    public final boolean objectIsImplicit; // irrelevant after evaluation
    public final Expression object;
    public final List<Expression> parameterExpressions;
    public final String modificationTimes; // summary of modification times of object and arguments and their linked variables

    public MethodCall(Identifier identifier,
                      Expression object,
                      MethodInfo methodInfo,
                      List<Expression> parameterExpressions) {
        this(identifier, false, object, methodInfo, methodInfo.returnType(), parameterExpressions,
                NO_MODIFICATION_TIMES, true);
    }

    public MethodCall(Identifier identifier,
                      boolean objectIsImplicit,
                      Expression object,
                      MethodInfo methodInfo,
                      ParameterizedType returnType,
                      List<Expression> parameterExpressions) {
        this(identifier, objectIsImplicit, object, methodInfo, returnType, parameterExpressions, NO_MODIFICATION_TIMES, true);
    }

    public MethodCall(Identifier identifier,
                      boolean objectIsImplicit,
                      Expression object,
                      MethodInfo methodInfo,
                      ParameterizedType returnType,
                      List<Expression> parameterExpressions,
                      String modificationTimes) {
        this(identifier, objectIsImplicit, object, methodInfo, returnType, parameterExpressions, modificationTimes, true);
    }

    private MethodCall(Identifier identifier,
                       boolean objectIsImplicit,
                       Expression object,
                       MethodInfo methodInfo,
                       ParameterizedType returnType,
                       List<Expression> parameterExpressions,
                       String modificationTimes,
                       boolean checkDelays) {
        super(identifier,
                object.getComplexity()
                        + methodInfo.getComplexity()
                        + parameterExpressions.stream().mapToInt(Expression::getComplexity).sum(),
                methodInfo, returnType);
        this.object = Objects.requireNonNull(object);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        assert !checkDelays
                || parameterExpressions.stream().noneMatch(Expression::isDelayed) : "Creating a method call with delayed arguments";
        this.objectIsImplicit = objectIsImplicit;
        this.modificationTimes = Objects.requireNonNull(modificationTimes);
    }

    // only used in the inequality system
    @Override
    public Variable variable() {
        List<Variable> variables = object.variables();
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
                        .filter(e -> !e.isEmpty()) // allows for removal of certain arguments
                        .collect(TranslationCollectors.toList(parameterExpressions));
        String newModificationTimes = Objects.requireNonNullElse(
                translationMap.modificationTimes(this, translatedObject, translatedParameters), modificationTimes);
        if (translatedMethod == methodInfo && translatedObject == object
                && translatedReturnType == concreteReturnType
                && translatedParameters == parameterExpressions
                && newModificationTimes.equals(modificationTimes)) {
            return this;
        }
        CausesOfDelay causesOfDelay = translatedParameters.stream()
                .map(Expression::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge)
                .merge(translatedObject.causesOfDelay());
        MethodCall translatedMc = new MethodCall(identifier, objectIsImplicit, translatedObject,
                translatedMethod, translatedReturnType, translatedParameters, newModificationTimes, causesOfDelay.isDone());
        if (causesOfDelay.isDelayed()) {
            return DelayedExpression.forMethod(identifier, translatedMethod, translatedMethod.returnType(),
                    translatedMc, causesOfDelay, Map.of());
        }
        if (translationMap.translateAgain()) {
            return translatedMc.translate(inspectionProvider, translationMap);
        }
        return translatedMc;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_METHOD;
    }

    @Override
    public void visit(Predicate<Element> predicate) {
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
        return sameMethod
                // FIXME see Basics_28, but undoubtedly other tests that require this!   && modificationTimes.equals(that.modificationTimes)
                // https://github.com/e2immu/e2immu/issues/56
                && parameterExpressions.equals(that.parameterExpressions)
                && object.equals(that.object);
    }

    /*
     the interface and the implementation, or the interface and sub-interface
     */
    private boolean checkSpecialCasesWhereDifferentMethodsAreEqual(MethodInfo m1, MethodInfo m2) {
        // the following line is there for tests:
        if (!m1.methodResolution.isSet() || !m2.methodResolution.isSet()) return false;
        Set<MethodInfo> overrides1 = m1.methodResolution.get().overrides();
        if (m2.typeInfo.isInterface() && overrides1.contains(m2)) return true;
        Set<MethodInfo> overrides2 = m2.methodResolution.get().overrides();
        return m1.typeInfo.isInterface() && overrides2.contains(m1);

        // any other?
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, parameterExpressions, methodInfo, modificationTimes);
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
        if (objectIsImplicit && qualification.doNotQualifyImplicit()) {
            outputBuilder.add(new Text(methodInfo.name));
        } else {
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
        if (forwardEvaluationInfo.isOnlySort()) {
            return evaluateComponents(context, forwardEvaluationInfo);
        }
        MethodInfo concreteMethod = concreteMethod(context, forwardEvaluationInfo);

        boolean breakCallCycleDelay = concreteMethod.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        boolean recursiveCall = recursiveCall(concreteMethod, context.evaluationContext());
        boolean firstInCallCycle = recursiveCall || breakCallCycleDelay;

        // is the method modifying, do we need to wait?
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(concreteMethod);
        DV modifiedMethod = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);

        DV modifiedBeforeCorrection = firstInCallCycle ? DV.FALSE_DV : modifiedMethod;

        // see Independent1_5, functional interface as parameter has @IgnoreModification
        // IMPORTANT: we derive @IgnoreModifications from "object" and not from "objectValue" at the moment
        // we're assuming it is only present contractually at the moment
        DV modified;
        if (!modifiedBeforeCorrection.valueIsFalse()) {
            DV ignoreMod = context.evaluationContext().getProperty(object, Property.IGNORE_MODIFICATIONS,
                    true, true);
            if (MultiLevel.IGNORE_MODS_DV.equals(ignoreMod)) {
                modified = DV.FALSE_DV;
            } else if (modifiedBeforeCorrection.valueIsTrue() && ignoreMod.isDelayed()) {
                modified = ignoreMod; // delay
            } else {
                modified = modifiedBeforeCorrection;
            }
        } else modified = modifiedBeforeCorrection;

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        DV notNullForward = notNullRequirementOnScope(concreteMethod,
                forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL));

        ImmutableData immutableData = firstInCallCycle ? NOT_EVENTUAL :
                computeContextImmutable(context, concreteMethod);

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

        // see DGSimplified_4, backupComparator. the functional interface's CNN cannot be upgraded to content not null,
        // because it is nullable
        boolean allowUpgradeCnnOfScope = objectValue instanceof IsVariableExpression ive &&
                builder.contextNotNullIsNotNullable(ive.variable());

        // process parameters
        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                objectResult, forwardEvaluationInfo, concreteMethod,
                firstInCallCycle, objectValue, allowUpgradeCnnOfScope);
        List<Expression> parameterValues = res.v;
        builder.compose(objectResult, res.k.build());

        // precondition
        Precondition precondition = EvaluatePreconditionFromMethod.evaluate(context, builder, identifier, concreteMethod,
                objectValue, parameterValues);
        builder.addPrecondition(precondition);

        // links from parameter into object
        // (the other direction, object into parameter, yields MODIFIED on the parameter)
        LinkedVariables linkedVariablesOfObject = linkedVariablesOfObject(context, objectValue);

        // see InlinedMethod_10, find3 vs find4
        if (modified.valueIsTrue()) {
            linkedVariablesOfObject.dependentVariables().forEach(v -> builder.markContextModified(v, DV.TRUE_DV));
        }

        List<LinkedVariables> linkedVariablesOfParameters;
        CausesOfDelay linkDelays = CausesOfDelay.EMPTY;
        if (!linkedVariablesOfObject.isEmpty()) {
            linkedVariablesOfParameters = LinkParameters.computeLinkedVariablesOfParameters(context,
                    parameterExpressions, parameterValues);
            Map<ParameterInfo, LinkedVariables> linksToLinkedToObject = firstInCallCycle ? Map.of() :
                    LinkParameters.fromParameterIntoObject(context,
                            context.getAnalyserContext().getMethodInspection(methodInfo),
                            linkedVariablesOfParameters, objectValue.returnType(),
                            objectValue.formalObjectType(context.getAnalyserContext()));
            if (!linksToLinkedToObject.isEmpty()) {
                for (Map.Entry<Variable, DV> e : linkedVariablesOfObject) {
                    Variable linkedToObject = e.getKey();
                    for (LinkedVariables lv : linksToLinkedToObject.values()) {
                        for (Map.Entry<Variable, DV> ee : lv) {
                            Variable linkedToParameter = ee.getKey();
                            DV fromLinkedToParameterToLinkedToObject = ee.getValue();
                            DV fromLinkedToObjectToObject = e.getValue();
                            DV combined = fromLinkedToParameterToLinkedToObject.max(fromLinkedToObjectToObject);
                            DV delayed;
                            if (objectValue.isDelayed()) {
                                /*
                                this extra delay is necessary to prevent Lambda_AAPI_17 to come to too early a link conclusion
                                adding stronger delays in LinkParameters.computeLinkedVariablesOfParameters is detrimental to
                                ECI_10.
                                 */
                                delayed = combined.causesOfDelay().merge(objectValue.causesOfDelay());
                            } else {
                                delayed = combined;
                            }
                            builder.link(linkedToParameter, linkedToObject, delayed);
                            linkDelays = linkDelays.merge(combined.causesOfDelay());
                        }
                    }
                }
            }
        } else {
            linkedVariablesOfParameters = null; // compute later, we don't want to compute if not needed
        }

        // links between parameters
        linksBetweenParameters(builder, context, concreteMethod, parameterValues, linkedVariablesOfParameters);

        // increment the time, irrespective of NO_VALUE
        CausesOfDelay incrementDelays;
        if (!firstInCallCycle) {
            incrementDelays = incrementStatementTime(methodAnalysis, builder, modified);
        } else {
            incrementDelays = CausesOfDelay.EMPTY;
        }

        CausesOfDelay delayedFinalizer = checkFinalizer(context, builder, methodAnalysis, objectValue);

        CausesOfDelay parameterDelays = parameterValues.stream().map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        CausesOfDelay delays1 = modified.causesOfDelay().merge(parameterDelays).merge(delayedFinalizer)
                .merge(objectResult.causesOfDelay()).merge(incrementDelays).merge(linkDelays);

/*
        Expression modifiedInstance;
        ModReturn modReturn = checkCompanionMethodsModifying(identifier, builder, context,
                concreteMethod, object, objectValue, parameterValues, this, modified);
        if (modReturn != null) {
            // mod delayed or true
            if (modReturn.expression != null) {
                // delay in expression
                // for now the only test that uses this wrapped linked variables is Finalizer_0; but it is really pertinent.
                modifiedInstance = linkedVariablesOfObject.isEmpty() ? modReturn.expression
                        : PropertyWrapper.propertyWrapper(modReturn.expression, linkedVariablesOfObject);
            } else {
                // delay in separate causes
                modifiedInstance = null;
                assert modReturn.causes != null && modReturn.causes.isDelayed();
                delays1 = delays1.merge(modReturn.causes);
            }
        } else {
            // no modification at all
            modifiedInstance = null;
        }*/

        // CausesOfDelay delays2 = modifiedInstance == null ? delays1 : delays1.merge(modifiedInstance.causesOfDelay());

        EvaluationResult mv = new EvaluateMethodCall(context, this, delays1)
                .methodValue(modified, methodAnalysis, objectIsImplicit, objectValue, concreteReturnType,
                        parameterValues, forwardEvaluationInfo, null, firstInCallCycle);
        builder.compose(mv);

        MethodInspection methodInspection = concreteMethod.methodInspection.get();
        complianceWithForwardRequirements(context, builder, methodAnalysis, methodInspection, forwardEvaluationInfo);

        checkCommonErrors(builder, context, concreteMethod, objectValue);

        if (identifier instanceof Identifier.PositionalIdentifier &&
                !(object instanceof VariableExpression ve && ve.variable() instanceof This
                        && parameterValues.isEmpty())) {
            builder.addEvaluatedExpression(identifier, builder.getExpression());
            builder.addEvaluatedExpression(identifier, objectValue);
            builder.addEvaluatedExpressions(identifier, parameterValues);
        }

        return builder.build();
    }

    private EvaluationResult evaluateComponents(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult objectEval = object.evaluate(context, forwardEvaluationInfo);
        List<Expression> evaluatedParams = parameterExpressions.stream()
                .map(e -> e.evaluate(context, forwardEvaluationInfo).getExpression()).toList();
        List<Expression> sortedParameters;
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysisNullWhenAbsent(methodInfo);
        if (methodAnalysis != null && methodAnalysis.hasParallelGroups()) {
            sortedParameters = methodAnalysis.sortAccordingToParallelGroupsAndNaturalOrder(parameterExpressions);
        } else {
            sortedParameters = evaluatedParams;
        }
        Expression mc = new MethodCall(identifier, objectIsImplicit, objectEval.getExpression(), methodInfo,
                returnType(), sortedParameters);
        return new EvaluationResult.Builder(context).setExpression(mc).build();
    }

    private LinkedVariables linkedVariablesOfObject(EvaluationResult context, Expression objectValue) {
        LinkedVariables linkedVariables = objectValue.linkedVariables(context);
        if (object instanceof IsVariableExpression ive) {
            return linkedVariables.merge(ive.linkedVariables(context));
        }
        return linkedVariables;
    }

    private MethodInfo concreteMethod(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        MethodInfo concreteMethod;

        /* abstract method... is there a concrete implementation? we should give preference to that one
           we don't allow switching when we're expanding an inline method ... this may lead to new variables
           being introduced, or context properties to change (Symbol for CNN, InlinedMethod_10 for new variables)

         */
        if (methodInfo.methodInspection.get().isAbstract() && forwardEvaluationInfo.allowSwitchingToConcreteMethod()) {
            EvaluationResult objProbe = object.evaluate(context, ForwardEvaluationInfo.DEFAULT);
            Expression expression = objProbe.value();
            TypeInfo typeInfo;
            if (expression instanceof VariableExpression ve) {
                Expression value = context.currentValue(ve.variable());
                typeInfo = value.typeInfoOfReturnType();
            } else {
                typeInfo = expression.typeInfoOfReturnType();
            }
            if (typeInfo != null) {
                MethodInfo concrete = methodInfo.implementationIn(typeInfo);
                concreteMethod = concrete == null ? methodInfo : concrete;
            } else {
                concreteMethod = methodInfo;
            }
        } else {
            concreteMethod = methodInfo;
        }
        return concreteMethod;
    }

    /* we have to probe the object first, to see if there is a value
       A. if there is a value, and the value offers a concrete implementation, we replace methodInfo by that
       concrete implementation.
       B. if there is no value, and the delay indicates that a concrete implementation may be forthcoming,
       we delay
       C otherwise (no value, no concrete implementation forthcoming) we continue with the abstract method.
       */

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

    private CausesOfDelay incrementStatementTime(MethodAnalysis methodAnalysis,
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
                    return modified.causesOfDelay().merge(initialTime);
                } else {
                    if (lastStatement.flowData().timeAfterSubBlocksNotYetSet()) {
                        increment = false;
                        // see e.g. Trie.recursivelyVisit: recursive call, but inside a lambda, so we don't see this
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
        return CausesOfDelay.EMPTY;
    }

    /*
    not computed, only contracted!
     */
    public void linksBetweenParameters(EvaluationResult.Builder builder,
                                       EvaluationResult context,
                                       MethodInfo concreteMethod,
                                       List<Expression> parameterValues,
                                       List<LinkedVariables> linkedVariables) {
        // key is dependent on values, but only if all of them are variable expressions
        Map<ParameterInfo, LinkedVariables> crossLinks = concreteMethod.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        List<LinkedVariables> linkedVariablesOfParameters = linkedVariables == null
                ? LinkParameters.computeLinkedVariablesOfParameters(context, parameterExpressions, parameterValues)
                : linkedVariables;
        crossLinks.forEach((pi, lv) -> lv.stream().forEach(e -> {
            ParameterInfo target = (ParameterInfo) e.getKey();
            boolean targetIsVarArgs = target.parameterInspection.get().isVarArgs();
            DV level = e.getValue();
            Expression targetExpression = parameterExpressions.get(target.index);
            Expression targetValue = parameterValues.get(target.index);
            Variable targetVariable = bestTargetVariable(targetExpression, targetValue);
            if (targetVariable != null) {
                Expression expression = bestExpression(parameterExpressions.get(pi.index), parameterValues.get(pi.index));
                tryLinkBetweenParameters(builder, context, targetVariable, target.index, targetIsVarArgs, level, expression,
                        parameterValues, linkedVariablesOfParameters);
            }
        }));
    }

    /*
     in order of importance:

     InlinedMethod priority over Lambda
     */

    private Expression bestExpression(Expression raw, Expression evaluated) {
        if (evaluated.isInstanceOf(IsVariableExpression.class)) return evaluated;
        if (evaluated.isInstanceOf(InlinedMethod.class)) return evaluated;
        MethodReference mr = evaluated.asInstanceOf(MethodReference.class);
        if (mr != null && mr.scope.isInstanceOf(IsVariableExpression.class)) return evaluated;
        return raw;
    }

    private Variable bestTargetVariable(Expression targetExpression, Expression targetValue) {
        IsVariableExpression ive = targetValue.asInstanceOf(IsVariableExpression.class);
        if (ive != null) {
            return ive.variable();
        }
        IsVariableExpression ive2 = targetExpression.asInstanceOf(IsVariableExpression.class);
        if (ive2 != null) {
            return ive2.variable();
        }
        return null;
    }

    /*
    example Independent1_2_1
    target = ts, index 0, not varargs, linked 4=common_hc to generator, index 1;
    target ts is modified; values are new String[4] and generator, linked variables are this.ts:2 and generator:2
     */
    private void tryLinkBetweenParameters(EvaluationResult.Builder builder,
                                          EvaluationResult context,
                                          Variable target,
                                          int targetIndex,
                                          boolean targetIsVarArgs,
                                          DV level,
                                          Expression source,
                                          List<Expression> parameterValues,
                                          List<LinkedVariables> linkedVariables) {
        IsVariableExpression vSource = source.asInstanceOf(IsVariableExpression.class);
        if (vSource != null) {
            // Independent1_2, DependentVariables_1
            linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, level, vSource,
                    parameterValues, linkedVariables);
        }
        MethodReference methodReference = source.asInstanceOf(MethodReference.class);
        if (methodReference != null) {
            // Independent1_3
            IsVariableExpression mrSource = methodReference.scope.asInstanceOf(IsVariableExpression.class);
            if (mrSource != null) {
                linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, level, mrSource,
                        parameterValues, linkedVariables);
            }
        }
        InlinedMethod inlinedMethod = source.asInstanceOf(InlinedMethod.class);
        if (inlinedMethod != null) {
            // Independent1_4 TODO written to fit exactly this situation, needs expanding
            // we decide between the first argument of the lambda and the return type
            // first, the return type TODO
            ParameterizedType typeOfHiddenContent = inlinedMethod.returnType().erased();
            ParameterizedType typeOfTarget = target.parameterizedType().erased();
            if (typeOfHiddenContent.equals(typeOfTarget)) {
                Expression srv = context.getAnalyserContext().getMethodAnalysis(inlinedMethod.methodInfo()).getSingleReturnValue();
                List<Variable> vars = srv.variables();
                for (Variable v : vars) {
                    if (v instanceof ParameterInfo piLambda && piLambda.owner != inlinedMethod.methodInfo()) {
                        DV l = srv.isDelayed() ? srv.causesOfDelay() : level;
                        linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, l,
                                new VariableExpression(piLambda.identifier, v),
                                parameterValues, linkedVariables);
                    }
                }
            }
        }
        // we must have both lambda and inline: lambda to provide the correct delays in LV, and inline to provide the
        // final value. Code is very similar
        Lambda lambda = source.asInstanceOf(Lambda.class);
        if (lambda != null) {
            ParameterizedType typeOfHiddenContent = lambda.concreteReturnType().erased();
            ParameterizedType typeOfTarget = target.parameterizedType().erased();
            if (typeOfHiddenContent.equals(typeOfTarget)) {
                Expression srv = context.getAnalyserContext().getMethodAnalysis(lambda.methodInfo).getSingleReturnValue();
                List<Variable> vars = srv.variables();
                for (Variable v : vars) {
                    if (v instanceof ParameterInfo piLambda && piLambda.owner != lambda.methodInfo) {
                        DV l = srv.isDelayed() ? srv.causesOfDelay() : level;
                        linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, l,
                                new VariableExpression(piLambda.identifier, v), parameterValues, linkedVariables);
                    }
                }
            }
        }
    }

    private void linksBetweenParametersVarArgs(EvaluationResult.Builder builder,
                                               int targetIndex,
                                               boolean targetIsVarArgs,
                                               DV level,
                                               IsVariableExpression vSource,
                                               List<Expression> parameterValues,
                                               List<LinkedVariables> linkedVariables) {
        if (!LinkedVariables.LINK_INDEPENDENT.equals(level)) {
            linksBetweenParameters(builder, vSource, targetIndex, level, parameterValues, linkedVariables);
            if (targetIsVarArgs) {
                for (int i = targetIndex + 1; i < parameterExpressions.size(); i++) {
                    linksBetweenParameters(builder, vSource, i, level, parameterValues, linkedVariables);
                }
            }
        }
    }

    private void linksBetweenParameters(EvaluationResult.Builder builder,
                                        IsVariableExpression source,
                                        int targetIndex,
                                        DV level,
                                        List<Expression> parameterValues,
                                        List<LinkedVariables> linkedVariables) {
        LinkedVariables targetLinks = linkedVariables.get(targetIndex);
        Expression parameterValue = parameterValues.get(targetIndex);
        CausesOfDelay delays = parameterValue.causesOfDelay().merge(source.causesOfDelay());
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
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().le(LinkedVariables.LINK_DEPENDENT))
                    .map(Map.Entry::getKey)
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

    public boolean hasEmptyModificationTimes() {
        return NO_MODIFICATION_TIMES.equals(modificationTimes);
    }

    public MethodCall copy(String modificationTimes) {
        return new MethodCall(identifier, objectIsImplicit, object, methodInfo, returnType(), parameterExpressions, modificationTimes);
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
    private ImmutableData computeContextImmutable(EvaluationResult evaluationContext, MethodInfo concreteMethod) {
        DV formalTypeImmutable = evaluationContext.getAnalyserContext().getTypeAnalysis(concreteMethod.typeInfo)
                .getProperty(Property.IMMUTABLE);
        if (formalTypeImmutable.isDelayed()) {
            return new ImmutableData(formalTypeImmutable.causesOfDelay(), formalTypeImmutable.causesOfDelay());
        }
        MultiLevel.Effective effective = MultiLevel.effective(formalTypeImmutable);
        if (effective != MultiLevel.Effective.EVENTUAL) {
            return NOT_EVENTUAL;
        }
        MethodAnalysis.Eventual eventual = evaluationContext.getAnalyserContext().getMethodAnalysis(concreteMethod).getEventual();
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

    public record ModReturn(Expression expression, CausesOfDelay causes) {
    }

    static ModReturn checkCompanionMethodsModifying(
            Identifier identifier,
            EvaluationResult.Builder builder,
            EvaluationResult context,
            MethodInfo methodInfo,
            Expression object,
            Expression objectValue,
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

        Expression newState = computeNewState(context, builder, methodInfo, objectValue, parameterValues);
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

            LinkedVariables linkedVariables = object.linkedVariables(context);
            LinkedVariables linkedVariablesValue = objectValue.linkedVariables(context);
            LinkedVariables linkedVariablesCombined = linkedVariables.merge(linkedVariablesValue);
            for (Map.Entry<Variable, DV> e : linkedVariablesCombined.variables().entrySet()) {
                DV dv = e.getValue();
                Variable variable = e.getKey();
                if (ive == null || !variable.equals(ive.variable())) {
                    if (dv.isDone()) {
                        if (dv.le(LinkedVariables.LINK_DEPENDENT)) {
                            ConstructorCall cc;
                            Expression i;
                            Expression varVal = context.currentValue(variable);
                            if ((cc = varVal.asInstanceOf(ConstructorCall.class)) != null && cc.constructor() != null) {
                                Properties valueProperties = context.evaluationContext().getValueProperties(cc);
                                i = Instance.forMethodResult(cc.identifier, cc.returnType(), valueProperties);
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
                        builder.modifyingMethodAccess(variable, delayed, linkedVariablesValue);
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
        if (objectValue.isInstanceOf(Instance.class) ||
                objectValue.isInstanceOf(ConstructorCall.class) && methodInfo.isConstructor) {
            newInstance = unwrap(objectValue);
            createInstanceBasedOn = null;
        } else if (ive != null) {
            Expression current = context.currentValue(ive.variable());
            if (current.isInstanceOf(Instance.class) || current.isInstanceOf(ConstructorCall.class) && methodInfo.isConstructor) {
                newInstance = unwrap(current);
                createInstanceBasedOn = null;
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
            Properties valueProperties = context.getAnalyserContext().defaultValueProperties(returnType,
                    MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            CausesOfDelay causesOfDelay = valueProperties.delays();
            if (causesOfDelay.isDelayed()) {
                if (context.evaluationContext().isMyself(returnType)) {
                    valueProperties = context.evaluationContext().valuePropertiesOfFormalType(returnType, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                } else {
                    return DelayedExpression.forMethod(identifier, methodInfo, objectValue.returnType(),
                            original, causesOfDelay, Map.of());
                }
            }
            newInstance = Instance.forGetInstance(objectValue.getIdentifier(), objectValue.returnType(), valueProperties);
        }
        return newInstance;
    }

    private static Expression computeNewState(EvaluationResult context,
                                              EvaluationResult.Builder builder,
                                              MethodInfo methodInfo,
                                              Expression objectValue,
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
            String objectModificationTimes = temporaryContext.modificationTimesOf(objectValue);
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

            if (aspectMethod != null && !methodInfo.isConstructor) {
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
            String params = currentContext.modificationTimesOf(translatedParameters.toArray(Expression[]::new));
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

    private static DV notNullRequirementOnScope(MethodInfo concreteMethod, DV notNullRequirement) {
        if (concreteMethod.typeInfo.typeInspection.get().isFunctionalInterface()
                && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
    }

    private void checkCommonErrors(EvaluationResult.Builder builder,
                                   EvaluationResult context,
                                   MethodInfo concreteMethod,
                                   Expression objectValue) {
        if (concreteMethod.fullyQualifiedName().equals("java.lang.String.toString()")) {
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
            method = concreteMethod;
        }

        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(method);
        DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD_ALT_TEMP);
        if (modified.valueIsTrue()
                && !context.evaluationContext().inConstructionOrInStaticWithRespectTo(object.returnType().typeInfo)
                && context.evaluationContext().cannotBeModified(objectValue).valueIsTrue()) {
            builder.raiseError(getIdentifier(), Message.Label.CALLING_MODIFYING_METHOD_ON_IMMUTABLE_OBJECT,
                    "Method: " + methodInfo.fullyQualifiedName + ", Type: " + objectValue.returnType());
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
                                "Result of method call " + methodInfo.fullyQualifiedName());
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
        int d = ListUtil.compare(parameterExpressions, mv.parameterExpressions);
        if (d != 0) return d;
        return object.compareTo(mv.object);
    }

    /*
     See Warnings_13.method3 for an example why you're better off with the concreteReturnType rather than the formal type.
     */

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        boolean recursiveCall = recursiveCall(methodInfo, context.evaluationContext());
        boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        if (recursiveCall || breakCallCycleDelay) {
            return property == Property.CONTEXT_MODIFIED ? DV.FALSE_DV : property.bestDv;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        // return the formal value
        DV formal = methodAnalysis.getProperty(property);
        if (property.propertyType == Property.PropertyType.VALUE) {

            boolean internalCycle = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();

            if (Property.IMMUTABLE == property) {
                DV dynamic = dynamicImmutable(formal, methodAnalysis, context);
                return internalCycle ? dynamic.maxIgnoreDelay(property.falseDv) : dynamic;
            }

            if (Property.INDEPENDENT == property) {
                DV immutable = getProperty(context, Property.IMMUTABLE, duringEvaluation);
                if (immutable.isDelayed()) return immutable;
                int immutableLevel = MultiLevel.level(immutable);
                DV independent;
                if (immutableLevel >= MultiLevel.Level.IMMUTABLE_HC.level) {
                    independent = MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
                } else {
                    independent = formal;
                }
                return internalCycle ? independent.maxIgnoreDelay(property.falseDv) : independent;
            }

            // formal can be a @NotNull contracted annotation on the method; we cannot dismiss it
            // problem is that it may have to be computed, which introduces an unresolved delay in the case of cyclic calls.
            DV fromConcrete = context.getAnalyserContext().defaultValueProperty(property, concreteReturnType);

            if (internalCycle) return fromConcrete.maxIgnoreDelay(formal).max(property.falseDv);
            return fromConcrete.max(formal);
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
        AnalyserContext analyserContext = context.getAnalyserContext();
        MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);

        /*
        There is one situation where we need to deviate from the value of the concrete return type: when we know
        through analysis of the statement, that the dynamic immutable value is higher than the formal one.

        E2Immutable_11: method firstEntry() should be immutable, rather than mutable. Must go through the
        immutableDeterminedByTypeParameters() code!

        Enum_6: method returnTwo must return mutable, even if the formal=dynamic value is higher
        MethodReferences_3: method stream() must return mutable: the type parameter is mutable, formal=dynamic is higher
         */
        DV dynamicImmutable = methodAnalysis.getProperty(Property.IMMUTABLE);
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(dynamicImmutable)) return dynamicImmutable;
        DV normalImmutable = analyserContext.typeImmutable(methodInspection.getReturnType());
        DV concreteImmutable = analyserContext.typeImmutable(concreteReturnType);
        CausesOfDelay causes = dynamicImmutable.causesOfDelay().merge(normalImmutable.causesOfDelay())
                .merge(concreteImmutable.causesOfDelay());
        if (causes.isDelayed()) return causes;
        if (dynamicImmutable.equals(normalImmutable)) {
            /*
             there is no difference between the immutability of a method and the immutability of its return type,
             so we don't have to do any acrobatics
             */
            return concreteImmutable;
        }
        assert MultiLevel.isMutable(normalImmutable);
        Inference inference = inferImmutable(analyserContext, concreteReturnType);
        if (inference.causes.isDelayed()) return inference.causes;
        return analyserContext.typeImmutable(concreteReturnType, inference.map);
    }

    private record Inference(CausesOfDelay causes, Map<ParameterizedType, DV> map) {
    }

    private static final Inference EMPTY = new Inference(CausesOfDelay.EMPTY, Map.of());

    private Inference inferImmutable(AnalyserContext analyserContext, ParameterizedType type) {
        if (type.typeInfo == null) return EMPTY;
        DV immutable = analyserContext.typeImmutable(type);
        if (immutable.isDelayed()) return new Inference(immutable.causesOfDelay(), Map.of());
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return EMPTY;

        Map<ParameterizedType, DV> map = new HashMap<>();
        map.put(type, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV);
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        if (MultiLevel.isMutable(immutable)) {
            /*
             all parameter types must be at least immutable_hc, for the main type to be immutable_hc; so we add them, unless
             they're obviously recursively immutable
             */
            for (ParameterizedType parameter : type.parameters) {
                Inference inference = inferImmutable(analyserContext, parameter);
                if (inference.causes.isDelayed()) {
                    causes = causes.merge(inference.causes);
                } else {
                    map.putAll(inference.map);
                }
            }
        }
        return new Inference(causes, map);
    }

    /*
    In general, the method result 'a', in 'a = b.method(c, d)', can link to 'b', 'c' and/or 'd'.
    Independence and immutability restrict the ability to link.

    The current implementation is heavily focused on understanding links towards the fields of a type,
    i.e., in sub = list.subList(0, 10), we want to link sub to list.

    Links from the parameters to the result (from 'c' to 'a', from 'd' to 'a') have currently only
    been implemented for @Identity methods (i.e., between 'a' and 'c').

    So we implement
    1/ void methods cannot link
    2/ if the method is @Identity, the result is linked to the 1st parameter 'c'
    3/ if the method is a factory method, the result is linked to the parameter values

    all other rules now determine whether we return an empty set, or the set {'a'}.

    4/ independence is determined by the independence value of the method, and the independence value of the object 'a'
     */

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        // RULE 1: void method cannot link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;
        boolean recursiveCall = recursiveCall(methodInfo, context.evaluationContext());
        boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        if (recursiveCall || breakCallCycleDelay) {
            return LinkedVariables.EMPTY;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.valueIsTrue()) {
            return parameterExpressions.get(0).linkedVariables(context).minimum(LinkedVariables.LINK_ASSIGNED);
        }
        if (identity.isDelayed() && !parameterExpressions.isEmpty()) {
            // temporarily link to both the object and the parameter, in a delayed way
            return object.linkedVariables(context)
                    .merge(parameterExpressions.get(0).linkedVariables(context))
                    .changeNonStaticallyAssignedToDelay(identity);
        }

        // RULE 3: otherwise, we link to the object, even if the object is 'this'
        // note that we cannot use STATICALLY_ASSIGNED here

        LinkedVariables linkedVariablesOfObject = object.linkedVariables(context).minimum(LinkedVariables.LINK_ASSIGNED);
        if (linkedVariablesOfObject.isEmpty()) {
            // there is no linking...
            return LinkedVariables.EMPTY;
        }
        ParameterizedType returnTypeOfObject = object.returnType();
        DV immutableOfObject = context.getAnalyserContext().typeImmutable(returnTypeOfObject);
        if (!context.evaluationContext().isMyself(returnTypeOfObject) && immutableOfObject.isDelayed()) {
            return linkedVariablesOfObject.changeToDelay(immutableOfObject);
        }
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableOfObject)) {
            /*
             if the result type immutable because of a choice in type parameters, methodIndependent will return
             INDEPENDENT_HC, but the concrete type is deeply immutable
             */
            return LinkedVariables.EMPTY;
        }

        DV methodIndependent = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        if (methodIndependent.isDelayed()) {
            // delay in method independent
            return linkedVariablesOfObject.changeToDelay(methodIndependent);
        }
        if (methodIndependent.equals(MultiLevel.INDEPENDENT_DV)) {
            // we know the result is independent of the object
            return LinkedVariables.EMPTY;
        }
        if (methodIndependent.equals(MultiLevel.DEPENDENT_DV)) {
            Map<Variable, DV> newLinked = new HashMap<>();
            CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
            for (Map.Entry<Variable, DV> e : linkedVariablesOfObject) {
                DV immutable = context.getAnalyserContext().typeImmutable(e.getKey().parameterizedType());
                assert e.getValue().lt(LinkedVariables.LINK_INDEPENDENT);

                if (variable() instanceof This) {
                    /*
                     without this line, we get loops of CONTEXT_IMMUTABLE delays, see e.g., Test_Util_07_Trie
                     */
                    newLinked.put(e.getKey(), LinkedVariables.LINK_DEPENDENT.max(e.getValue()));
                } else if (immutable.isDelayed()) {
                    causesOfDelay = causesOfDelay.merge(immutable.causesOfDelay());
                } else if (MultiLevel.isMutable(immutable)) {
                    newLinked.put(e.getKey(), LinkedVariables.LINK_DEPENDENT.max(e.getValue()));
                } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                    newLinked.put(e.getKey(), LinkedVariables.LINK_COMMON_HC);
                }
            }
            if (causesOfDelay.isDelayed()) {
                return linkedVariablesOfObject.changeToDelay(causesOfDelay);
            }
            return LinkedVariables.of(newLinked);
        }
        assert MultiLevel.INDEPENDENT_HC_DV.equals(methodIndependent);
        ComputeIndependent computeIndependent = new ComputeIndependent(context.getAnalyserContext(),
                context.getCurrentType());
        Map<Variable, DV> newLinked = new HashMap<>();
        for (Map.Entry<Variable, DV> e : linkedVariablesOfObject) {
            ParameterizedType pt1 = e.getKey().parameterizedType();
            // how does the return type fit in the object (or at least, the variable linked to the object)
            DV linkLevel = computeIndependent.directedLinkLevelOfTwoHCRelatedTypes(concreteReturnType, pt1);
            if (!LinkedVariables.LINK_INDEPENDENT.equals(linkLevel)) {
                newLinked.put(e.getKey(), linkLevel);
            }
        }
        return LinkedVariables.of(newLinked);
    }

    @Override
    public boolean isNumeric() {
        return methodInfo.returnType().isNumeric();
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
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

    /*
    assumption: if the object is a variable, and the method is non-modifying and its result implements Iterable,
    then we're looping over the object! (see .sublist(), .entrySet(), ...)
     */
    @Override
    public Either<CausesOfDelay, Set<Variable>> loopSourceVariables(AnalyserContext analyserContext,
                                                                    ParameterizedType parameterizedType) {
        if (object instanceof VariableExpression ve) {
            MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(methodInfo);
            DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD);
            if (modified.isDelayed()) return Either.left(modified.causesOfDelay());
            if (modified.valueIsFalse()) {
                return VariableExpression.loopSourceVariables(analyserContext, ve.variable(), returnType(), parameterizedType);
            }
        }
        return EvaluationContext.NO_LOOP_SOURCE_VARIABLES;
    }

    public String getModificationTimes() {
        return modificationTimes;
    }
}

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
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
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
import org.e2immu.analyser.util2.PackedIntMap;
import org.e2immu.analyser.util.Pair;
import org.e2immu.graph.analyser.PackedInt;
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
    public void visit(Visitor visitor) {
        if (visitor.beforeExpression(this)) {
            object.visit(visitor);
            parameterExpressions.forEach(p -> p.visit(visitor));
        }
        visitor.afterExpression(this);
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
            MethodCall methodCall;
            TypeExpression typeExpression;
            if ((methodCall = object.asInstanceOf(MethodCall.class)) != null) {
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
            } else if ((typeExpression = object.asInstanceOf(TypeExpression.class)) != null) {
                /*
                we may or may not need to write the type here.
                (we check methodInspection is set, because of debugOutput)
                 */
                assert methodInfo.isStatic();
                TypeInfo typeInfo = typeExpression.parameterizedType.typeInfo;
                TypeName typeName = typeInfo.typeName(qualification.qualifierRequired(typeInfo));
                outputBuilder.add(new QualifiedName(methodInfo.name, typeName,
                        qualification.qualifierRequired(methodInfo) ? YES : NO_METHOD));
                if (guideGenerator != null) start = true;
            } else if ((ve = object.asInstanceOf(VariableExpression.class)) != null &&
                       ve.variable() instanceof This thisVar) {
                //     (we check methodInspection is set, because of debugOutput)
                assert !methodInfo.isStatic() : "Have a static method with scope 'this'? "
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
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        if (forwardEvaluationInfo.isOnlySort()) {
            return evaluateComponents(context, forwardEvaluationInfo);
        }
        MethodInfo concreteMethod = concreteMethod(context, forwardEvaluationInfo);

        boolean breakCallCycleDelay = concreteMethod.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        boolean recursiveCall = MethodLinkHelper.recursiveCall(concreteMethod, context.evaluationContext());
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
        if (objectValue.isNullConstant() && forwardEvaluationInfo.isComplainInlineConditional()) {
            builder.raiseError(object.getIdentifier(), Message.Label.NULL_POINTER_EXCEPTION);
        }

        // see DGSimplified_4, backupComparator. the functional interface's CNN cannot be upgraded to content not null,
        // because it is nullable
        boolean allowUpgradeCnnOfScope = objectValue instanceof IsVariableExpression ive &&
                                         builder.contextNotNullIsNotNullable(ive.variable());

        // process parameters
        EvaluateParameters.Result res = EvaluateParameters.go(parameterExpressions, objectResult, forwardEvaluationInfo,
                concreteMethod, firstInCallCycle, objectValue, allowUpgradeCnnOfScope);
        List<Expression> parameterValues = res.evaluationResults().stream().map(EvaluationResult::getExpression).toList();
        builder.compose(objectResult, res.builder().build());

        // precondition
        Precondition precondition = EvaluatePreconditionFromMethod.evaluate(context, builder, identifier, concreteMethod,
                objectValue, parameterValues);
        builder.addPrecondition(precondition);

        // links, 1st: param -> object and param <-> param
        MethodLinkHelper methodLinkHelper = new MethodLinkHelper(context, methodInfo, methodAnalysis);
        EvaluationResult parametersToObject = methodLinkHelper.fromParametersIntoObject(objectResult,
                res.evaluationResults(), true, true);
        LinkedVariables linkedVariablesOfObject = methodLinkHelper.getLinkedVariablesOfObject();
        builder.compose(parametersToObject);

        // links, 2nd: object -> result; this will be the result of the expression
        LinkedVariables lvsResult = methodLinkHelper.linkedVariables(objectResult, res.evaluationResults(),
                concreteReturnType);

        // increment the time, irrespective of NO_VALUE
        CausesOfDelay incrementDelays;
        if (!firstInCallCycle) {
            incrementDelays = incrementStatementTime(methodAnalysis, builder, modified);
        } else {
            incrementDelays = CausesOfDelay.EMPTY;
        }

        CausesOfDelay delayedFinalizer = checkFinalizer(context, builder, methodAnalysis, objectResult);

        CausesOfDelay parameterDelays = parameterValues.stream().map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        CausesOfDelay linkDelays = parametersToObject.causesOfDelay().merge(lvsResult.causesOfDelay());
        CausesOfDelay delays1 = modified.causesOfDelay().merge(parameterDelays).merge(delayedFinalizer)
                .merge(objectResult.causesOfDelay()).merge(incrementDelays).merge(linkDelays);


        Expression modifiedInstance;
        ModReturn modReturn = checkCompanionMethodsModifying(identifier, builder, context,
                concreteMethod, object, objectResult, parameterValues, this, modified);
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
        }

        CausesOfDelay delays2 = modifiedInstance == null ? delays1 : delays1.merge(modifiedInstance.causesOfDelay());

        EvaluationResult mv = new EvaluateMethodCall(context, this, delays2)
                .methodValue(modified, methodAnalysis, objectIsImplicit, objectResult, concreteReturnType,
                        res.evaluationResults(), lvsResult, forwardEvaluationInfo, modifiedInstance, firstInCallCycle);
        builder.compose(mv);
        builder.setLinkedVariablesOfExpression(lvsResult);

        complianceWithForwardRequirements(context, builder, methodAnalysis, forwardEvaluationInfo);

        checkCommonErrors(builder, context, concreteMethod, objectValue);

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
        return new EvaluationResultImpl.Builder(context).setExpression(mc).build();
    }

    private MethodInfo concreteMethod(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        MethodInfo concreteMethod;

        /* abstract method... is there a concrete implementation? we should give preference to that one
           we don't allow switching when we're expanding an inline method ... this may lead to new variables
           being introduced, or context properties to change (Symbol for CNN, InlinedMethod_10 for new variables)

         */
        if (methodInfo.isAbstract() && forwardEvaluationInfo.allowSwitchingToConcreteMethod()) {
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


    private CausesOfDelay incrementStatementTime(MethodAnalysis methodAnalysis,
                                                 EvaluationResultImpl.Builder builder,
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


    // we raise an error IF a finalizer method is called on a parameter, or on a field inside a finalizer method
    private CausesOfDelay checkFinalizer(EvaluationResult context,
                                         EvaluationResultImpl.Builder builder,
                                         MethodAnalysis methodAnalysis,
                                         EvaluationResult objectResult) {
        if (methodAnalysis.getProperty(Property.FINALIZER).valueIsTrue()) {
            IsVariableExpression ive;
            if ((ive = objectResult.getExpression().asInstanceOf(IsVariableExpression.class)) != null) {
                if (raiseErrorForFinalizer(context, builder, ive.variable())) {
                    return CausesOfDelay.EMPTY;
                }
            }
            // check links of this expression
            LinkedVariables linked = objectResult.linkedVariablesOfExpression();
            if (linked.isDelayed()) {
                // we'll have to come back, we need to know the linked variables
                return linked.causesOfDelay();
            }
            linked.variables()
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().le(LV.LINK_DEPENDENT))
                    .map(Map.Entry::getKey)
                    .forEach(v -> raiseErrorForFinalizer(context, builder, v));
        }
        return CausesOfDelay.EMPTY;
    }

    private boolean raiseErrorForFinalizer(EvaluationResult context,
                                           EvaluationResultImpl.Builder builder, Variable variable) {
        if (variable.parameterizedType().bestTypeInfo().equals(methodInfo.typeInfo)) {
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
        } // else: e.g. InlinedMethod_AAPI_13, Finalizer_4; contrast with Finalizer_0
        return false;
    }

    public boolean hasEmptyModificationTimes() {
        return NO_MODIFICATION_TIMES.equals(modificationTimes);
    }

    public MethodCall copy(String modificationTimes) {
        return new MethodCall(identifier, objectIsImplicit, object, methodInfo, returnType(), parameterExpressions, modificationTimes);
    }

    public MethodCall withObject(Expression newObject) {
        return new MethodCall(identifier, false, newObject, methodInfo, returnType(), parameterExpressions, modificationTimes);
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
            EvaluationResultImpl.Builder builder,
            EvaluationResult context,
            MethodInfo methodInfo,
            Expression object,
            EvaluationResult objectResult,
            List<Expression> parameterValues,
            Expression original,
            DV modified) {
        if (modified.valueIsFalse()) return null;

        CausesOfDelay delayMarker = DelayFactory.createDelay(new SimpleCause(context.evaluationContext().getLocation(Stage.EVALUATION),
                CauseOfDelay.Cause.CONSTRUCTOR_TO_INSTANCE));
        Expression objectValue = objectResult.value();
        if (objectValue.isDelayed() || modified.isDelayed()) {
            return new ModReturn(null, delayMarker);
        }

        IsVariableExpression ive = object == null ? null : object.asInstanceOf(IsVariableExpression.class);
        IsVariableExpression iveValue = objectValue.asInstanceOf(IsVariableExpression.class);

        Expression newInstance = createNewInstance(context, methodInfo, objectValue, iveValue, identifier, original);
        boolean inLoop = iveValue instanceof VariableExpression ve && ve.getSuffix() instanceof VariableExpression.VariableInLoop;

        Expression newState = computeNewState(context, builder, methodInfo, objectResult, parameterValues);
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

            LinkedVariables linkedVariablesCombined = objectResult.linkedVariablesOfExpression();
            for (Map.Entry<Variable, LV> e : linkedVariablesCombined.variables().entrySet()) {
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
                        // FIXME this used to be linkedVariablesValue (only those of the value, not of the original one)
                        //   check that we still get correct results
                        LinkedVariables linkedVariablesValue = objectResult.linkedVariablesOfExpression();
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
                                              EvaluationResult objectResult,
                                              List<Expression> parameterValues) {
        Expression state;
        BooleanConstant TRUE = new BooleanConstant(context.getPrimitives(), true);
        Expression objectValue = objectResult.value();
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
            LinkedVariables linkedVariables = objectResult.linkedVariablesOfExpression();
            String objectModificationTimes = temporaryContext.modificationTimesOf(linkedVariables);
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

    private static DV notNullRequirementOnScope(MethodInfo concreteMethod, DV notNullRequirement) {
        if (concreteMethod.typeInfo.typeInspection.get().isFunctionalInterface()
            && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
    }

    private void checkCommonErrors(EvaluationResultImpl.Builder builder,
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
                                                   EvaluationResultImpl.Builder builder,
                                                   MethodAnalysis methodAnalysis,
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
                                                                                             cnnTravelsToFields && v instanceof FieldReference fr && fr.fieldInfo().owner.primaryType() == context.getCurrentType()));
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
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        return PackedIntMap.of(concreteReturnType.typesReferenced2(weight),
                object.typesReferenced2(weight),
                parameterExpressions.stream()
                        .flatMap(e -> e.typesReferenced2(weight).stream()).collect(PackedIntMap.collector())
        );
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
        boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        boolean cycle = breakCallCycleDelay || MethodLinkHelper.recursiveCall(methodInfo, context.evaluationContext());
        if (cycle) {
            if (Property.NOT_NULL_EXPRESSION == property) {
                return returnType().isPrimitiveExcludingVoid()
                        ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV;
            }
            if (Property.CONTEXT_MODIFIED == property) {
                return DV.FALSE_DV;
            }
            return property.bestDv;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        // return the formal value
        DV formal = methodAnalysis.getProperty(property);
        if (property.propertyType == Property.PropertyType.VALUE) {
            IsMyself isMyself = context.evaluationContext().isMyself(concreteReturnType);
            if (isMyself.toFalse(property)) return property.falseDv;

            if (Property.IMMUTABLE == property) {
                DynamicImmutableOfMethod dynamic = new DynamicImmutableOfMethod(context, methodInfo,
                        parameterExpressions, concreteReturnType);
                return dynamic.dynamicImmutable(formal, methodAnalysis);
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
                return independent;
            }

            // formal can be a @NotNull contracted annotation on the method; we cannot dismiss it
            // problem is that it may have to be computed, which introduces an unresolved delay in the case of cyclic calls.
            DV fromConcrete = context.getAnalyserContext().defaultValueProperty(property, concreteReturnType);
            return fromConcrete.max(formal);
        }
        return formal;
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

    public boolean objectIsThisOrSuper() {
        VariableExpression ve;
        if ((ve = object.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof This) return true;
        return !methodInfo.isStatic() && objectIsImplicit;
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

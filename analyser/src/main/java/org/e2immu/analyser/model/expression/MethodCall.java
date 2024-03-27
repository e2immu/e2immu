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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
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
import org.e2immu.analyser.util2.PackedIntMap;
import org.e2immu.graph.analyser.PackedInt;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.output.QualifiedName.Required.NO_METHOD;
import static org.e2immu.analyser.output.QualifiedName.Required.YES;


public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions, OneVariable {
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
        LinkedVariables linkedVariablesOfObject = objectResult.linkedVariablesOfExpression();
        assert linkedVariablesOfObject != null : "Problem in " + object.getClass();

        // precondition
        Precondition precondition = EvaluatePreconditionFromMethod.evaluate(context, builder, identifier, concreteMethod,
                objectValue, parameterValues);
        builder.addPrecondition(precondition);

        // ----- START LINKS -----

        // links, 1st: param -> object and param <-> param
        ParameterizedType objectType = methodInfo.isStatic() ? null : object.returnType();
        MethodLinkHelper methodLinkHelper = new MethodLinkHelper(context, methodInfo, methodAnalysis);
        MethodLinkHelper.FromParameters fp = methodLinkHelper.fromParametersIntoObject(objectType, concreteReturnType,
                parameterExpressions, res.evaluationResults(), true, true);
        LinkedVariables linkedVariablesOfObjectFromParams = fp.intoObject().linkedVariablesOfExpression();
        builder.compose(fp.intoObject());

        linkedVariablesOfObject.stream().forEach(e ->
                linkedVariablesOfObjectFromParams.stream().forEach(e2 ->
                        builder.link(e.getKey(), e2.getKey(), e.getValue().max(e2.getValue()))
                ));

        // links, 2nd: object -> result; this will be the result of the expression
        // copy the link result from the parameters into the lvs of the object. There can be a result when
        // a parameter is a functional interface returning a value
        LinkedVariables lvsResult1 = methodLinkHelper.linkedVariablesMethodCallObjectToReturnType(objectResult,
                res.evaluationResults(), concreteReturnType);
        LinkedVariables lvsResult = fp.intoResult() == null ? lvsResult1
                : lvsResult1.merge(fp.intoResult().linkedVariablesOfExpression());

        // ----- END LINKS -----

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

        CausesOfDelay linkDelays = fp.intoObject().causesOfDelay().merge(lvsResult.causesOfDelay());
        CausesOfDelay delays1 = modified.causesOfDelay().merge(parameterDelays).merge(delayedFinalizer)
                .merge(objectResult.causesOfDelay()).merge(incrementDelays).merge(linkDelays);


        Expression modifiedInstance;
        MethodCallCompanion.ModReturn modReturn = MethodCallCompanion.checkCompanionMethodsModifying(identifier,
                builder, context, concreteMethod, object, objectResult.value(),
                objectResult.linkedVariablesOfExpression(), parameterValues, this, modified);
        if (modReturn != null) {
            // mod delayed or true
            if (modReturn.expression() != null) {
                // delay in expression
                // for now the only test that uses this wrapped linked variables is Finalizer_0; but it is really pertinent.
                modifiedInstance = linkedVariablesOfObjectFromParams.isEmpty() ? modReturn.expression()
                        : PropertyWrapper.propertyWrapper(modReturn.expression(), linkedVariablesOfObjectFromParams);
            } else {
                // delay in separate causes
                modifiedInstance = null;
                assert modReturn.causes() != null && modReturn.causes().isDelayed();
                delays1 = delays1.merge(modReturn.causes());
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

    private static DV notNullRequirementOnScope(MethodInfo concreteMethod, DV notNullRequirement) {
        if (concreteMethod.typeInfo.typeInspection.get(concreteMethod.typeInfo.fullyQualifiedName).isFunctionalInterface()
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

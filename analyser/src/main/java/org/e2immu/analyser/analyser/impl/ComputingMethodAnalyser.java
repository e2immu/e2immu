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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.ExpandableAnalyserContextImpl;
import org.e2immu.analyser.analyser.statementanalyser.StatementAnalyserImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.DetectEventual;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.*;

public class ComputingMethodAnalyser extends MethodAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputingMethodAnalyser.class);

    public static final String STATEMENT_ANALYSER = "StatementAnalyser";
    public static final String OBTAIN_MOST_COMPLETE_PRECONDITION = "obtainMostCompletePrecondition";
    public static final String COMPUTE_MODIFIED = "computeModified";
    public static final String COMPUTE_MODIFIED_CYCLES = "computeModifiedCycles";
    public static final String COMPUTE_RETURN_VALUE = "computeReturnValue";
    public static final String COMPUTE_IMMUTABLE = "computeImmutable";
    public static final String COMPUTE_CONTAINER = "computeContainer";
    public static final String DETECT_MISSING_STATIC_MODIFIER = "detectMissingStaticModifier";
    public static final String EVENTUAL_PREP_WORK = "eventualPrepWork";
    public static final String ANNOTATE_EVENTUAL = "annotateEventual";
    public static final String COMPUTE_INDEPENDENT = "methodIsIndependent";

    private final TypeAnalysis typeAnalysis;
    public final StatementAnalyserImpl firstStatementAnalyser;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final Set<PrimaryTypeAnalyser> locallyCreatedPrimaryTypeAnalysers = new HashSet<>();

    private Map<FieldInfo, FieldAnalyser> myFieldAnalysers;

    /*
    Note that MethodLevelData is not part of the shared state, as the "lastStatement", where it resides,
    is only computed in the first step of the analyser components.
     */
    private record SharedState(EvaluationContext evaluationContext) {
    }


    public ComputingMethodAnalyser(MethodInfo methodInfo,
                                   TypeAnalysis typeAnalysis,
                                   MethodAnalysisImpl.Builder methodAnalysis,
                                   List<? extends ParameterAnalyser> parameterAnalysers,
                                   List<ParameterAnalysis> parameterAnalyses,
                                   Map<CompanionMethodName, CompanionAnalyser> companionAnalysers,
                                   boolean isSAM,
                                   AnalyserContext analyserContextInput) {
        super(methodInfo, methodAnalysis, parameterAnalysers,
                parameterAnalyses, companionAnalysers, isSAM, analyserContextInput);
        assert methodAnalysis.analysisMode == Analysis.AnalysisMode.COMPUTED;
        this.typeAnalysis = typeAnalysis;

        Block block = methodInspection.getMethodBody();
        if (block.isEmpty()) {
            firstStatementAnalyser = null;
        } else {
            boolean inSyncBlock = methodInfo.isSynchronized()
                    || methodInfo.isConstructor
                    || methodInfo.methodResolution.get().partOfConstruction() == MethodResolution.CallStatus.PART_OF_CONSTRUCTION
                    || methodInfo.methodInspection.get().isStaticBlock();
            firstStatementAnalyser = StatementAnalyserImpl.recursivelyCreateAnalysisObjects(analyserContext,
                    this, null, block.structure.statements(), "", true, inSyncBlock);
            methodAnalysis.setFirstStatement(firstStatementAnalyser.statementAnalysis);
        }

        AnalyserProgram analyserProgram = analyserContextInput.getAnalyserProgram();
        AnalyserComponents.Builder<String, SharedState> builder = new AnalyserComponents.Builder<>(analyserProgram);
        assert firstStatementAnalyser != null;

        // order: Companion analyser, Parameter analysers, Statement analysers, Method analyser parts
        // rest of the order (as determined in PrimaryTypeAnalyser): fields, types

        for (CompanionAnalyser companionAnalyser : companionAnalysers.values()) {
            builder.add(companionAnalyser.companionMethodName.toString(), AnalyserProgram.Step.INITIALISE,
                    (sharedState -> companionAnalyser.analyse(sharedState.evaluationContext.getIteration())));
        }

        for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
            AnalysisStatus.AnalysisResultSupplier<SharedState> parameterAnalyserAction = (sharedState) -> {
                AnalyserResult result = parameterAnalyser.analyse(sharedState.evaluationContext.getIteration());
                analyserResultBuilder.add(result);
                return result.analysisStatus();
            };

            builder.add("Parameter " + parameterAnalyser.getParameterInfo().name, AnalyserProgram.Step.INITIALISE,
                    parameterAnalyserAction);
        }

        AnalysisStatus.AnalysisResultSupplier<SharedState> statementAnalyser = (sharedState) -> {
            AnalyserResult result = firstStatementAnalyser
                    .analyseAllStatementsInBlock(sharedState.evaluationContext.getIteration(),
                            ForwardAnalysisInfo.startOfMethod(analyserContext.getPrimitives()),
                            sharedState.evaluationContext.getClosure());
            analyserResultBuilder.add(result, false, false);
            this.locallyCreatedPrimaryTypeAnalysers.addAll(result.localAnalysers());
            return result.analysisStatus();
        };

        builder.add(STATEMENT_ANALYSER, AnalyserProgram.Step.INITIALISE, statementAnalyser)
                .add(COMPUTE_MODIFIED, AnalyserProgram.Step.MODIFIED, (sharedState) -> computeModified())
                .add(COMPUTE_MODIFIED_CYCLES, AnalyserProgram.Step.MODIFIED,
                        (sharedState -> methodInfo.isConstructor ? DONE : computeModifiedInternalCycles()))
                .add(OBTAIN_MOST_COMPLETE_PRECONDITION, (sharedState) -> obtainMostCompletePrecondition())
                .add(COMPUTE_RETURN_VALUE, (sharedState) -> methodInfo.noReturnValue() ? DONE : computeReturnValue())
                .add(COMPUTE_IMMUTABLE, sharedState -> methodInfo.noReturnValue() ? DONE : computeImmutable())
                .add(COMPUTE_CONTAINER, sharedState -> methodInfo.noReturnValue() ? DONE : computeContainer())
                .add(DETECT_MISSING_STATIC_MODIFIER, (iteration) -> methodInfo.isConstructor ? DONE : detectMissingStaticModifier())
                .add(EVENTUAL_PREP_WORK, (sharedState) -> methodInfo.isConstructor ? DONE : eventualPrepWork(sharedState))
                .add(ANNOTATE_EVENTUAL, (sharedState) -> methodInfo.isConstructor ? DONE : annotateEventual(sharedState))
                .add(COMPUTE_INDEPENDENT, this::computeIndependent);

        analyserComponents = builder.build();
    }

    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        ExpandableAnalyserContextImpl expandable = (ExpandableAnalyserContextImpl) analyserContext;
        typeAnalysers.forEach(expandable::addPrimaryTypeAnalyser);
    }

    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return locallyCreatedPrimaryTypeAnalysers.stream();
    }

    @Override
    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    @Override
    public void initialize() {
        super.initialize();

        Map<FieldInfo, FieldAnalyser> myFieldAnalysers = new HashMap<>();
        analyserContext.fieldAnalyserStream().forEach(analyser -> {
            if (analyser.getFieldInfo().owner == methodInfo.typeInfo) {
                myFieldAnalysers.put(analyser.getFieldInfo(), analyser);
            }
        });
        this.myFieldAnalysers = Map.copyOf(myFieldAnalysers);
    }

    // called from primary type analyser
    @Override
    public AnalyserResult analyse(int iteration, EvaluationContext closure) {
        LOGGER.debug("Analysing method {}", methodInfo.fullyQualifiedName());
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), closure);
        SharedState sharedState = new SharedState(evaluationContext);

        try {
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);
            analyserResultBuilder.setAnalysisStatus(analysisStatus);

            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                    .debugConfiguration().afterMethodAnalyserVisitors();
            if (!visitors.isEmpty()) {
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                            evaluationContext, methodInfo, methodAnalysis,
                            parameterAnalyses, analyserComponents.getStatusesAsMap(),
                            analyserResultBuilder::getMessageStream));
                }
            }
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    @Override
    public void makeImmutable() {
        if (firstStatementAnalyser != null) {
            firstStatementAnalyser.makeImmutable();
        }
    }

    private AnalysisStatus detectMissingStaticModifier() {
        if (!methodInfo.methodInspection.get().isStatic() && !methodInfo.typeInfo.isInterface() && methodInfo.isNotATestMethod()) {
            // we need to check if there's fields being read/assigned/
            if (absentUnlessStatic(VariableInfo::isRead) &&
                    absentUnlessStatic(VariableInfo::isAssigned) &&
                    !getThisAsVariable().isRead() &&
                    methodInfo.isNotOverridingAnyOtherMethod() &&
                    !methodInfo.methodInspection.get().isDefault()) {
                MethodResolution methodResolution = methodInfo.methodResolution.get();
                if (methodResolution.staticMethodCallsOnly()) {
                    analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(),
                            Message.Label.METHOD_SHOULD_BE_MARKED_STATIC));
                    return DONE;
                }
            }
        }
        return DONE;
    }

    private boolean absentUnlessStatic(Predicate<VariableInfo> variableProperty) {
        return methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fieldReference && fieldReference.scopeIsThis())
                .allMatch(vi -> !variableProperty.test(vi) || vi.variable().isStatic());
    }

    // simply copy from last statement, but only when set/delays over
    private AnalysisStatus obtainMostCompletePrecondition() {
        assert methodAnalysis.precondition.isVariable();

        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();
        if (methodLevelData.combinedPrecondition.isVariable()) {
            methodAnalysis.precondition.setVariable(methodLevelData.combinedPrecondition.get());
            return methodLevelData.combinedPrecondition.get().expression().causesOfDelay();
        }
        methodAnalysis.precondition.setFinal(methodLevelData.combinedPrecondition.get());
        return DONE;
    }

    private AnalysisStatus annotateEventual(SharedState sharedState) {
        assert !methodAnalysis.eventualIsSet();

        DetectEventual detectEventual = new DetectEventual(methodInfo, methodAnalysis, typeAnalysis, analyserContext);
        MethodAnalysis.Eventual eventual = detectEventual.detect(sharedState.evaluationContext);
        if (eventual.causesOfDelay().isDelayed()) {
            methodAnalysis.setEventualDelay(eventual);
            return eventual.causesOfDelay();
        }
        methodAnalysis.setEventual(eventual);
        if (eventual == MethodAnalysis.NOT_EVENTUAL) {
            return DONE;
        }

        LOGGER.debug("Marking {} with only data {}", methodInfo.distinguishingName(), eventual);
        AnnotationExpression annotation = detectEventual.makeAnnotation(eventual);
        methodAnalysis.annotations.put(annotation, true);
        return DONE;
    }

    private AnalysisStatus eventualPrepWork(SharedState sharedState) {
        assert !methodAnalysis.preconditionForEventual.isSet();

        TypeInfo typeInfo = methodInfo.typeInfo;
        List<FieldAnalysis> fieldAnalysesOfTypeInfo = myFieldAnalysers.values().stream()
                .map(FieldAnalyser::getFieldAnalysis).toList();

        while (true) {
            // FIRST CRITERION: is there a non-@Final field?

            DV finalOverFields = fieldAnalysesOfTypeInfo.stream()
                    .map(fa -> fa.getProperty(Property.FINAL))
                    .reduce(Property.FINAL.bestDv, DV::min);
            if (finalOverFields.isDelayed()) {
                LOGGER.debug("Delaying eventual in {} until we know about @Final of fields",
                        methodInfo.fullyQualifiedName);
                return finalOverFields.causesOfDelay();
            }
            if (finalOverFields.valueIsFalse()) {
                // OK!
                break;
            }

            // SECOND CRITERION: is there an eventually immutable field? We can ignore delays if the concrete type of the field
            // is the same as the owner

            DV haveEventuallyImmutableFields = fieldAnalysesOfTypeInfo
                    .stream()
                    .map(fa -> {
                        ParameterizedType concreteType = fa.concreteTypeNullWhenDelayed();
                        boolean acceptDelay = concreteType == null || concreteType.typeInfo != null &&
                                concreteType.typeInfo.topOfInterdependentClassHierarchy() !=
                                        fa.getFieldInfo().owner.topOfInterdependentClassHierarchy();
                        DV immutable = fa.getProperty(Property.EXTERNAL_IMMUTABLE);
                        return acceptDelay && immutable.isDelayed() ? immutable :
                                DV.fromBoolDv(MultiLevel.isEventuallyE1Immutable(immutable)
                                        || MultiLevel.isEventuallyE2Immutable(immutable));
                    })
                    .reduce(DV.TRUE_DV, DV::min);
            if (haveEventuallyImmutableFields.isDelayed()) {
                LOGGER.debug("Delaying eventual in {} until we know about @Immutable of fields", methodInfo.fullyQualifiedName);

                return haveEventuallyImmutableFields.causesOfDelay();
            }
            if (haveEventuallyImmutableFields.valueIsTrue()) {
                break;
            }

            // THIRD CRITERION: is there a field whose content can change? Non-primitive, not transparent, not E2

            DV haveContentChangeableField = fieldAnalysesOfTypeInfo.stream()
                    .map(fa -> {
                        DV transparent = fa.isTransparentType();
                        if (transparent.isDelayed()) return transparent;
                        DV immutable = fa.getProperty(Property.EXTERNAL_IMMUTABLE);

                        boolean contentChangeable = !MultiLevel.isAtLeastEventuallyE2Immutable(immutable)
                                && !fa.getFieldInfo().type.isPrimitiveExcludingVoid()
                                && !fa.isTransparentType().valueIsTrue();
                        return DV.fromBoolDv(contentChangeable);
                    })
                    .reduce(DV.FALSE_DV, DV::max);
            if (haveContentChangeableField.isDelayed()) {
                LOGGER.debug("Delaying eventual in {} until we know about transparent types of fields",
                        methodInfo.fullyQualifiedName);
                return haveContentChangeableField.causesOfDelay();
            }
            if (haveContentChangeableField.valueIsTrue()) {
                break;
            }

            // GO UP TO PARENT

            ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass();
            if (parentClass.isJavaLangObject()) {
                LOGGER.debug("No eventual annotation in {}: found no non-final fields", methodInfo.distinguishingName());
                methodAnalysis.preconditionForEventual.set(Optional.empty());
                return DONE;
            }
            typeInfo = parentClass.bestTypeInfo();
            TypeInspection typeInspection = analyserContext.getTypeInspection(typeInfo);
            fieldAnalysesOfTypeInfo = typeInspection.fields().stream().map(analyserContext::getFieldAnalysis).toList();
        }

        if (methodAnalysis.precondition.isVariable()) {
            LOGGER.debug("Delaying compute @Only and @Mark, precondition not set (weird, should be set by now)");
            return methodAnalysis.precondition.get().expression().causesOfDelay();
        }
        Precondition precondition = methodAnalysis.precondition.get();
        if (precondition.isEmpty()) {

            // code to detect the situation as in Lazy
            Precondition combinedPrecondition = null;
            for (FieldAnalyser fieldAnalyser : myFieldAnalysers.values()) {
                if (fieldAnalyser.getFieldAnalysis().getProperty(Property.FINAL).valueIsFalse()) {
                    FieldReference fr = new FieldReference(analyserContext, fieldAnalyser.getFieldInfo());
                    StatementAnalysis beforeAssignment = statementBeforeAssignment(fr);
                    if (beforeAssignment != null) {
                        ConditionManager cm = beforeAssignment.stateData().getConditionManagerForNextStatement();
                        if (cm.state().isDelayed()) {
                            LOGGER.debug("Delaying compute @Only, @Mark, delay in state {} {}", beforeAssignment.index(),
                                    methodInfo.fullyQualifiedName);
                            return cm.state().causesOfDelay();
                        }
                        Expression state = cm.state();
                        if (!state.isBoolValueTrue()) {
                            Filter filter = new Filter(sharedState.evaluationContext, Filter.FilterMode.ACCEPT);
                            Filter.FilterResult<FieldReference> filterResult = filter.filter(state,
                                    filter.individualFieldClause(analyserContext, true));
                            Expression inResult = filterResult.accepted().get(fr);
                            if (inResult != null) {
                                Precondition pc = new Precondition(inResult, List.of(new Precondition.StateCause()));
                                if (combinedPrecondition == null) {
                                    combinedPrecondition = pc;
                                } else {
                                    combinedPrecondition = combinedPrecondition.combine(sharedState.evaluationContext, pc);
                                }
                            }
                        }
                    }
                }
            }
            LOGGER.debug("No @Mark @Only annotation in {} from precondition, found {} from assignment",
                    methodInfo.distinguishingName(), combinedPrecondition);
            methodAnalysis.preconditionForEventual.set(Optional.ofNullable(combinedPrecondition));
            return DONE;
        }

        /*
        FilterMode.ALL will find clauses in Or and in And constructs. See SetOnce.copy for an example of an Or
        construct.
         */
        Filter filter = new Filter(sharedState.evaluationContext, Filter.FilterMode.ALL);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(precondition.expression(),
                filter.individualFieldClause(analyserContext));
        if (filterResult.accepted().isEmpty()) {
            LOGGER.debug("No @Mark/@Only annotation in {}: found no individual field preconditions",
                    methodInfo.distinguishingName());
            methodAnalysis.preconditionForEventual.set(Optional.empty());
            return DONE;
        }
        Expression[] preconditionExpressions = filterResult.accepted().values().toArray(Expression[]::new);
        LOGGER.debug("Did prep work for @Only, @Mark, found precondition on variables {} in {}", precondition,
                filterResult.accepted().keySet(), methodInfo.distinguishingName());

        Expression and = And.and(sharedState.evaluationContext, preconditionExpressions);
        methodAnalysis.preconditionForEventual.set(Optional.of(new Precondition(and, precondition.causes())));
        return DONE;
    }

    private static final Pattern INDEX_PATTERN = Pattern.compile("(\\d+)(-C|-E|:M)");

    private StatementAnalysis statementBeforeAssignment(FieldReference fieldReference) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        VariableInfo vi = lastStatement.findOrNull(fieldReference, VariableInfoContainer.Level.MERGE);
        if (vi != null && vi.isAssigned()) {
            Matcher m = INDEX_PATTERN.matcher(vi.getAssignmentIds().getLatestAssignment());
            if (m.matches()) {
                int index = Integer.parseInt(m.group(1)) - 1;
                if (index >= 0) {
                    String id = "" + index; // TODO numeric padding to same length
                    return findStatementAnalyser(id).getStatementAnalysis();
                }
            }
        }
        return null;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private AnalysisStatus computeReturnValue() {
        assert !methodAnalysis.singleReturnValue.isFinal();

        // some immediate short-cuts.
        // if we cannot cast 'this' to the current type or the other way round, the method cannot be fluent
        // see Fluent_0 for one way, and Store_7 for the other direction
        ParameterizedType myType = methodInfo.typeInfo.asParameterizedType(analyserContext);
        if (myType.isNotAssignableFromTo(analyserContext, methodInspection.getReturnType())) {
            methodAnalysis.setProperty(Property.FLUENT, DV.FALSE_DV);
        }

        /*
        down-or-upcast allowed
         */
        if (methodInspection.getParameters().isEmpty() ||
                methodInspection.getReturnType().isNotAssignableFromTo(analyserContext,
                        methodInspection.getParameters().get(0).parameterizedType)) {
            methodAnalysis.setProperty(Property.IDENTITY, DV.FALSE_DV);
        }

        VariableInfo variableInfo = getReturnAsVariable();
        Expression value = variableInfo.getValue();
        if (variableInfo.isDelayed() || value.isInitialReturnExpression()) {

            // it is possible that none of the return statements are reachable... in which case there should be no delay,
            // and no SRV
            if (noReturnStatementReachable()) {
                UnknownExpression ue = UnknownExpression.forNoReturnValue(methodInfo.identifier, methodInfo.returnType());
                methodAnalysis.singleReturnValue.setFinal(ue);
                methodAnalysis.setProperty(Property.IDENTITY, DV.FALSE_DV);
                methodAnalysis.setProperty(Property.FLUENT, DV.FALSE_DV);
                methodAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                methodAnalysis.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV);
                methodAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT_DV);
                methodAnalysis.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
                return DONE;
            }
            LOGGER.debug("Method {} has return value {}, delaying", methodInfo.distinguishingName(),
                    value.debugOutput());
            if (variableInfo.isDelayed()) {
                methodAnalysis.singleReturnValue.setVariable(delayedSrv(variableInfo.getValue().causesOfDelay()));
                return variableInfo.getValue().causesOfDelay();
            }
            throw new UnsupportedOperationException("? no delays, and initial return expression even though return statements are reachable");
        }

        // try to compute the dynamic immutable status of value

        Expression valueBeforeInlining = value;
        if (!value.isConstant()) {
            DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD);
            if (modified.isDelayed()) {
                LOGGER.debug("Delaying return value of {}, waiting for MODIFIED (we may try to inline!)", methodInfo.distinguishingName);
                methodAnalysis.singleReturnValue.setVariable(delayedSrv(modified.causesOfDelay()));
                return modified.causesOfDelay();
            }
            if (modified.valueIsFalse()) {
                /*
                 As a general rule, we make non-modifying methods inline-able, even if they contain references to variable
                 fields and local loop variables. It'll depend on where they are expanded
                 whether the result is something sensible or not.
                 */
                if (value.isDelayed()) {
                    methodAnalysis.singleReturnValue.setVariable(delayedSrv(value.causesOfDelay()));
                    return value.causesOfDelay();
                }
                value = createInlinedMethod(value);
                if (value.isDelayed()) {
                    methodAnalysis.singleReturnValue.setVariable(delayedSrv(value.causesOfDelay()));
                    return value.causesOfDelay(); // FINAL
                }
            }
        }

        DV notNullExpression = variableInfo.getProperty(NOT_NULL_EXPRESSION);
        assert notNullExpression.isDone();

        /* we already have a value for the value property NNE. We wait, however, until we have a value for ENN as well
        if we take the non-constructing methods along for NNE computation.
         */
        DV externalNotNull;
        if (analyserContext.getConfiguration().analyserConfiguration().computeContextPropertiesOverAllMethods() ||
                methodInfo.methodResolution.get().partOfConstruction() == MethodResolution.CallStatus.PART_OF_CONSTRUCTION) {
            externalNotNull = variableInfo.getProperty(EXTERNAL_NOT_NULL);
            if (externalNotNull.isDelayed()) {
                LOGGER.debug("Delaying return value of {}, waiting for NOT_NULL", methodInfo.fullyQualifiedName);
                methodAnalysis.singleReturnValue.setVariable(delayedSrv(externalNotNull.causesOfDelay()));
                return externalNotNull.causesOfDelay();
            }
        } else {
            externalNotNull = MultiLevel.NOT_INVOLVED_DV;
        }
        DV notNull = notNullExpression.max(externalNotNull);
        methodAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, notNull);

        boolean valueIsConstantField;
        VariableExpression ve;
        if (value instanceof InlinedMethod inlined
                && (ve = inlined.expression().asInstanceOf(VariableExpression.class)) != null
                && ve.variable() instanceof FieldReference fieldReference) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            DV constantField = fieldAnalysis.getProperty(Property.CONSTANT);
            if (constantField.isDelayed()) {
                LOGGER.debug("Delaying return value of {}, waiting for effectively final value's @Constant designation",
                        methodInfo.distinguishingName);
                methodAnalysis.singleReturnValue.setVariable(delayedSrv(constantField.causesOfDelay()));
                return constantField.causesOfDelay();
            }
            valueIsConstantField = constantField.valueIsTrue();
        } else valueIsConstantField = false;

        boolean isConstant = value.isConstant() || valueIsConstantField;

        methodAnalysis.singleReturnValue.setFinal(value);
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (isConstant) {
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, value);
            methodAnalysis.annotations.put(constantAnnotation, true);
        } else {
            methodAnalysis.annotations.put(e2.constant, false);
        }
        methodAnalysis.setProperty(Property.CONSTANT, DV.fromBoolDv(isConstant));
        LOGGER.debug("Mark method {} as @Constant? {}", methodInfo.fullyQualifiedName(), isConstant);

        VariableExpression vv;
        boolean isFluent = (vv = valueBeforeInlining.asInstanceOf(VariableExpression.class)) != null &&
                vv.variable() instanceof This thisVar &&
                thisVar.typeInfo == methodInfo.typeInfo;
        methodAnalysis.setProperty(Property.FLUENT, DV.fromBoolDv(isFluent));
        LOGGER.debug("Mark method {} as @Fluent? {}", methodInfo.fullyQualifiedName(), isFluent);

        DV currentIdentity = methodAnalysis.getProperty(IDENTITY);
        if (currentIdentity.isDelayed()) {
            VariableExpression ve2;
            boolean isIdentity = (ve2 = valueBeforeInlining.asInstanceOf(VariableExpression.class)) != null &&
                    ve2.variable() instanceof ParameterInfo pi && pi.getMethod() == methodInfo && pi.index == 0;
            methodAnalysis.setProperty(IDENTITY, DV.fromBoolDv(isIdentity));
        }
        return DONE;
    }

    private AnalysisStatus computeImmutable() {
        if (methodAnalysis.getPropertyFromMapDelayWhenAbsent(IMMUTABLE).isDone()) return DONE;
        DV immutable = computeImmutableValue();
        if (immutable.isDelayed()) return immutable.causesOfDelay();
        methodAnalysis.setProperty(IMMUTABLE, immutable);
        LOGGER.debug("Set @Immutable to {} on {}", immutable, methodInfo.fullyQualifiedName);
        return DONE;
    }

    private AnalysisStatus computeContainer() {
        if (methodAnalysis.getPropertyFromMapDelayWhenAbsent(CONTAINER).isDone()) return DONE;
        DV container = computeContainerValue();
        if (container.isDelayed()) return container.causesOfDelay();
        methodAnalysis.setProperty(CONTAINER, container);
        LOGGER.debug("Set @Container to {} on {}", container, methodInfo.fullyQualifiedName);
        return DONE;
    }

    private DV computeContainerValue() {
        if (!methodAnalysis.singleReturnValue.isFinal()) {
            LOGGER.debug("Delaying @Container on {} until return value is set", methodInfo.fullyQualifiedName);
            return methodInfo.delay(CauseOfDelay.Cause.VALUE).merge(methodAnalysis.singleReturnValue.get().causesOfDelay());
        }
        Expression expression = methodAnalysis.singleReturnValue.get();
        if (expression.isConstant()) {
            return MultiLevel.CONTAINER_DV;
        }
        VariableInfo variableInfo = getReturnAsVariable();

        DV dynamic = variableInfo.getProperty(CONTAINER);
        DV dynamicExt = variableInfo.getProperty(EXTERNAL_CONTAINER);
        return dynamic.max(dynamicExt);
    }

    private DV computeImmutableValue() {
        DV formalImmutable = analyserContext.defaultImmutable(methodInfo.returnType(), true);
        if (formalImmutable.equals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)) {
            return formalImmutable;
        }

        if (!methodAnalysis.singleReturnValue.isFinal()) {
            LOGGER.debug("Delaying @Immutable on {} until return value is set", methodInfo.fullyQualifiedName);
            return methodInfo.delay(CauseOfDelay.Cause.VALUE).merge(methodAnalysis.singleReturnValue.get().causesOfDelay());
        }
        Expression expression = methodAnalysis.singleReturnValue.get();
        if (expression.isConstant()) {
            return MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
        }
        VariableInfo variableInfo = getReturnAsVariable();

        DV dynamic = variableInfo.getProperty(IMMUTABLE);
        DV dynamicExt = variableInfo.getProperty(EXTERNAL_IMMUTABLE);
        return formalImmutable.max(dynamic).max(dynamicExt);
    }

    /*
    Create an inlined method based on the returned value
     */
    private Expression createInlinedMethod(Expression value) {
        assert value.isDone();
        return InlinedMethod.of(Identifier.generate(), methodInfo, value, analyserContext);
    }


    private boolean noReturnStatementReachable() {
        StatementAnalysis firstStatement = methodAnalysis.getFirstStatement();
        return !recursivelyFindReachableReturnStatement(firstStatement);
    }

    private static boolean recursivelyFindReachableReturnStatement(StatementAnalysis statementAnalysis) {
        StatementAnalysis sa = statementAnalysis;
        while (!sa.flowData().isUnreachable()) {
            if (sa.statement() instanceof ReturnStatement) return true;
            for (Optional<StatementAnalysis> first : sa.navigationData().blocks.get()) {
                if (first.isPresent() && recursivelyFindReachableReturnStatement(first.get())) {
                    return true;
                }
            }
            if (sa.navigationData().next.get().isEmpty()) break;
            sa = sa.navigationData().next.get().get();
        }
        return false;
    }

    /*
    Compute modified "external" cycles not really needed, since the analyser handles each primary type separately.
    So they will be executed in a particular order anyway.
    This method deals with cyclic calls within the primary type, which the analyser can handle together.

    The MethodCall expression has separate logic to not change the CNN of the object for cyclic and recursive calls.
    Similar code exists in EvaluateParameters.
     */
    private AnalysisStatus computeModifiedInternalCycles() {
        boolean isCycle = methodInfo.partOfCallCycle();
        if (!isCycle) return DONE;

        DV modified = methodAnalysis.getProperty(Property.TEMP_MODIFIED_METHOD);
        TypeAnalysisImpl.Builder builder = (TypeAnalysisImpl.Builder) typeAnalysis;
        Set<MethodInfo> cycle = methodInfo.methodResolution.get().methodsOfOwnClassReached();
        TypeAnalysisImpl.CycleInfo cycleInfo = builder.nonModifiedCountForMethodCallCycle.getOrCreate(cycle, x -> new TypeAnalysisImpl.CycleInfo());

        // we decide for the group
        if (modified.valueIsTrue()) {
            if (!cycleInfo.modified.isSet()) cycleInfo.modified.set();
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.TRUE_DV);
            return DONE;
        }

        // others have decided for us
        if (cycleInfo.modified.isSet()) {
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.TRUE_DV);
            return DONE;
        }

        if (modified.valueIsFalse()) {
            if (!cycleInfo.nonModified.contains(methodInfo)) cycleInfo.nonModified.add(methodInfo);

            if (cycleInfo.nonModified.size() == cycle.size()) {
                methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
                return DONE;
            }
        }
        // we all agree
        if (cycleInfo.nonModified.size() == cycle.size()) {
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
            return DONE;
        }
        // wait
        if (modified.valueIsFalse()) {
            LOGGER.debug("Delaying @Modified of method {}, cycle", methodInfo.fullyQualifiedName);
            return methodInfo.delay(CauseOfDelay.Cause.MODIFIED_CYCLE);
        }
        LOGGER.debug("Delaying @Modified of method {}, modified", methodInfo.fullyQualifiedName);
        return modified.causesOfDelay();
    }

    private AnalysisStatus computeModified() {
        boolean isCycle = methodInfo.partOfCallCycle();
        Property property = isCycle ? Property.TEMP_MODIFIED_METHOD : Property.MODIFIED_METHOD;

        if (methodAnalysis.getProperty(property).isDone()) return DONE;
        if (methodInfo.isConstructor) {
            methodAnalysis.setProperty(MODIFIED_METHOD, DV.TRUE_DV);
            return DONE;
        }
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        // first step, check (my) field assignments
        boolean fieldAssignments = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fr &&
                        fieldInMyTypeHierarchy(fr.fieldInfo, methodInfo.typeInfo))
                .anyMatch(VariableInfo::isAssigned);
        if (fieldAssignments) {
            LOGGER.debug("Method {} is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(property, DV.TRUE_DV);
            return DONE;
        }

        // if there are no field assignments, there may be modifying method calls

        // second step, check that CM is present (this generally implies that links have been established)
        DV contextModified = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference fr &&
                        fieldInMyTypeHierarchy(fr.fieldInfo, methodInfo.typeInfo))
                .map(vi -> vi.getProperty(CONTEXT_MODIFIED))
                .reduce(DV.FALSE_DV, DV::max);
        if (contextModified.isDelayed()) {
            LOGGER.debug("Method {}: Not deciding on @Modified yet: no context modified",
                    methodInfo.distinguishingName());
            methodAnalysis.setProperty(property, contextModified);
            return contextModified.causesOfDelay();
        }

        if (contextModified.valueIsFalse()) { // also in static cases, sometimes a modification is written to "this" (MethodCall)
            VariableInfo thisVariable = getThisAsVariable();
            if (thisVariable != null) {
                DV thisModified = thisVariable.getProperty(Property.CONTEXT_MODIFIED);
                if (thisModified.isDelayed()) {
                    LOGGER.debug("In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                            methodInfo.distinguishingName());
                    return thisModified.causesOfDelay();
                }
                contextModified = thisModified;
            }
        } // else: already true, so no need to look at this

        if (contextModified.valueIsFalse()) {
            // if there are no modifying method calls, we still may have a modifying method
            // this will be due to calling undeclared SAMs, or calling non-modifying methods in a circular type situation
            // (A.nonModifying() calls B.modifying() on a parameter (NOT a field, so nonModifying is just that) which itself calls A.modifying()
            // NOTE that in this situation we cannot have a container, as we require a modifying! (TODO check this statement is correct)
            boolean circular = methodLevelData.getCallsPotentiallyCircularMethod();
            if (circular) {
                DV haveModifying = findOtherModifyingElements();
                if (haveModifying.isDelayed()) return haveModifying.causesOfDelay();
                contextModified = haveModifying;
            }
        }
        if (contextModified.valueIsFalse()) {
            DV maxModified = methodLevelData.copyModificationStatusFrom.stream()
                    .map(mi -> mi.getKey().methodAnalysis.get().getProperty(Property.MODIFIED_METHOD))
                    .reduce(DV.MIN_INT_DV, DV::max);
            if (maxModified != DV.MIN_INT_DV) {
                if (maxModified.isDelayed()) {
                    LOGGER.debug("Delaying modification on method {}, waiting to copy", methodInfo.distinguishingName());
                    return maxModified.causesOfDelay();
                }
                contextModified = maxModified;
            }
        }
        LOGGER.debug("Mark method {} as {}", methodInfo.distinguishingName(),
                contextModified.valueIsTrue() ? "@Modified" : "@NotModified");
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(property, contextModified);
        return DONE;
    }

    public boolean fieldInMyTypeHierarchy(FieldInfo fieldInfo, TypeInfo typeInfo) {
        if (typeInfo == fieldInfo.owner) return true;
        TypeInspection inspection = analyserContext.getTypeInspection(typeInfo);
        ParameterizedType parentClass = inspection.parentClass();
        if (!parentClass.isJavaLangObject()) {
            return fieldInMyTypeHierarchy(fieldInfo, parentClass.typeInfo);
        }
        return typeInfo.primaryType() == fieldInfo.owner.primaryType();
    }

    private DV findOtherModifyingElements() {
        boolean nonPrivateFields = myFieldAnalysers.values().stream()
                .filter(fa -> fa.getFieldInfo().type.isFunctionalInterface() && fa.getFieldAnalysis().isDeclaredFunctionalInterface())
                .anyMatch(fa -> !fa.getFieldInfo().isPrivate());
        if (nonPrivateFields) {
            return DV.TRUE_DV;
        }
        // We also check independence (maybe the user calls a method which returns one of the fields,
        // and calls a modification directly)

        return methodInfo.typeInfo.typeInspection.get()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .map(this::modifiedOrNotTransparentAndDependent)
                .reduce(DV.FALSE_DV, DV::max);
    }

    private DV modifiedOrNotTransparentAndDependent(MethodInfo methodInfo) {
        MethodAnalysis ma = analyserContext.getMethodAnalysis(methodInfo);
        DV mm = ma.getProperty(Property.MODIFIED_METHOD);
        if (!mm.valueIsFalse()) return mm;
        DV transparent = analyserContext.isTransparentOrAtLeastEventuallyE2Immutable(methodInfo.returnType(), methodInfo.typeInfo);
        if (transparent.isDelayed()) return transparent;
        DV independent = ma.getProperty(INDEPENDENT);
        if (independent.isDelayed()) return independent;
        return DV.fromBoolDv(transparent.valueIsFalse() && independent.equals(MultiLevel.DEPENDENT_DV));
    }

    private AnalysisStatus computeIndependent(SharedState sharedState) {
        if (methodAnalysis.getProperty(INDEPENDENT).isDone()) {
            return DONE;
        }

        if (methodInfo.noReturnValue()) {
            methodAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            return DONE;
        }
        // here, we compute the independence of the return value
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        if (lastStatement == null) {
            // the method always throws an exception
            methodAnalysis.setProperty(INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            return DONE;
        }

        DV immutable = methodAnalysis.getPropertyFromMapDelayWhenAbsent(IMMUTABLE);
        VariableInfo variableInfo = getReturnAsVariable();
        ParameterizedType type = methodInspection.getReturnType();
        DV independent = computeIndependent(variableInfo, immutable, type, sharedState.evaluationContext().getCurrentType(),
                analyserContext);
        if (independent.isDelayed()) return independent.causesOfDelay();
        methodAnalysis.setProperty(INDEPENDENT, independent);
        return DONE;
    }

    static DV computeIndependent(VariableInfo variableInfo,
                                 DV immutable,
                                 ParameterizedType type,
                                 TypeInfo currentType,
                                 AnalysisProvider analysisProvider) {
        if (immutable.equals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)) {
            return MultiLevel.INDEPENDENT_DV;
        }
        LinkedVariables linkedVariables = variableInfo.getLinkedVariables();
        if (linkedVariables.isDelayed()) return linkedVariables.causesOfDelay();
        DV minFields = linkedVariables.variables().entrySet().stream()
                .filter(e -> e.getKey() instanceof FieldReference fr && fr.scopeIsThis() || e.getKey() instanceof This)
                .map(Map.Entry::getValue)
                .reduce(DV.MAX_INT_DV, DV::min);

        if (minFields.isDelayed()) return minFields;
        if (minFields == DV.MAX_INT_DV) return MultiLevel.INDEPENDENT_DV;

        DV typeHidden = analysisProvider.getTypeAnalysis(currentType).isPartOfHiddenContent(type);
        if (typeHidden.isDelayed()) return typeHidden;
        if (typeHidden.valueIsFalse() && minFields.le(MultiLevel.DEPENDENT_DV)) {
            return MultiLevel.DEPENDENT_DV;
        }
        // on the sliding scale now
        //combination of statically immutable (type) and dynamically immutable (value property)
        if (immutable.isDelayed()) return immutable;
        int immutableLevel = MultiLevel.level(immutable);
        if (immutableLevel < MultiLevel.Level.IMMUTABLE_2.level) return MultiLevel.INDEPENDENT_1_DV;
        return MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
    }

    @Override
    public Stream<Message> getMessageStream() {
        Stream<Message> localAnalyserStream = locallyCreatedPrimaryTypeAnalysers.stream()
                .flatMap(Analyser::getMessageStream);
        return Stream.concat(super.getMessageStream(), Stream.concat(localAnalyserStream,
                getParameterAnalysers().stream().flatMap(ParameterAnalyser::getMessageStream)));
    }

    public MethodLevelData methodLevelData() {
        return methodAnalysis.methodLevelData();
    }

    @Override
    public void logAnalysisStatuses() {
        AnalysisStatus statusOfStatementAnalyser = analyserComponents.getStatus(ComputingMethodAnalyser.STATEMENT_ANALYSER);
        if (statusOfStatementAnalyser.isDelayed()) {
            StatementAnalyser statement = findFirstStatementWithDelays(firstStatementAnalyser);
            assert statement != null;
            AnalyserComponents<String, StatementAnalyserSharedState> analyserComponentsOfStatement = statement.getAnalyserComponents();
            LOGGER.warn("Analyser components of first statement with delays {} of {}:\n{}", statement.index(),
                    methodInfo.fullyQualifiedName(),
                    analyserComponentsOfStatement.details());
            AnalysisStatus statusOfMethodLevelData = analyserComponentsOfStatement.getStatus(StatementAnalyserImpl.ANALYSE_METHOD_LEVEL_DATA);
            if (statusOfMethodLevelData.isDelayed()) {
                AnalyserComponents<String, MethodLevelData.SharedState> analyserComponentsOfMethodLevelData =
                        statement.getStatementAnalysis().methodLevelData().analyserComponents;
                LOGGER.warn("Analyser components of method level data of last statement {} of {}:\n{}", statement.index(),
                        methodInfo.fullyQualifiedName(),
                        analyserComponentsOfMethodLevelData.details());
            }
        }
    }

    private StatementAnalyser findFirstStatementWithDelays(StatementAnalyser firstStatementAnalyser) {
        StatementAnalyser sa = firstStatementAnalyser;
        while (sa != null) {
            AnalyserComponents<String, StatementAnalyserSharedState> components = sa.getAnalyserComponents();
            if (components.getStatusesAsMap().values().stream().anyMatch(AnalysisStatus::isDelayed)) {
                return sa;
            }
            if (!sa.navigationData().next.isSet()) return sa;
            Optional<StatementAnalyser> opt = sa.navigationData().next.get();
            if (opt.orElse(null) == null) return sa;
            sa = opt.get();
        }
        return null;
    }

    // occurs as often in a flatMap as not, so a stream version is useful
    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? Stream.empty() :
                methodAnalysis.getLastStatement().streamOfLatestInfoOfVariablesReferringTo(fieldInfo);
    }

    public VariableInfo getThisAsVariable() {
        StatementAnalysis last = methodAnalysis.getLastStatement();
        if (last == null) return null;
        return last.getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName + ".this");
    }

    public VariableInfo getReturnAsVariable() {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement(true);
        assert lastStatement != null; // either a constructor, and then we shouldn't ask; or compilation error
        return lastStatement.getLatestVariableInfo(methodInfo.fullyQualifiedName());
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        return firstStatementAnalyser.navigateTo(index);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            super(iteration, conditionManager, closure);
        }

        @Override
        public EvaluationContext child(Expression condition) {
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition,
                    Precondition.empty(getPrimitives()));
            return ComputingMethodAnalyser.this.new EvaluationContextImpl(iteration, cm, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public DV getProperty(Variable variable, Property property) {
            if (variable instanceof FieldReference fieldReference) {
                Property vp = external(property);
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                Property vp = property == Property.NOT_NULL_EXPRESSION
                        ? Property.NOT_NULL_PARAMETER : property;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            if (variable instanceof This thisVar) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(thisVar.typeInfo);
                return typeAnalysis.getProperty(property);
            }
            throw new UnsupportedOperationException("Variable: " + variable.fullyQualifiedName() + " type " + variable.getClass());
        }

        private Property external(Property property) {
            if (property == NOT_NULL_EXPRESSION) return EXTERNAL_NOT_NULL;
            if (property == IMMUTABLE) return EXTERNAL_IMMUTABLE;
            if (property == CONTAINER) return EXTERNAL_CONTAINER;
            return property;
        }

        @Override
        public DV getProperty(Expression value,
                              Property property,
                              boolean duringEvaluation,
                              boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression ve) {
                return getProperty(ve.variable(), property);
            }
            return value.getProperty(this, property, true);
        }

        // needed in re-evaluation of inlined method in DetectEventual, before calling analyseExpression
        @Override
        public Expression currentValue(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable);
        }

        @Override
        public MethodAnalyserImpl getCurrentMethod() {
            return ComputingMethodAnalyser.this;
        }

        @Override
        public TypeInfo getCurrentType() {
            return methodInfo.typeInfo;
        }
    }
}

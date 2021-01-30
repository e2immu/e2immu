/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.config.EvaluationResultVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.FlowData.Execution.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;
import static org.e2immu.analyser.util.StringUtil.pad;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser>, HoldsAnalysers {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);
    public static final String ANALYSE_METHOD_LEVEL_DATA = "analyseMethodLevelData";

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final ExpandableAnalyserContextImpl analyserContext;
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    //private ConditionManager localConditionManager;
    private AnalysisStatus analysisStatus;
    private AnalyserComponents<String, SharedState> analyserComponents;

    private final SetOnce<List<PrimaryTypeAnalyser>> localAnalysers = new SetOnce<>();

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index,
                              boolean inSyncBlock) {
        this.analyserContext = new ExpandableAnalyserContextImpl(Objects.requireNonNull(analyserContext));
        this.myMethodAnalyser = Objects.requireNonNull(methodAnalyser);
        this.statementAnalysis = new StatementAnalysis(analyserContext.getPrimitives(),
                methodAnalyser.methodAnalysis, statement, parent, index, inSyncBlock);
    }

    public static StatementAnalyser recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock) {
        Objects.requireNonNull(myMethodAnalyser);
        Objects.requireNonNull(myMethodAnalyser.methodAnalysis);

        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
        }
        StatementAnalyser first = null;
        StatementAnalyser previous = null;
        for (Statement statement : statements) {
            String padded = pad(statementIndex, statements.size());
            String iPlusSt = indices.isEmpty() ? "" + padded : indices + "." + padded;
            StatementAnalyser statementAnalyser = new StatementAnalyser(analyserContext, myMethodAnalyser, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.statementAnalysis.navigationData.next.set(Optional.of(statementAnalyser.statementAnalysis));
                previous.navigationData.next.set(Optional.of(statementAnalyser));
            }
            previous = statementAnalyser;
            if (first == null) first = statementAnalyser;

            int blockIndex = 0;
            List<Optional<StatementAnalyser>> blocks = new ArrayList<>();
            List<Optional<StatementAnalysis>> analysisBlocks = new ArrayList<>();

            Structure structure = statement.getStructure();
            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;

            if (structure.haveStatements()) {
                String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);

                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                        statementAnalyser.statementAnalysis, structure.getStatements(),
                        indexWithBlock, true, newInSyncBlock);
                blocks.add(Optional.of(subStatementAnalyser));
                analysisBlocks.add(Optional.of(subStatementAnalyser.statementAnalysis));
            } else {
                analysisBlocks.add(Optional.empty());
            }
            blockIndex++;
            for (Structure subStatements : structure.subStatements()) {
                if (subStatements.haveStatements()) {
                    String indexWithBlock = iPlusSt + "." + pad(blockIndex, structure.subStatements().size() + 1);

                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                            statementAnalyser.statementAnalysis, subStatements.getStatements(),
                            indexWithBlock, true, newInSyncBlock);
                    blocks.add(Optional.of(subStatementAnalyser));
                    analysisBlocks.add(Optional.of(subStatementAnalyser.statementAnalysis));
                } else {
                    blocks.add(Optional.empty());
                    analysisBlocks.add(Optional.empty());
                }
                blockIndex++;
            }
            statementAnalyser.statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
            statementAnalyser.navigationData.blocks.set(ImmutableList.copyOf(blocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.statementAnalysis.navigationData.next.set(Optional.empty());
            previous.navigationData.next.set(Optional.empty());
        }
        return first;

    }

    @Override
    public String toString() {
        return "Statement " + index() + " of " + myMethodAnalyser.methodInfo.fullyQualifiedName;
    }

    record PreviousAndFirst(StatementAnalyser previous, StatementAnalyser first) {
    }

    /**
     * Main recursive method, which follows the navigation chain for all statements in a block. When called from the method analyser,
     * it loops over the statements of the method.
     *
     * @param iteration           the current iteration
     * @param forwardAnalysisInfo information from the level above
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    public StatementAnalyserResult analyseAllStatementsInBlock(int iteration, ForwardAnalysisInfo forwardAnalysisInfo, EvaluationContext closure) {
        try {
            // skip all the statements that are already in the DONE state...
            PreviousAndFirst previousAndFirst = goToFirstStatementToAnalyse();
            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();

            if (previousAndFirst.first == null) {
                // nothing left to analyse
                return builder.setAnalysisStatus(DONE).build();
            }

            StatementAnalyser previousStatement = previousAndFirst.previous;
            StatementAnalyser statementAnalyser = previousAndFirst.first;
            do {
                boolean wasReplacement;
                if (analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
                    wasReplacement = false;
                } else {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager(), closure);
                    // first attempt at detecting a transformation
                    wasReplacement = checkForPatterns(evaluationContext);
                    statementAnalyser = statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.statementAnalysis;
                StatementAnalyserResult result = statementAnalyser.analyseSingleStatement(iteration, closure,
                        wasReplacement, previousStatementAnalysis, forwardAnalysisInfo);
                builder.add(result);
                previousStatement = statementAnalyser;

                statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            } while (statementAnalyser != null);
            return builder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception while analysing block {} of: {}", index(), myMethodAnalyser.methodInfo.fullyQualifiedName());
            throw rte;
        }
    }

    @Override
    public NavigationData<StatementAnalyser> getNavigationData() {
        return navigationData;
    }

    @Override
    public StatementAnalyser followReplacements() {
        if (navigationData.replacement.isSet()) {
            return navigationData.replacement.get().followReplacements();
        }
        return this;
    }

    // note that there is a clone of this in statementAnalysis
    @Override
    public StatementAnalyser lastStatement() {
        if (statementAnalysis.flowData.isUnreachable() && statementAnalysis.parent == null) {
            throw new UnsupportedOperationException("The first statement can never be unreachable");
        }
        return followReplacements().navigationData.next.get().map(statementAnalyser -> {
            if (statementAnalyser.statementAnalysis.flowData.isUnreachable()) {
                return this;
            }
            return statementAnalyser.lastStatement();
        }).orElse(this);
    }

    @Override
    public String index() {
        return statementAnalysis.index;
    }

    @Override
    public Statement statement() {
        return statementAnalysis.statement;
    }

    @Override
    public StatementAnalysis parent() {
        return statementAnalysis.parent;
    }

    @Override
    public void wireNext(StatementAnalyser newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
        statementAnalysis.navigationData.next.set(newStatement == null ? Optional.empty() : Optional.of(newStatement.statementAnalysis));
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalyser> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(evaluationContext.getAnalyserContext(),
                evaluationContext.getCurrentMethod(),
                parent(), statements, startIndex, false, statementAnalysis.inSyncBlock);
    }

    private PreviousAndFirst goToFirstStatementToAnalyse() {
        StatementAnalyser statementAnalyser = followReplacements();
        StatementAnalyser previous = null;
        while (statementAnalyser != null && statementAnalyser.analysisStatus == DONE) {
            previous = statementAnalyser;
            statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            if (statementAnalyser != null) {
                statementAnalyser = statementAnalyser.followReplacements();
            }
        }
        return new PreviousAndFirst(previous, statementAnalyser);
    }

    private boolean checkForPatterns(EvaluationContext evaluationContext) {
        PatternMatcher<StatementAnalyser> patternMatcher = analyserContext.getPatternMatcher();
        if (!analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
            MethodInfo methodInfo = myMethodAnalyser.methodInfo;
            return patternMatcher.matchAndReplace(methodInfo, this, evaluationContext);
        }
        return false;
    }

    private Location getLocation() {
        return statementAnalysis.location();
    }

    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    record SharedState(EvaluationContext evaluationContext,
                       StatementAnalyserResult.Builder builder,
                       StatementAnalysis previous,
                       ForwardAnalysisInfo forwardAnalysisInfo,
                       ConditionManager localConditionManager) {
    }

    /**
     * @param iteration      the iteration
     * @param wasReplacement boolean, to ensure that the effect of a replacement warrants continued analysis
     * @param previous       null if there was no previous statement in this block
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    private StatementAnalyserResult analyseSingleStatement(int iteration,
                                                           EvaluationContext closure,
                                                           boolean wasReplacement,
                                                           StatementAnalysis previous,
                                                           ForwardAnalysisInfo forwardAnalysisInfo) {
        try {
            if (analysisStatus == null) {
                //assert localConditionManager == null : "expected null localConditionManager";
                assert analyserComponents == null : "expected null analyser components";

                analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                        .add("checkUnreachableStatement", this::checkUnreachableStatement)
                        .add("initialiseOrUpdateVariables", this::initialiseOrUpdateVariables)
                        .add("analyseTypesInStatement", this::analyseTypesInStatement)
                        .add("evaluationOfMainExpression", this::evaluationOfMainExpression)
                        .add("subBlocks", this::subBlocks)
                        .add("analyseFlowData", sharedState -> statementAnalysis.flowData.analyse(this, previous,
                                sharedState.forwardAnalysisInfo.execution()))
                        .add("freezeAssignmentInBlock", this::freezeAssignmentInBlock)
                        .add("checkNotNullEscapesAndPreconditions", this::checkNotNullEscapesAndPreconditions)
                        .add(ANALYSE_METHOD_LEVEL_DATA, sharedState -> statementAnalysis.methodLevelData.analyse(sharedState, statementAnalysis,
                                previous == null ? null : previous.methodLevelData,
                                statementAnalysis.stateData))
                        .add("checkUnusedReturnValue", sharedState -> checkUnusedReturnValueOfMethodCall())
                        .add("checkUselessAssignments", sharedState -> checkUselessAssignments())
                        .add("checkUnusedLocalVariables", sharedState -> checkUnusedLocalVariables())
                        .add("checkUnusedLoopVariables", sharedState -> checkUnusedLoopVariables())
                        .build();
            }


            boolean startOfNewBlock = previous == null;
            ConditionManager localConditionManager;
            if (startOfNewBlock) {
                localConditionManager = forwardAnalysisInfo.conditionManager();
            } else {
                Expression combinedPrecondition = previous.methodLevelData.getCombinedPreconditionOrDelay(statementAnalysis.primitives);
                EvaluationContext tmpEvaluationContext = new EvaluationContextImpl(iteration,
                        ConditionManager.initialConditionManager(previous.primitives), closure);
                boolean combinedPreconditionIsDelayed = tmpEvaluationContext.isDelayed(combinedPrecondition);
                localConditionManager = previous.stateData.getConditionManagerForNextStatement()
                        .withPrecondition(combinedPrecondition, combinedPreconditionIsDelayed);
            }

            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
            EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, localConditionManager, closure);
            SharedState sharedState = new SharedState(evaluationContext, builder, previous, forwardAnalysisInfo, localConditionManager);
            AnalysisStatus overallStatus = analyserComponents.run(sharedState);

            StatementAnalyserResult result = sharedState.builder()
                    .addMessages(statementAnalysis.messages.stream())
                    .setAnalysisStatus(overallStatus)
                    .combineAnalysisStatus(wasReplacement ? PROGRESS : DONE).build();
            analysisStatus = result.analysisStatus;

            visitStatementVisitors(statementAnalysis.index, result, sharedState);

            log(ANALYSER, "Returning from statement {} of {} with analysis status {}", statementAnalysis.index,
                    myMethodAnalyser.methodInfo.name, analysisStatus);
            return result;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception while analysing statement {} of {}", index(), myMethodAnalyser.methodInfo.fullyQualifiedName());
            throw rte;
        }
    }

    private AnalysisStatus freezeAssignmentInBlock(SharedState sharedState) {
        if (statementAnalysis.statement instanceof LoopStatement) {
            statementAnalysis.localVariablesAssignedInThisLoop.freeze();
        }
        return DONE; // only in first iteration
    }

    // in a separate task, so that it can be skipped when the statement is unreachable
    private AnalysisStatus initialiseOrUpdateVariables(SharedState sharedState) {
        if (sharedState.evaluationContext.getIteration() == 0) {
            statementAnalysis.initIteration0(sharedState.evaluationContext, myMethodAnalyser.methodInfo, sharedState.previous);
        } else {
            statementAnalysis.initIteration1Plus(sharedState.evaluationContext, myMethodAnalyser.methodInfo, sharedState.previous);
        }

        if (!statementAnalysis.flowData.initialTimeIsSet()) {
            int time;
            if (sharedState.previous != null) {
                time = sharedState.previous.flowData.getTimeAfterSubBlocks();
            } else if (statementAnalysis.parent != null) {
                time = statementAnalysis.parent.flowData.getTimeAfterEvaluation();
            } else {
                time = 0; // start
            }
            statementAnalysis.flowData.setInitialTime(time, index());
        }
        return RUN_AGAIN;
    }

    private void visitStatementVisitors(String statementId, StatementAnalyserResult result, SharedState sharedState) {
        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVariableVisitors) {
            statementAnalysis.variables.stream()
                    .map(Map.Entry::getValue)
                    .forEach(variableInfoContainer -> statementAnalyserVariableVisitor.visit(
                            new StatementAnalyserVariableVisitor.Data(
                                    sharedState.evaluationContext.getIteration(),
                                    sharedState.evaluationContext,
                                    myMethodAnalyser.methodInfo,
                                    statementId,
                                    variableInfoContainer.current().name(),
                                    variableInfoContainer.current().variable(),
                                    variableInfoContainer.current().getValue(),
                                    sharedState.evaluationContext.isDelayed(variableInfoContainer.current().getValue()),
                                    variableInfoContainer.current().getProperties(),
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVisitors) {
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            result,
                            sharedState.evaluationContext.getIteration(),
                            sharedState.evaluationContext,
                            myMethodAnalyser.methodInfo,
                            statementAnalysis,
                            statementAnalysis.index,
                            statementAnalysis.stateData.getConditionManagerForNextStatement().condition(),
                            statementAnalysis.stateData.getConditionManagerForNextStatement().state(),
                            statementAnalysis.stateData.getConditionManagerForNextStatement().absoluteState(sharedState.evaluationContext),
                            sharedState.localConditionManager(),
                            analyserComponents.getStatusesAsMap()));
        }
    }

    private AnalysisStatus analyseTypesInStatement(SharedState sharedState) {
        if (!localAnalysers.isSet()) {
            Stream<TypeInfo> locallyDefinedTypes = Stream.concat(statementAnalysis.statement.getStructure().findTypeDefinedInStatement().stream(),
                    statement() instanceof LocalClassDeclaration lcd ? Stream.of(lcd.typeInfo) : Stream.empty());
            List<PrimaryTypeAnalyser> analysers = locallyDefinedTypes.map(typeInfo -> {
                PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyser(analyserContext,
                        typeInfo.typeResolution.get().sortedType(),
                        analyserContext.getConfiguration(),
                        analyserContext.getPrimitives(),
                        analyserContext.getE2ImmuAnnotationExpressions());
                primaryTypeAnalyser.initialize();
                return primaryTypeAnalyser;
            }).collect(Collectors.toUnmodifiableList());
            localAnalysers.set(analysers);

            boolean haveNext = navigationData.next.get().isPresent();
            // first, simple propagation of those analysers that we've already accumulated
            if (haveNext) {
                navigationData.next.get().get().analyserContext.addAll(analyserContext);
                navigationData.blocks.get().forEach(opt -> opt.ifPresent(sa -> sa.analyserContext.addAll(analyserContext)));
            }

            // in the subsequent statements, we'll want to used this local class declaration!
            if (statement() instanceof LocalClassDeclaration localClassDeclaration) {
                if (haveNext) {
                    // we'll need to ensure that the local type's analysers are available in the coming statements
                    StatementAnalyser next = navigationData.next.get().get();
                    localAnalysers.get().forEach(next.analyserContext::addPrimaryTypeAnalyser);
                } else {
                    statementAnalysis.ensure(Message.newMessage(getLocation(),
                            Message.USELESS_LOCAL_CLASS_DECLARATION, localClassDeclaration.typeInfo.simpleName));
                }
            }
        }

        AnalysisStatus analysisStatus = DONE;
        for (PrimaryTypeAnalyser analyser : localAnalysers.get()) {
            log(ANALYSER, "------- Starting local analyser {} ------", analyser.getName());
            AnalysisStatus lambdaStatus = analyser.analyse(sharedState.evaluationContext.getIteration(), sharedState.evaluationContext);
            log(ANALYSER, "------- Ending local analyser   {} ------", analyser.getName());
            analysisStatus = analysisStatus.combine(lambdaStatus);
            statementAnalysis.ensureMessages(analyser.getMessageStream());
        }


        return analysisStatus;
    }

    @Override
    public void makeImmutable() {
        if (localAnalysers.isSet()) {
            localAnalysers.get().forEach(PrimaryTypeAnalyser::makeImmutable);
        }
        for (Optional<StatementAnalyser> block : navigationData.blocks.get()) {
            block.ifPresent(StatementAnalyser::makeImmutable);
        }
        navigationData.next.get().ifPresent(StatementAnalyser::makeImmutable);
    }

    // executed only once per statement, at the very beginning of the loop
    // we're assuming that the flowData are computed correctly
    private AnalysisStatus checkUnreachableStatement(SharedState sharedState) {
        // if the previous statement was not reachable, we won't reach this one either
        if (sharedState.previous != null && sharedState.previous.flowData.getGuaranteedToBeReachedInMethod() == NEVER) {
            statementAnalysis.flowData.setGuaranteedToBeReached(NEVER);
            return DONE_ALL;
        }
        FlowData.Execution execution = sharedState.forwardAnalysisInfo().execution();
        Expression state = sharedState.localConditionManager.state();
        boolean stateIsDelayed = sharedState.evaluationContext.isDelayed(state);
        if (statementAnalysis.flowData.computeGuaranteedToBeReachedReturnUnreachable(sharedState.previous, execution,
                state, stateIsDelayed) && !statementAnalysis.inErrorState(Message.UNREACHABLE_STATEMENT)) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
            return DONE_ALL; // means: don't run any of the other steps!!
        }
        return sharedState.localConditionManager.isDelayed() || execution == DELAYED_EXECUTION ? DELAYS : DONE;
    }

    private Boolean isEscapeAlwaysExecutedInCurrentBlock() {
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        if (bestAlways == InterruptsFlow.DELAYED) return null;
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return statementAnalysis.flowData.getGuaranteedToBeReachedInCurrentBlock() == FlowData.Execution.ALWAYS;
        }
        return false;
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
    For every variable, a number of steps are executed:
    - it is created, some local copies are created, and it is prepared for EVAL level
    - values, linked variables are set

    Finally, general modifications are carried out
     */
    private AnalysisStatus apply(SharedState sharedState,
                                 EvaluationResult evaluationResult,
                                 StatementAnalysis statementAnalysis) {
        AnalysisStatus status = evaluationResult.someValueWasDelayed() ? DELAYS : DONE;

        if (evaluationResult.addCircularCallOrUndeclaredFunctionalInterface()) {
            statementAnalysis.methodLevelData.addCircularCallOrUndeclaredFunctionalInterface();
        }

        for (Map.Entry<Variable, EvaluationResult.ChangeData> entry : evaluationResult.changeData().entrySet()) {

            Variable variable = entry.getKey();
            EvaluationResult.ChangeData changeData = entry.getValue();

            // make a copy because we might add a variable when linking the local loop copy
            Set<Variable> additionalLinks = new HashSet<>(ensureVariables(sharedState.evaluationContext, variable,
                    changeData, evaluationResult.statementTime()));

            // we're now guaranteed to find the variable
            VariableInfoContainer vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
            VariableInfo vi = vic.best(VariableInfoContainer.Level.EVALUATION);
            VariableInfo vi1 = vic.getPreviousOrInitial();
            assert vi != vi1 : "There should already be a different EVALUATION object";

            if (changeData.markAssignment()) {
                Expression valueToWrite = maybeValueNeedsState(sharedState, vic, vi1, bestValue(changeData, vi1));
                boolean valueToWriteIsDelayed = sharedState.evaluationContext.isDelayed(valueToWrite);

                log(ANALYSER, "Write value {} to variable {}", valueToWrite, variable.fullyQualifiedName());
                // first do the properties that come with the value; later, we'll write the ones in changeData
                Map<VariableProperty, Integer> propertiesToSet = sharedState.evaluationContext.getValueProperties(valueToWrite);
                vic.setValue(valueToWrite, valueToWriteIsDelayed, changeData.staticallyAssignedVariables(), propertiesToSet, false);

                if (vic.isLocalVariableInLoopDefinedOutside()) {
                    VariableInfoContainer local = addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
                    if (local != null && !evaluationResult.someValueWasDelayed() && !valueToWriteIsDelayed) {
                        // assign the value of the assignment to the local copy created
                        log(ANALYSER, "Write value {} to local copy variable {}", valueToWrite, local.current().variable().fullyQualifiedName());
                        Map<VariableProperty, Integer> props2 = sharedState.evaluationContext.getValueProperties(valueToWrite);
                        local.setValue(valueToWrite, valueToWriteIsDelayed, changeData.staticallyAssignedVariables(), props2, false);
                        local.setLinkedVariables(new LinkedVariables(Set.of(vi.variable())), false);
                        additionalLinks.add(local.current().variable());
                    }
                }
            } else if (changeData.value() != null) {
                // a modifying method caused an updated instance value
                boolean valueIsDelayed = sharedState.evaluationContext.isDelayed(changeData.value());
                vic.setValue(changeData.value(), valueIsDelayed,
                        changeData.staticallyAssignedVariables(), changeData.properties(), false);
            } else if (variable instanceof This || !evaluationResult.someValueWasDelayed() && !changeData.haveDelayesCausedByMethodCalls()) {
                // we're not assigning (and there is no change in instance because of a modifying method)
                // only then we copy from INIT to EVAL
                vic.setValue(vi1.getValue(), vi1.isDelayed(), vi1.getStaticallyAssignedVariables(), vi1.getProperties(), false);
            }

            if (vi.isDelayed()) {
                log(DELAYED, "Apply of {}, {} is delayed because of unknown value for {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                status = DELAYS;
            } else if (changeData.haveMethodDelay()) {
                log(DELAYED, "Apply of {}, {} is delayed because of delay in method call on {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName, variable);
                status = DELAYS;
            }

            LinkedVariables mergedLinkedVariables = writeMergedLinkedVariables(changeData, variable, vi, vi1, additionalLinks);
            if (mergedLinkedVariables != LinkedVariables.DELAY && vi.isNotDelayed()) {
                vic.setLinkedVariables(mergedLinkedVariables, false);
            } else if (vi.getLinkedVariables() == LinkedVariables.DELAY) {
                status = DELAYS;
            }

            // set properties
            // there's one that we intercept, to make sure that not-null travels to the parameter analyser asap
            // via the statement analyser result

            for (Map.Entry<VariableProperty, Integer> e : changeData.properties().entrySet()) {
                VariableProperty property = e.getKey();
                int pv = e.getValue();

                vic.setProperty(property, pv, false, VariableInfoContainer.Level.EVALUATION);
                if (property == VariableProperty.NOT_NULL && variable instanceof ParameterInfo parameterInfo) {
                    log(VARIABLE_PROPERTIES, "Propagating not-null value of {} to {}", pv, variable.fullyQualifiedName());
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, pv));
                }
            }
        }

        evaluationResult.messages().getMessageStream().forEach(statementAnalysis::ensure);

        if (status == DONE && evaluationResult.precondition() != null) {
            boolean preconditionIsDelayed = sharedState.evaluationContext.isDelayed(evaluationResult.precondition());
            statementAnalysis.stateData.setPrecondition(evaluationResult.precondition(), preconditionIsDelayed);
        }
        if (status == DONE && !statementAnalysis.methodLevelData.internalObjectFlows.isFrozen()) {
            boolean delays = false;
            for (ObjectFlow objectFlow : evaluationResult.getObjectFlowStream().collect(Collectors.toSet())) {
                if (objectFlow.isDelayed()) {
                    delays = true;
                } else if (!statementAnalysis.methodLevelData.internalObjectFlows.contains(objectFlow)) {
                    statementAnalysis.methodLevelData.internalObjectFlows.add(objectFlow);
                }
            }
            if (delays) {
                log(DELAYED, "Apply of {}, {} is delayed because of internal object flows",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
                status = DELAYS;
            } else {
                statementAnalysis.methodLevelData.internalObjectFlows.freeze();
            }
        }

        // debugging...
        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(),
                    myMethodAnalyser.methodInfo, statementAnalysis.index, evaluationResult));
        }

        return status;
    }

    /*
    first of all in 'apply' we need to ensure that all variables exist, and have a proper assignmentId and readId.

    We need to do:
    - generally ensure a EVALUATION level for each variable occurring, with correct assignmentId, readId
    - create fields + local copies of variable fields, because they don't exist in the first iteration
    - link the fields to their local copies (or at least, compute these links)

    Local variables, This, Parameters will already exist, minimally in INITIAL level
    Fields (and forms of This (super...)) will not exist in the first iteration; they need creating
     */
    private Set<Variable> ensureVariables(EvaluationContext evaluationContext,
                                          Variable variable,
                                          EvaluationResult.ChangeData changeData,
                                          int newStatementTime) {
        VariableInfoContainer vic;
        if (!statementAnalysis.variables.isSet(variable.fullyQualifiedName())) {
            vic = statementAnalysis.createVariable(evaluationContext, variable, statementAnalysis.flowData.getInitialTime());
        } else {
            vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
        }
        VariableInfo initial = vic.getPreviousOrInitial();
        Set<Variable> additionalLinksForThisVariable;
        if (variable instanceof FieldReference fieldReference &&
                initial.isConfirmedVariableField() && !changeData.readAtStatementTime().isEmpty()) {
            // ensure all of the local copies
            additionalLinksForThisVariable =
                    ensureLocalCopiesOfVariableField(changeData.readAtStatementTime(), fieldReference, initial);
        } else {
            additionalLinksForThisVariable = Set.of();
        }
        String id = index() + VariableInfoContainer.Level.EVALUATION;
        String assignmentId = changeData.markAssignment() ? id : initial.getAssignmentId();
        // we do not set readId to the empty set when markAssignment... we'd rather keep the old value
        // we will compare the recency anyway
        String readId = changeData.readAtStatementTime().isEmpty() ? initial.getReadId() : id;
        int statementTime = statementAnalysis.statementTimeForVariable(analyserContext, variable, newStatementTime);

        vic.ensureEvaluation(assignmentId, readId, statementTime, changeData.readAtStatementTime());

        return additionalLinksForThisVariable;
    }

    private Set<Variable> ensureLocalCopiesOfVariableField(Set<Integer> statementTimes,
                                                           FieldReference fieldReference,
                                                           VariableInfo initial) {
        Set<Variable> set = new HashSet<>();
        for (int statementTime : statementTimes) {
            LocalVariableReference localCopy = statementAnalysis.variableInfoOfFieldWhenReading(analyserContext,
                    fieldReference, initial, statementTime);
            set.add(localCopy);
        }
        return set;
    }

    private LinkedVariables writeMergedLinkedVariables(EvaluationResult.ChangeData changeData,
                                                       Variable variable,
                                                       VariableInfo vi,
                                                       VariableInfo vi1,
                                                       Set<Variable> additionalLinks) {
        if (changeData.linkedVariables() == LinkedVariables.DELAY) {
            log(DELAYED, "Apply of {}, {} is delayed because of linked variables of {}",
                    index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                    variable.fullyQualifiedName());
            return LinkedVariables.DELAY;
        }

        if (vi.getStatementTime() == VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            log(DELAYED, "Apply of step {}, {} is delayed because of variable field delay",
                    index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            return LinkedVariables.DELAY;
        }

        // no assignment, we need to copy, potentially add to previous value

        LinkedVariables previousValue = vi1.getLinkedVariables();
        LinkedVariables toAddFromPreviousValue;
        if (changeData.markAssignment()) {
            if (vi.isConfirmedVariableField()) {
                if (previousValue == LinkedVariables.DELAY) {
                    log(DELAYED, "Apply of {}, {} is delayed because of previous value delay of linked variables of variable field {}",
                            index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                            variable.fullyQualifiedName());
                    return LinkedVariables.DELAY;
                }
                toAddFromPreviousValue = previousValue.removeAllButLocalCopiesOf(variable);
            } else {
                toAddFromPreviousValue = LinkedVariables.EMPTY;
            }
        } else {
            if (previousValue == LinkedVariables.DELAY) {
                log(DELAYED, "Apply of {}, {} is delayed because of previous value delay of linked variables of {}",
                        index(), myMethodAnalyser.methodInfo.fullyQualifiedName,
                        variable.fullyQualifiedName());
                return LinkedVariables.DELAY;
            }
            toAddFromPreviousValue = previousValue;
        }
        // note that the null here is actual presence or absence in a map...
        LinkedVariables mergedValue = toAddFromPreviousValue.merge(changeData.linkedVariables())
                .merge(new LinkedVariables(additionalLinks));
        log(ANALYSER, "Set linked variables of {} to {} in {}, {}",
                variable, mergedValue, index(), myMethodAnalyser.methodInfo.fullyQualifiedName);

        assert mergedValue != LinkedVariables.DELAY;
        return mergedValue;
    }

    /*
    See among others Loops_1: when a variable is assigned in a loop, it is possible that interrupts (break, etc.) have
    caused a state. If this variable is defined outside the loop, it'll have to have a value when coming out of the loop.
    The added state helps to avoid unconditional assignments.
    In i=3; while(c) { if(x) break; i=5; }, we return x?3:5; x will most likely be dependent on the loop and, be
    turned into some generic integer

    In i=3; while(c) { i=5; if(x) break; }, we return c?5:3, as soon as c has a value

    Q: what is the best place for this piece of code? EvalResult?? This here seems too late
     */
    private Expression maybeValueNeedsState(SharedState sharedState, VariableInfoContainer vic, VariableInfo vi1,
                                            Expression value) {
        boolean valueIsDelayed = sharedState.evaluationContext.isDelayed(value);
        if (valueIsDelayed ||
                vic.getVariableInLoop().variableType() != VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE) {
            // not applicable
            return value;
        }
        ConditionManager localConditionManager = sharedState.localConditionManager;
        //if (localConditionManager.isDelayed()) return NoValue.EMPTY;
        Expression state;
        if (!localConditionManager.state().isBoolValueTrue()) {
            state = localConditionManager.state();
        } else if (!localConditionManager.condition().isBoolValueTrue()) {
            state = localConditionManager.condition();
        } else {
            state = null;
        }
        if (state != null) {
            // do not take vi1 itself, but "the" local copy of the variable
            Expression valueOfVariablePreAssignment = sharedState.evaluationContext.currentValue(vi1.variable(),
                    statementAnalysis.statementTime(VariableInfoContainer.Level.INITIAL), true);
            return EvaluateInlineConditional.conditionalValueConditionResolved(sharedState.evaluationContext, state, value,
                    valueOfVariablePreAssignment, ObjectFlow.NO_FLOW).value();
        }
        return value;
    }

    /*
     we keep track which local variables are assigned in a loop

     add this variable name to all parent loop statements until definition of the local variable
     in this way, a version can be introduced to be used before this assignment.

     at the same time, a new local copy has to be created in this statement to be used after the assignment
     */
    private VariableInfoContainer addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName) {
        StatementAnalysis sa = statementAnalysis;
        String loopIndex = null;
        while (sa != null) {
            if (!sa.variables.isSet(fullyQualifiedName)) return null;
            VariableInfoContainer localVic = sa.variables.get(fullyQualifiedName);
            if (!localVic.isLocalVariableInLoopDefinedOutside()) return null;
            if (sa.statement instanceof LoopStatement) {
                if (!sa.localVariablesAssignedInThisLoop.contains(fullyQualifiedName)) {
                    sa.localVariablesAssignedInThisLoop.add(fullyQualifiedName);
                }
                loopIndex = sa.index;
            }
            sa = sa.parent;
        }
        assert loopIndex != null;

        String newFqn = statementAnalysis.createLocalLoopCopyFQN(vic, vic.best(VariableInfoContainer.Level.EVALUATION));
        if (!statementAnalysis.variables.isSet(newFqn)) {
            LocalVariableReference newLvr = createLocalCopyOfLoopVariable(vic, newFqn);

            VariableInfoContainer newVic = VariableInfoContainerImpl.newVariable(newLvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                    new VariableInLoop(loopIndex, index(), VariableInLoop.VariableType.LOOP_COPY), navigationData.hasSubBlocks());
            String assigned = index() + VariableInfoContainer.Level.INITIAL;
            String read = index() + VariableInfoContainer.Level.EVALUATION;
            newVic.ensureEvaluation(assigned, read, VariableInfoContainer.NOT_A_VARIABLE_FIELD, Set.of());

            statementAnalysis.variables.put(newFqn, newVic);

            // value will be set in main apply
            return newVic;
        }
        return statementAnalysis.variables.get(newFqn);
    }

    private Expression bestValue(EvaluationResult.ChangeData valueChangeData, VariableInfo vi1) {
        if (valueChangeData != null && valueChangeData.value() != null) {
            return valueChangeData.value();
        }
        if (vi1 != null) {
            return vi1.getValue();
        }
        return null;
    }

    /*
    Not-null escapes should not contribute to preconditions.
    All the rest does.
     */
    private AnalysisStatus checkNotNullEscapesAndPreconditions(SharedState sharedState) {
        if (statementAnalysis.statement instanceof AssertStatement) return DONE; // is dealt with in subBlocks
        Boolean escapeAlwaysExecuted = isEscapeAlwaysExecutedInCurrentBlock();
        if (escapeAlwaysExecuted == null) {
            log(DELAYED, "Delaying check precondition of statement {}, interrupt condition unknown", index());
            return DELAYS;
        }
        if (statementAnalysis.stateData.conditionManagerIsNotYetSet()) {
            log(DELAYED, "Delaying check precondition of statement {}, no condition manager", index());
            return DELAYS;
        }
        if (escapeAlwaysExecuted) {
            Set<Variable> nullVariables = statementAnalysis.stateData.getConditionManagerForNextStatement()
                    .findIndividualNullInCondition(sharedState.evaluationContext, true);
            for (Variable nullVariable : nullVariables) {
                log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.fullyQualifiedName());
                if (nullVariable instanceof ParameterInfo parameterInfo) {
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                    disableErrorsOnIfStatement();
                }
            }
            // escapeCondition should filter out all != null, == null clauses
            Expression precondition = statementAnalysis.stateData.getConditionManagerForNextStatement().precondition(sharedState.evaluationContext);
            boolean preconditionIsDelayed = sharedState.evaluationContext.isDelayed(precondition);
            Expression translated = sharedState.evaluationContext.acceptAndTranslatePrecondition(precondition);
            if (translated != null) {
                log(VARIABLE_PROPERTIES, "Escape with precondition {}", translated);
                statementAnalysis.stateData.setPrecondition(translated, preconditionIsDelayed);
                disableErrorsOnIfStatement();
                return DONE;
            }
        }

        if (statementAnalysis.stateData.preconditionIsEmpty()) {
            // it could have been set from the assert (step4) or apply via a method call
            statementAnalysis.stateData.setPrecondition(new BooleanConstant(statementAnalysis.primitives, true), false);
        }
        return DONE;
    }

    /*
    Method to help avoiding errors in the following situation:

    if(parameter == null) throw new NullPointerException();

    This will cause a @NotNull on the parameter, which in turn renders parameter == null equal to "false", which causes errors.
    The switch avoids raising this error

     */
    private void disableErrorsOnIfStatement() {
        StatementAnalysis sa = statementAnalysis.enclosingConditionalStatement();
        if (!sa.stateData.statementContributesToPrecondition.isSet()) {
            log(VARIABLE_PROPERTIES, "Disable errors on enclosing if-statement {}", sa.index);
            sa.stateData.statementContributesToPrecondition.set();
        }
    }

    private VariableInfoContainer findReturnAsVariableForWriting() {
        String fqn = myMethodAnalyser.methodInfo.fullyQualifiedName();
        return statementAnalysis.findForWriting(fqn);
    }

    /*
    we create the variable(s), to make sure they exist in INIT level, but defer computation of their value to step 3.
    In effect, we split int i=3; into int i (INIT); i=3 (EVAL);

    Loop and catch variables are special in that their scope is restricted to the statement and its block.
    We deal with them here, however they are assigned in the structure.

    Explicit constructor invocation uses "updaters" in the structure, but that is essentially level 3 evaluation.

    The for-statement has explicit initialisation and updating. These statements need evaluation, but the actual
    values are only used for independent for-loop analysis (not yet implemented) rather than for assigning real
    values to the loop variable.

    Loop (and catch) variables will be defined in level 2. A special local variable with a $<index> suffix will
    be created to represent a generic loop value.

    The special thing about creating variables at level 2 in a statement is that they are not transferred to the next statement,
    nor are they merged into level 4.
     */
    private List<Expression> initialisersAndUpdaters(SharedState sharedState) {
        List<Expression> expressionsToEvaluate = new ArrayList<>();

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e), or for(int i=...), or int i=3, j=4;
        // variable will be set to a NewObject case of a catch

        if (sharedState.forwardAnalysisInfo.catchVariable() != null) {
            // inject a catch(E1 | E2 e) { } exception variable, directly with assigned value, "read"
            LocalVariableCreation catchVariable = sharedState.forwardAnalysisInfo.catchVariable();
            String name = catchVariable.localVariable.name();
            if (!statementAnalysis.variables.isSet(name)) {
                LocalVariableReference lvr = new LocalVariableReference(analyserContext, catchVariable.localVariable, List.of());
                VariableInfoContainer vic = VariableInfoContainerImpl.newCatchVariable(lvr, index(),
                        NewObject.forCatchOrThis(statementAnalysis.primitives, lvr.parameterizedType()),
                        statementAnalysis.navigationData.hasSubBlocks());
                statementAnalysis.variables.put(name, vic);
            }
        }

        for (Expression expression : statementAnalysis.statement.getStructure().initialisers()) {
            Expression initialiserToEvaluate;

            if (expression instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr;
                VariableInfoContainer vic;
                if (!statementAnalysis.variables.isSet(lvc.localVariable.name())) {

                    // create the local (loop, catch) variable

                    lvr = new LocalVariableReference(analyserContext, lvc.localVariable, List.of());
                    VariableInLoop variableInLoop = statement() instanceof LoopStatement ?
                            new VariableInLoop(index(), null, VariableInLoop.VariableType.LOOP) : VariableInLoop.NOT_IN_LOOP;
                    vic = VariableInfoContainerImpl.newVariable(lvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD,
                            variableInLoop, statementAnalysis.navigationData.hasSubBlocks());

                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.localVariablesAssignedInThisLoop.add(lvr.fullyQualifiedName());
                    }
                    statementAnalysis.variables.put(lvc.localVariable.name(), vic);
                } else {
                    vic = statementAnalysis.variables.get(lvc.localVariable.name());
                    lvr = (LocalVariableReference) vic.current().variable();
                }

                // what should we evaluate? catch: assign a value which will be read; for(int i=0;...) --> 0 instead of i=0;
                if (statement() instanceof LoopStatement) {
                    initialiserToEvaluate = lvc.expression;
                    // but, because we don't evaluate the assignment, we need to assign some value to the loop variable
                    // otherwise we'll get delays
                    // especially in the case of forEach, the lvc.expression is empty, anyway
                    // an assignment may be difficult. The value is never used, only local copies are
                    vic.setValue(NewObject.forCatchOrThis(statementAnalysis.primitives, lvr.parameterizedType()), false,
                            LinkedVariables.EMPTY, Map.of(), true);
                    vic.setLinkedVariables(LinkedVariables.EMPTY, true);
                } else {
                    initialiserToEvaluate = lvc; // == expression
                }
            } else initialiserToEvaluate = expression;

            if (initialiserToEvaluate != null && initialiserToEvaluate != EmptyExpression.EMPTY_EXPRESSION) {
                expressionsToEvaluate.add(initialiserToEvaluate);
            }
        }

        // part 2: updaters, + determine which local variables are modified in the updaters

        if (statementAnalysis.statement instanceof LoopStatement) {
            for (Expression expression : statementAnalysis.statement.getStructure().updaters()) {
                expression.visit(e -> {
                    if (e instanceof Assignment assignment && assignment.target instanceof VariableExpression ve) {
                        if (!(statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) &&
                                !statementAnalysis.localVariablesAssignedInThisLoop.contains(ve.name())) {
                            statementAnalysis.localVariablesAssignedInThisLoop.add(ve.name());
                        }
                        expressionsToEvaluate.add(assignment); // we do evaluate the assignment no that the var will be there
                    }
                });
            }
        } else if (statementAnalysis.statement instanceof ExplicitConstructorInvocation) {
            Structure structure = statement().getStructure();
            expressionsToEvaluate.addAll(structure.updaters());
        }

        return expressionsToEvaluate;
    }

    private List<Expression> localVariablesInLoop() {
        if (statementAnalysis.localVariablesAssignedInThisLoop == null) {
            return List.of(); // not for us
        }
        // part 3, iteration 1+: ensure local loop variable copies and their values

        if (!statementAnalysis.localVariablesAssignedInThisLoop.isFrozen()) {
            return null; // DELAY
        }
        List<Expression> expressionsToEvaluate = new ArrayList<>();
        statementAnalysis.localVariablesAssignedInThisLoop.stream().forEach(fqn -> {
            assert statement() instanceof LoopStatement;

            VariableInfoContainer vic = statementAnalysis.findForWriting(fqn); // must exist already

            // assign to local variable that has been created at Level 2 in this statement
            String newFqn = fqn + "$" + index(); // must be compatible with statementAnalysis.createLocalLoopCopyFQN
            if (!statementAnalysis.variables.isSet(newFqn)) {
                LocalVariableReference newLvr = createLocalCopyOfLoopVariable(vic, newFqn);
                String assigned = index() + VariableInfoContainer.Level.INITIAL;
                String read = index() + VariableInfoContainer.Level.EVALUATION;
                VariableInfoContainer newVic = VariableInfoContainerImpl.newLoopVariable(newLvr, assigned,
                        read,
                        NewObject.localVariableInLoop(statementAnalysis.primitives, newLvr.parameterizedType()),
                        vic.current().getProperties(),
                        new LinkedVariables(Set.of(vic.current().variable())),
                        new VariableInLoop(index(), null, VariableInLoop.VariableType.LOOP_COPY),
                        true);
                statementAnalysis.variables.put(newFqn, newVic);
            }
        });
        return expressionsToEvaluate;
    }

    private LocalVariableReference createLocalCopyOfLoopVariable(VariableInfoContainer vic, String newFqn) {
        Variable loopVariable = vic.current().variable();
        LocalVariable localVariable = new LocalVariable.Builder()
                .addModifier(LocalVariableModifier.FINAL)
                .setName(newFqn)
                .setParameterizedType(loopVariable.parameterizedType())
                .setOwningType(myMethodAnalyser.methodInfo.typeInfo)
                .setIsLocalCopyOf(loopVariable)
                .build();
        return new LocalVariableReference(analyserContext, localVariable, List.of());
    }

    private AnalysisStatus evaluationOfMainExpression(SharedState sharedState) {
        List<Expression> expressionsFromInitAndUpdate = initialisersAndUpdaters(sharedState);
        List<Expression> expressionsFromLocalVariablesInLoop = localVariablesInLoop();
        /*
        if we're in a loop statement and there are delays (localVariablesAssignedInThisLoop not frozen)
        we have to come back!
         */
        AnalysisStatus analysisStatus = expressionsFromLocalVariablesInLoop == null ? DELAYS : DONE;
        if (expressionsFromLocalVariablesInLoop != null) {
            expressionsFromInitAndUpdate.addAll(expressionsFromLocalVariablesInLoop);
        }

        Structure structure = statementAnalysis.statement.getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
            // try-statement has no main expression, and it may not have initialisers; break; continue; ...
            if (!statementAnalysis.stateData.valueOfExpressionIsSet()) {
                statementAnalysis.stateData.setValueOfExpression(EmptyExpression.EMPTY_EXPRESSION, false);
            }
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.copyTimeAfterExecutionFromInitialTime();
            }
            if (!statementAnalysis.methodLevelData.internalObjectFlows.isFrozen()) {
                statementAnalysis.methodLevelData.internalObjectFlows.freeze();
            }
            if (statementAnalysis.statement instanceof BreakStatement breakStatement) {
                StatementAnalysis.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(breakStatement);
                Expression state = sharedState.localConditionManager.stateUpTo(sharedState.evaluationContext, correspondingLoop.steps());
                boolean stateIsDelayed = sharedState.evaluationContext.isDelayed(state);
                correspondingLoop.statementAnalysis().stateData.addStateOfInterrupt(index(), state, stateIsDelayed);
                if (sharedState.evaluationContext.isDelayed(state)) return DELAYS;
            } else if (statement() instanceof LocalClassDeclaration localClassDeclaration) {
                EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext);
                PrimaryTypeAnalyser primaryTypeAnalyser =
                        localAnalysers.get().stream().filter(pta -> pta.primaryType == localClassDeclaration.typeInfo).findFirst().orElseThrow();
                builder.markVariablesFromPrimaryTypeAnalyser(primaryTypeAnalyser);
                return apply(sharedState, builder.build(), statementAnalysis);
            }
            return analysisStatus;
        }

        try {
            if (structure.expression() != EmptyExpression.EMPTY_EXPRESSION) {
                expressionsFromInitAndUpdate.add(structure.expression());
            }
            // Too dangerous to use CommaExpression.comma, because it filters out constants etc.!
            Expression toEvaluate = expressionsFromInitAndUpdate.size() == 1 ? expressionsFromInitAndUpdate.get(0) :
                    new CommaExpression(expressionsFromInitAndUpdate);
            EvaluationResult result = toEvaluate.evaluate(sharedState.evaluationContext, structure.forwardEvaluationInfo());
            if (statementAnalysis.flowData.timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterEvaluation(result.statementTime(), index());
            }

            AnalysisStatus statusPost = apply(sharedState, result, statementAnalysis).combine(analysisStatus);

            Expression value = result.value();
            boolean valueIsDelayed = sharedState.evaluationContext.isDelayed(value) || statusPost == DELAYS;

            if (statementAnalysis.statement instanceof ReturnStatement) {
                statusPost = step3_Return(sharedState, value, valueIsDelayed).combine(statusPost);
            } else if (statementAnalysis.statement instanceof ForEachStatement) {
                step3_ForEach(sharedState, value);
            } else if (!valueIsDelayed && (statementAnalysis.statement instanceof IfElseStatement ||
                    statementAnalysis.statement instanceof SwitchStatement ||
                    statementAnalysis.statement instanceof AssertStatement)) {
                value = step3_IfElse_Switch_Assert(sharedState, value);
            }

            // the value can be delayed even if it is "true", for example (Basics_3)
            boolean valueIsDelayed2 = sharedState.evaluationContext.isDelayed(value) || statusPost == DELAYS;
            statementAnalysis.stateData.setValueOfExpression(value, valueIsDelayed2);

            return statusPost;
        } catch (RuntimeException rte) {
            LOGGER.warn("Failed to evaluate main expression (step 3) in statement {}", statementAnalysis.index);
            throw rte;
        }
    }

    /*
    modify the value of the return variable according to the evaluation result and the current state

    consider
    if(x) return a;
    return b;

    after the if, the state is !x, and the return variable has the value x?a:<return>
    we should not immediately overwrite, but take the existing return value into account, and return x?a:b
     */
    private AnalysisStatus step3_Return(SharedState sharedState, Expression value, boolean valueIsDelayed) {
        ReturnVariable returnVariable = new ReturnVariable(myMethodAnalyser.methodInfo);
        Expression currentReturnValue = statementAnalysis.initialValueOfReturnVariable(returnVariable);
        // do NOT check for delays on currentReturnValue, we need to make the VIC

        ConditionManager localConditionManager = sharedState.localConditionManager;
        Expression newReturnValue;
        if (localConditionManager.isDelayed()) return DELAYS;
        // no state, or no previous return statements
        if (localConditionManager.state().isBoolValueTrue() || currentReturnValue instanceof UnknownExpression) {
            newReturnValue = value;
        } else if (myMethodAnalyser.methodInfo.returnType().equals(statementAnalysis.primitives.booleanParameterizedType)) {
            newReturnValue = new And(statementAnalysis.primitives).append(sharedState.evaluationContext, localConditionManager.state(),
                    value);
        } else {
            newReturnValue = EvaluateInlineConditional.conditionalValueConditionResolved(sharedState.evaluationContext,
                    localConditionManager.state(), value, currentReturnValue, ObjectFlow.NO_FLOW).getExpression();
        }
        boolean newReturnValueIsDelayed = sharedState.evaluationContext.isDelayed(newReturnValue);
        VariableInfoContainer vic = statementAnalysis.findForWriting(returnVariable);
        vic.ensureEvaluation(index() + VariableInfoContainer.Level.EVALUATION.label,
                VariableInfoContainer.NOT_YET_READ, VariableInfoContainer.NOT_A_VARIABLE_FIELD, Set.of());
        Map<VariableProperty, Integer> properties = sharedState.evaluationContext.getValueProperties(newReturnValue);
        vic.setValue(newReturnValue, newReturnValueIsDelayed, LinkedVariables.EMPTY, properties, false);
        LinkedVariables newLinkedVariables = sharedState.evaluationContext.linkedVariables(value);
        if (newLinkedVariables == LinkedVariables.DELAY) {
            log(DELAYED, "Delaying evaluation because of linked variables of return statement {} in {}",
                    index(), myMethodAnalyser.methodInfo.fullyQualifiedName);
            return DELAYS;
        }
        vic.setLinkedVariables(newLinkedVariables, false);
        return newReturnValueIsDelayed ? DELAYS : DONE;
    }

    // a special case, which allows us to set not null
    private void step3_ForEach(SharedState sharedState, Expression value) {
        if (sharedState.evaluationContext.getProperty(value, VariableProperty.NOT_NULL) >= MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL) {
            Structure structure = statementAnalysis.statement.getStructure();
            LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
            VariableInfoContainer vic = statementAnalysis.findForWriting(lvc.localVariable.name());
            vic.ensureEvaluation(VariableInfoContainer.NOT_YET_ASSIGNED, //index() + VariableInfoContainer.Level.EVALUATION.label,
                    VariableInfoContainer.NOT_YET_READ, VariableInfoContainer.NOT_A_VARIABLE_FIELD, Set.of());
            VariableInfo initial = vic.getPreviousOrInitial();
            vic.setValue(initial.getValue(), initial.isDelayed(), LinkedVariables.EMPTY, Map.of(), false);
            vic.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL, VariableInfoContainer.Level.EVALUATION);
            vic.setLinkedVariables(initial.getLinkedVariables(), false);
        }
    }

    private Expression step3_IfElse_Switch_Assert(SharedState sharedState, Expression value) {
        assert value != null;

        Expression evaluated = sharedState.localConditionManager.evaluate(sharedState.evaluationContext, value);

        boolean noEffect = evaluated.isConstant();

        if (noEffect && !statementAnalysis.stateData.statementContributesToPrecondition.isSet()) {
            String message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData.blocks.get();
            if (statementAnalysis.statement instanceof IfElseStatement) {
                message = Message.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = evaluated.isBoolValueTrue();
                    if (!isTrue) {
                        // important: we register the error on the IfElse rather than the first statement in the block, because that one
                        // really is excluded from all analysis
                        statementAnalysis.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo, firstStatement.index),
                                Message.UNREACHABLE_STATEMENT));
                    }
                    // guaranteed to be reached in block is always ALWAYS because it is the first statement
                    firstStatement.flowData.setGuaranteedToBeReachedInMethod(isTrue ? ALWAYS : NEVER);
                });
                if (blocks.size() == 2) {
                    blocks.get(1).ifPresent(firstStatement -> {
                        boolean isTrue = evaluated.isBoolValueTrue();
                        if (isTrue) {
                            // important: we register the error on the IfElse rather than the first statement in the block, because that one
                            // really is excluded from all analysis
                            statementAnalysis.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo, firstStatement.index),
                                    Message.UNREACHABLE_STATEMENT));
                        }
                        firstStatement.flowData.setGuaranteedToBeReachedInMethod(isTrue ? NEVER : ALWAYS);
                    });
                }
            } else if (statementAnalysis.statement instanceof AssertStatement) {
                boolean isTrue = evaluated.isBoolValueTrue();
                if (isTrue) {
                    message = Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE;
                } else {
                    message = Message.ASSERT_EVALUATES_TO_CONSTANT_FALSE;
                    Optional<StatementAnalysis> next = statementAnalysis.navigationData.next.get();
                    next.ifPresent(nextAnalysis -> {
                        nextAnalysis.flowData.setGuaranteedToBeReached(NEVER);
                        nextAnalysis.ensure(Message.newMessage(new Location(myMethodAnalyser.methodInfo, nextAnalysis.index),
                                Message.UNREACHABLE_STATEMENT));
                    });
                }
            } else {
                // switch
                message = Message.CONDITION_EVALUATES_TO_CONSTANT;
            }
            statementAnalysis.ensure(Message.newMessage(sharedState.evaluationContext.getLocation(), message));
            return evaluated;
        }
        return value;
    }

    private AnalysisStatus subBlocks(SharedState sharedState) {
        List<Optional<StatementAnalyser>> startOfBlocks = navigationData.blocks.get();
        AnalysisStatus analysisStatus = sharedState.localConditionManager.isDelayed() ? DELAYS : DONE;

        if (!startOfBlocks.isEmpty()) {
            analysisStatus = step4_haveSubBlocks(sharedState, startOfBlocks).combine(analysisStatus);
        } else {
            if (statementAnalysis.statement instanceof AssertStatement) {
                Expression assertion = statementAnalysis.stateData.getValueOfExpression();
                boolean expressionIsDelayed = statementAnalysis.stateData.valueOfExpressionIsDelayed();
                statementAnalysis.stateData.setPrecondition(assertion, expressionIsDelayed);

                if (!expressionIsDelayed) {
                    log(VARIABLE_PROPERTIES, "Assertion escape with precondition {}", assertion);
                    statementAnalysis.stateData.statementContributesToPrecondition.set();
                } else {
                    analysisStatus = DELAYS;
                }
            }
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.copyTimeAfterSubBlocksFromTimeAfterExecution();
            }
        }
        // fallback statement in case the local condition manager for next statement hasn't been set yet
        statementAnalysis.stateData.ensureLocalConditionManagerForNextStatement(sharedState.localConditionManager);
        return analysisStatus;
    }

    private record ExecutionOfBlock(FlowData.Execution execution,
                                    StatementAnalyser startOfBlock,
                                    ConditionManager conditionManager,
                                    Expression condition,
                                    boolean isDefault,
                                    LocalVariableCreation catchVariable) {

        public boolean escapesAlwaysButNotWithPrecondition() {
            if (execution != NEVER && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().statementAnalysis;
                return lastStatement.flowData.interruptStatus() == ALWAYS && !lastStatement.flowData.escapesViaException();
            }
            return false;
        }

        public boolean alwaysExecuted() {
            return execution == ALWAYS && startOfBlock != null;
        }
    }

    private AnalysisStatus step4_haveSubBlocks(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        List<ExecutionOfBlock> executions = step4a_determineExecution(sharedState, startOfBlocks);
        AnalysisStatus analysisStatus = DONE;


        int blocksExecuted = 0;
        for (ExecutionOfBlock executionOfBlock : executions) {
            if (executionOfBlock.startOfBlock != null) {
                if (executionOfBlock.execution != NEVER) {
                    StatementAnalyserResult result = executionOfBlock.startOfBlock.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                            new ForwardAnalysisInfo(executionOfBlock.execution, executionOfBlock.conditionManager, executionOfBlock.catchVariable),
                            evaluationContext.getClosure());
                    sharedState.builder.add(result);
                    analysisStatus = analysisStatus.combine(result.analysisStatus);
                    blocksExecuted++;
                } else {
                    // ensure that the first statement is unreachable
                    FlowData flowData = executionOfBlock.startOfBlock.statementAnalysis.flowData;
                    flowData.setGuaranteedToBeReachedInMethod(NEVER);

                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.ensure(Message.newMessage(getLocation(), Message.EMPTY_LOOP));
                    }
                }
            }
        }

        if (blocksExecuted > 0) {
            boolean atLeastOneBlockExecuted = atLeastOneBlockExecuted(executions);

            // note that isEscapeAlwaysExecuted cannot be delayed (otherwise, it wasn't ALWAYS?)
            List<StatementAnalysis.ConditionAndLastStatement> lastStatements = executions.stream()
                    .filter(ex -> ex.startOfBlock != null && !ex.startOfBlock.statementAnalysis.flowData.isUnreachable())
                    .map(ex -> new StatementAnalysis.ConditionAndLastStatement(ex.condition, ex.startOfBlock.lastStatement(),
                            ex.startOfBlock.lastStatement().isEscapeAlwaysExecutedInCurrentBlock() == Boolean.TRUE))
                    .collect(Collectors.toUnmodifiableList());

            int maxTime = lastStatements.stream()
                    .map(StatementAnalysis.ConditionAndLastStatement::lastStatement)
                    .mapToInt(sa -> sa.statementAnalysis.flowData.getTimeAfterSubBlocks())
                    .max().orElseThrow();

            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterSubBlocks(maxTime, index());
            }

            // need timeAfterSubBlocks set already
            statementAnalysis.copyBackLocalCopies(evaluationContext, sharedState.localConditionManager.state(), lastStatements,
                    atLeastOneBlockExecuted, maxTime);

            // compute the escape situation of the sub-blocks
            Expression addToStateAfterStatement = addToStateAfterStatement(evaluationContext, executions);
            if (!addToStateAfterStatement.isBoolValueTrue()) {
                ConditionManager newLocalConditionManeger = sharedState.localConditionManager
                        .newForNextStatementDoNotChangePrecondition(evaluationContext, addToStateAfterStatement);
                statementAnalysis.stateData.ensureLocalConditionManagerForNextStatement(newLocalConditionManeger);
                log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", addToStateAfterStatement);
            }
        } else {
            int maxTime = statementAnalysis.flowData.getTimeAfterEvaluation();
            if (statementAnalysis.flowData.timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData.setTimeAfterSubBlocks(maxTime, index());
            }
            statementAnalysis.copyBackLocalCopies(evaluationContext, sharedState.localConditionManager.state(), List.of(), false, maxTime);
        }
        return analysisStatus;
    }

    private boolean atLeastOneBlockExecuted(List<ExecutionOfBlock> list) {
        if (list.stream().anyMatch(ExecutionOfBlock::alwaysExecuted)) return true;
        // we have a default, and all conditions have code, and are possible
        return list.stream().anyMatch(e -> e.isDefault && e.startOfBlock != null) &&
                list.stream().allMatch(e -> e.execution == CONDITIONALLY && e.startOfBlock != null);
    }

    private Expression addToStateAfterStatement(EvaluationContext evaluationContext, List<ExecutionOfBlock> list) {
        BooleanConstant TRUE = new BooleanConstant(evaluationContext.getPrimitives(), true);
        if (statementAnalysis.statement instanceof IfElseStatement) {
            ExecutionOfBlock e0 = list.get(0);
            if (list.size() == 1) {
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    return Negation.negate(evaluationContext, list.get(0).condition);
                }
                return TRUE;
            }
            if (list.size() == 2) {
                ExecutionOfBlock e1 = list.get(1);
                boolean escape1 = e1.escapesAlwaysButNotWithPrecondition();
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    if (escape1) {
                        // both if and else escape
                        return new BooleanConstant(evaluationContext.getPrimitives(), false);
                    }
                    // if escapes
                    return list.get(1).condition;
                }
                if (escape1) {
                    // else escapes
                    return list.get(0).condition;
                }
                return TRUE;
            }
            throw new UnsupportedOperationException("Impossible, if {} else {} has 2 blocks maximum.");
        }
        // a switch statement has no primary block, only subStructures, one per SwitchEntry

        // make an And of NOTs for all those conditions where the switch entry escapes
        if (statementAnalysis.statement instanceof SwitchStatement) {
            Expression[] components = list.stream().filter(ExecutionOfBlock::escapesAlwaysButNotWithPrecondition).map(e -> e.condition).toArray(Expression[]::new);
            if (components.length == 0) return TRUE;
            return new And(evaluationContext.getPrimitives()).append(evaluationContext, components);
        }
        // TODO SwitchExpressions?

        /*
        loop statements: result should be !condition || <any exit of exactly this loop, no return> ...
        forEach loop does not have a condition.
         */
        if (statementAnalysis.statement instanceof LoopStatement &&
                !(statementAnalysis.statement instanceof ForEachStatement)) {
            List<Expression> ors = new ArrayList<>();
            statementAnalysis.stateData.statesOfInterruptsStream().forEach(stateOnInterrupt ->
                    ors.add(evaluationContext.replaceLocalVariables(stateOnInterrupt)));
            ors.add(evaluationContext.replaceLocalVariables(Negation.negate(evaluationContext, list.get(0).condition)));
            return new Or(statementAnalysis.primitives).append(evaluationContext, ors);
        }

        if (statementAnalysis.statement instanceof SynchronizedStatement && list.get(0).startOfBlock != null) {
            Expression lastState = list.get(0).startOfBlock.lastStatement().statementAnalysis.stateData.getConditionManagerForNextStatement().state();
            return evaluationContext.replaceLocalVariables(lastState);
        }
        return TRUE;
    }


    private List<ExecutionOfBlock> step4a_determineExecution(SharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        List<ExecutionOfBlock> executions = new ArrayList<>(startOfBlocks.size());

        Expression value = statementAnalysis.stateData.getValueOfExpression();
        boolean valueIsDelayed = statementAnalysis.stateData.valueOfExpressionIsDelayed();
        assert valueIsDelayed == sharedState.evaluationContext.isDelayed(value); // sanity check
        Structure structure = statementAnalysis.statement.getStructure();
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        // main block

        // some loops are never executed, and we can see that
        FlowData.Execution firstBlockStatementsExecution = structure.statementExecution().apply(value, evaluationContext);
        FlowData.Execution firstBlockExecution = statementAnalysis.flowData.execution(firstBlockStatementsExecution);

        executions.add(makeExecutionOfPrimaryBlock(sharedState.localConditionManager, firstBlockExecution, startOfBlocks, value,
                valueIsDelayed));

        for (int count = 1; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements().get(count - 1);
            Expression conditionForSubStatement;
            boolean conditionForSubStatementIsDelayed;

            boolean isDefault = false;
            FlowData.Execution statementsExecution = subStatements.statementExecution().apply(value, evaluationContext);
            if (statementsExecution == FlowData.Execution.DEFAULT) {
                isDefault = true;
                conditionForSubStatement = defaultCondition(evaluationContext, executions);
                conditionForSubStatementIsDelayed = evaluationContext.isDelayed(conditionForSubStatement);
                if (conditionForSubStatement.isBoolValueFalse()) statementsExecution = NEVER;
                else if (conditionForSubStatement.isBoolValueTrue()) statementsExecution = ALWAYS;
                else if (conditionForSubStatementIsDelayed) statementsExecution = DELAYED_EXECUTION;
                else statementsExecution = CONDITIONALLY;
            } else if (statementsExecution == ALWAYS) {
                conditionForSubStatement = new BooleanConstant(statementAnalysis.primitives, true);
                conditionForSubStatementIsDelayed = false;
            } else if (statementsExecution == NEVER) {
                conditionForSubStatement = null; // will not be executed anyway
                conditionForSubStatementIsDelayed = false;
            } else if (statement() instanceof TryStatement) { // catch
                conditionForSubStatement = NewObject.forCatchOrThis(statementAnalysis.primitives, statementAnalysis.primitives.booleanParameterizedType);
                conditionForSubStatementIsDelayed = false;
            } else if (statement() instanceof SwitchEntry switchEntry) {
                Expression constant = switchEntry.switchVariableAsExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT).value();
                conditionForSubStatement = Equals.equals(evaluationContext, value, constant, ObjectFlow.NO_FLOW);
                conditionForSubStatementIsDelayed = evaluationContext.isDelayed(conditionForSubStatement);
            } else throw new UnsupportedOperationException();

            FlowData.Execution execution = statementAnalysis.flowData.execution(statementsExecution);

            ConditionManager subCm = execution == NEVER ? null :
                    sharedState.localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives,
                            conditionForSubStatement, conditionForSubStatementIsDelayed);
            boolean inCatch = statement() instanceof TryStatement && !subStatements.initialisers().isEmpty(); // otherwise, it is finally
            LocalVariableCreation catchVariable = inCatch ? (LocalVariableCreation) subStatements.initialisers().get(0) : null;
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm,
                    conditionForSubStatement, isDefault, catchVariable));
        }

        return executions;
    }

    private ExecutionOfBlock makeExecutionOfPrimaryBlock(ConditionManager localConditionManager,
                                                         FlowData.Execution firstBlockExecution,
                                                         List<Optional<StatementAnalyser>> startOfBlocks,
                                                         Expression value,
                                                         boolean valueIsDelayed) {
        Structure structure = statementAnalysis.statement.getStructure();
        Expression condition;
        ConditionManager cm;
        if (firstBlockExecution == NEVER) {
            cm = null;
            condition = null;
        } else if (structure.expressionIsCondition()) {
            cm = localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives, value,
                    valueIsDelayed);
            condition = value;
        } else {
            cm = localConditionManager;
            condition = new BooleanConstant(statementAnalysis.primitives, true);
        }
        return new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, condition,
                false, null);
    }

    private Expression defaultCondition(EvaluationContext evaluationContext, List<ExecutionOfBlock> executions) {
        Primitives primitives = evaluationContext.getPrimitives();
        List<Expression> previousConditions = executions.stream().map(e -> e.condition).collect(Collectors.toList());
        if (previousConditions.isEmpty()) {
            return new BooleanConstant(primitives, true);
        }
        Expression[] negated = previousConditions.stream().map(c -> Negation.negate(evaluationContext, c))
                .toArray(Expression[]::new);
        return new And(primitives, ObjectFlow.NO_FLOW).append(evaluationContext, negated);
    }

    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li NYR + local variable created higher up + return: <code>int i=0; if(xxx) { i=3; return; }</code></li>
     *     <li>NYR + escape: <code>int i=0; if(xxx) { i=3; throw new UnsupportedOperationException(); }</code></li>
     * </ul>
     */
    private AnalysisStatus checkUselessAssignments() {
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData.bestAlwaysInterrupt();
        if (bestAlwaysInterrupt == InterruptsFlow.DELAYED) {
            log(DELAYED, "Delaying checking useless assignment in {}, because interrupt status unknown", index());
            return DELAYS;
        }
        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().getVariableInLoop() != VariableInLoop.COPY_FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> !(vi.variable() instanceof ReturnVariable)) // that's for the compiler!
                    .forEach(variableInfo -> {
                        if (variableInfo.notReadAfterAssignment()) {
                            boolean isLocalAndLocalToThisBlock = statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name());
                            if (bestAlwaysInterrupt == InterruptsFlow.ESCAPE ||
                                    isLocalAndLocalToThisBlock ||
                                    variableInfo.variable().isLocal() && bestAlwaysInterrupt == InterruptsFlow.RETURN &&
                                            localVariableAssignmentInThisBlock(variableInfo)) {
                                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.USELESS_ASSIGNMENT, variableInfo.name()));
                            }
                        }
                    });
        }
        return DONE;
    }

    private boolean localVariableAssignmentInThisBlock(VariableInfo variableInfo) {
        assert variableInfo.variable().isLocal();
        if (!variableInfo.isAssigned()) return false;
        return StringUtil.inSameBlock(variableInfo.getAssignmentId(), index());
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variables.stream()
                    .filter(e -> e.getValue().getStatementIndexOfThisLoopVariable() == null &&
                            e.getValue().getVariableInLoop() != VariableInLoop.COPY_FROM_ENCLOSING_METHOD)
                    .map(e -> e.getValue().current())
                    .filter(vi -> statementAnalysis.isLocalVariableAndLocalToThisBlock(vi.name()) && !vi.isRead())
                    .forEach(vi -> statementAnalysis.ensure(Message.newMessage(getLocation(),
                            Message.UNUSED_LOCAL_VARIABLE, vi.name())));
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedLoopVariables() {
        if (statement() instanceof LoopStatement && !statementAnalysis.containsMessage(Message.EMPTY_LOOP)) {
            statementAnalysis.variables.stream()
                    .filter(e -> index().equals(e.getValue().getStatementIndexOfThisLoopVariable()))
                    .forEach(e -> {
                        String loopVarFqn = e.getKey();
                        StatementAnalyser first = navigationData.blocks.get().get(0).orElse(null);
                        StatementAnalysis statementAnalysis = first == null ? null : first.lastStatement().statementAnalysis;
                        if (statementAnalysis == null || !statementAnalysis.variables.isSet(loopVarFqn) ||
                                !statementAnalysis.variables.get(loopVarFqn).current().isRead()) {
                            this.statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNUSED_LOOP_VARIABLE, loopVarFqn));
                        }
                    });
        }
        return DONE;
    }

    /*
     * Can be delayed
     */
    private AnalysisStatus checkUnusedReturnValueOfMethodCall() {
        if (statementAnalysis.statement instanceof ExpressionAsStatement eas && eas.expression instanceof MethodCall) {
            MethodCall methodCall = (MethodCall) (((ExpressionAsStatement) statementAnalysis.statement).expression);
            if (Primitives.isVoid(methodCall.methodInfo.returnType())) return DONE;
            MethodAnalysis methodAnalysis = getMethodAnalysis(methodCall.methodInfo);
            int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
            if (identity == Level.DELAY) return DELAYS;
            if (identity == Level.TRUE) return DONE;
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.FALSE) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
            }
        }
        return DONE;
    }

    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalyser(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
    }

    public List<StatementAnalyser> lastStatementsOfNonEmptySubBlocks() {
        return navigationData.blocks.get().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(sa -> !sa.statementAnalysis.flowData.isUnreachable())
                .map(StatementAnalyser::lastStatement)
                .collect(Collectors.toList());
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {
        private final boolean disableEvaluationOfMethodCallsUsingCompanionMethods;

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            this(iteration, conditionManager, closure, false);
        }

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager,
                                      EvaluationContext closure,
                                      boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            super(iteration, conditionManager, closure);
            this.disableEvaluationOfMethodCallsUsingCompanionMethods = disableEvaluationOfMethodCallsUsingCompanionMethods;
        }

        @Override
        public boolean allowedToIncrementStatementTime() {
            return !statementAnalysis.inSyncBlock;
        }

        @Override
        public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
            return getAnalyserContext().inAnnotatedAPIAnalysis() || disableEvaluationOfMethodCallsUsingCompanionMethods;
        }

        @Override
        public Set<String> allUnqualifiedVariableNames() {
            return statementAnalysis.allUnqualifiedVariableNames(analyserContext, getCurrentType());
        }

        @Override
        public int getIteration() {
            return iteration;
        }

        @Override
        public TypeInfo getCurrentType() {
            return myMethodAnalyser.methodInfo.typeInfo;
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return myMethodAnalyser;
        }

        @Override
        public ObjectFlow getObjectFlow(Variable variable, int statementTime) {
            return null;
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return StatementAnalyser.this;
        }

        @Override
        public Location getLocation() {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
        }

        @Override
        public Location getLocation(Expression expression) {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index, expression);
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return child(condition, false);
        }

        @Override
        public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            boolean conditionIsDelayed = isDelayed(condition);
            return new EvaluationContextImpl(iteration,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, conditionIsDelayed),
                    closure,
                    disableEvaluationOfMethodCallsUsingCompanionMethods);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty) {

            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof VariableExpression) {
                Variable variable = ((VariableExpression) value).variable();
                int v = statementAnalysis.getPropertyOfCurrent(variable, variableProperty);
                if (VariableProperty.NOT_NULL == variableProperty && notNullAccordingToConditionManager(variable)) {
                    return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL, v);
                }
                return v;
            }

            if (VariableProperty.NOT_NULL == variableProperty) {
                int directNN = value.getProperty(this, VariableProperty.NOT_NULL);
                if (directNN >= MultiLevel.EFFECTIVELY_NOT_NULL) return directNN;
                Expression valueIsNotNull = Negation.negate(this, Equals.equals(this,
                        value, NullConstant.NULL_CONSTANT, ObjectFlow.NO_FLOW, false));
                Expression evaluation = conditionManager.evaluate(this, valueIsNotNull);
                if (evaluation.isBoolValueTrue()) {
                    return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL, directNN);
                }
                return directNN;
            }

            // redirect to Value.getProperty()
            // this is the only usage of this method; all other evaluation of a Value in an evaluation context
            // must go via the current method
            return value.getProperty(this, variableProperty);

        }

        private boolean notNullAccordingToConditionManager(Variable variable) {
            Set<Variable> notNullVariablesInState = conditionManager.findIndividualNullInState(this, false);
            if (notNullVariablesInState.contains(variable)) return true;
            Set<Variable> notNullVariablesInCondition = conditionManager.findIndividualNullInCondition(this, false);
            return notNullVariablesInCondition.contains(variable);
        }

        private VariableInfo findForReading(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            if (closure != null && isNotMine(variable)) {
                return ((EvaluationContextImpl) closure).findForReading(variable, statementTime, isNotAssignmentTarget);
            }
            return statementAnalysis.initialValueForReading(variable, statementTime, isNotAssignmentTarget);
        }

        private boolean isNotMine(Variable variable) {
            return getCurrentType() != variable.getOwningType();
        }

        // we pass on the information about the potential newly created local variable copy
        @Override
        public Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            VariableInfo variableInfo = findForReading(variable, statementTime, isNotAssignmentTarget);
            Expression value = variableInfo.getValue();
            // important! do not use variable in the next statement, but vi.variable()
            // we could have redirected from a variable field to a local variable copy
            return value instanceof NewObject ?
                    new VariableExpression(variableInfo.variable(), variableInfo.getObjectFlow()) : value;
        }

        @Override
        public NewObject currentInstance(Variable variable, int statementTime) {
            VariableInfo variableInfo = findForReading(variable, statementTime, true);
            Expression value = variableInfo.getValue();

            // redirect to other variable
            if (value instanceof VariableExpression variableValue) {
                assert variableValue.variable() != variable :
                        "Variable " + variable.fullyQualifiedName() + " has been assigned a VariableValue value pointing to itself";
                return currentInstance(variableValue.variable(), statementTime);
            }
            if (value instanceof NewObject instance) return instance;
            return null;
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            VariableInfo vi = statementAnalysis.findOrThrow(variable);
            return vi.getProperty(variableProperty); // ALWAYS from the map!!!!
        }

        @Override
        public int getPropertyFromPreviousOrInitial(Variable variable, VariableProperty variableProperty, int statementTime) {
            VariableInfo vi = findForReading(variable, statementTime, true);
            return vi.getProperty(variableProperty);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return Stream.empty(); // TODO
        }

        @Override
        public LinkedVariables linkedVariables(Expression value) {
            assert value != null;
            if (value instanceof VariableExpression variableValue) {
                return linkedVariables(variableValue.variable());
            }
            return value.linkedVariables(this);
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
            boolean notSelf = typeInfo != getCurrentType();
            if (notSelf) {
                VariableInfo variableInfo = statementAnalysis.findOrThrow(variable);
                int immutable = variableInfo.getProperty(VariableProperty.IMMUTABLE);
                if (immutable == MultiLevel.DELAY) return LinkedVariables.DELAY;
                if (MultiLevel.isE2Immutable(immutable)) return LinkedVariables.EMPTY;
            }
            VariableInfo variableInfo = statementAnalysis.findOrThrow(variable);
            // we've encountered the variable before
            if (variableInfo.linkedVariablesIsSet()) {
                return variableInfo.getLinkedVariables().merge(new LinkedVariables(Set.of(variable)));
            }
            return LinkedVariables.DELAY; // delay
        }

        @Override
        public int getInitialStatementTime() {
            return statementAnalysis.flowData.getInitialTime();
        }

        @Override
        public int getFinalStatementTime() {
            return statementAnalysis.flowData.getTimeAfterSubBlocks();
        }

        /*
        go from local loop variable to instance when exiting a loop statement;
        both for merging and for the state

        non-null status to be copied from the variable; delays should/can work but need testing.
        A positive @NotNull is transferred, a positive @Nullable is replaced by a DELAY...
         */
        @Override
        public Expression replaceLocalVariables(Expression mergeValue) {
            if (statementAnalysis.statement instanceof LoopStatement) {
                Map<Expression, Expression> map = statementAnalysis.variables.stream()
                        .filter(e -> statementAnalysis.index.equals(e.getValue().getStatementIndexOfThisShadowVariable()))
                        .collect(Collectors.toUnmodifiableMap(
                                e -> new VariableExpression(e.getValue().current().variable()),
                                e -> NewObject.genericMergeResult(getPrimitives(), e.getValue().current())));
                return mergeValue.reEvaluate(this, map).value();
            }
            return mergeValue;
        }

        /*
        Need to translate local copies of fields into fields.
        Should we do only their first appearance? ($0)
         */
        @Override
        public Expression acceptAndTranslatePrecondition(Expression precondition) {
            if (precondition.isBooleanConstant()) return null;
            Map<Expression, Expression> translationMap = precondition.variables().stream()
                    .filter(v -> v instanceof LocalVariableReference lvr &&
                            lvr.variable.isLocalCopyOf() instanceof FieldReference)
                    .collect(Collectors.toUnmodifiableMap(VariableExpression::new,
                            v -> new VariableExpression(((LocalVariableReference) v).variable.isLocalCopyOf())));
            Expression translated;
            if (translationMap.isEmpty()) {
                translated = precondition;
            } else {
                // we need an evaluation context that simply translates, but does not interpret stuff
                EvaluationContext evaluationContext = new ConditionManager.EvaluationContextImpl(getPrimitives());
                translated = precondition.reEvaluate(evaluationContext, translationMap).getExpression();
            }
            if (translated.variables().stream()
                    .allMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference)) {
                return translated;
            }
            return null;
        }

        @Override
        public boolean isPresent(Variable variable) {
            return statementAnalysis.variables.isSet(variable.fullyQualifiedName());
        }

        @Override
        public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
            return localAnalysers.isSet() ? localAnalysers.get() : null;
        }

        @Override
        public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
            return statementAnalysis.variables.stream().filter(e -> e.getValue().current().variable() instanceof LocalVariableReference);
        }

        @Override
        public LinkedVariables getStaticallyAssignedVariables(Variable variable, int statementTime) {
            VariableInfo variableInfo = findForReading(variable, statementTime, true);
            return variableInfo.getStaticallyAssignedVariables();
        }
    }
}

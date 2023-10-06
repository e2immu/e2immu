package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.FlowDataConstants;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.Merge;
import org.e2immu.analyser.analyser.util.ComputeLinkedVariables;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.ConditionAndVariableInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.SwitchStatementOldStyle;
import org.e2immu.analyser.model.statement.TryStatement;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.analyser.Stage.MERGE;

class MergeVariables {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeVariables.class);

    private final StatementAnalysis statementAnalysis;
    private final String index;
    private final Statement statement;


    MergeVariables(StatementAnalysis statementAnalysis) {
        this.statementAnalysis = statementAnalysis;
        this.index = statementAnalysis.index();
        this.statement = statementAnalysis.statement();
    }

    /**
     * @param toMerge  variables which will go through the merge process, they will get a -M VariableInfo
     * @param toIgnore variables to be ignored by the merge process, they will not get a -M VariableInfo
     * @param toRemove references to these variables (in value, in scope of field ref) will be replaced by Instance objects
     */
    private record PrepareMerge(EvaluationContext evaluationContext,
                                List<VariableInfoContainer> toMerge,
                                List<VariableInfoContainer> toIgnore,
                                Set<Variable> toRemove,
                                TranslationMapImpl.Builder bestValueForToRemove,
                                Map<Variable, Variable> renames,
                                TranslationMapImpl.Builder translationMap) {
        PrepareMerge(EvaluationContext evaluationContext) {
            this(evaluationContext, new LinkedList<>(), new LinkedList<>(), new HashSet<>(), new TranslationMapImpl.Builder(),
                    new HashMap<>(), new TranslationMapImpl.Builder());
        }

        // we go over the list of variables to merge, and try to find if we need to rename them because
        // some scope has to be removed (field reference x.y where x cannot exist anymore).
        // this method relies on bestValueForToRemove
        // at the same time, when BOTH are present in the toMerge (in a subsequent iteration)
        // we remove the non-renamed
        // return new scope variables to be created and assigned
        private Set<LocalVariableReference> computeRenames() {
            TranslationMap renameMap = bestValueForToRemove.build();
            Set<LocalVariableReference> newScopeVariables = new HashSet<>();
            toMerge.removeIf(vic -> prepareRenameDecideToRemove(vic, renameMap, newScopeVariables));
            toIgnore.removeIf(vic -> prepareRenameDecideToRemove(vic, renameMap, newScopeVariables));
            return newScopeVariables;
        }

        private boolean prepareRenameDecideToRemove(VariableInfoContainer vic,
                                                    TranslationMap renameMap,
                                                    Set<LocalVariableReference> newScopeVariables) {
            Variable variable = vic.current().variable();
            RenameVariableResult rvr = renameVariable(variable, renameMap);
            if (rvr != null) {
                newScopeVariables.addAll(rvr.newScopeVariables);
                renames.put(variable, rvr.variable);
                translationMap.addVariableExpression(variable, rvr.variableExpression);
                translationMap.put(variable, rvr.variable);
                return false;
            }
            // scope variables that are created here, are filled in using renames
            return variable.hasScopeVariableCreatedAt(evaluationContext.statementIndex());
        }

        private record RenameVariableResult(Variable variable,
                                            VariableExpression variableExpression,
                                            List<LocalVariableReference> newScopeVariables) {
        }

        // serious example for this method is VariableScope_10; VS_6 shows that field references need to remain:
        // otherwise, we cannot see their assignment easily.
        private RenameVariableResult renameVariable(Variable variable, TranslationMap translationMap) {
            if (variable instanceof FieldReference fr) {
                Expression newScope = fr.scope.translate(evaluationContext.getAnalyserContext(), translationMap);
                if (newScope != fr.scope) {
                    assert fr.scopeVariable != null;
                    String name = "scope-" + fr.scopeVariable.simpleName() + ":" + evaluationContext.statementIndex();
                    // if statement index is 2, then 2~ is after 2.x.x, but before 3
                    VariableNature vn = new VariableNature.ScopeVariable(evaluationContext.statementIndex());
                    LocalVariable lv = new LocalVariable(Set.of(LocalVariableModifier.FINAL), name,
                            fr.scope.returnType(), List.of(), fr.getOwningType(), vn);
                    LocalVariableReference scopeVariable = new LocalVariableReference(lv, newScope);
                    Expression scope = new VariableExpression(fr.scope.getIdentifier(), scopeVariable);
                    FieldReference newFr = new FieldReference(evaluationContext.getAnalyserContext(), fr.fieldInfo,
                            scope, scopeVariable, fr.getOwningType());
                    VariableExpression ve = new VariableExpression(fr.scope.getIdentifier(), newFr,
                            VariableExpression.NO_SUFFIX, scope, null);
                    return new RenameVariableResult(newFr, ve, List.of(scopeVariable));
                }
                if (fr.scopeVariable instanceof FieldReference) {
                    RenameVariableResult rvr = renameVariable(fr.scopeVariable, translationMap);
                    if (rvr != null) {
                        throw new UnsupportedOperationException("Implement!");
                    }
                }
            }
            return null;
        }
    }

    // as a general remark: This and ReturnVariable variables are always merged, never removed
    private PrepareMerge mergeActions(EvaluationContext evaluationContext,
                                      List<ConditionAndLastStatement> lastStatements,
                                      Set<Variable> mergeEvenIfNotInSubBlocks) {
        PrepareMerge pm = new PrepareMerge(evaluationContext);
        // some variables will be picked up in the sub-blocks, others in the current block, others in both
        // we want to deal with them only once, though.
        Set<Variable> seen = new HashSet<>();
        Map<Variable, VariableInfoContainer> useVic = statementAnalysis.rawVariableStream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getValue().current().variable(), Map.Entry::getValue));

        // first group: variables seen in any of the sub blocks.
        // this includes those that do not exist at the top-level, i.e., they're local to the sub-blocks
        // they should never bubble up
        // variables in loop defined outside will be merged, and receive a special value in "replaceLocalVariables"
        // field references with a scope which needs to be removed, should equally be removed
        //
        // if recognized: remove (into remove+ignore), otherwise merge
        // we must add the VIC of the current statement, if it exists! If not we still cannot use the one from "below",
        // we must create a new one, but only if we're merging!
        Stream<VariableInfoContainer> fromSubBlocks = lastStatements.stream()
                .flatMap(st -> st.lastStatement().getStatementAnalysis().rawVariableStream().map(Map.Entry::getValue))
                .filter(vic -> vic.hasBeenAccessedInThisBlock(index));
        mergeAction(pm, seen, fromSubBlocks, vn -> vn.removeInSubBlockMerge(index), v -> true, useVic);

        // second group: those that exist at the block level, and were not present in any of the sub-blocks
        // (because of the "seen" map variables from the sub-blocks are ignored)
        //
        // should be removed and not merged: any variable created in this top level, such as
        // loop variables of for-each, for with LVC, try resource, instance-of pattern
        // variables in loop defined outside must have been accessed in the sub-block, by definition
        //
        // normal variables not accessed in the block, defined before the block, can be ignored
        //
        // if recognized: remove (into remove+ignore), otherwise ignore

        Set<Variable> alsoMergeStaticallyAssigned = new HashSet<>(mergeEvenIfNotInSubBlocks);
        Set<Variable> staticallyLinked = pm.toMerge.stream()
                .flatMap(vic -> vic.best(EVALUATION).getLinkedVariables().staticallyAssignedStream())
                .collect(Collectors.toUnmodifiableSet());
        alsoMergeStaticallyAssigned.addAll(staticallyLinked);

        Stream<VariableInfoContainer> atTopLevel = statementAnalysis.rawVariableStream().map(Map.Entry::getValue);
        mergeAction(pm, seen, atTopLevel, vn -> vn.removeInMerge(index),
                alsoMergeStaticallyAssigned::contains, null);
        return pm;
    }

    private void mergeAction(PrepareMerge prepareMerge,
                             Set<Variable> seen,
                             Stream<VariableInfoContainer> stream,
                             Predicate<VariableNature> remove,
                             Predicate<Variable> mergeWhenNotRemove,
                             Map<Variable, VariableInfoContainer> useVic) {
        stream.forEach(vic -> {
            Variable variable = vic.current().variable();
            // vicToAdd is null when there is no VIC at this level. In the case a merge, we'll need to create one,
            // and we can send on the current one. In the case of toIgnore, we must skip this variable!
            // see Loops_19 as an example (variable "key", to be ignored in statement "1")
            VariableInfoContainer vicToAdd = useVic == null ? vic : useVic.get(variable);
            if (seen.add(variable)) {
                if (remove.test(vic.variableNature())) {
                    if (vicToAdd != null) {
                        prepareMerge.toIgnore.add(vicToAdd);
                    }
                    prepareMerge.toRemove.add(variable);
                } else {
                    if (mergeWhenNotRemove.test(variable)) {
                        VariableInfoContainer vicMerge = vicToAdd == null ? vic : vicToAdd;
                        if (useVic != null && vicToAdd == null) {
                            // we'll copy from "vic"
                            vic.ensureCopyToMerge();
                        }
                        /*
                        See Loops_21: if the vic was copied from lastStatements, and it did not exist before, we must
                        ensure that a delayed initial gets updated when the initial of the lastStatements is done.
                         */
                        if (vic != vicToAdd
                                && vicToAdd != null
                                && vic.isCopyToMerge()
                                && vicToAdd.getPreviousOrInitial().getValue().isDelayed()
                                && !vic.getPreviousOrInitial().getValue().isDelayed()) {
                            vicToAdd.copyInitialFrom(vic.getPreviousOrInitial());
                        }
                        prepareMerge.toMerge.add(vicMerge);
                    } else if (vicToAdd != null) {
                        prepareMerge.toIgnore.add(vicToAdd);
                    }
                    /* The following code fragment replaces the variable expressions with loop suffix by a delayed or instance value.
                    For now, it seems not necessary to do this (see e.g. TrieSimplified_5)
                    if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside && outside.statementIndex().equals(index)) {
                        VariableExpression ve = new VariableExpression(variable, new VariableExpression.VariableInLoop(index));
                        VariableInfo best = vic.best(EVALUATION);
                        Expression value;
                        Properties valueProperties = best.valueProperties();
                        CausesOfDelay delays = valueProperties.delays();
                        if (best.getValue().isDelayed() || delays.isDelayed()) {
                            value = DelayedVariableExpression.forLocalVariableInLoop(variable, delays);
                        } else {
                            value = Instance.forLoopVariable(index, variable, valueProperties);
                        }
                        prepareMerge.translationMap.put(ve, value);
                    }*/
                }
            }
        });
    }

    record ConditionAndLastStatement(Expression condition,
                                            Expression absoluteState,
                                            String firstStatementIndexForOldStyleSwitch,
                                            StatementAnalyser lastStatement,
                                            DV executionOfLastStatement,
                                            boolean alwaysEscapes,
                                            boolean alwaysEscapesOrReturns) {
    }

    record MergeResult(ProgressAndDelay analysisStatus, Expression translatedAddToStateAfterMerge) {
    }

    /**
     * From child blocks into the parent block; determine the value and properties for the current statement
     *
     * @param evaluationContext       for expression evaluations
     * @param lastStatements          the last statement of each of the blocks
     * @param atLeastOneBlockExecuted true if we can (potentially) discard the current value
     * @param statementTime           the statement time of subBlocks
     * @param setCnnVariables         variables that should receive CNN >= ENN because of escape in sub-block
     */
    MergeResult mergeVariablesFromSubBlocks(EvaluationContext evaluationContext,
                                                   Expression stateOfConditionManagerBeforeExecution,
                                                   Expression addToStateAfterMerge,
                                                   List<ConditionAndLastStatement> lastStatements,
                                                   boolean atLeastOneBlockExecuted,
                                                   int statementTime,
                                                   Map<Variable, DV> setCnnVariables) {

        // we need to make a synthesis of the variable state of fields, local copies, etc.
        // some blocks are guaranteed to be executed, others are only executed conditionally.
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        PrepareMerge prepareMerge = mergeActions(evaluationContext, lastStatements, setCnnVariables.keySet());
        // 2 more steps: fill in PrepareMerge.bestValueForToRemove, then compute renames
        TranslationMapImpl.Builder outOfScopeBuilder = new TranslationMapImpl.Builder();
        Map<Variable, Expression> afterFiltering = new HashMap<>();
        for (Variable toRemove : prepareMerge.toRemove) {
            boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;
            List<ConditionAndVariableInfo> toMerge = filterSubBlocks(evaluationContext, lastStatements, toRemove,
                    inSwitchStatementOldStyle);
            if (!toMerge.isEmpty()) { // a try statement can have more than one, it's only the first we're interested
                // and finally, copy the result into prepareMerge
                VariableInfo best = toMerge.get(0).variableInfo();
                Identifier identifier = best.getIdentifier();
                Expression outOfScopeValue;
                org.e2immu.analyser.analyser.Properties bestProperties = best.valueProperties();
                if (best.getValue().isDelayed() || bestProperties.delays().isDelayed()) {
                    CausesOfDelay causes = best.getValue().causesOfDelay().merge(bestProperties.delays().causesOfDelay());
                    outOfScopeValue = DelayedExpression.forOutOfScope(identifier, toRemove.simpleName(),
                            toRemove.parameterizedType(),
                            best.getValue(),
                            causes);
                    afterFiltering.put(toRemove, outOfScopeValue);
                } else {
                    // at the moment, there may be references to other variables to be removed inside the best.getValue()
                    // they will be replaced soon in applyTranslations()
                    // on the other hand, we will already avoid self-references!
                    // NOTE: Instance is based on identifier and type

                    outOfScopeValue = Instance.forMerge(identifier, best.variable().parameterizedType(), bestProperties);
                    // the following rule works fine for VS_10, but may be too limited
                    // TODO should we loop over all VDOL's, add them to the translation map?
                    if (isVariableInLoopDefinedOutside(best.getValue())) {
                        afterFiltering.put(toRemove, outOfScopeValue);
                    } else {
                        afterFiltering.put(toRemove, best.getValue());
                    }
                }
                outOfScopeBuilder.addVariableExpression(best.variable(), outOfScopeValue);

            } // else: See e.g. Loops_3: block not executed
        }
        TranslationMap instances = outOfScopeBuilder.setRecurseIntoScopeVariables(true).build();
        for (Map.Entry<Variable, Expression> entry : afterFiltering.entrySet()) {
            Expression expression = entry.getValue();
            Variable toRemove = entry.getKey();
            Expression bestValue = expression.translate(evaluationContext.getAnalyserContext(), instances);
            prepareMerge.bestValueForToRemove.addVariableExpression(toRemove, bestValue);
            prepareMerge.translationMap.addVariableExpression(toRemove, bestValue);
            prepareMerge.translationMap.put(expression, bestValue);
        }

        Map<Variable, LinkedVariables> linkedVariablesMap = new HashMap<>();
        // fields never go out of scope, but their scope variables may. "computeRenames" will create
        // new field references, each with new scope variables that also don't go out of scope anymore.
        // the same applies to dependent variables
        Set<LocalVariableReference> newScopeVariables = prepareMerge.computeRenames();
        for (LocalVariableReference lvr : newScopeVariables) {
            VariableInfoContainer newScopeVar;
            if (!statementAnalysis.variableIsSet(lvr.fullyQualifiedName())) {
                newScopeVar = statementAnalysis.createVariable(evaluationContext, lvr,
                        statementAnalysis.statementTime(MERGE),
                        new VariableNature.ScopeVariable(evaluationContext.statementIndex()));
                newScopeVar.ensureMerge(evaluationContext.getLocation(MERGE),
                        evaluationContext.statementIndex() + ":M");
            } else {
                newScopeVar = statementAnalysis.getVariable(lvr.fullyQualifiedName());
            }
            org.e2immu.analyser.analyser.Properties propertiesOfLvr = evaluationContext.getValueProperties(lvr.assignmentExpression);
            Expression scopeValue;
            CausesOfDelay causesOfDelay = propertiesOfLvr.delays();
            if (causesOfDelay.isDelayed() && lvr.assignmentExpression.isDone()) {
                scopeValue = DelayedExpression.forDelayedValueProperties(lvr.assignmentExpression.getIdentifier(),
                        lvr.assignmentExpression.returnType(), lvr.assignmentExpression, causesOfDelay, org.e2immu.analyser.analyser.Properties.EMPTY);
            } else {
                scopeValue = lvr.assignmentExpression;
            }
            newScopeVar.setValue(scopeValue, null, propertiesOfLvr, MERGE);
            groupPropertyValues.setDefaultsForScopeVariable(lvr);
        }
        TranslationMap translationMap = prepareMerge.translationMap.build();

        Set<Variable> variablesWhereMergeOverwrites = new HashSet<>();

        CausesOfDelay delay = CausesOfDelay.EMPTY;
        boolean progress = false;
        Map<Variable, Integer> modificationTimes = new HashMap<>();

        for (VariableInfoContainer vic : prepareMerge.toMerge) {
            VariableInfo current = vic.current();
            Variable variable = current.variable();
            assert !variable.hasScopeVariableCreatedAt(index) : "should have been removed";
            Variable renamed = prepareMerge.renames.getOrDefault(variable, variable);

            VariableInfoContainer destination;
            if (!statementAnalysis.variableIsSet(renamed.fullyQualifiedName())) {
                VariableNature variableNature;
                if (renamed instanceof FieldReference fr) {
                    if (fr.scope.isDelayed()) {
                        variableNature = new VariableNature.DelayedScope();
                    } else {
                        variableNature = vic.variableNature();
                    }
                } else {
                    variableNature = vic.variableNature();
                }
                destination = statementAnalysis.createVariable(evaluationContext, renamed, statementTime, variableNature);
            } else if (variable == renamed) {
                destination = vic;
            } else {
                destination = statementAnalysis.getVariable(renamed.fullyQualifiedName());
            }
            if (destination.variableNature() instanceof VariableNature.DelayedScope ds) {
                ds.setIteration(evaluationContext.getIteration());
            }
            boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;

            Merge.ExpressionAndProperties overwriteValue = overwrite(evaluationContext, variable);
            assert overwriteValue == null || renamed == variable : "Overwrites do not go together with renames?";

            // use "variable" here rather than "renamed", this deals with data in the sub-blocks
            boolean localAtLeastOneBlock;
            List<ConditionAndVariableInfo> toMerge = filterSubBlocks(evaluationContext, lastStatements, variable,
                    inSwitchStatementOldStyle);
            if (variable instanceof ReturnVariable) {
                int sum = lastStatements.stream().mapToInt(cav -> cav.alwaysEscapes() ? 1 : 0).sum();
                localAtLeastOneBlock = atLeastOneBlockExecuted && lastStatements.size() == toMerge.size() + sum;
            } else if (variable instanceof DependentVariable dv
                    && dv.statementIndex.startsWith(index + ".")) {
                localAtLeastOneBlock = true; // the dependent variable was dynamically created inside one of the blocks
            } else {
                // in a Try statement, the blocks are successive rather than exclusive
                int sum = lastStatements.stream().mapToInt(cav -> cav.alwaysEscapesOrReturns() ? 1 : 0).sum();
                localAtLeastOneBlock = atLeastOneBlockExecuted && (statement instanceof TryStatement ||
                        lastStatements.size() == toMerge.size() + sum);
            }
            if (toMerge.size() > 0) {
                try {
                    CausesOfDelay executionDelay = toMerge.stream().map(cavi -> cavi.executionOfLastStatement().causesOfDelay())
                            .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
                    Merge merge = new Merge(evaluationContext, destination, executionDelay);

                    // the main merge operation
                    ProgressAndDelay pad = merge.merge(stateOfConditionManagerBeforeExecution, overwriteValue,
                            localAtLeastOneBlock, toMerge, groupPropertyValues, translationMap);
                    delay = delay.merge(pad.causes());
                    progress |= pad.progress();

                    Stream<LinkedVariables> toMergeLvStream = toMerge.stream().map(cav -> cav.variableInfo().getLinkedVariables());
                    Stream<LinkedVariables> lvStream;
                    if (localAtLeastOneBlock) {
                        lvStream = toMergeLvStream;
                    } else {
                        LinkedVariables previousLv = vic.best(EVALUATION).getLinkedVariables();
                        lvStream = Stream.concat(Stream.of(previousLv), toMergeLvStream);
                    }
                    LinkedVariables linkedVariables = lvStream
                            .map(lv -> lv.translate(evaluationContext.getAnalyserContext(), translationMap))
                            .reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
                    if (executionDelay.isDelayed()) {
                        linkedVariablesMap.put(renamed, linkedVariables.changeNonStaticallyAssignedToDelay(executionDelay));
                    } else {
                        linkedVariablesMap.put(renamed, linkedVariables);
                    }
                    if (localAtLeastOneBlock) variablesWhereMergeOverwrites.add(renamed);

                    if (localAtLeastOneBlock &&
                            checkForOverwritingPreviousAssignment(variable, current, vic.variableNature(), toMerge)) {
                        assert variable == renamed : "Overwriting previous assignments doesn't go together with renames";
                        statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(MERGE),
                                Message.Label.OVERWRITING_PREVIOUS_ASSIGNMENT, variable.simpleName()));
                    }
                    IntStream modificationTimeOfSubs = toMerge.stream().mapToInt(cav -> cav.variableInfo().getModificationTimeOrNegative());
                    int maxMod = modificationTimeOfSubs.reduce(0, (t1, t2) -> t1 < 0 || t2 < 0 ? -1 : Math.max(t1, t2));
                    modificationTimes.put(variable, maxMod);
                } catch (Throwable throwable) {
                    LOGGER.warn("Caught exception while merging variable {} (rename to {}} in {}, {}", variable, renamed,
                            statementAnalysis.methodAnalysis().getMethodInfo().fullyQualifiedName, index);
                    throw throwable;
                }
            } else if (destination.hasMerge()) {
                assert evaluationContext.getIteration() > 0; // or it wouldn't have had a merge
                // in previous iterations there was data for us, but now there isn't; copy from I/E into M
                progress |= destination.copyFromEvalIntoMerge(groupPropertyValues);
                linkedVariablesMap.put(renamed, destination.best(MERGE).getLinkedVariables());
            } // else: see e.g. Lambda_19Merge; for now no reason to do anything more
        }

        statementAnalysis.rawVariableStream().forEach(e -> {
            if (e.getValue().variableNature() instanceof VariableNature.DelayedScope ds && ds.getIteration() < evaluationContext.getIteration()) {
                LOGGER.debug("Removing {}, not actively merged anymore", e.getKey());
                e.getValue().remove();
            }
        });

        CausesOfDelay conditionCauses = lastStatements.stream().map(cav -> cav.condition.causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        ProgressAndDelay soFar = new ProgressAndDelay(progress, delay);
        ProgressAndDelay mergeStatus = linkingAndGroupProperties(evaluationContext, groupPropertyValues, linkedVariablesMap,
                modificationTimes, statementTime,
                variablesWhereMergeOverwrites, newScopeVariables, prepareMerge, setCnnVariables, translationMap,
                conditionCauses, soFar).addProgress(progress);

        Expression translatedAddToState = addToStateAfterMerge == null ? null :
                addToStateAfterMerge.translate(evaluationContext.getAnalyserContext(), translationMap);
        return new MergeResult(mergeStatus, translatedAddToState);
    }

    private boolean isVariableInLoopDefinedOutside(Expression value) {
        IsVariableExpression ive;
        if ((ive = value.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfoContainer vic = statementAnalysis.findOrNull(ive.variable());
            return vic != null && index.equals(vic.variableNature().getStatementIndexOfBlockVariable());
        }
        return false;
    }

    /**
     * In some rare situations we do not want to merge, but to write a specific value.
     * This is the case when the exit value of a loop is known; see Range_3
     */
    private Merge.ExpressionAndProperties overwrite(EvaluationContext evaluationContext, Variable variable) {
        if (statement instanceof LoopStatement) {
            Expression exit = statementAnalysis.rangeData().getRange().exitValue(statementAnalysis.primitives(), variable);
            if (exit != null && statementAnalysis.stateData().noExitViaReturnOrBreak()) {
                Properties properties = evaluationContext.getValueProperties(exit);
                return new Merge.ExpressionAndProperties(exit, properties);
            }
        }
        return null;
    }

    private List<ConditionAndVariableInfo> filterSubBlocks(EvaluationContext evaluationContext,
                                                           List<ConditionAndLastStatement> lastStatements,
                                                           Variable variable,
                                                           boolean inSwitchStatementOldStyle) {
        String fqn = variable.fullyQualifiedName();
        return lastStatements.stream()
                .filter(e2 -> e2.lastStatement().getStatementAnalysis().variableIsSet(fqn))
                .map(e2 -> {
                    VariableInfoContainer vic2 = e2.lastStatement().getStatementAnalysis().getVariable(fqn);
                    return new ConditionAndVariableInfo(e2.condition(), e2.absoluteState(),
                            vic2.current(), e2.alwaysEscapes(), e2.alwaysEscapesOrReturns(),
                            vic2.variableNature(), e2.firstStatementIndexForOldStyleSwitch(),
                            e2.lastStatement().getStatementAnalysis().index(),
                            index,
                            e2.lastStatement().getStatementAnalysis(),
                            e2.executionOfLastStatement,
                            variable,
                            evaluationContext);
                })
                .filter(cav -> acceptVariableForMerging(cav, inSwitchStatementOldStyle)).toList();
    }

    private boolean acceptVariableForMerging(ConditionAndVariableInfo cav, boolean inSwitchStatementOldStyle) {
        if (inSwitchStatementOldStyle) {
            assert cav.firstStatementIndexForOldStyleSwitch() != null;
            // if the variable is assigned in the block, it has to be assigned after the first index
            // "the block" is the switch statement; otherwise,
            String cavLatest = cav.variableInfo().getAssignmentIds().getLatestAssignmentIndex();
            if (cavLatest.compareTo(index) > 0) {
                return cav.firstStatementIndexForOldStyleSwitch().compareTo(cavLatest) <= 0;
            }
            return cav.firstStatementIndexForOldStyleSwitch().compareTo(cav.variableInfo().getReadId()) <= 0;
        }
        return cav.variableInfo().isRead() || cav.variableInfo().isAssigned();
    }

    private record BackLinkForEachResult(Set<Variable> newlyCreated, CausesOfDelay delays) {
    }

    private ProgressAndDelay linkingAndGroupProperties(EvaluationContext evaluationContext,
                                                       GroupPropertyValues groupPropertyValues,
                                                       Map<Variable, LinkedVariables> linkedVariablesMap,
                                                       Map<Variable, Integer> modificationTimes,
                                                       int statementTime,
                                                       Set<Variable> variablesWhereMergeOverwrites,
                                                       Set<LocalVariableReference> newlyCreatedScopeVariables,
                                                       PrepareMerge prepareMerge,
                                                       Map<Variable, DV> setCnnVariables,
                                                       TranslationMap translationMap,
                                                       CausesOfDelay conditionCauses,
                                                       ProgressAndDelay delay) {

        BackLinkForEachResult backLink;
        if (statement instanceof ForEachStatement) {
            backLink = backLinkIterable(evaluationContext, linkedVariablesMap);
        } else {
            backLink = new BackLinkForEachResult(Set.of(), CausesOfDelay.EMPTY);
        }
        for (VariableInfoContainer vic : prepareMerge.toIgnore) {
            Variable variable = vic.current().variable();
            Variable renamed = prepareMerge.renames.get(variable);
            if (renamed != null) {
                ensureDestination(renamed, vic, evaluationContext, statementAnalysis.statementTime(MERGE));
            }
        }

        // then, per cluster of variables
        // which variables should we consider? linkedVariablesMap provides the linked variables from the sub-blocks
        // create looks at these+previous, minus those to be removed.
        Function<Variable, LinkedVariables> linkedVariablesFromBlocks =
                v -> linkedVariablesMap.getOrDefault(v, LinkedVariables.EMPTY);
        // we include -E in touched, see Basics_8 (j, k in statement 4)
        Set<Variable> touched = touchedStream(linkedVariablesMap, newlyCreatedScopeVariables, prepareMerge);
        boolean oneBranchHasBecomeUnreachable = oneBranchHasBecomeUnreachable();
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(statementAnalysis, MERGE,
                oneBranchHasBecomeUnreachable,
                (vic, v) -> !touched.contains(v),
                variablesWhereMergeOverwrites,
                linkedVariablesFromBlocks, evaluationContext);

        boolean progress = computeLinkedVariables.writeLinkedVariables(computeLinkedVariables, touched,
                prepareMerge.toRemove, linkedVariablesMap.keySet());

        for (Variable variable : touched) {
            if ((!linkedVariablesMap.containsKey(variable) || backLink.newlyCreated.contains(variable)) &&
                    !(variable instanceof LocalVariableReference lvr && newlyCreatedScopeVariables.contains(lvr))) {
                VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
                Variable renamed = prepareMerge.renames.get(variable);
                if (renamed != null) {
                    // copy from vic into the renamed variable
                    VariableInfoContainer vicRenamed = statementAnalysis.getVariable(renamed.fullyQualifiedName());
                    progress |= vic.copyNonContextFromPreviousOrEvalToMergeOfOther(groupPropertyValues, vicRenamed);
                } else {
                    progress |= vic.copyNonContextFromPreviousOrEvalToMerge(groupPropertyValues);
                }
            }
        }
        HashSet<VariableInfoContainer> ignoredNotTouched = new HashSet<>(prepareMerge.toIgnore);
        ignoredNotTouched.removeIf(vic -> touched.contains(vic.current().variable())
                || prepareMerge.renames.containsKey(vic.current().variable()));

        CausesOfDelay externalDelaysOnIgnoredVariables = CausesOfDelay.EMPTY;
        for (VariableInfoContainer vic : ignoredNotTouched) {
            CausesOfDelay delays = vic.copyAllFromPreviousOrEvalIntoMergeIfMergeExists();
            externalDelaysOnIgnoredVariables = externalDelaysOnIgnoredVariables.merge(delays);
            // IMPORTANT: no progress associated with this---is that correct?
        }

        if (translationMap.hasVariableTranslations()) {
            groupPropertyValues.translate(evaluationContext.getAnalyserContext(), translationMap);
        }

        ProgressAndDelay ennStatus = computeLinkedVariables.write(EXTERNAL_NOT_NULL,
                groupPropertyValues.getMap(EXTERNAL_NOT_NULL));

        Map<Variable, DV> cnnMap = groupPropertyValues.getMap(CONTEXT_NOT_NULL);
        for (Map.Entry<Variable, DV> e : setCnnVariables.entrySet()) {
            cnnMap.merge(e.getKey(), e.getValue(), DV::max);
        }
        ProgressAndDelay cnnStatus = computeLinkedVariables.write(CONTEXT_NOT_NULL, cnnMap);

        ProgressAndDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        ProgressAndDelay extContStatus = computeLinkedVariables.write(CONTAINER_RESTRICTION,
                groupPropertyValues.getMap(CONTAINER_RESTRICTION));

        ProgressAndDelay extIgnModStatus = computeLinkedVariables.write(EXTERNAL_IGNORE_MODIFICATIONS,
                groupPropertyValues.getMap(EXTERNAL_IGNORE_MODIFICATIONS));

        // extra delay.causes(): before we know whether branches get excluded, we cannot decide
        ProgressAndDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE,
                groupPropertyValues.getMap(CONTEXT_IMMUTABLE));

        ProgressAndDelay cContStatus = computeLinkedVariables.write(CONTEXT_CONTAINER,
                groupPropertyValues.getMap(CONTEXT_CONTAINER));

        int statementTimeDelta = statementTime - statementAnalysis.statementTime(EVALUATION);
        ProgressAndDelay cmStatus = computeLinkedVariables.writeContextModified(evaluationContext.getAnalyserContext(),
                groupPropertyValues.getMap(CONTEXT_MODIFIED), Map.of(), statementTimeDelta, modificationTimes,
                conditionCauses, true);

        CausesOfDelay delaysOfValuesOfIgnored = prepareMerge.toIgnore.stream()
                .map(vic -> vic.best(MERGE).getValue().causesOfDelay()).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        return delay
                .combine(backLink.delays)
                .combine(ennStatus).combine(cnnStatus).combine(cmStatus).combine(extImmStatus)
                .combine(extContStatus).combine(cImmStatus).combine(cContStatus).combine(extIgnModStatus)
                .merge(externalDelaysOnIgnoredVariables)
                .merge(delaysOfValuesOfIgnored)
                .addProgress(progress);
    }

    /*
    Example code: 'for(T t: ts) { consumer.accept(t); }'
    t links to consumer:3, ts:3, we want to connect consumer and ts with :4

    See Independent1_4.
    The reason we need this code is that links are unidirectional, and the links from t will disappear as the loop var
    goes out of scope.
    The reason we need this code HERE is that we must have the delay to consumer:-1 from the very first iteration.
    We cannot add it during evaluationOfForEachVariable() in the first iteration, as we don't know which variable to link to.
     */
    private BackLinkForEachResult backLinkIterable(EvaluationContext evaluationContext,
                                                   Map<Variable, LinkedVariables> linkedVariablesMap) {

        Set<Variable> newToLinkedVariablesMap = new HashSet<>();
        CausesOfDelay causes = CausesOfDelay.EMPTY;

        StatementAnalysis first = statementAnalysis.navigationData().blocks.get().get(0).orElse(null);
        StatementAnalysis last = first == null ? null : first.lastStatement();
        if (last != null) {
            Expression initialiser = statement.getStructure().initialisers().get(0);
            if (initialiser instanceof LocalVariableCreation lvc) {
                assert lvc.hasSingleDeclaration();
                Variable loopVar = lvc.localVariableReference;
                VariableInfo latestVariableInfo = last.getLatestVariableInfo(loopVar.fullyQualifiedName());
                if (latestVariableInfo != null) {
                    LinkedVariables linkedOfLoopVar = latestVariableInfo.getLinkedVariables();
                    // any --3--> link to a variable not local to the loop, we also point to the iterable as <--4-->

                    EvaluationResult context = EvaluationResultImpl.from(evaluationContext);
                    LinkedVariables linkedVariables = statementAnalysis.stateData().valueOfExpressionGet()
                            .linkedVariables(context);

                    for (Map.Entry<Variable, DV> lve : linkedVariables) {
                        Variable iterableVar = lve.getKey();

                        for (Map.Entry<Variable, DV> e : linkedOfLoopVar) {
                            Variable targetOfLoopVar = e.getKey();
                            if (!targetOfLoopVar.equals(iterableVar)) {
                                if (!linkedVariablesMap.containsKey(targetOfLoopVar)) {
                                    newToLinkedVariablesMap.add(targetOfLoopVar);
                                }
                                if (!linkedVariablesMap.containsKey(iterableVar)) {
                                    newToLinkedVariablesMap.add(iterableVar);
                                }
                                if (e.getValue().isDelayed()) {
                                    link(linkedVariablesMap, iterableVar, targetOfLoopVar, e.getValue());
                                    link(linkedVariablesMap, targetOfLoopVar, iterableVar, e.getValue());
                                    causes = causes.merge(e.getValue().causesOfDelay());
                                } else if (LinkedVariables.LINK_IS_HC_OF.equals(e.getValue())) {
                                    link(linkedVariablesMap, iterableVar, targetOfLoopVar, LinkedVariables.LINK_COMMON_HC);
                                    link(linkedVariablesMap, targetOfLoopVar, iterableVar, LinkedVariables.LINK_COMMON_HC);
                                }
                            }
                        }
                    }
                } else {
                    LOGGER.debug("possible in iteration 0 of a loop, see e.g. Loops_3");
                }
            } else throw new UnsupportedOperationException("?? expect lvc, got " + initialiser);
        }
        return new BackLinkForEachResult(Set.copyOf(newToLinkedVariablesMap), causes);
    }

    private void link(Map<Variable, LinkedVariables> linkedVariablesMap, Variable from, Variable to, DV linkLevel) {
        LinkedVariables lv = linkedVariablesMap.get(from);
        if (lv == null) {
            linkedVariablesMap.put(from, LinkedVariables.of(to, linkLevel));
        } else {
            linkedVariablesMap.put(from, lv.merge(LinkedVariables.of(to, linkLevel)));
        }
    }

    /*
    actually: at least one branch, this is possible in switch statements. Generally, it'll be the one of the two arms
    in an "if-else" construct, or the single branch of an "if".
     */
    private boolean oneBranchHasBecomeUnreachable() {
        if (statementAnalysis.navigationData().hasSubBlocks()) {
            return statementAnalysis.navigationData().blocks.get().stream()
                    .anyMatch(optSa -> optSa.isPresent() && optSa.get().flowData().isUnreachable());
        }
        return false;
    }

    private Set<Variable> touchedStream(Map<Variable, LinkedVariables> linkedVariablesMap, Set<? extends Variable> newlyCreatedScopeVariables, PrepareMerge prepareMerge) {
        Stream<Variable> currentVariableStream = statementAnalysis.variableInfoContainerStream()
                .filter(vic -> vic.hasEvaluation() ||
                        // the following condition is necessary to include fields with a scope in
                        // newlyCreatedScopeVariables, see e.g. InstanceOf_16
                        vic.current().variable().containsAtLeastOneOf(newlyCreatedScopeVariables))
                .map(e -> e.current().variable());
        Stream<Variable> linkedVariableStream = linkedVariablesMap.values().stream()
                .flatMap(lv -> lv.variables().keySet().stream());
        return Stream.concat(newlyCreatedScopeVariables.stream(), Stream.concat(
                        Stream.concat(linkedVariablesMap.keySet().stream(), linkedVariableStream),
                        currentVariableStream))
                .filter(v -> !prepareMerge.toRemove.contains(v)
                        && !prepareMerge.renames.containsKey(v)
                        && statementAnalysis.variableIsSet(v.fullyQualifiedName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void ensureDestination(Variable renamed,
                                   VariableInfoContainer vic,
                                   EvaluationContext evaluationContext,
                                   int statementTime) {
        if (!statementAnalysis.variableIsSet(renamed.fullyQualifiedName())) {
            VariableNature variableNature;
            if (renamed instanceof FieldReference fr) {
                if (fr.scope.isDelayed()) {
                    variableNature = new VariableNature.DelayedScope();
                } else {
                    variableNature = vic.variableNature();
                }
            } else {
                variableNature = vic.variableNature();
            }
            statementAnalysis.createVariable(evaluationContext, renamed, statementTime, variableNature);
        }
    }

    private boolean checkForOverwritingPreviousAssignment(Variable variable,
                                                          VariableInfo initial,
                                                          VariableNature variableNature,
                                                          List<ConditionAndVariableInfo> toMerge) {
        String fqn = variable.fullyQualifiedName();
        if (!(variable instanceof LocalVariableReference)) return false;
        if (variableNature instanceof VariableNature.LoopVariable ||
                variableNature instanceof VariableNature.Pattern) return false;
        if (initial.notReadAfterAssignment(index)) {
            // so now we know it is a local variable, it has been assigned to outside the sub-blocks, but not yet read
            int countAssignments = 0;
            for (ConditionAndVariableInfo cav : toMerge) {
                VariableInfoContainer localVic = cav.lastStatement().getVariableOrDefaultNull(fqn);
                if (localVic != null) {
                    VariableInfo current = localVic.current();
                    if (!current.isAssigned()) {
                        if (!current.isRead()) continue;
                        return false;
                    }
                    String assignmentIndex = current.getAssignmentIds().getLatestAssignmentIndex();
                    if (assignmentIndex.compareTo(index) < 0) continue;
                    String earliestAssignmentIndex = current.getAssignmentIds().getEarliestAssignmentIndex();
                    if (earliestAssignmentIndex.compareTo(index) < 0) {
                        // some branch is still relying on the earlier value
                        return false;
                    }

                    countAssignments++;
                    StatementAnalysis sa = statementAnalysis.navigateTo(assignmentIndex);
                    assert sa != null;
                    if (!sa.flowData().getGuaranteedToBeReachedInCurrentBlock().equals(FlowDataConstants.ALWAYS))
                        return false;
                    if (current.isRead()) {
                        if (current.getReadId().compareTo(current.getAssignmentIds().getLatestAssignment()) < 0) {
                            return false;
                        }
                        // so there is reading AFTER... but
                        // we'll need to double-check that there was no reading before the assignment!
                        // secondly, we want to ensure that the assignment takes place unconditionally in the block

                        VariableInfoContainer atAssignment = sa.getVariable(fqn);
                        VariableInfo vi1 = atAssignment.current();
                        assert vi1.isAssigned();
                        // <= here instead of <; solves e.g. i+=1 (i = i + 1, read first, then assigned, same stmt)
                        if (vi1.isRead() && vi1.getReadId().compareTo(vi1.getAssignmentIds().getLatestAssignment()) <= 0) {
                            return false;
                        }

                        // else: assignment was before this merge... no bother; any reading will be after or not our problem
                    }
                }
            }
            return countAssignments > 0; // if not assigned, not read... just ignore
        }
        return false;
    }
}
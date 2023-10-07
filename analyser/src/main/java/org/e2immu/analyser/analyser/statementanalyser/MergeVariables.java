package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.util.ComputeLinkedVariables;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.ConditionAndVariableInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.SwitchStatementOldStyle;
import org.e2immu.analyser.model.variable.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

        PrepareMerge prepareMerge = mergeActions(evaluationContext, lastStatements, setCnnVariables.keySet());
        // 2 more steps: fill in PrepareMerge.bestValueForToRemove, then compute renames
        TranslationMapImpl.Builder outOfScopeBuilder = new TranslationMapImpl.Builder();
        Map<Variable, Expression> afterFiltering = new HashMap<>();
        for (Variable toRemove : prepareMerge.toRemove) {

            List<ConditionAndVariableInfo> toMerge = filterSubBlocks(evaluationContext, lastStatements, toRemove);
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
        /*
        Recurse: yes, if the value has self references; no, to keep the new scope variable system alive
         */
        TranslationMap instances = outOfScopeBuilder.setRecurseIntoScopeVariables(true).build();
        for (Map.Entry<Variable, Expression> entry : afterFiltering.entrySet()) {
            Expression expression = entry.getValue();
            Variable toRemove = entry.getKey();
            Expression bestValue = expression.translate(evaluationContext.getAnalyserContext(), instances);
            prepareMerge.bestValueForToRemove.addVariableExpression(toRemove, bestValue);
            prepareMerge.translationMap.addVariableExpression(toRemove, bestValue);
            prepareMerge.translationMap.put(expression, bestValue);
        }

        // fields never go out of scope, but their scope variables may. "computeRenames" will create
        // new field references, each with new scope variables that also don't go out of scope anymore.
        // the same applies to dependent variables
        Set<LocalVariableReference> newScopeVariables = prepareMerge.computeRenames();
        CausesOfDelay newScopeVariableDelay = CausesOfDelay.EMPTY;
        GroupPropertyValues groupPropertyValuesOfNewScopeVariables = new GroupPropertyValues();

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
            groupPropertyValuesOfNewScopeVariables.setDefaultsForScopeVariable(lvr);
            newScopeVariableDelay = newScopeVariableDelay.merge(scopeValue.causesOfDelay());
        }
        TranslationMap translationMap = prepareMerge.translationMap.build();

        ProgressAndDelay soFar = new ProgressAndDelay(false, newScopeVariableDelay);

        MergeValueOfSingleVariable merge = new MergeValueOfSingleVariable(evaluationContext, statementTime,
                atLeastOneBlockExecuted, lastStatements, statementAnalysis, stateOfConditionManagerBeforeExecution,
                translationMap);
        Function<Variable, Variable> rename = v -> prepareMerge.renames.getOrDefault(v, v);
        Function<Variable, List<ConditionAndVariableInfo>> filterSub = v ->
                filterSubBlocks(evaluationContext, lastStatements, v);
        for (VariableInfoContainer vic : prepareMerge.toMerge) {
            ProgressAndDelay pad = merge.go(vic, rename, filterSub);
            soFar = soFar.combine(pad);
        }

        statementAnalysis.rawVariableStream().forEach(e -> {
            if (e.getValue().variableNature() instanceof VariableNature.DelayedScope ds && ds.getIteration() < evaluationContext.getIteration()) {
                LOGGER.debug("Removing {}, not actively merged anymore", e.getKey());
                e.getValue().remove();
            }
        });

        CausesOfDelay conditionCauses = lastStatements.stream().map(cav -> cav.condition.causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        groupPropertyValuesOfNewScopeVariables.addAll(merge.getGroupPropertyValues());
        MergeLinkingAndGroupProperties m = new MergeLinkingAndGroupProperties(evaluationContext, statementAnalysis);
        ProgressAndDelay mergeStatus = m.linkingAndGroupProperties(
                groupPropertyValuesOfNewScopeVariables,
                merge.getLinkedVariablesMap(),
                merge.getModificationTimes(), statementTime,
                merge.getVariablesWhereMergeOverwrites(),
                newScopeVariables, prepareMerge.renames,
                prepareMerge.toRemove,
                prepareMerge.toIgnore,
                setCnnVariables, translationMap,
                conditionCauses);

        ProgressAndDelay status = mergeStatus.combine(soFar);

        Expression translatedAddToState = addToStateAfterMerge == null ? null :
                addToStateAfterMerge.translate(evaluationContext.getAnalyserContext(), translationMap);
        return new MergeResult(status, translatedAddToState);
    }

    private boolean isVariableInLoopDefinedOutside(Expression value) {
        IsVariableExpression ive;
        if ((ive = value.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfoContainer vic = statementAnalysis.findOrNull(ive.variable());
            return vic != null && index.equals(vic.variableNature().getStatementIndexOfBlockVariable());
        }
        return false;
    }


    private List<ConditionAndVariableInfo> filterSubBlocks(EvaluationContext evaluationContext,
                                                           List<ConditionAndLastStatement> lastStatements,
                                                           Variable variable) {
        String fqn = variable.fullyQualifiedName();
        boolean inSwitchStatementOldStyle = statement instanceof SwitchStatementOldStyle;
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
}
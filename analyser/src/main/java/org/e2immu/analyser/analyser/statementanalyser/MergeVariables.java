package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.ConditionAndVariableInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.SwitchStatementOldStyle;
import org.e2immu.analyser.model.variable.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

import static org.e2immu.analyser.analyser.Stage.MERGE;

class MergeVariables {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeVariables.class);

    private final StatementAnalysis statementAnalysis;
    private final EvaluationContext evaluationContext;

    MergeVariables(EvaluationContext evaluationContext, StatementAnalysis statementAnalysis) {
        this.statementAnalysis = statementAnalysis;
        this.evaluationContext = evaluationContext;
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

    MergeResult mergeVariablesFromSubBlocks(Expression stateOfConditionManagerBeforeExecution,
                                            Expression addToStateAfterMerge,
                                            List<ConditionAndLastStatement> lastStatements,
                                            boolean atLeastOneBlockExecuted,
                                            int statementTime,
                                            Map<Variable, DV> setCnnVariables) {
        Function<Variable, List<ConditionAndVariableInfo>> filterSub = v -> filterSubBlocks(lastStatements, v);

        PrepareMergeVariables.Data prepData = new PrepareMergeVariables(evaluationContext, statementAnalysis)
                .go(filterSub, lastStatements, setCnnVariables.keySet());

        // fields never go out of scope, but their scope variables may. "computeRenames" will create
        // new field references, each with new scope variables that also don't go out of scope anymore.
        // the same applies to dependent variables
        Set<LocalVariableReference> newScopeVariables = prepData.computeRenames();
        NewScopeVariables nsv = ensureNewScopeVariables(newScopeVariables);

        TranslationMap translationMap = prepData.translationMap().build();
        ProgressAndDelay soFar = new ProgressAndDelay(false, nsv.causes);
        MergeValueOfSingleVariable merge = new MergeValueOfSingleVariable(evaluationContext, statementTime,
                atLeastOneBlockExecuted, lastStatements, statementAnalysis, stateOfConditionManagerBeforeExecution,
                translationMap);
        Function<Variable, Variable> rename = v -> prepData.renames().getOrDefault(v, v);

        for (VariableInfoContainer vic : prepData.toMerge()) {
            ProgressAndDelay pad = merge.go(vic, rename, filterSub);
            soFar = soFar.combine(pad);
        }

        statementAnalysis.rawVariableStream().forEach(e -> {
            if (e.getValue().variableNature() instanceof VariableNature.DelayedScope ds
                    && ds.getIteration() < evaluationContext.getIteration()) {
                LOGGER.debug("Removing {}, not actively merged anymore", e.getKey());
                e.getValue().remove();
            }
        });

        CausesOfDelay conditionCauses = lastStatements.stream().map(cav -> cav.condition.causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        nsv.groupPropertyValues.addAll(merge.getGroupPropertyValues());
        MergeLinkingAndGroupProperties m = new MergeLinkingAndGroupProperties(evaluationContext, statementAnalysis);
        ProgressAndDelay mergeStatus = m.linkingAndGroupProperties(
                nsv.groupPropertyValues,
                merge.getLinkedVariablesMap(),
                merge.getModificationTimes(), statementTime,
                merge.getVariablesWhereMergeOverwrites(),
                newScopeVariables, prepData.renames(),
                prepData.toRemove(),
                prepData.toIgnore(),
                setCnnVariables, translationMap,
                conditionCauses);

        ProgressAndDelay status = mergeStatus.combine(soFar);

        Expression translatedAddToState = addToStateAfterMerge == null ? null :
                addToStateAfterMerge.translate(evaluationContext.getAnalyserContext(), translationMap);
        return new MergeResult(status, translatedAddToState);
    }

    private record NewScopeVariables(CausesOfDelay causes, GroupPropertyValues groupPropertyValues) {
    }

    /*
    Ensure that the new scope variables have been created, and have a MERGE value set to the
    assignment expression in the LVR object. Value properties will be written according to the value.
     */
    private NewScopeVariables ensureNewScopeVariables(Set<LocalVariableReference> newScopeVariables) {
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();
        CausesOfDelay delay = CausesOfDelay.EMPTY;
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
            Properties propertiesOfLvr = evaluationContext.getValueProperties(lvr.assignmentExpression);
            Expression scopeValue;
            CausesOfDelay propertyDelay = propertiesOfLvr.delays();
            if (propertyDelay.isDelayed() && lvr.assignmentExpression.isDone()) {
                scopeValue = DelayedExpression.forDelayedValueProperties(lvr.assignmentExpression.getIdentifier(),
                        lvr.assignmentExpression.returnType(), lvr.assignmentExpression, propertyDelay,
                        Properties.EMPTY);
            } else {
                scopeValue = lvr.assignmentExpression;
            }
            newScopeVar.setValue(scopeValue, null, propertiesOfLvr, MERGE);
            groupPropertyValues.setDefaultsForScopeVariable(lvr);
            delay = delay.merge(scopeValue.causesOfDelay());
        }
        return new NewScopeVariables(delay, groupPropertyValues);
    }

    private List<ConditionAndVariableInfo> filterSubBlocks(List<ConditionAndLastStatement> lastStatements,
                                                           Variable variable) {
        String fqn = variable.fullyQualifiedName();
        boolean inSwitchStatementOldStyle = statementAnalysis.statement() instanceof SwitchStatementOldStyle;
        return lastStatements.stream()
                .filter(e2 -> e2.lastStatement().getStatementAnalysis().variableIsSet(fqn))
                .map(e2 -> {
                    VariableInfoContainer vic2 = e2.lastStatement().getStatementAnalysis().getVariable(fqn);
                    return new ConditionAndVariableInfo(e2.condition(), e2.absoluteState(),
                            vic2.current(), e2.alwaysEscapes(), e2.alwaysEscapesOrReturns(),
                            vic2.variableNature(), e2.firstStatementIndexForOldStyleSwitch(),
                            e2.lastStatement().getStatementAnalysis().index(),
                            statementAnalysis.index(),
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
            if (cavLatest.compareTo(statementAnalysis.index()) > 0) {
                return cav.firstStatementIndexForOldStyleSwitch().compareTo(cavLatest) <= 0;
            }
            return cav.firstStatementIndexForOldStyleSwitch().compareTo(cav.variableInfo().getReadId()) <= 0;
        }
        return cav.variableInfo().isRead() || cav.variableInfo().isAssigned();
    }
}
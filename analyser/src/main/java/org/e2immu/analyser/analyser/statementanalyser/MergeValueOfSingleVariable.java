package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.delay.FlowDataConstants;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.nonanalyserimpl.Merge;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.TryStatement;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.analyser.Stage.MERGE;

class MergeValueOfSingleVariable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeValueOfSingleVariable.class);

    private final String index;
    private final StatementAnalysis statementAnalysis;
    private final EvaluationContext evaluationContext;
    private final int statementTime;
    private final Statement statement;
    private final List<MergeVariables.ConditionAndLastStatement> lastStatements;
    private final boolean atLeastOneBlockExecuted;
    private final Expression stateOfConditionManagerBeforeExecution;
    private final TranslationMap translationMap;

    MergeValueOfSingleVariable(EvaluationContext evaluationContext,
                               int statementTime,
                               boolean atLeastOneBlockExecuted,
                               List<MergeVariables.ConditionAndLastStatement> lastStatements,
                               StatementAnalysis statementAnalysis,
                               Expression stateOfConditionManagerBeforeExecution,
                               TranslationMap translationMap) {
        this.evaluationContext = evaluationContext;
        this.statementTime = statementTime;
        this.statementAnalysis = statementAnalysis;
        this.index = statementAnalysis.index();
        this.statement = statementAnalysis.statement();
        this.atLeastOneBlockExecuted = atLeastOneBlockExecuted;
        this.translationMap = translationMap;
        this.stateOfConditionManagerBeforeExecution = stateOfConditionManagerBeforeExecution;
        this.lastStatements = lastStatements;
    }

    private final Map<Variable, Integer> modificationTimes = new HashMap<>();
    private final Map<Variable, LinkedVariables> linkedVariablesMap = new HashMap<>();
    private final Set<Variable> variablesWhereMergeOverwrites = new HashSet<>();
    private final GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

    public ProgressAndDelay go(VariableInfoContainer vic,
                               Function<Variable, Variable> renameFunction,
                               Function<Variable, List<ConditionAndVariableInfo>> filterSubBlocks) {
        VariableInfo current = vic.current();
        Variable variable = current.variable();
        assert !variable.hasScopeVariableCreatedAt(index) : "should have been removed";
        Variable renamed = renameFunction.apply(variable);

        VariableInfoContainer destination;
        if (!statementAnalysis.variableIsSet(renamed.fullyQualifiedName())) {
            VariableNature variableNature;
            if (renamed instanceof FieldReference fr) {
                if (fr.scope().isDelayed()) {
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
        Merge.ExpressionAndProperties overwriteValue = overwrite(evaluationContext, variable);
        assert overwriteValue == null || renamed == variable : "Overwrites do not go together with renames?";

        // use "variable" here rather than "renamed", this deals with data in the sub-blocks
        boolean localAtLeastOneBlock;
        List<ConditionAndVariableInfo> toMerge = filterSubBlocks.apply(variable);
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
        boolean progress = false;
        CausesOfDelay delay = CausesOfDelay.EMPTY;

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

        return new ProgressAndDelay(progress, delay);
    }

    public Set<Variable> getVariablesWhereMergeOverwrites() {
        return variablesWhereMergeOverwrites;
    }

    public Map<Variable, Integer> getModificationTimes() {
        return modificationTimes;
    }

    public Map<Variable, LinkedVariables> getLinkedVariablesMap() {
        return linkedVariablesMap;
    }

    public GroupPropertyValues getGroupPropertyValues() {
        return groupPropertyValues;
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

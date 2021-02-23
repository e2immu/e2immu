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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.Freezable;
import org.e2immu.analyser.util.SetOnce;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VariableInfoContainerImpl extends Freezable implements VariableInfoContainer {

    private final VariableInLoop variableInLoop;

    private final Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial;
    private final SetOnce<VariableInfoImpl> evaluation = new SetOnce<>();
    private final SetOnce<VariableInfoImpl> merge;

    private final Level levelForPrevious;

    /*
    factory method for existing variables
     */
    public static VariableInfoContainerImpl existingVariable(VariableInfoContainer previous,
                                                             String statementIndexForLocalVariableInLoop,
                                                             boolean previousIsParent,
                                                             boolean statementHasSubBlocks) {
        Objects.requireNonNull(previous);
        return new VariableInfoContainerImpl(computeVariableInLoop(previous, statementIndexForLocalVariableInLoop),
                Either.left(previous),
                statementHasSubBlocks ? new SetOnce<>() : null,
                previousIsParent ? Level.EVALUATION : Level.MERGE);
    }

    /*
   factory method for existing variables in enclosing methods
    */
    public static VariableInfoContainerImpl copyOfExistingVariableInEnclosingMethod(VariableInfoContainer previous,
                                                                                    boolean statementHasSubBlocks) {
        Objects.requireNonNull(previous);
        VariableInfo outside = previous.current();
        VariableInfoImpl initial = new VariableInfoImpl(outside.variable(), NOT_YET_ASSIGNED,
                NOT_YET_READ, NOT_A_VARIABLE_FIELD, Set.of());
        initial.newVariable();
        initial.setValue(outside.getValue(), outside.isDelayed());
        if (outside.getLinkedVariables() != LinkedVariables.DELAY)
            initial.setLinkedVariables(outside.getLinkedVariables());
        return new VariableInfoContainerImpl(VariableInLoop.COPY_FROM_ENCLOSING_METHOD,
                Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null,
                Level.MERGE);
    }


    /*
    factory method for new variables
     */
    public static VariableInfoContainerImpl newVariable(Variable variable,
                                                        int statementTime,
                                                        VariableInLoop variableInLoop,
                                                        boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(variable, NOT_YET_ASSIGNED, NOT_YET_READ, statementTime, Set.of());
        // no newVariable, because either setValue is called immediately after this method, or the explicit newVariableWithoutValue()
        return new VariableInfoContainerImpl(variableInLoop, Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
        factory method for new catch variables
         */
    public static VariableInfoContainerImpl newCatchVariable(Variable variable,
                                                             String index,
                                                             Expression value,
                                                             boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(variable, index + Level.INITIAL,
                index + Level.EVALUATION, NOT_A_VARIABLE_FIELD, Set.of());
        initial.newVariable();
        initial.setValue(value, false);
        initial.setLinkedVariables(LinkedVariables.EMPTY);
        return new VariableInfoContainerImpl(VariableInLoop.NOT_IN_LOOP,
                Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
    factory method for new loop variables
     */
    public static VariableInfoContainerImpl newLoopVariable(Variable variable,
                                                            String assignedId,
                                                            String readId,
                                                            Expression value,
                                                            Map<VariableProperty, Integer> properties,
                                                            LinkedVariables linkedVariables,
                                                            VariableInLoop variableInLoop,
                                                            boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(variable, assignedId, readId, VariableInfoContainer.NOT_A_VARIABLE_FIELD, Set.of());
        initial.newVariable();
        initial.setValue(value, false);
        properties.forEach(initial::setProperty);
        initial.setLinkedVariables(linkedVariables);
        return new VariableInfoContainerImpl(variableInLoop, Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
   factory method for new variables
    */
    public static VariableInfoContainerImpl existingLocalVariableIntoLoop(VariableInfoContainer previous,
                                                                          VariableInLoop variableInLoop,
                                                                          boolean previousIsParent) {
        return new VariableInfoContainerImpl(variableInLoop,
                Either.left(previous),
                new SetOnce<>(),
                previousIsParent ? Level.EVALUATION : Level.MERGE);
    }

    private VariableInfoContainerImpl(VariableInLoop variableInLoop,
                                      Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial,
                                      SetOnce<VariableInfoImpl> merge,
                                      Level levelForPrevious) {
        this.variableInLoop = variableInLoop;
        this.previousOrInitial = previousOrInitial;
        this.merge = merge;
        this.levelForPrevious = levelForPrevious;
    }

    private static VariableInLoop computeVariableInLoop(VariableInfoContainer previous,
                                                        String statementIndexForLocalVariableInLoop) {
        if (statementIndexForLocalVariableInLoop == null) {
            return previous.getVariableInLoop();
        }
        String prevStatementId = previous.getVariableInLoop().variableType() == VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE ?
                previous.getVariableInLoop().statementId() : null;
        if (prevStatementId != null && !statementIndexForLocalVariableInLoop.startsWith(prevStatementId)) {
            // we go back out
            return VariableInLoop.NOT_IN_LOOP;
        }
        return previous.getVariableInLoop();
    }

    @Override
    public void newVariableWithoutValue() {
        assert !hasMerge();
        assert !hasEvaluation();
        assert isInitial();
        ((VariableInfoImpl) getPreviousOrInitial()).newVariable();
    }

    @Override
    public VariableInLoop getVariableInLoop() {
        return variableInLoop;
    }

    @Override
    public boolean isInitial() {
        return previousOrInitial.isRight();
    }

    @Override
    public void setStatementTime(int statementTime) {
        if (isInitial()) previousOrInitial.getRight().setStatementTime(statementTime);
        if (evaluation.isSet()) evaluation.get().setStatementTime(statementTime);
    }

    @Override
    public VariableInfo current() {
        if (merge != null && merge.isSet()) return merge.get();
        return currentExcludingMerge();
    }

    private VariableInfoImpl currentExcludingMerge() {
        if (evaluation.isSet()) return evaluation.get();
        if (previousOrInitial.isLeft()) return (VariableInfoImpl) previousOrInitial.getLeft().best(levelForPrevious);
        return previousOrInitial.getRight();
    }

    @Override
    public VariableInfo best(Level level) {
        if (level == Level.MERGE && merge != null && merge.isSet()) return merge.get();
        if ((level == Level.MERGE || level == Level.EVALUATION) && evaluation.isSet()) return evaluation.get();
        return previousOrInitial.isLeft() ? previousOrInitial.getLeft().best(levelForPrevious) : previousOrInitial.getRight();
    }

    @Override
    public VariableInfo getPreviousOrInitial() {
        return previousOrInitial.isLeft() ? previousOrInitial.getLeft().best(levelForPrevious) : previousOrInitial.getRight();
    }

    @Override
    public void setValue(Expression value,
                         boolean valueIsDelayed,
                         LinkedVariables staticallyAssignedVariables,
                         Map<VariableProperty, Integer> propertiesToSet, boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        variableInfo.setValue(value, valueIsDelayed);

        propertiesToSet.forEach((vp, v) -> {
            int inMap = variableInfo.getProperty(vp, org.e2immu.analyser.model.Level.DELAY);
            if (v > inMap) variableInfo.setProperty(vp, v);
        });
        variableInfo.setStaticallyAssignedVariables(staticallyAssignedVariables);
    }

    @Override
    public void setLinkedVariables(LinkedVariables linkedVariables, boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(linkedVariables);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        variableInfo.setLinkedVariables(linkedVariables);
    }

    @Override
    public void setStaticallyAssignedVariables(LinkedVariables staticallyAssignedVariables, boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(staticallyAssignedVariables);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        variableInfo.setStaticallyAssignedVariables(staticallyAssignedVariables);
    }

    @Override
    public void setProperty(VariableProperty variableProperty,
                            int value,
                            boolean failWhenTryingToWriteALowerValue,
                            Level level) {
        ensureNotFrozen();
        Objects.requireNonNull(variableProperty);
        VariableInfoImpl variableInfo = switch (level) {
            case INITIAL -> (VariableInfoImpl) getPreviousOrInitial();
            case EVALUATION -> evaluation.get();
            case MERGE -> this.merge == null || !this.merge.isSet() ? evaluation.get() : this.merge.get();
        };

        int current = variableInfo.getProperty(variableProperty);
        if (current == org.e2immu.analyser.model.Level.DELAY) {
            if (value != org.e2immu.analyser.model.Level.DELAY) variableInfo.setProperty(variableProperty, value);
        } else if (current != value && (current < value || failWhenTryingToWriteALowerValue)) {
            throw new UnsupportedOperationException("Trying to write a different value " + value +
                    ", already have " + current + ", property " + variableProperty +
                    ", variable " + current().variable().fullyQualifiedName());
        }
    }

    @Override
    public boolean hasEvaluation() {
        return evaluation.isSet();
    }

    @Override
    public boolean hasMerge() {
        return merge != null && merge.isSet();
    }

    @Override
    public void ensureEvaluation(String assignmentId, String readId, int statementTime, Set<Integer> readAtStatementTimes) {
        if (!evaluation.isSet()) {
            VariableInfoImpl pi = (VariableInfoImpl) getPreviousOrInitial();

            /* in many situations the following assertions would hold; however, calling from MethodLevelData they do not
           assert !assignmentId.equals(NOT_YET_ASSIGNED) || !pi.isAssigned();
            assert !readId.equals(NOT_YET_READ) || !assignmentId.equals(NOT_YET_ASSIGNED) || !pi.isRead();
             */

            VariableInfoImpl eval = new VariableInfoImpl(pi.variable(), assignmentId, readId, statementTime, readAtStatementTimes);
            evaluation.set(eval);
            if (!pi.valueIsSet()) {
                eval.setValue(pi.getValue(), true);
            }
        } else if (!evaluation.get().statementTimeIsSet() && statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            evaluation.get().setStatementTime(statementTime);
        }
    }

    @Override
    public void writeStaticallyAssignedVariablesToEvaluation(LinkedVariables staticallyAssignedVariables) {
        VariableInfo vi1 = getPreviousOrInitial();
        VariableInfoImpl write;
        if (!evaluation.isSet()) {
            write = new VariableInfoImpl(vi1.variable(), vi1.getAssignmentId(), vi1.getReadId(), vi1.getStatementTime(), vi1.getReadAtStatementTimes());
            if (vi1.linkedVariablesIsSet()) write.setLinkedVariables(vi1.getLinkedVariables());
            if (vi1.valueIsSet()) write.setValue(vi1.getValue(), true);
            evaluation.set(write);
            vi1.propertyStream().filter(e -> e.getKey() != VariableProperty.CONTEXT_NOT_NULL && e.getKey() != VariableProperty.CONTEXT_MODIFIED)
                    .forEach(e -> write.setProperty(e.getKey(), e.getValue()));
        } else {
            write = evaluation.get();
        }
        write.setStaticallyAssignedVariables(staticallyAssignedVariables);
    }

    @Override
    public void setObjectFlow(ObjectFlow objectFlow, boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(objectFlow);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        variableInfo.setObjectFlow(objectFlow);
    }

    /*
    Copy from one statement to the next, iteration 1+, into 'evaluation'
    when reading and assigning don't do it. This occurs when another variable
    holds this variable as a value. And evaluation level is created, even though
    the variable is not read/assigned in the main expression.
     */
    @Override
    public void copy() {
        assert previousOrInitial.isLeft() : "No point in copying when we are an initial";
        VariableInfo previous = previousOrInitial.getLeft().best(levelForPrevious);

        assert this.evaluation.isSet();

        VariableInfoImpl evaluation = this.evaluation.get();
        boolean noAssignmentInThisStatement = isNotAssignedInThisStatement();
        boolean notReadInThisStatement = !isReadInThisStatement();
        if (noAssignmentInThisStatement && notReadInThisStatement) {
            previous.propertyStream()
                    .filter(e -> e.getKey() != VariableProperty.CONTEXT_MODIFIED && e.getKey() != VariableProperty.CONTEXT_NOT_NULL)
                    .forEach(e ->
                            setProperty(e.getKey(), e.getValue(), false, Level.EVALUATION));

            if (previous.valueIsSet()) {
                evaluation.setValue(previous.getValue(), previous.isDelayed());
            }
            if (previous.objectFlowIsSet()) {
                evaluation.setObjectFlow(previous.getObjectFlow());
            }
            if (previous.linkedVariablesIsSet()) {
                evaluation.setLinkedVariables(previous.getLinkedVariables());
            }
            // can have been modified by a remapping after assignments in StatementAnalyser.apply
            if(!evaluation.staticallyAssignedVariablesIsSet()) {
                evaluation.setStaticallyAssignedVariables(previous.getStaticallyAssignedVariables());
            }
        }
        if (previous.statementTimeIsSet()) {
            boolean confirmedNotVariableField = previous.getStatementTime() == NOT_A_VARIABLE_FIELD;
            if (noAssignmentInThisStatement && (confirmedNotVariableField || notReadInThisStatement)) {
                evaluation.setStatementTime(previous.getStatementTime());
            }
        }
    }

    @Override
    public void copyFromEvalIntoMerge(Map<Variable, Integer> contextNotNull, Map<Variable, Integer> contextModified) {
        assert hasMerge();

        VariableInfo eval = best(Level.EVALUATION);
        VariableInfoImpl mergeImpl = merge.get();
        mergeImpl.setValue(eval.getValue(), eval.isDelayed());
        mergeImpl.setLinkedVariables(eval.getLinkedVariables());
        eval.propertyStream()
                .forEach(e -> {
                    VariableProperty vp = e.getKey();
                    int value = e.getValue();
                    if (vp == VariableProperty.CONTEXT_MODIFIED) contextModified.put(eval.variable(), value);
                    else if (vp == VariableProperty.CONTEXT_NOT_NULL) contextNotNull.put(eval.variable(), value);
                    else mergeImpl.setProperty(vp, value);
                });
    }

    @Override
    public void merge(EvaluationContext evaluationContext,
                      Expression stateOfDestination,
                      boolean atLeastOneBlockExecuted,
                      List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                      Map<Variable, Integer> contextNotNull,
                      Map<Variable, Integer> contextModified) {
        Objects.requireNonNull(mergeSources);
        Objects.requireNonNull(evaluationContext);
        assert merge != null;

        VariableInfoImpl existing = currentExcludingMerge();
        if (!merge.isSet()) {
            merge.set(existing.mergeIntoNewObject(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources,
                    contextNotNull, contextModified));
        } else {
            merge.get().mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, existing, mergeSources,
                    contextNotNull, contextModified);
        }
    }
}

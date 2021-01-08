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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;

public class VariableInfoContainerImpl extends Freezable implements VariableInfoContainer {

    private final VariableInLoop variableInLoop;

    private final Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial;
    private final SetOnce<VariableInfoImpl> evaluation = new SetOnce<>();
    private final SetOnce<VariableInfoImpl> merge;

    /*
    constructor for existing variables
     */
    public VariableInfoContainerImpl(VariableInfoContainer previous,
                                     String statementIndexForLocalVariableInLoop,
                                     boolean statementHasSubBlocks) {
        Objects.requireNonNull(previous);
        previousOrInitial = Either.left(previous);
        variableInLoop = computeVariableInLoop(previous, statementIndexForLocalVariableInLoop);
        merge = statementHasSubBlocks ? new SetOnce<>() : null;
    }

    /*
    constructor for new variables
     */
    public VariableInfoContainerImpl(Variable variable,
                                     int statementTime,
                                     VariableInLoop variableInLoop,
                                     boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(variable, NOT_YET_ASSIGNED, NOT_YET_READ, statementTime, Set.of());
        previousOrInitial = Either.right(initial);
        this.variableInLoop = variableInLoop;
        merge = statementHasSubBlocks ? new SetOnce<>() : null;
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
        if (previousOrInitial.isLeft()) return (VariableInfoImpl) previousOrInitial.getLeft().current();
        return previousOrInitial.getRight();
    }

    @Override
    public VariableInfo best(Level level) {
        if (level == Level.MERGE && merge != null && merge.isSet()) return merge.get();
        if ((level == Level.MERGE || level == Level.EVALUATION) && evaluation.isSet()) return evaluation.get();
        return previousOrInitial.isLeft() ? previousOrInitial.getLeft().current() : previousOrInitial.getRight();
    }

    @Override
    public VariableInfo getPreviousOrInitial() {
        return previousOrInitial.isLeft() ? previousOrInitial.getLeft().current() : previousOrInitial.getRight();
    }

    @Override
    public void setValue(Expression value, Map<VariableProperty, Integer> propertiesToSet, boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        if (value != NO_VALUE) {
            variableInfo.setValue(value);
        }
        propertiesToSet.forEach((vp, v) -> {
            int inMap = variableInfo.getProperty(vp, org.e2immu.analyser.model.Level.DELAY);
            if (v > inMap) variableInfo.setProperty(vp, v);
        });
    }

    @Override
    public void setLinkedVariables(LinkedVariables linkedVariables, boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(linkedVariables);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        variableInfo.setLinkedVariables(linkedVariables);
    }

    @Override
    public void setProperty(VariableProperty variableProperty,
                            int value,
                            boolean failWhenTryingToWriteALowerValue,
                            Level level) {
        ensureNotFrozen();
        Objects.requireNonNull(variableProperty);
        VariableInfoImpl variableInfo = switch (level) {
            case INITIAL -> previousOrInitial.getRight();
            case EVALUATION -> evaluation.get();
            case MERGE -> this.merge == null || !this.merge.isSet() ? evaluation.get() : this.merge.get();
        };

        int current = variableInfo.getProperty(variableProperty);
        if (current < value) {
            variableInfo.setProperty(variableProperty, value);
        } else if (current != value && failWhenTryingToWriteALowerValue) {
            throw new UnsupportedOperationException("Trying to write a lower value " + value +
                    ", already have " + current + ", property " + variableProperty);
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
            assert !assignmentId.equals(NOT_YET_ASSIGNED) || !pi.isAssigned();
            assert !readId.equals(NOT_YET_READ) || !assignmentId.equals(NOT_YET_ASSIGNED) || !pi.isRead();
            VariableInfoImpl eval = new VariableInfoImpl(pi.variable(), assignmentId, readId, statementTime, readAtStatementTimes);
            evaluation.set(eval);
        } else if(!evaluation.get().statementTimeIsSet() && statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY){
            evaluation.get().setStatementTime(statementTime);
        }
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
    The assignment ID marks beforehand when we can expect value changes.
    The XXX marks when we can expect linked variable changes.
     */
    @Override
    public void copy(boolean isParent) {
        assert previousOrInitial.isLeft() : "No point in copying when we are an initial";
        Level level = isParent ? Level.EVALUATION: Level.MERGE;
        VariableInfo previous = previousOrInitial.getLeft().best(level);

        assert this.evaluation.isSet();

        previous.propertyStream().forEach(e ->
                setProperty(e.getKey(), e.getValue(), false, Level.EVALUATION));

        VariableInfoImpl evaluation = this.evaluation.get();
        boolean noAssignmentInThisStatement = previous.getAssignmentId().equals(evaluation.getAssignmentId());
        if (noAssignmentInThisStatement) {
            if (previous.valueIsSet()) {
                evaluation.setValue(previous.getValue());
            }
            if (previous.objectFlowIsSet()) {
                evaluation.setObjectFlow(previous.getObjectFlow());
            }
            if (previous.statementTimeIsSet()) {
                evaluation.setStatementTime(previous.getStatementTime());
            }
            boolean notReadInThisStatement = previous.getReadId().equals(evaluation.getReadId());
            if (notReadInThisStatement && previous.linkedVariablesIsSet()) {
                evaluation.setLinkedVariables(previous.getLinkedVariables());
            }
        }
    }

    @Override
    public void merge(EvaluationContext evaluationContext,
                      Expression stateOfDestination,
                      boolean atLeastOneBlockExecuted,
                      Map<Expression, VariableInfo> mergeSources) {
        Objects.requireNonNull(mergeSources);
        Objects.requireNonNull(evaluationContext);
        assert merge != null;

        VariableInfoImpl existing = currentExcludingMerge();
        if (!merge.isSet()) {
            merge.set(existing.mergeIntoNewObject(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, mergeSources));
        } else {
            merge.get().mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, existing, mergeSources);
        }
    }
}

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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.support.Either;
import org.e2immu.support.Freezable;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VariableInfoContainerImpl extends Freezable implements VariableInfoContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInfoContainerImpl.class);

    private final VariableNature variableNature;

    private final Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial;
    private final SetOnce<VariableInfoImpl> evaluation = new SetOnce<>();
    private final SetOnce<VariableInfoImpl> merge;

    private final Level levelForPrevious;

    /*
    factory method for existing variables; potentially revert VariableDefinedOutsideLoop nature
     */
    public static VariableInfoContainerImpl existingVariable(VariableInfoContainer previous,
                                                             String statementIndex,
                                                             boolean previousIsParent,
                                                             boolean statementHasSubBlocks) {
        Objects.requireNonNull(previous);
        return new VariableInfoContainerImpl(potentiallyRevertVariableDefinedOutsideLoop(previous, statementIndex),
                Either.left(previous),
                statementHasSubBlocks ? new SetOnce<>() : null,
                previousIsParent ? Level.EVALUATION : Level.MERGE);
    }

    /*
     */
    private static VariableNature potentiallyRevertVariableDefinedOutsideLoop(VariableInfoContainer previous,
                                                                              String statementIndex) {
        if (statementIndex != null &&
                previous.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop inLoop &&
                !statementIndex.startsWith(inLoop.statementIndexOfLoop())) {
            // we go back out
            return inLoop.previousVariableNature();
        }
        return previous.variableNature();
    }

    /*
   factory method for existing variables in enclosing methods

   these variables must be implicitly final
    */
    public static VariableInfoContainerImpl copyOfExistingVariableInEnclosingMethod(VariableInfoContainer previous,
                                                                                    boolean statementHasSubBlocks) {
        Objects.requireNonNull(previous);
        VariableInfo outside = previous.current();
        VariableInfoImpl initial = new VariableInfoImpl(outside.variable(), NOT_YET_ASSIGNED,
                NOT_YET_READ, NOT_A_VARIABLE_FIELD, Set.of(), outside.valueIsSet() ? null : outside.getValue());
        initial.newVariable(false);
        initial.setValue(outside.getValue(), outside.isDelayed());
        if (outside.getLinkedVariables() != LinkedVariables.DELAY)
            initial.setLinkedVariables(outside.getLinkedVariables());
        return new VariableInfoContainerImpl(VariableNature.FROM_ENCLOSING_METHOD,
                Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null,
                Level.MERGE);
    }


    /*
    factory method for new variables
     */
    public static VariableInfoContainerImpl newLocalCopyOfVariableField(Variable variable,
                                                                        String readId,
                                                                        boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(variable, NOT_YET_ASSIGNED,
                readId, NOT_A_VARIABLE_FIELD, Set.of(), null);
        VariableNature variableNature = variable instanceof LocalVariableReference lvr
                ? lvr.variable.nature() : VariableNature.NORMAL;
        // no newVariable, because either setValue is called immediately after this method, or the explicit newVariableWithoutValue()
        return new VariableInfoContainerImpl(variableNature, Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
    factory method for new variables, explicitly setting the variableNature
     */
    public static VariableInfoContainerImpl newVariable(Variable variable,
                                                        int statementTime,
                                                        VariableNature variableNature,
                                                        boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(variable, NOT_YET_ASSIGNED, NOT_YET_READ, statementTime, Set.of(), null);
        // no newVariable, because either setValue is called immediately after this method, or the explicit newVariableWithoutValue()
        return new VariableInfoContainerImpl(variableNature, Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
        factory method for new catch variables
         */
    public static VariableInfoContainerImpl newCatchVariable(LocalVariableReference lvr,
                                                             String index,
                                                             Expression value,
                                                             boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(lvr, index + Level.INITIAL,
                index + Level.EVALUATION, NOT_A_VARIABLE_FIELD, Set.of(), null);
        initial.newVariable(true);
        initial.setValue(value, false);
        initial.setLinkedVariables(LinkedVariables.EMPTY);

        return new VariableInfoContainerImpl(lvr.variable.nature(),
                Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
    factory method for new loop variables
     */
    public static VariableInfoContainerImpl newLoopVariable(LocalVariableReference lvr,
                                                            String assignedId,
                                                            String readId,
                                                            Expression value,
                                                            Map<VariableProperty, Integer> properties,
                                                            LinkedVariables staticallyAssignedVariables,
                                                            boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(lvr, assignedId, readId,
                VariableInfoContainer.NOT_A_VARIABLE_FIELD, Set.of(), null);
        initial.setValue(value, false);
        properties.forEach(initial::setProperty);
        int cnn = initial.getProperty(VariableProperty.CONTEXT_NOT_NULL);
        if (cnn == org.e2immu.analyser.model.Level.DELAY) {
            initial.setProperty(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE);
        }
        int cm = initial.getProperty(VariableProperty.CONTEXT_MODIFIED);
        if (cm == org.e2immu.analyser.model.Level.DELAY) {
            initial.setProperty(VariableProperty.CONTEXT_MODIFIED, org.e2immu.analyser.model.Level.FALSE);
        }
        int enn = initial.getProperty(VariableProperty.EXTERNAL_NOT_NULL);
        if (enn == org.e2immu.analyser.model.Level.DELAY) {
            initial.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED);
        }
        int pm = initial.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD);
        if (pm == org.e2immu.analyser.model.Level.DELAY) {
            initial.setProperty(VariableProperty.CONTEXT_PROPAGATE_MOD, org.e2immu.analyser.model.Level.FALSE);
        }
        int extImm = initial.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
        if (extImm == org.e2immu.analyser.model.Level.DELAY) {
            initial.setProperty(VariableProperty.EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED);
        }
        int cImm = initial.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
        if (cImm == org.e2immu.analyser.model.Level.DELAY) {
            initial.setProperty(VariableProperty.CONTEXT_IMMUTABLE, MultiLevel.MUTABLE);
        }
        initial.setStaticallyAssignedVariables(staticallyAssignedVariables);
        initial.setLinkedVariables(LinkedVariables.EMPTY);
        return new VariableInfoContainerImpl(lvr.variable.nature(),
                Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
   factory method for new variables
    */
    public static VariableInfoContainerImpl existingLocalVariableIntoLoop(VariableInfoContainer previous,
                                                                          VariableNature variableNature,
                                                                          boolean previousIsParent) {
        return new VariableInfoContainerImpl(variableNature,
                Either.left(previous),
                new SetOnce<>(),
                previousIsParent ? Level.EVALUATION : Level.MERGE);
    }

    private VariableInfoContainerImpl(VariableNature variableNature,
                                      Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial,
                                      SetOnce<VariableInfoImpl> merge,
                                      Level levelForPrevious) {
        this.variableNature = variableNature;
        this.previousOrInitial = previousOrInitial;
        this.merge = merge;
        this.levelForPrevious = levelForPrevious;
    }


    @Override
    public void newVariableWithoutValue() {
        assert !hasMerge();
        assert !hasEvaluation();
        assert isInitial();
        ((VariableInfoImpl) getPreviousOrInitial()).newVariable(false);
        setLinkedVariables(LinkedVariables.EMPTY, true);
    }

    @Override
    public VariableNature variableNature() {
        return variableNature;
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
        try {
            variableInfo.setValue(value, valueIsDelayed);
        } catch (IllegalStateException ise) {
            LOGGER.error("Variable {}: try to write value {}, already have {}", variableInfo.variable().fullyQualifiedName(),
                    value, variableInfo.getValue());
            throw ise;
        }
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

            VariableInfoImpl eval = new VariableInfoImpl(pi.variable(), assignmentId, readId, statementTime,
                    readAtStatementTimes, pi.valueIsSet() ? null : pi.getValue());
            evaluation.set(eval);
            if (!pi.valueIsSet()) {
                eval.setValue(pi.getValue(), true);
            }
        } else if (evaluation.get().statementTimeDelayed() && statementTime != VariableInfoContainer.VARIABLE_FIELD_DELAY) {
            evaluation.get().setStatementTime(statementTime);
        }
    }

    @Override
    public void writeStaticallyAssignedVariablesToEvaluation(LinkedVariables staticallyAssignedVariables) {
        VariableInfo vi1 = getPreviousOrInitial();
        VariableInfoImpl write;
        if (!evaluation.isSet()) {
            write = new VariableInfoImpl(vi1.variable(), vi1.getAssignmentId(), vi1.getReadId(), vi1.getStatementTime(),
                    vi1.getReadAtStatementTimes(), vi1.valueIsSet() ? null : vi1.getValue());
            if (vi1.linkedVariablesIsSet()) write.setLinkedVariables(vi1.getLinkedVariables());
            if (vi1.valueIsSet()) write.setValue(vi1.getValue(), false);
            vi1.propertyStream().filter(e -> !GroupPropertyValues.PROPERTIES.contains(e.getKey()))
                    .forEach(e -> write.setProperty(e.getKey(), e.getValue()));
            evaluation.set(write);
        } else {
            write = evaluation.get();
        }
        write.setStaticallyAssignedVariables(staticallyAssignedVariables);
    }

    @Override
    public void prepareEvaluationForWritingContextProperties() {
        if (!evaluation.isSet()) {
            VariableInfo vi1 = getPreviousOrInitial();
            evaluation.set(prepareForWritingContextProperties(vi1));
        }
    }

    @Override
    public void prepareMergeForWritingContextProperties() {
        if (!merge.isSet()) {
            VariableInfo vi1 = best(Level.EVALUATION);
            merge.set(prepareForWritingContextProperties(vi1));
        }
    }

    @Override
    public boolean isPrevious() {
        return previousOrInitial.isLeft();
    }

    private VariableInfoImpl prepareForWritingContextProperties(VariableInfo vi1) {
        VariableInfoImpl write = new VariableInfoImpl(vi1.variable(), vi1.getAssignmentId(),
                vi1.getReadId(), vi1.getStatementTime(), vi1.getReadAtStatementTimes(), vi1.valueIsSet() ? null : vi1.getValue());
        if (vi1.linkedVariablesIsSet()) write.setLinkedVariables(vi1.getLinkedVariables());
        if (vi1.valueIsSet()) write.setValue(vi1.getValue(), false);
        write.setStaticallyAssignedVariables(vi1.getStaticallyAssignedVariables());
        vi1.propertyStream().filter(e -> !GroupPropertyValues.PROPERTIES.contains(e.getKey()))
                .forEach(e -> write.setProperty(e.getKey(), e.getValue()));
        return write;
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
                    .filter(e -> !GroupPropertyValues.PROPERTIES.contains(e.getKey()))
                    .forEach(e ->
                            setProperty(e.getKey(), e.getValue(), false, Level.EVALUATION));

            if (previous.valueIsSet()) {
                evaluation.setValue(previous.getValue(), previous.isDelayed());
            }
            if (previous.linkedVariablesIsSet()) {
                evaluation.setLinkedVariables(previous.getLinkedVariables());
            }
            // can have been modified by a remapping after assignments in StatementAnalyser.apply
            if (!evaluation.staticallyAssignedVariablesIsSet()) {
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
    public void copyFromEvalIntoMerge(GroupPropertyValues groupPropertyValues) {
        assert hasMerge();

        VariableInfo eval = best(Level.EVALUATION);
        Variable v = eval.variable();
        VariableInfoImpl mergeImpl = merge.get();
        mergeImpl.setValue(eval.getValue(), eval.isDelayed());
        if (eval.linkedVariablesIsSet()) {
            mergeImpl.setLinkedVariables(eval.getLinkedVariables());
        }
        eval.propertyStream()
                .forEach(e -> {
                    VariableProperty vp = e.getKey();
                    int value = e.getValue();
                    if (GroupPropertyValues.PROPERTIES.contains(vp)) {
                        groupPropertyValues.set(vp, v, value);
                    } else {
                        mergeImpl.setProperty(vp, value);
                    }
                });
        for (VariableProperty variableProperty : GroupPropertyValues.PROPERTIES) {
            groupPropertyValues.setIfKeyAbsent(variableProperty, v, org.e2immu.analyser.model.Level.DELAY);
        }
    }

    @Override
    public void merge(EvaluationContext evaluationContext,
                      Expression stateOfDestination,
                      boolean atLeastOneBlockExecuted,
                      List<StatementAnalysis.ConditionAndVariableInfo> mergeSources,
                      GroupPropertyValues groupPropertyValues) {
        Objects.requireNonNull(mergeSources);
        Objects.requireNonNull(evaluationContext);
        assert merge != null;

        VariableInfoImpl existing = currentExcludingMerge();
        if (!merge.isSet()) {
            merge.set(existing.mergeIntoNewObject(evaluationContext, stateOfDestination, atLeastOneBlockExecuted,
                    mergeSources, groupPropertyValues));
        } else {
            merge.get().mergeIntoMe(evaluationContext, stateOfDestination, atLeastOneBlockExecuted, existing,
                    mergeSources, groupPropertyValues);
        }
    }
}

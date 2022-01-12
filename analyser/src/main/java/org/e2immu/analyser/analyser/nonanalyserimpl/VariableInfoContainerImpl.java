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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.Instance;
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

import static org.e2immu.analyser.analyser.AssignmentIds.NOT_YET_ASSIGNED;

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
                !statementIndex.startsWith(inLoop.statementIndex())) {
            // we go back out
            return inLoop.previousVariableNature();
        }
        return previous.variableNature();
    }

    /*
   factory method for existing variables in enclosing methods

   these variables must be implicitly final
    */
    public static VariableInfoContainerImpl copyOfExistingVariableInEnclosingMethod(
            Location location,
            VariableInfoContainer previous,
            boolean statementHasSubBlocks) {
        Objects.requireNonNull(previous);
        VariableInfo outside = previous.current();
        VariableInfoImpl initial = new VariableInfoImpl(location, outside.variable(), NOT_YET_ASSIGNED,
                NOT_YET_READ, Set.of(), outside.valueIsSet() ? null : outside.getValue());
        initial.newVariable(false);
        initial.setValue(outside.getValue());
        if (!outside.getLinkedVariables().isDelayed()) initial.setLinkedVariables(outside.getLinkedVariables());

        return new VariableInfoContainerImpl(VariableNature.FROM_ENCLOSING_METHOD,
                Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null,
                Level.MERGE);
    }

    /*
    factory method for new variables
     */
    public static VariableInfoContainerImpl newLocalCopyOfVariableField(
            Location location,
            Variable variable,
            String readId,
            boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(location, variable, NOT_YET_ASSIGNED,
                readId, Set.of(), null);
        VariableNature variableNature = variable instanceof LocalVariableReference lvr
                ? lvr.variable.nature() : VariableNature.METHOD_WIDE;
        // no newVariable, because either setValue is called immediately after this method, or the explicit newVariableWithoutValue()
        return new VariableInfoContainerImpl(variableNature, Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
    factory method for new variables, explicitly setting the variableNature
     */
    public static VariableInfoContainerImpl newVariable(Location location,
                                                        Variable variable,
                                                        VariableNature variableNature,
                                                        boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(location, variable, NOT_YET_ASSIGNED, NOT_YET_READ,
                Set.of(), null);
        // no newVariable, because either setValue is called immediately after this method, or the explicit newVariableWithoutValue()
        return new VariableInfoContainerImpl(variableNature, Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
        factory method for new catch variables
        we need to overwrite the VariableNature because the original one has no index to define the scope
    */
    public static VariableInfoContainerImpl newCatchVariable(Location location,
                                                             LocalVariableReference lvr,
                                                             String index,
                                                             Instance value,
                                                             boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(location, lvr, new AssignmentIds(index + Level.INITIAL),
                index + Level.EVALUATION, Set.of(), null);
        initial.newVariable(true);
        initial.setValue(value);
        value.valueProperties().stream().forEach(e -> initial.setProperty(e.getKey(), e.getValue()));
        initial.setLinkedVariables(LinkedVariables.EMPTY);
        return new VariableInfoContainerImpl(new VariableNature.NormalLocalVariable(index),
                Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
    factory method for new loop variables
     */
    public static VariableInfoContainerImpl newLoopVariable(Location location,
                                                            LocalVariableReference lvr,
                                                            String assignedId,
                                                            String readId,
                                                            Expression value,
                                                            Map<Property, DV> properties,
                                                            LinkedVariables linkedVariables,
                                                            boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(location, lvr, new AssignmentIds(assignedId), readId, Set.of(),
                null);
        initial.setValue(value);
        properties.forEach(initial::setProperty);
        initial.ensureProperty(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);
        initial.ensureProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        initial.ensureProperty(Property.EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV);
        initial.ensureProperty(Property.EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED_DV);
        initial.ensureProperty(Property.CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV);

        initial.setLinkedVariables(linkedVariables);
        return new VariableInfoContainerImpl(lvr.variable.nature(),
                Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null);
    }

    /*
   factory method for new variables
    */
    public static VariableInfoContainerImpl existingLocalVariableIntoLoop(VariableInfoContainer previous,
                                                                          String statementIndex,
                                                                          boolean previousIsParent) {
        return new VariableInfoContainerImpl(
                new VariableNature.VariableDefinedOutsideLoop(previous.variableNature(), statementIndex),
                Either.left(previous),
                new SetOnce<>(),
                previousIsParent ? Level.EVALUATION : Level.MERGE);
    }

    private VariableInfoContainerImpl(VariableNature variableNature,
                                      Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial,
                                      SetOnce<VariableInfoImpl> merge,
                                      Level levelForPrevious) {
        this.variableNature = Objects.requireNonNull(variableNature);
        this.previousOrInitial = previousOrInitial;
        this.merge = merge;
        this.levelForPrevious = levelForPrevious;
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
    public VariableInfo current() {
        if (merge != null && merge.isSet()) return merge.get();
        return currentExcludingMerge();
    }

    private VariableInfoImpl getToWrite(Level level) {
        return switch (level) {
            case INITIAL -> (VariableInfoImpl) getPreviousOrInitial();
            case EVALUATION -> evaluation.get();
            case MERGE -> merge.get();
        };
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
        return getPreviousOrInitial();
    }

    @Override
    public VariableInfo getPreviousOrInitial() {
        return previousOrInitial.isLeft() ? previousOrInitial.getLeft().best(levelForPrevious) : previousOrInitial.getRight();
    }

    @Override
    public void setValue(Expression value,
                         LinkedVariables linkedVariables,
                         Map<Property, DV> propertiesToSet,
                         boolean initialOrEvaluation) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = initialOrEvaluation ? previousOrInitial.getRight() : evaluation.get();
        try {
            variableInfo.setValue(value);
        } catch (IllegalStateException ise) {
            LOGGER.error("Variable {}: try to write value {}, already have {}", variableInfo.variable().fullyQualifiedName(),
                    value, variableInfo.getValue());
            throw ise;
        }
        boolean valueIsDone = value.isDone();
        propertiesToSet.forEach((vp, v) -> {
            if (v.isDelayed() && valueIsDone && EvaluationContext.VALUE_PROPERTIES.contains(vp)) {
                throw new IllegalStateException("Not allowed to even try to set delay on a value property");
            }
            variableInfo.setProperty(vp, v);
        });
        try {
            variableInfo.setLinkedVariables(linkedVariables);
        } catch (IllegalStateException ise) {
            LOGGER.error("Variable {}: try to set statically assigned variables to '{}', already have '{}'",
                    variableInfo.variable().fullyQualifiedName(),
                    linkedVariables, variableInfo.getLinkedVariables());
            throw ise;
        }
    }


    @Override
    public void setLinkedVariables(LinkedVariables linkedVariables, Level level) {
        ensureNotFrozen();
        Objects.requireNonNull(linkedVariables);
        assert level != Level.INITIAL;
        VariableInfoImpl variableInfo = getToWrite(level);
        variableInfo.setLinkedVariables(linkedVariables);
    }

    @Override
    public void setProperty(Property property,
                            DV value,
                            boolean failWhenTryingToWriteALowerValue,
                            Level level) {
        ensureNotFrozen();
        Objects.requireNonNull(property);
        VariableInfoImpl variableInfo = getToWrite(level);
        variableInfo.setProperty(property, value);
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
    public void ensureEvaluation(Location location,
                                 AssignmentIds assignmentIds,
                                 String readId,
                                 Set<Integer> readAtStatementTimes) {
        if (!evaluation.isSet()) {
            VariableInfoImpl pi = (VariableInfoImpl) getPreviousOrInitial();

            /* in many situations the following assertions would hold; however, calling from MethodLevelData they do not
           assert !assignmentId.equals(NOT_YET_ASSIGNED) || !pi.isAssigned();
            assert !readId.equals(NOT_YET_READ) || !assignmentId.equals(NOT_YET_ASSIGNED) || !pi.isRead();
             */

            VariableInfoImpl eval = new VariableInfoImpl(location, pi.variable(), assignmentIds, readId,
                    readAtStatementTimes, pi.valueIsSet() ? null : pi.getValue());
            evaluation.set(eval);
            if (!pi.valueIsSet()) {
                eval.setValue(pi.getValue());
            }
        }
    }

    /*
    private void writeLinkedVariablesEnsureEvaluation(LinkedVariables linkedVariables) {
        VariableInfo vi1 = getPreviousOrInitial();
        VariableInfoImpl write;
        if (!evaluation.isSet()) {
            write = new VariableInfoImpl(vi1.variable(), vi1.getAssignmentIds(), vi1.getReadId(), vi1.getStatementTime(),
                    vi1.getReadAtStatementTimes(), vi1.valueIsSet() ? null : vi1.getValue());
            if (vi1.valueIsSet()) write.setValue(vi1.getValue(), false);
            vi1.propertyStream().filter(e -> !GroupPropertyValues.PROPERTIES.contains(e.getKey()))
                    .forEach(e -> write.setProperty(e.getKey(), e.getValue()));
            evaluation.set(write);
        } else {
            write = evaluation.get();
        }
        write.setLinkedVariables(linkedVariables);
    }
*/
    @Override
    public VariableInfo ensureLevelForPropertiesLinkedVariables(Location location, Level level) {
        if (level.equals(Level.EVALUATION) && !evaluation.isSet()) {
            VariableInfo vi1 = getPreviousOrInitial();
            VariableInfoImpl vi = prepareForWritingContextProperties(location, vi1);
            evaluation.set(vi);
            return vi;
        }
        if (level.equals(Level.MERGE) && !has(Level.MERGE)) {
            VariableInfo vi1 = best(Level.EVALUATION);
            if (merge == null) {
                throw new UnsupportedOperationException("Cannot have a merge on " + vi1.variable().fullyQualifiedName());
            }
            VariableInfoImpl vi = prepareForWritingContextProperties(location, vi1);
            merge.set(vi);
            return vi;
        }
        return best(level);
    }

    @Override
    public boolean isPrevious() {
        return previousOrInitial.isLeft();
    }

    @Override
    public boolean has(Level level) {
        return switch (level) {
            case INITIAL -> true;
            case EVALUATION -> evaluation.isSet();
            case MERGE -> merge != null && merge.isSet();
        };
    }

    private VariableInfoImpl prepareForWritingContextProperties(Location location, VariableInfo vi1) {
        VariableInfoImpl write = new VariableInfoImpl(location, vi1.variable(), vi1.getAssignmentIds(),
                vi1.getReadId(), vi1.getReadAtStatementTimes(), vi1.valueIsSet() ? null : vi1.getValue());
        if (vi1.valueIsSet()) write.setValue(vi1.getValue());
        write.setLinkedVariables(vi1.getLinkedVariables());
        vi1.propertyStream().filter(e -> !GroupPropertyValues.PROPERTIES.contains(e.getKey()))
                .forEach(e -> write.setProperty(e.getKey(), e.getValue()));
        return write;
    }

    /*
    Copy from one statement to the next, iteration 1+, into 'evaluation'
    when reading and assigning don't do it. This occurs when another variable
    holds this variable as a value. An evaluation level is created, even though
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
                evaluation.setValue(previous.getValue());
            }
            evaluation.setLinkedVariables(previous.getLinkedVariables());
        }
    }

    @Override
    public void copyFromEvalIntoMerge(GroupPropertyValues groupPropertyValues) {
        assert hasMerge();

        VariableInfo eval = best(Level.EVALUATION);
        Variable v = eval.variable();
        VariableInfoImpl mergeImpl = merge.get();
        mergeImpl.setValue(eval.getValue());
        mergeImpl.setLinkedVariables(eval.getLinkedVariables());

        eval.propertyStream()
                .forEach(e -> {
                    Property vp = e.getKey();
                    DV value = e.getValue();
                    if (GroupPropertyValues.PROPERTIES.contains(vp)) {
                        groupPropertyValues.set(vp, v, value);
                    } else {
                        mergeImpl.setProperty(vp, value);
                    }
                });
        for (Property property : GroupPropertyValues.PROPERTIES) {
            groupPropertyValues.setIfKeyAbsent(property, v, property.valueWhenAbsent());
        }
    }

    @Override
    public Expression merge(EvaluationContext evaluationContext,
                            Expression stateOfDestination,
                            Expression postProcessState,
                            Expression overwriteValue,
                            boolean atLeastOneBlockExecuted,
                            List<ConditionAndVariableInfo> mergeSources,
                            GroupPropertyValues groupPropertyValues) {
        Objects.requireNonNull(mergeSources);
        Objects.requireNonNull(evaluationContext);
        assert merge != null : "For variable " + getPreviousOrInitial().variable().fullyQualifiedName();

        Expression postProcess = activate(postProcessState, evaluationContext);

        VariableInfoImpl existing = currentExcludingMerge();
        if (!merge.isSet()) {
            VariableInfoImpl vii = existing.mergeIntoNewObject(evaluationContext, stateOfDestination,
                    postProcess, overwriteValue, atLeastOneBlockExecuted,
                    mergeSources, groupPropertyValues);
            merge.set(vii);
        } else {
            merge.get().mergeIntoMe(evaluationContext, stateOfDestination,
                    postProcess, overwriteValue, atLeastOneBlockExecuted, existing,
                    mergeSources, groupPropertyValues);
        }
        return merge.get().getValue();
    }

    // post-processing the state is only done under certain limited conditions, currently ONLY to
    // merge variables, defined outside the loop but assigned inside, back to the outside of the loop.
    private Expression activate(Expression postProcessState, EvaluationContext evaluationContext) {
        if (variableNature instanceof VariableNature.VariableDefinedOutsideLoop outside
                && outside.statementIndex().equals(evaluationContext.statementIndex())) {
            return postProcessState;
        }
        return null;
    }
}

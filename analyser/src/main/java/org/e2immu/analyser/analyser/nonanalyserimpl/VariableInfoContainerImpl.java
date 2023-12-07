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
import org.e2immu.analyser.analyser.delay.ProgressWrapper;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.expression.DelayedWrappedExpression;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.e2immu.analyser.analyser.AssignmentIds.NOT_YET_ASSIGNED;
import static org.e2immu.analyser.analyser.Property.*;

public class VariableInfoContainerImpl extends Freezable implements VariableInfoContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInfoContainerImpl.class);

    private final VariableNature variableNature;

    private final Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial;
    private final SetOnce<VariableInfoImpl> evaluation = new SetOnce<>();
    private final SetOnce<VariableInfoImpl> merge;

    private final Stage levelForPrevious;

    private final FlipSwitch removed = new FlipSwitch();
    private final SetOnceMap<Property, DV> propertyOverrides;

    private final FlipSwitch copyToMerge = new FlipSwitch();

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
                previousIsParent ? Stage.EVALUATION : Stage.MERGE,
                previous.propertyOverrides());
    }

    /*
    StatementIndex null means: later iterations, not the creation of VICs.
    See Loops_23 for an example to motivate the while() loop.
     */
    private static VariableNature potentiallyRevertVariableDefinedOutsideLoop(VariableInfoContainer previous,
                                                                              String statementIndex) {
        VariableNature previousVariableNature = previous.variableNature();
        if (statementIndex != null) {
            VariableNature vn = previousVariableNature;
            while (vn instanceof VariableNature.VariableDefinedOutsideLoop inLoop && !statementIndex.startsWith(inLoop.statementIndex())) {
                vn = inLoop.previousVariableNature();
            }
            return vn;
        }
        return previousVariableNature;
    }

    /*
   factory method for existing variables in enclosing methods

   these variables must be implicitly final
    */
    public static VariableInfoContainerImpl copyOfExistingVariableInEnclosingMethod(
            Location location,
            VariableInfoContainer previous,
            boolean statementHasSubBlocks,
            Expression newValue) {
        Objects.requireNonNull(previous);
        VariableInfo outside = previous.current();
        VariableInfoImpl initial = new VariableInfoImpl(location, outside.variable(), NOT_YET_ASSIGNED,
                NOT_YET_READ, Set.of(), newValue, outside.variable().statementTime());
        initial.newVariable();
        initial.setValue(newValue);
        // we leave the LinkedVariables at NOT_YET_SET, a value will be copied in from iteration 1+
        assert initial.getLinkedVariables() == LinkedVariables.NOT_YET_SET;

        outside.propertyStream().forEach(e -> {
            Property property = e.getKey();
            DV current = initial.getProperty(property, null);
            if (current == null || current.isDelayed()) {
                initial.setProperty(property, e.getValue());
            }
        });
        return new VariableInfoContainerImpl(VariableNature.FROM_ENCLOSING_METHOD,
                Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null,
                Stage.MERGE,
                previous.propertyOverrides());
    }

    /*
    factory method for new variables, explicitly setting the variableNature
     */
    public static VariableInfoContainerImpl newVariable(Location location,
                                                        Variable variable,
                                                        VariableNature variableNature,
                                                        boolean statementHasSubBlocks) {
        VariableInfoImpl initial = new VariableInfoImpl(location, variable, NOT_YET_ASSIGNED, NOT_YET_READ,
                Set.of(), null, variable.statementTime());
        initial.setModificationTimeIfNotYetSet(0);
        // no newVariable, because either setValue is called immediately after this method, or the explicit newVariableWithoutValue()
        return new VariableInfoContainerImpl(variableNature, Either.right(initial),
                statementHasSubBlocks ? new SetOnce<>() : null, null, new SetOnceMap<>());
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
        VariableInfoImpl initial = new VariableInfoImpl(location, lvr, new AssignmentIds(index + Stage.INITIAL),
                index + Stage.EVALUATION, Set.of(), null, IGNORE_STATEMENT_TIME);
        initial.newVariable();
        initial.setValue(value);
        value.valueProperties().stream().forEach(e -> initial.setProperty(e.getKey(), e.getValue()));
        initial.setLinkedVariables(LinkedVariables.EMPTY);
        return new VariableInfoContainerImpl(new VariableNature.NormalLocalVariable(index),
                Either.right(initial), statementHasSubBlocks ? new SetOnce<>() : null, null,
                new SetOnceMap<>());
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
                previousIsParent ? Stage.EVALUATION : Stage.MERGE,
                previous.propertyOverrides());
    }

    private VariableInfoContainerImpl(VariableNature variableNature,
                                      Either<VariableInfoContainer, VariableInfoImpl> previousOrInitial,
                                      SetOnce<VariableInfoImpl> merge,
                                      Stage levelForPrevious,
                                      SetOnceMap<Property, DV> propertyOverrides) {
        this.variableNature = Objects.requireNonNull(variableNature);
        this.previousOrInitial = previousOrInitial;
        this.merge = merge;
        this.levelForPrevious = levelForPrevious;
        this.propertyOverrides = propertyOverrides;
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
    public boolean isRecursivelyInitial() {
        if (previousOrInitial.isRight()) return true;
        VariableInfoContainer previous = previousOrInitial.getLeft();
        // levelForPrevious == E or M
        if (!previous.hasEvaluation() && (levelForPrevious == Stage.EVALUATION || !previous.hasMerge())) {
            return previous.isRecursivelyInitial();
        }
        return false;
    }

    public VariableInfo getRecursiveInitialOrNull() {
        if (previousOrInitial.isRight()) return previousOrInitial.getRight();
        VariableInfoContainer previous = previousOrInitial.getLeft();
        // levelForPrevious == E or M
        if (!previous.hasEvaluation() && (levelForPrevious == Stage.EVALUATION || !previous.hasMerge())) {
            return previous.getRecursiveInitialOrNull();
        }
        return null;
    }

    @Override
    public VariableInfo current() {
        if (merge != null && merge.isSet()) return merge.get();
        return currentExcludingMerge();
    }

    private VariableInfoImpl getToWrite(Stage stage) {
        return switch (stage) {
            case INITIAL -> (VariableInfoImpl) getRecursiveInitialOrNull();
            case EVALUATION -> evaluation.get();
            case MERGE -> merge.get();
        };
    }

    VariableInfoImpl currentExcludingMerge() {
        if (evaluation.isSet()) return evaluation.get();
        if (previousOrInitial.isLeft()) return (VariableInfoImpl) previousOrInitial.getLeft().best(levelForPrevious);
        return previousOrInitial.getRight();
    }

    @Override
    public VariableInfo best(Stage level) {
        if (level == Stage.MERGE && merge != null && merge.isSet()) return merge.get();
        if ((level == Stage.MERGE || level == Stage.EVALUATION) && evaluation.isSet()) return evaluation.get();
        return getPreviousOrInitial();
    }

    @Override
    public VariableInfo getPreviousOrInitial() {
        return previousOrInitial.isLeft() ? previousOrInitial.getLeft().best(levelForPrevious) : previousOrInitial.getRight();
    }

    @Override
    public boolean setValue(Expression value,
                            LinkedVariables linkedVariables,
                            Properties properties,
                            Stage stage) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = getToWrite(stage);
        AtomicBoolean progress = new AtomicBoolean();
        try {
            progress.set(variableInfo.setValue(value));
        } catch (IllegalStateException ise) {
            LOGGER.error("Variable {}: try to write value {}, already have {}", variableInfo.variable().fullyQualifiedName(),
                    value, variableInfo.getValue());
            throw ise;
        }
        boolean valueIsDone = value.isDone() && !value.isNotYetAssigned();
        properties.stream().forEach(e -> {
            DV v = e.getValue();
            Property vp = e.getKey();
            if (v.isDelayed() && valueIsDone && vp.propertyType == Property.PropertyType.VALUE) {
                throw new IllegalStateException("Not allowed to even try to set delay on a value property");
            }
            if (variableInfo.setProperty(vp, v)) {
                progress.set(true);
            }
        });
        if (linkedVariables != null) {
            try {
                if (variableInfo.setLinkedVariables(linkedVariables)) progress.set(true);
            } catch (IllegalStateException ise) {
                LOGGER.error("Variable {}: try to set statically assigned variables to '{}', already have '{}'",
                        variableInfo.variable().fullyQualifiedName(),
                        linkedVariables, variableInfo.getLinkedVariables());
                throw ise;
            }
        }
        return progress.get();
    }

    @Override
    public void safeSetValue(Expression value,
                             LinkedVariables linkedVariables,
                             Properties properties,
                             Stage stage) {
        ensureNotFrozen();
        Objects.requireNonNull(value);
        VariableInfoImpl variableInfo = getToWrite(stage);

        if (!variableInfo.valueIsSet()) {
            variableInfo.setValue(value);
            boolean valueIsDone = value.isDone() && !value.isNotYetAssigned();
            properties.stream().forEach(e -> {
                DV v = e.getValue();
                Property vp = e.getKey();
                if (v.isDelayed() && valueIsDone && vp.propertyType == Property.PropertyType.VALUE) {
                    throw new IllegalStateException("Not allowed to even try to set delay on a value property");
                }
                DV current = variableInfo.getProperty(vp, null);
                if (current == null || current.isDelayed()) {
                    variableInfo.setProperty(vp, v);
                } else if (v.isDone() && !current.equals(v)) {
                    throw new IllegalStateException("Changing value from " + current + " to " + v + " for " + vp + ", var " + variableInfo.variable());
                }
            });
        }
        if (linkedVariables != null) {
            variableInfo.setLinkedVariables(linkedVariables);
        }
    }


    @Override
    public boolean setLinkedVariables(LinkedVariables linkedVariables, Stage level) {
        ensureNotFrozen();
        Objects.requireNonNull(linkedVariables);
        assert level != Stage.INITIAL;
        VariableInfoImpl variableInfo = getToWrite(level);
        return variableInfo.setLinkedVariables(linkedVariables);
    }

    // return progress
    @Override
    public boolean setProperty(Property property,
                               DV value,
                               boolean doNotFailWhenTryingToWriteALowerValue,
                               Stage level) {
        // we do not write in some other VIC's merge or evaluation:
        if (level == Stage.INITIAL && !isRecursivelyInitial()) return false;
        ensureNotFrozen();
        Objects.requireNonNull(property);
        VariableInfoImpl variableInfo = getToWrite(level);
        if (doNotFailWhenTryingToWriteALowerValue) {
            DV current = variableInfo.getProperty(property, null);
            if (current != null && !current.isDelayed()) {
                return false;
            }
        }
        return variableInfo.setProperty(property, value);
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
                    readAtStatementTimes, pi.valueIsSet() ? null : pi.getValue(), pi.variable().statementTime());
            evaluation.set(eval);
            if (!pi.valueIsSet()) {
                eval.setValue(pi.getValue());
            }
        }
    }

    @Override
    public void ensureMerge(Location location, String currentIndex) {
        if (!merge.isSet()) {
            VariableInfoImpl pi = (VariableInfoImpl) getPreviousOrInitial();
            AssignmentIds assignmentIds = new AssignmentIds(currentIndex);
            VariableInfoImpl vii = new VariableInfoImpl(location, pi.variable(), assignmentIds, NOT_YET_READ,
                    Set.of(), pi.valueIsSet() ? null : pi.getValue(), pi.variable().statementTime());
            this.merge.set(vii);
        }
    }

    @Override
    public void setDelayedValue(CausesOfDelay causesOfDelay, Stage level) {
        VariableInfoImpl vii = getToWrite(level);
        Expression merged = vii.getValue().mergeDelays(causesOfDelay);
        vii.setValue(merged);
    }

    @Override
    public VariableInfo ensureLevelForPropertiesLinkedVariables(Location location, Stage level) {
        if (level.equals(Stage.EVALUATION) && !evaluation.isSet()) {
            VariableInfo vi1 = getPreviousOrInitial();
            VariableInfoImpl vi = prepareForWritingContextProperties(location, vi1);
            evaluation.set(vi);
            return vi;
        }
        if (level.equals(Stage.MERGE) && !has(Stage.MERGE)) {
            VariableInfo vi1 = best(Stage.EVALUATION);
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
    public boolean has(Stage level) {
        return switch (level) {
            case INITIAL -> true;
            case EVALUATION -> evaluation.isSet();
            case MERGE -> merge != null && merge.isSet();
        };
    }

    private VariableInfoImpl prepareForWritingContextProperties(Location location, VariableInfo vi1) {
        VariableInfoImpl write = new VariableInfoImpl(location, vi1.variable(), vi1.getAssignmentIds(),
                vi1.getReadId(), vi1.getReadAtStatementTimes(), vi1.valueIsSet() ? null : vi1.getValue(),
                vi1.variable().statementTime());
        write.setValue(vi1.getValue());
        vi1.propertyStream().filter(e -> !e.getKey().isGroupProperty())
                .forEach(e -> {
                    assert e.getKey().propertyType != Property.PropertyType.VALUE
                            || vi1.getValue().isDelayed() || e.getValue().isDone();
                    write.setProperty(e.getKey(), e.getValue());
                });
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
                    .filter(e -> !e.getKey().isGroupProperty())
                    .forEach(e -> {
                        assert e.getKey().propertyType != Property.PropertyType.VALUE
                                || previous.getValue().isDelayed() || e.getValue().isDone();
                        setProperty(e.getKey(), e.getValue(), false, Stage.EVALUATION);
                    });

            evaluation.setValue(previous.getValue());
            //   evaluation.setLinkedVariables(previous.getLinkedVariables());
        } else if (previous.getValue().isDelayed() && evaluation.getValue().isDelayed()) {
            // copy the delay, so that we know what the cause of delay is
            // this also speeds up Context Modified, see Container_3
            evaluation.setValue(previous.getValue());
        }
    }

    public AnalysisStatus copyFromPreviousOrInitialIntoEvaluation() {
        assert this.evaluation.isSet();
        VariableInfo previous = getPreviousOrInitial();
        AtomicBoolean progress = new AtomicBoolean();
        previous.propertyStream()
                .filter(e -> !e.getKey().isGroupProperty())
                .forEach(e -> {
                    assert e.getKey().propertyType != Property.PropertyType.VALUE
                            || previous.getValue().isDelayed()
                            || previous.getValue().isNotYetAssigned()
                            || e.getValue().isDone();
                    boolean p = setProperty(e.getKey(), e.getValue(), false,
                            Stage.EVALUATION);
                    if (p) progress.set(true);
                });
        VariableInfoImpl evaluation = this.evaluation.get();
        if (evaluation.setValue(previous.getValue())) progress.set(true);
        CausesOfDelay causes = previous.getValue().causesOfDelay().merge(previous.getLinkedVariables().causesOfDelay());
        return ProgressWrapper.of(progress.get(), causes);
    }

    // when a block changes to Execution.NEVER (see e.g. EvaluateToConstant)
    // we're not too worried about overwriting info
    @Override
    public CausesOfDelay copyAllFromPreviousOrEvalIntoMergeIfMergeExists() {
        if (hasMerge()) {
            VariableInfo best = best(Stage.EVALUATION);
            VariableInfoImpl mergeImpl = merge.get();
            if (mergeImpl.getValue().isDelayed()) mergeImpl.setValue(best.getValue());
            if (!mergeImpl.linkedVariablesIsSet()) {
                mergeImpl.setLinkedVariables(best.getLinkedVariables());
            }
            best.getProperties().forEach((k, v) -> {
                DV dv = mergeImpl.getProperty(k, null);
                if (dv == null || dv.isDelayed()) mergeImpl.setProperty(k, v);
            });
            return mergeImpl.getProperty(Property.EXTERNAL_IMMUTABLE).causesOfDelay()
                    .merge(mergeImpl.getProperty(Property.EXTERNAL_NOT_NULL).causesOfDelay())
                    .merge(mergeImpl.getProperty(Property.CONTAINER_RESTRICTION).causesOfDelay())
                    .merge(mergeImpl.getProperty(Property.EXTERNAL_IGNORE_MODIFICATIONS).causesOfDelay());
        }
        return CausesOfDelay.EMPTY;
    }

    @Override
    public boolean copyNonContextFromPreviousOrEvalToMerge(GroupPropertyValues groupPropertyValues) {
        return copyNonContextFromPreviousOrEvalToMergeOfOther(groupPropertyValues, this);
    }

    @Override
    public boolean copyNonContextFromPreviousOrEvalToMergeOfOther(GroupPropertyValues groupPropertyValues,
                                                                  VariableInfoContainer vicRenamed) {
        assert vicRenamed.hasMerge();
        VariableInfo eval = best(Stage.EVALUATION);
        Variable v = eval.variable();
        VariableInfoImpl mergeImpl = ((VariableInfoContainerImpl) vicRenamed).merge.get();
        AtomicBoolean progress = new AtomicBoolean(mergeImpl.setValue(eval.getValue()));
        // Linked variables have already been written/copied using ComputeLinkedVariables.writeLinkedVariables()

        eval.propertyStream()
                .forEach(e -> {
                    Property vp = e.getKey();
                    DV value = e.getValue();
                    // other variables may refer to us, but we'll not be written by the clustering
                    if (vp.isGroupProperty()) {
                        groupPropertyValues.set(vp, v, value);
                    } else {
                        assert vp.propertyType != Property.PropertyType.VALUE
                                || eval.getValue().isDelayed() || eval.getValue().isNotYetAssigned() || value.isDone();
                        if (mergeImpl.setProperty(vp, value)) progress.set(true);
                    }
                });
        return progress.get();
    }

    @Override
    public void copyInitialFrom(VariableInfo vi1) {
        VariableInfoImpl dest = getToWrite(Stage.INITIAL);
        assert dest != null;
        dest.setValue(vi1.getValue());
        vi1.getProperties().forEach((k, v) -> {
            // See External_14 (exclude EXTERNAL), Loops_21
            if (k.propertyType != PropertyType.EXTERNAL) {
                DV dv = dest.getProperty(k, null);
                if (dv == null || dv.isDelayed()) dest.setProperty(k, v);
            }
        });
    }

    @Override
    public boolean copyFromEvalIntoMerge(GroupPropertyValues groupPropertyValues) {
        assert hasMerge();

        VariableInfo eval = best(Stage.EVALUATION);
        Variable v = eval.variable();
        VariableInfoImpl mergeImpl = merge.get();
        AtomicBoolean progress = new AtomicBoolean(mergeImpl.setValue(eval.getValue()));
        if (mergeImpl.setLinkedVariables(eval.getLinkedVariables())) {
            progress.set(true);
        }

        eval.propertyStream()
                .forEach(e -> {
                    Property vp = e.getKey();
                    DV value = e.getValue();
                    if (vp.isGroupProperty()) {
                        groupPropertyValues.set(vp, v, value);
                    } else {
                        assert vp.propertyType != Property.PropertyType.VALUE
                                || eval.getValue().isDelayed() || value.isDone();
                        if (mergeImpl.setProperty(vp, value)) {
                            progress.set(true);
                        }
                    }
                });
        for (Property property : GroupPropertyValues.PROPERTIES) {
            groupPropertyValues.setIfKeyAbsent(property, v, property.valueWhenAbsent());
        }
        return progress.get();
    }

    void setMerge(VariableInfoImpl vii) {
        merge.set(vii);
    }

    VariableInfoImpl getMerge() {
        return merge.get();
    }

    public boolean canMerge() {
        return merge != null;
    }

    // mainly for debugging
    public Stage getLevelForPrevious() {
        return levelForPrevious;
    }

    @Override
    public String toString() {
        String code = (isInitial() ? "I" : "P") + (hasEvaluation() ? "E" : "-") + (hasMerge() ? "M" : "-");
        return current().variable().fullyQualifiedName() + " " + code;
    }

    @Override
    public boolean isNotRemoved() {
        return !removed.isSet();
    }

    public void remove() {
        if (!removed.isSet()) removed.set();
    }

    public boolean previousIsRemoved() {
        return previousOrInitial.isLeft() && !previousOrInitial.getLeft().isNotRemoved();
    }

    @Override
    public void createAndWriteDelayedWrappedExpressionForEval(Identifier dweId,
                                                              Expression expression,
                                                              Properties properties,
                                                              CausesOfDelay causesOfDelay) {
        VariableInfoImpl vii = getToWrite(Stage.EVALUATION);

        DV immBreakCurr = properties.getOrDefaultNull(IMMUTABLE_BREAK);
        DV immBreak = immBreakCurr == null || immBreakCurr.isDelayed() ? properties.get(IMMUTABLE) : immBreakCurr;
        Properties map = Properties.of(Map.of(IMMUTABLE_BREAK, immBreak));
        Properties merged = properties.merge(map);

        DelayedWrappedExpression dwe = new DelayedWrappedExpression(dweId, vii.variable(),
                expression, merged, vii.getLinkedVariables(), causesOfDelay);
        vii.setValue(dwe);
    }

    @Override
    public void markContextModifiedOverride(DV value) {
        assert value.isDone();
        DV inMap = propertyOverrides.getOrDefaultNull(CONTEXT_MODIFIED);
        if (inMap == null) {
            propertyOverrides.put(CONTEXT_MODIFIED, value);
        } else if (!inMap.equals(value)) {
            throw new UnsupportedOperationException("Overwriting the override, from " + inMap + " to " + value);
        }
    }

    @Override
    public SetOnceMap<Property, DV> propertyOverrides() {
        return propertyOverrides;
    }

    @Override
    public void setModificationTimeIfNotYetSet(int modificationTime, Stage stage) {
        VariableInfoImpl vii = getToWrite(stage);
        vii.setModificationTimeIfNotYetSet(modificationTime);
    }

    @Override
    public boolean isCopyToMerge() {
        return copyToMerge.isSet();
    }

    @Override
    public void ensureCopyToMerge() {
        if (!copyToMerge.isSet()) copyToMerge.set();
    }
}

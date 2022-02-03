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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

/*
Contains all side effects of analysing an expression.
The 'apply' method of the analyser executes them.

It contains:

- an increased statement time, caused by calls to those methods that increase time
- the computed result (value)
- a sequence of stored result, for CommaExpressions and explicit constructor invocations
- an assembled precondition, gathered from calls to methods that have preconditions
- a map of value changes (and linked var, property, ...)
- a list of error messages
- a list of object flows (for later)

Critically, the apply method will effect the value changes before executing the modifications.
Important value changes are:
- a variable has been read at a given statement time
- a variable has been assigned a value
- the linked variables of a variable have been computed

We track delays in state change
 */
public record EvaluationResult(EvaluationContext evaluationContext,
                               int statementTime,
                               Expression value,
                               List<Expression> storedValues,
                               CausesOfDelay causesOfDelay,
                               CausesOfDelay eventualDelays,
                               Messages messages,
                               Map<Variable, ChangeData> changeData,
                               Precondition precondition,
                               boolean addCircularCall) {

    public EvaluationResult {
        assert changeData.values().stream().noneMatch(ecd -> ecd.linkedVariables == null);
        assert causesOfDelay.causesStream().noneMatch(cause -> cause.cause() == CauseOfDelay.Cause.MIN_INT)
                : "Causes of delay: " + causesOfDelay;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationResult.class);

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    public Stream<Map.Entry<Variable, ChangeData>> getExpressionChangeStream() {
        return changeData.entrySet().stream();
    }

    public Expression getExpression() {
        return value;
    }

    /*
    This version of the method redirects to the original, after checking with immediate CNN on variables.
    Example: 'a.x() && a != null' will simplify to 'a.x()', as 'a' will have CNN=5 after evaluating the first part
    of the expression.
     */

    public boolean isNotNull0(boolean useEnnInsteadOfCnn) {
        assert evaluationContext != null;
        if (value instanceof VariableExpression variableExpression) {
            ChangeData cd = changeData.get(variableExpression.variable());
            if (cd != null) {
                DV inChangeData = cd.properties.getOrDefault(Property.CONTEXT_NOT_NULL, null);
                if (inChangeData != null && inChangeData.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return true;
            }
        }
        return evaluationContext.isNotNull0(value, useEnnInsteadOfCnn);
    }

    /**
     * Any of [value, markAssignment, linkedVariables]
     * can be used independently: possibly we want to mark assignment, but still have NO_VALUE for the value.
     * The stateOnAssignment can also still be NO_VALUE while the value is known, and vice versa.
     * <p>
     * Link1 goes from the argument (the owner of the changeData) to the variable in the scope of a method
     * that features a @Dependent1 parameter. (collection.add(t) will have "collection" in the linked1Variables
     * of the changeData of "t").
     */
    public record ChangeData(Expression value,
                             CausesOfDelay delays,
                             CausesOfDelay stateIsDelayed,
                             boolean markAssignment,
                             Set<Integer> readAtStatementTime,
                             LinkedVariables linkedVariables,
                             LinkedVariables toRemoveFromLinkedVariables,
                             Map<Property, DV> properties) {
        public ChangeData {
            Objects.requireNonNull(linkedVariables);
            Objects.requireNonNull(readAtStatementTime);
            Objects.requireNonNull(properties);
        }

        public ChangeData merge(ChangeData other) {
            LinkedVariables combinedLinkedVariables = linkedVariables.merge(other.linkedVariables);
            LinkedVariables combinedToRemove = toRemoveFromLinkedVariables.merge(other.toRemoveFromLinkedVariables);
            Set<Integer> combinedReadAtStatementTime = SetUtil.immutableUnion(readAtStatementTime, other.readAtStatementTime);
            Map<Property, DV> combinedProperties = VariableInfo.mergeIgnoreAbsent(properties, other.properties);
            return new ChangeData(other.value == null ? value : other.value,
                    delays.merge(other.delays),
                    other.stateIsDelayed, // and not a merge!
                    other.markAssignment || markAssignment,
                    combinedReadAtStatementTime,
                    combinedLinkedVariables,
                    combinedToRemove,
                    combinedProperties);
        }

        public DV getProperty(Property property) {
            return properties.getOrDefault(property, property.falseDv);
        }

        public boolean isMarkedRead() {
            return !readAtStatementTime.isEmpty();
        }
    }

    public DV getProperty(Expression expression, Property property) {
        if (expression instanceof VariableExpression ve) {
            ChangeData changeData = changeData().get(ve.variable());
            if (changeData != null) {
                DV inChangeData = changeData.properties.getOrDefault(property, null);
                if (inChangeData != null) return inChangeData;
            }
            return evaluationContext.getPropertyFromPreviousOrInitial(ve.variable(), property);
        }
        return expression.getProperty(evaluationContext, property, true);
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final Messages messages = new Messages();
        private CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        private CausesOfDelay eventualDelays = CausesOfDelay.EMPTY;
        private Expression value;
        private List<Expression> storedExpressions;
        private int statementTime;
        private final Map<Variable, ChangeData> valueChanges = new HashMap<>();
        private Precondition precondition;
        private boolean addCircularCallOrUndeclaredFunctionalInterface;

        // for a constant EvaluationResult
        public Builder() {
            evaluationContext = null;
        }

        public Builder(EvaluationContext evaluationContext) {
            this.evaluationContext = evaluationContext;
            this.statementTime = evaluationContext.getInitialStatementTime();
        }

        public Builder compose(EvaluationResult... previousResults) {
            assert previousResults != null;
            for (EvaluationResult evaluationResult : previousResults) {
                append(false, evaluationResult);
            }
            return this;
        }

        public void composeIgnoreExpression(EvaluationResult... previousResults) {
            assert previousResults != null;
            for (EvaluationResult evaluationResult : previousResults) {
                append(true, evaluationResult);
            }
        }

        public Builder compose(Iterable<EvaluationResult> previousResults) {
            for (EvaluationResult evaluationResult : previousResults) {
                append(false, evaluationResult);
            }
            return this;
        }

        public void composeStore(EvaluationResult evaluationResult) {
            if (storedExpressions == null) storedExpressions = new LinkedList<>();
            storedExpressions.add(evaluationResult.getExpression());
            append(false, evaluationResult);
        }

        private void append(boolean ignoreExpression, EvaluationResult evaluationResult) {
            if (!ignoreExpression && evaluationResult.value != null) {
                setExpression(evaluationResult.value);
            }
            this.causesOfDelay = this.causesOfDelay.merge(evaluationResult.causesOfDelay);
            this.messages.addAll(evaluationResult.getMessageStream());

            for (Map.Entry<Variable, ChangeData> e : evaluationResult.changeData.entrySet()) {
                valueChanges.merge(e.getKey(), e.getValue(), ChangeData::merge);
            }

            statementTime = Math.max(statementTime, evaluationResult.statementTime);

            if (evaluationResult.precondition != null) {
                if (precondition == null) {
                    precondition = evaluationResult.precondition;
                } else {
                    precondition = precondition.combine(evaluationContext, evaluationResult.precondition);
                }
            }

            eventualDelays = eventualDelays.merge(evaluationResult.eventualDelays);
        }

        public void incrementStatementTime() {
            if (evaluationContext.allowedToIncrementStatementTime()) {
                statementTime++;
            }
        }

        public void addCausesOfDelay(CausesOfDelay causesOfDelay) {
            this.causesOfDelay = this.causesOfDelay.merge(causesOfDelay);
        }

        public Builder setExpression(Expression value) {
            assert value != null;

            this.value = value;
            return this;
        }

        public Expression getExpression() {
            return value;
        }

        public int getIteration() {
            return evaluationContext == null ? -1 : evaluationContext.getIteration();
        }

        public EvaluationResult build() {
            return build(true);
        }

        public EvaluationResult build(boolean includeDelaysOnValue) {
            if (value != null) {
                valueChanges.values().forEach(cd -> addCausesOfDelay(cd.delays));
                if (includeDelaysOnValue) {
                    // this is the default; but when assigning to a variable, there is no reason to worry about
                    // the delays on that value; Container_0
                    addCausesOfDelay(value.causesOfDelay());
                }
            }
            return new EvaluationResult(evaluationContext, statementTime, value,
                    storedExpressions == null ? null : List.copyOf(storedExpressions),
                    causesOfDelay,
                    eventualDelays,
                    messages,
                    valueChanges,
                    precondition,
                    addCircularCallOrUndeclaredFunctionalInterface);
        }

        /**
         * Primary method to generate Context Not Null on a variable.
         *
         * @param variable        the variable which occurs in the not null context
         * @param value           the variable's value. This can be a variable expression again (redirect).
         * @param notNullRequired the minimal not null requirement; must be > NULLABLE.
         */
        public void variableOccursInNotNullContext(Variable variable, Expression value, DV notNullRequired) {
            assert evaluationContext != null;
            assert value != null;
            if (notNullRequired.equals(MultiLevel.NULLABLE_DV)) return;
            if (notNullRequired.isDelayed()) {
                // simply set the delay
                setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
                return;
            }
            if (variable instanceof This) return; // nothing to be done here

            if (notNullRequired.equals(MultiLevel.EFFECTIVELY_NOT_NULL_DV) &&
                    (evaluationContext.notNullAccordingToConditionManager(variable)
                            || evaluationContext.notNullAccordingToConditionManager(value))) {
                return; // great, no problem, no reason to complain nor increase the property
            }
            DV contextNotNull = getPropertyFromInitial(variable, Property.CONTEXT_NOT_NULL);
            if (contextNotNull.equals(MultiLevel.NULLABLE_DV)) {
                setProperty(variable, Property.IN_NOT_NULL_CONTEXT, DV.TRUE_DV); // so we can raise an error
            }
            setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
        }

        /*
        it is important that the value is read from initial (-C), and not from evaluation (-E)
         */
        private DV getPropertyFromInitial(Variable variable, Property property) {
            ChangeData changeData = valueChanges.get(variable);
            if (changeData != null) {
                DV inChangeData = changeData.properties.getOrDefault(property, null);
                if (inChangeData != null) return inChangeData;
            }
            return evaluationContext.getPropertyFromPreviousOrInitial(variable, property);
        }

        public Builder markRead(Variable variable) {
            ChangeData ecd = valueChanges.get(variable);
            ChangeData newEcd;
            if (ecd == null) {
                newEcd = new ChangeData(null,
                        CausesOfDelay.EMPTY, CausesOfDelay.EMPTY, false, Set.of(statementTime),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays, ecd.stateIsDelayed, ecd.markAssignment,
                        SetUtil.immutableUnion(ecd.readAtStatementTime, Set.of(statementTime)), ecd.linkedVariables,
                        ecd.toRemoveFromLinkedVariables,
                        ecd.properties);
            }
            valueChanges.put(variable, newEcd);

            // we do this because this. is often implicit (all other scopes will be marked read explicitly!)
            // when explicit, there may be two MarkRead modifications, which will eventually be merged
            if (variable instanceof FieldReference fr && fr.scope instanceof VariableExpression ve) {
                markRead(ve.variable());
            }
            return this;
        }

        public void raiseError(Identifier identifier, Message.Label messageLabel) {
            assert evaluationContext != null;
            if (evaluationContext.haveCurrentStatement()) {
                Message message = Message.newMessage(evaluationContext.getLocation(identifier), messageLabel);
                messages.add(message);
            } else { // e.g. companion analyser
                LOGGER.warn("Analyser error: {}", messageLabel);
            }
        }

        public void raiseError(Identifier identifier, Message.Label messageLabel, String extra) {
            assert evaluationContext != null;
            if (evaluationContext.haveCurrentStatement()) {
                Message message = Message.newMessage(evaluationContext.getLocation(identifier), messageLabel, extra);
                messages.add(message);
            } else {
                LOGGER.warn("Analyser error: {}, {}", messageLabel, extra);
            }
        }

        /* when evaluating a variable field, a new local copy of the variable may be created
           when this happens, we need to link the field to this local copy
           this linking takes place in the value changes map, so that the linked variables can be set once, correctly.
         */
        public Expression currentExpression(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
            ChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression == null || currentExpression.value == null) {
                assert evaluationContext != null;
                return evaluationContext.currentValue(variable, forwardEvaluationInfo);
            }
            return currentExpression.value;
        }

        // called when a new instance is needed because of a modifying method call, or when a variable doesn't have
        // an instance yet. Not called upon assignment.
        private void assignInstanceToVariable(Variable variable, Expression instance, LinkedVariables linkedVariables) {
            ChangeData current = valueChanges.get(variable);
            ChangeData newVcd;
            if (current == null) {
                CausesOfDelay stateIsDelayed = evaluationContext.getConditionManager().causesOfDelay();
                newVcd = new ChangeData(instance, stateIsDelayed, stateIsDelayed, false, Set.of(),
                        linkedVariables, LinkedVariables.EMPTY,
                        Map.of());
            } else {
                newVcd = new ChangeData(instance, current.delays, current.stateIsDelayed, current.markAssignment,
                        current.readAtStatementTime, linkedVariables, current.toRemoveFromLinkedVariables, current.properties);
            }
            valueChanges.put(variable, newVcd);
        }

        /*
        idea: EXT_IMM goes from statement to statement, starting with the value of field analyser (fields, params linked to fields) / type analyser (this)
        we modify along the way if the variable calls a method that changes from BEFORE to AFTER
         */

        public void variableOccursInEventuallyImmutableContext(Identifier identifier,
                                                               Variable variable,
                                                               DV requiredImmutable,
                                                               DV nextImmutable) {
            // no reason, not a method call that changed state
            if (requiredImmutable.equals(MultiLevel.MUTABLE_DV) || requiredImmutable.equals(MultiLevel.NOT_INVOLVED_DV)) {
                return;
            }
            DV currentImmutable = getPropertyFromInitial(variable, Property.EXTERNAL_IMMUTABLE);
            // not_involved: local variables, any type that has no real EXTERNAL_IMMUTABLE value FIXME but local variables can also go through this progression? let's do params and fields first
            // mutable: fields can be mutable before the immutability type is known (public, not explicitly final)
            if (currentImmutable.equals(MultiLevel.MUTABLE_DV) || currentImmutable.equals(MultiLevel.NOT_INVOLVED_DV)) {
                return; // not relevant to this topic
            }
            // but when it matters, the delay on required/next is the same as the delay on EXT_IMM
            assert !requiredImmutable.isDelayed() || currentImmutable.isDelayed();
            if (requiredImmutable.isDelayed()) {
                // this is just a marker to ensure that SAApply/SAEvaluationOfMainExpression does not reach DONE in this iteration
                // markEventualDelay(requiredImmutable.causesOfDelay());
                return;
            }
            // context immutable starts at 1, but this code only kicks in once it has received a value
            // before that value (before the first eventual call, the precondition system reigns
            if (currentImmutable.ge(MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV)) {
                if (MultiLevel.isBeforeThrowWhenNotEventual(requiredImmutable)
                        && !MultiLevel.isBeforeThrowWhenNotEventual(currentImmutable)) {
                    raiseError(identifier, Message.Label.EVENTUAL_BEFORE_REQUIRED);
                } else if (MultiLevel.isAfterThrowWhenNotEventual(requiredImmutable)
                        && !MultiLevel.isAfterThrowWhenNotEventual(currentImmutable)) {
                    raiseError(identifier, Message.Label.EVENTUAL_AFTER_REQUIRED);
                }
            }
            // everything proceeds as normal, we change EXTERNAL_IMMUTABLE
            assert nextImmutable.isDone();
            setProperty(variable, Property.EXTERNAL_IMMUTABLE, nextImmutable);
        }

        private void markEventualDelay(CausesOfDelay causesOfDelay) {
            assert causesOfDelay.isDelayed();
            this.eventualDelays = this.eventualDelays.merge(causesOfDelay);
        }

        /**
         * @param variable the variable in whose context the modification takes place
         * @param modified possibly delayed
         */
        public void markContextModified(Variable variable, DV modified) {
            assert evaluationContext != null;
            DV ignoreContentModifications = variable instanceof FieldReference fr ? evaluationContext.getAnalyserContext()
                    .getFieldAnalysis(fr.fieldInfo).getProperty(Property.IGNORE_MODIFICATIONS)
                    : DV.FALSE_DV;
            if (!ignoreContentModifications.valueIsTrue()) {
                LOGGER.debug("Mark method object as context modified {}: {}", modified, variable.fullyQualifiedName());
                ChangeData cd = valueChanges.get(variable);
                // if the variable is not present yet (a field), we expect it to have been markedRead
                if (cd != null && cd.isMarkedRead() || evaluationContext.isPresent(variable)) {
                    DV currentValue = cd == null ? null : cd.properties.get(Property.CONTEXT_MODIFIED);
                    if (currentValue == null || !currentValue.valueIsTrue()) {
                        setProperty(variable, Property.CONTEXT_MODIFIED, modified);
                    }
                }
                    /*
                    The following code is not allowed, see Container_3: it typically causes a MarkRead in an iteration>0
                    which does not play nice with the copying rules that copy when never read/assigned to.
                    We must rely on normal MethodLevelData linking computation
                    if (value instanceof VariableExpression redirect) {
                        setProperty(redirect.variable(), VariableProperty.MODIFIED, modified);
                        markRead(redirect.variable());
                    }
                    */
            } else {
                LOGGER.debug("Skip marking method object as context modified: {}", variable.fullyQualifiedName());
            }
        }

        /*
        parameters and fields use EXT_CONTAINER to raise errors; they do not wait for a valid value property.

         */
        public void variableOccursInContainerContext(Variable variable, DV containerRequired) {
            assert evaluationContext != null;

            if (containerRequired.isDelayed()) {
                setProperty(variable, Property.CONTEXT_CONTAINER, containerRequired);
                return;
            }
            if (containerRequired.equals(MultiLevel.NOT_CONTAINER_DV)) {
                setProperty(variable, Property.CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
                return;
            }
            if (variable instanceof FieldReference || variable instanceof ParameterInfo) {
                // will come back later
                DV external = getPropertyFromInitial(variable, Property.EXTERNAL_CONTAINER);
                if (external.equals(MultiLevel.NOT_CONTAINER_DV)) {
                    Message message = Message.newMessage(evaluationContext.getLocation(), Message.Label.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                    messages.add(message);
                }
                setProperty(variable, Property.CONTEXT_CONTAINER, MultiLevel.CONTAINER_DV);
                return;
            }
            DV container = getPropertyFromInitial(variable, Property.CONTAINER);
            if (container.isDelayed()) {
                setProperty(variable, Property.CONTEXT_CONTAINER, container);
                return;
            }
            if (container.equals(MultiLevel.CONTAINER_DV)) {
                return;
            }
            Message message = Message.newMessage(evaluationContext.getLocation(), Message.Label.MODIFICATION_NOT_ALLOWED, variable.simpleName());
            messages.add(message);
            setProperty(variable, Property.CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        }

        public Builder assignmentToSelfIgnored(Variable variable) {
            Message message = Message.newMessage(evaluationContext.getLocation(), Message.Label.ASSIGNMENT_TO_SELF, variable.fullyQualifiedName());
            messages.add(message);
            return this;
        }

        public void assignmentToCurrentValue(Variable variable) {
            Message message = Message.newMessage(evaluationContext.getLocation(), Message.Label.ASSIGNMENT_TO_CURRENT_VALUE, variable.fullyQualifiedName());
            messages.add(message);
        }

        /*
        Called from Assignment and from LocalVariableCreation.
         */
        public Builder assignment(Variable assignmentTarget,
                                  Expression resultOfExpression,
                                  LinkedVariables linkedVariables) {
            assert evaluationContext != null;

            // if we assign a new value to a target, the other variables linking to the target
            // lose this linking (See e.g. Basics_7)
            removeVariableFromOtherLinkedVariables(assignmentTarget);

            CausesOfDelay stateIsDelayed = evaluationContext.getConditionManager().stateDelayedOrPreconditionDelayed();
            // NOTE: we cannot use the @NotNull of the result in DelayedExpression.forState (Loops_1 is a good counter-example)
            boolean markAssignment = resultOfExpression != EmptyExpression.EMPTY_EXPRESSION;

            // in case both state and result of expression are delayed, we give preference to the result
            // we do NOT take the delayed state into account when the assignment target is the reason for the delay
            // see FirstThen_0 and Singleton_7
            Expression value = stateIsDelayed.isDelayed()
                    && !resultOfExpression.isDelayed()
                    && !evaluationContext.getConditionManager().isReasonForDelay(assignmentTarget)
                    ? DelayedExpression.forState(resultOfExpression.returnType(),
                    resultOfExpression.linkedVariables(evaluationContext).changeAllToDelay(stateIsDelayed),
                    stateIsDelayed) : resultOfExpression;

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(assignmentTarget);
            if (ecd == null) {
                newEcd = new ChangeData(value, value.causesOfDelay().merge(stateIsDelayed),
                        stateIsDelayed, markAssignment, Set.of(), linkedVariables, LinkedVariables.EMPTY, Map.of());
            } else {
                newEcd = new ChangeData(value, ecd.delays.merge(stateIsDelayed).merge(value.causesOfDelay()),
                        ecd.stateIsDelayed.merge(stateIsDelayed), ecd.markAssignment || markAssignment,
                        ecd.readAtStatementTime, linkedVariables, LinkedVariables.EMPTY, ecd.properties);
            }
            valueChanges.put(assignmentTarget, newEcd);
            return this;
        }

        private void removeVariableFromOtherLinkedVariables(Variable assignmentTarget) {
            LinkedVariables currentLinkedVariables = evaluationContext.linkedVariables(assignmentTarget);
            if (currentLinkedVariables == null) return; // nothing, we don't even know this variable yet
            for (Variable variable : currentLinkedVariables.variables().keySet()) {
                if (!variable.equals(assignmentTarget)) {
                    LinkedVariables linkedVariables = evaluationContext.linkedVariables(variable);
                    //  it is possible that the variable cannot be found; see InstanceOf_11
                    // the problem is that ER looks at the previous situation, where linked variables
                    // exist which will not exist in this statement
                    if (linkedVariables != null && linkedVariables.contains(assignmentTarget)) {
                        removeFromLinkedVariables(variable, assignmentTarget);
                    }
                }
            }
        }

        private void removeFromLinkedVariables(Variable variable, Variable assignmentTarget) {
            LinkedVariables removeLv = LinkedVariables.of(assignmentTarget, LinkedVariables.ASSIGNED_DV);
            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, CausesOfDelay.EMPTY, CausesOfDelay.EMPTY, false,
                        Set.of(), LinkedVariables.EMPTY, removeLv, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays, ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, LinkedVariables.EMPTY,
                        removeLv.merge(ecd.toRemoveFromLinkedVariables), ecd.properties);
            }
            valueChanges.put(variable, newEcd);
        }

        // Used in transformation of parameter lists
        public void setProperty(Variable variable, Property property, DV value) {
            assert evaluationContext != null;

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, value.causesOfDelay(), CausesOfDelay.EMPTY, false, Set.of(),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(property, value));
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays.merge(value.causesOfDelay()), ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables, ecd.toRemoveFromLinkedVariables,
                        mergeProperties(ecd.properties, Map.of(property, value)));
            }
            valueChanges.put(variable, newEcd);
        }

        private Map<Property, DV> mergeProperties
                (Map<Property, DV> m1, Map<Property, DV> m2) {
            Map<Property, DV> res = new HashMap<>(m1);
            m2.forEach((vp, v) -> res.merge(vp, v, DV::max));
            return Map.copyOf(res);
        }

        /*
      we use a null value for inScope to indicate a delay
       */
        public void link(Variable from, Variable to, DV level) {
            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(from);
            LinkedVariables linked = new LinkedVariables(Map.of(to, level));
            if (ecd == null) {
                newEcd = new ChangeData(null, level.causesOfDelay(),
                        CausesOfDelay.EMPTY, false, Set.of(), linked, LinkedVariables.EMPTY, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays.merge(level.causesOfDelay()),
                        ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables.merge(linked), ecd.toRemoveFromLinkedVariables, ecd.properties);
            }
            valueChanges.put(from, newEcd);
        }

        public void addPrecondition(Precondition newPrecondition) {
            if (precondition == null) {
                precondition = newPrecondition;
            } else {
                precondition = precondition.combine(evaluationContext, newPrecondition);
            }
        }

        public void modifyingMethodAccess(Variable variable, Expression newInstance, LinkedVariables linkedVariables) {
            assignInstanceToVariable(variable, newInstance, linkedVariables);
        }

        public void addErrorAssigningToFieldOutsideType(FieldInfo fieldInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(evaluationContext.getLocation(),
                    Message.Label.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE,
                    fieldInfo.fullyQualifiedName()));
        }

        public void addParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(parameterInfo.newLocation(),
                    Message.Label.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        public void addCircularCall() {
            addCircularCallOrUndeclaredFunctionalInterface = true;
        }

        public int getStatementTime() {
            return statementTime;
        }

        public void addDelayOnPrecondition(CausesOfDelay causes) {
            addPrecondition(Precondition.forDelayed(DelayedExpression.forPrecondition(evaluationContext.getPrimitives(), causes)));
        }

    }
}

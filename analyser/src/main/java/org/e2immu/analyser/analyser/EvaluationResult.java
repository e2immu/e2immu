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

import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NewObject;
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

import static org.e2immu.analyser.util.Logger.LogTarget.CONTEXT_MODIFICATION;
import static org.e2immu.analyser.util.Logger.log;

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
                               boolean someValueWasDelayed,
                               Messages messages,
                               Map<Variable, ChangeData> changeData,
                               Precondition precondition,
                               boolean addCircularCall,
                               Map<WithInspectionAndAnalysis, Boolean> causesOfContextModificationDelay) {

    public EvaluationResult {
        assert changeData.values().stream().noneMatch(ecd -> ecd.linkedVariables == null);
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
                Integer inChangeData = cd.properties.getOrDefault(VariableProperty.CONTEXT_NOT_NULL, null);
                if (inChangeData != null && inChangeData >= MultiLevel.EFFECTIVELY_NOT_NULL) return true;
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
                             boolean stateIsDelayed,
                             boolean markAssignment,
                             Set<Integer> readAtStatementTime,
                             LinkedVariables linkedVariables,
                             LinkedVariables staticallyAssignedVariables,
                             LinkedVariables linked1Variables,
                             Map<VariableProperty, Integer> properties) {
        public ChangeData {
            Objects.requireNonNull(linkedVariables);
            Objects.requireNonNull(readAtStatementTime);
            Objects.requireNonNull(staticallyAssignedVariables);
            Objects.requireNonNull(linked1Variables);
            Objects.requireNonNull(properties);
        }

        public ChangeData merge(ChangeData other) {
            LinkedVariables combinedLinkedVariables = linkedVariables.merge(other.linkedVariables);
            LinkedVariables combinedStaticallyAssignedVariables = staticallyAssignedVariables
                    .merge(other.staticallyAssignedVariables);
            LinkedVariables combinedLinked1Variables = linked1Variables.merge(other.linked1Variables);
            Set<Integer> combinedReadAtStatementTime = SetUtil.immutableUnion(readAtStatementTime, other.readAtStatementTime);
            Map<VariableProperty, Integer> combinedProperties = VariableInfoImpl.mergeIgnoreAbsent(properties, other.properties);
            return new ChangeData(other.value == null ? value : other.value,
                    other.stateIsDelayed,
                    other.markAssignment || markAssignment,
                    combinedReadAtStatementTime,
                    combinedLinkedVariables,
                    combinedStaticallyAssignedVariables,
                    combinedLinked1Variables,
                    combinedProperties);
        }

        public boolean haveContextMethodDelay() {
            return properties.getOrDefault(VariableProperty.CONTEXT_MODIFIED_DELAY, Level.DELAY) == Level.TRUE;
        }

        public boolean havePropagationModificationDelay() {
            return properties.getOrDefault(VariableProperty.PROPAGATE_MODIFICATION_DELAY, Level.DELAY) == Level.TRUE;
        }

        public boolean haveDelaysCausedByMethodCalls() {
            return properties.getOrDefault(VariableProperty.SCOPE_DELAY, Level.DELAY) == Level.TRUE;
        }

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }
    }

    public int getProperty(Expression expression, VariableProperty variableProperty) {
        if (expression instanceof VariableExpression ve) {
            ChangeData changeData = changeData().get(ve.variable());
            if (changeData != null) {
                Integer inChangeData = changeData.properties.getOrDefault(variableProperty, null);
                if (inChangeData != null) return inChangeData;
            }
            return evaluationContext.getPropertyFromPreviousOrInitial(ve.variable(), variableProperty, statementTime);
        }
        return expression.getProperty(evaluationContext, variableProperty, true);
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final Messages messages = new Messages();
        private Expression value;
        private List<Expression> storedExpressions;
        private int statementTime;
        private final Map<Variable, ChangeData> valueChanges = new HashMap<>();
        private Precondition precondition;
        private boolean addCircularCallOrUndeclaredFunctionalInterface;
        private boolean someValueWasDelayed;
        private final Map<WithInspectionAndAnalysis, Boolean> causesOfContextModificationDelays = new HashMap<>();

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
            causesOfContextModificationDelays.putAll(evaluationResult.causesOfContextModificationDelay);
        }

        public void incrementStatementTime() {
            if (evaluationContext.allowedToIncrementStatementTime()) {
                statementTime++;
            }
        }

        public Builder setExpression(Expression value) {
            assert value != null;

            this.value = value;
            someValueWasDelayed |= evaluationContext != null && evaluationContext.isDelayed(value) || value instanceof DelayedExpression;
            return this;
        }

        public Expression getExpression() {
            return value;
        }

        public int getIteration() {
            return evaluationContext == null ? -1 : evaluationContext.getIteration();
        }

        public EvaluationResult build() {
            return new EvaluationResult(evaluationContext, statementTime, value,
                    storedExpressions == null ? null : List.copyOf(storedExpressions),
                    someValueWasDelayed,
                    messages,
                    valueChanges,
                    precondition,
                    addCircularCallOrUndeclaredFunctionalInterface,
                    Map.copyOf(causesOfContextModificationDelays));
        }

        /**
         * Primary method to generate Context Not Null on a variable.
         *
         * @param variable        the variable which occurs in the not null context
         * @param value           the variable's value. This can be a variable expression again (redirect).
         * @param notNullRequired the minimal not null requirement; must be > NULLABLE.
         */
        public void variableOccursInNotNullContext(Variable variable, Expression value, int notNullRequired) {
            assert evaluationContext != null;
            assert value != null;
            assert notNullRequired > MultiLevel.NULLABLE;

            if (variable instanceof This) return; // nothing to be done here

            if (notNullRequired == MultiLevel.EFFECTIVELY_NOT_NULL &&
                    evaluationContext.notNullAccordingToConditionManager(variable)) {
                return; // great, no problem, no reason to complain nor increase the property
            }

            int notNullValue = evaluationContext.getProperty(value, VariableProperty.NOT_NULL_EXPRESSION, true, false);
            if (notNullValue < notNullRequired) { // also do delayed values
                // so intrinsically we can have null.
                // if context not null is already high enough, don't complain
                int contextNotNull = getPropertyFromInitial(variable, VariableProperty.CONTEXT_NOT_NULL);
                if (contextNotNull == MultiLevel.NULLABLE) {
                    setProperty(variable, VariableProperty.IN_NOT_NULL_CONTEXT, Level.TRUE); // so we can raise an error
                }
                setProperty(variable, VariableProperty.CONTEXT_NOT_NULL, notNullRequired);
            }

        }

        private int getContainerFromInitial(Expression expression) {
            if (expression instanceof VariableExpression variableExpression) {
                return getPropertyFromInitial(variableExpression.variable(), VariableProperty.CONTAINER);
            }
            return evaluationContext.getProperty(expression, VariableProperty.CONTAINER, true, false);
        }

        /*
        it is important that the value is read from initial (-C), and not from evaluation (-E)
         */
        private int getPropertyFromInitial(Variable variable, VariableProperty variableProperty) {
            ChangeData changeData = valueChanges.get(variable);
            if (changeData != null) {
                Integer inChangeData = changeData.properties.getOrDefault(variableProperty, null);
                if (inChangeData != null) return inChangeData;
            }
            return evaluationContext.getPropertyFromPreviousOrInitial(variable, variableProperty, statementTime);
        }

        public Builder markRead(Variable variable) {
            return markRead(variable, true);
        }

        private Builder markRead(Variable variable, boolean recurse) {
            ChangeData ecd = valueChanges.get(variable);
            ChangeData newEcd;
            if (ecd == null) {
                newEcd = new ChangeData(null,
                        false, false, Set.of(statementTime),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        SetUtil.immutableUnion(ecd.readAtStatementTime, Set.of(statementTime)), ecd.linkedVariables,
                        ecd.staticallyAssignedVariables, ecd.linked1Variables, ecd.properties);
            }
            valueChanges.put(variable, newEcd);

            // we do this because this. is often implicit (all other scopes will be marked read explicitly!)
            // when explicit, there may be two MarkRead modifications, which will eventually be merged
            if (recurse && variable instanceof FieldReference fr && fr.scope instanceof VariableExpression ve) {
                markRead(ve.variable(), true);
            }
            return this;
        }

        public void raiseError(Identifier identifier, Message.Label messageLabel) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
                Message message = Message.newMessage(evaluationContext.getLocation(identifier), messageLabel);
                messages.add(message);
            } else { // e.g. companion analyser
                LOGGER.warn("Analyser error: {}", messageLabel);
            }
        }

        public void raiseError(Identifier identifier, Message.Label messageLabel, String extra) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
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
                return evaluationContext.currentValue(variable, statementTime, forwardEvaluationInfo);
            }
            return currentExpression.value;
        }

        // called when a new instance is needed because of a modifying method call, or when a variable doesn't have
        // an instance yet. Not called upon assignment.
        private void assignInstanceToVariable(Variable variable, Expression instance, LinkedVariables
                linkedVariables) {
            ChangeData current = valueChanges.get(variable);
            ChangeData newVcd;
            if (current == null) {
                boolean stateIsDelayed = evaluationContext.getConditionManager().isDelayed();
                newVcd = new ChangeData(instance, stateIsDelayed, false, Set.of(), linkedVariables,
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of());
            } else {
                newVcd = new ChangeData(instance, current.stateIsDelayed, current.markAssignment,
                        current.readAtStatementTime, linkedVariables, current.staticallyAssignedVariables,
                        current.linked1Variables, current.properties);
            }
            valueChanges.put(variable, newVcd);
        }

        public void markContextModifiedDelay(Variable variable) {
            setProperty(variable, VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE);
        }

        public void markPropagateModificationDelay(Variable variable) {
            setProperty(variable, VariableProperty.PROPAGATE_MODIFICATION_DELAY, Level.TRUE);
        }

        public void markContextNotNullDelay(Variable variable) {
            setProperty(variable, VariableProperty.CONTEXT_NOT_NULL_DELAY, Level.TRUE);
        }

        public void markContextImmutableDelay(Variable variable) {
            setProperty(variable, VariableProperty.CONTEXT_IMMUTABLE_DELAY, Level.TRUE);
        }

        public void variableOccursInEventuallyImmutableContext(Identifier identifier,
                                                               Variable variable, int requiredImmutable, int nextImmutable) {
            // context immutable starts at 1, but this code only kicks in once it has received a value
            // before that value (before the first eventual call, the precondition system reigns
            int currentImmutable = getPropertyFromInitial(variable, VariableProperty.CONTEXT_IMMUTABLE);
            if (currentImmutable >= MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK) {
                if (MultiLevel.isBeforeThrowWhenNotEventual(requiredImmutable) && !MultiLevel.isBeforeThrowWhenNotEventual(currentImmutable)) {
                    raiseError(identifier, Message.Label.EVENTUAL_BEFORE_REQUIRED);
                } else if (MultiLevel.isAfterThrowWhenNotEventual(requiredImmutable) && !MultiLevel.isAfterThrowWhenNotEventual(currentImmutable)) {
                    raiseError(identifier, Message.Label.EVENTUAL_AFTER_REQUIRED);
                }
            }
            // everything proceeds as normal
            assert evaluationContext == null || nextImmutable != Level.DELAY ||
                    evaluationContext.createDelay("variableOccursInContext",
                            variable.fullyQualifiedName() + "@" + evaluationContext.statementIndex() + DelayDebugger.D_CONTEXT_IMMUTABLE);
            setProperty(variable, VariableProperty.CONTEXT_IMMUTABLE, nextImmutable);
        }

        public void markMethodCalled(Variable variable) {
            assert evaluationContext != null;

            Variable v;
            if (variable instanceof This) {
                v = variable;
            } else if (variable.concreteReturnType().typeInfo == evaluationContext.getCurrentType()) {
                v = new This(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentType());
            } else v = null;
            if (v != null) {
                setProperty(v, VariableProperty.METHOD_CALLED, Level.TRUE);
            }
        }

        public void markContextModified(Variable variable, int modified) {
            assert evaluationContext != null;
            if (evaluationContext.isPresent(variable)) {
                int ignoreContentModifications = variable instanceof FieldReference fr ? evaluationContext.getAnalyserContext()
                        .getFieldAnalysis(fr.fieldInfo).getProperty(VariableProperty.IGNORE_MODIFICATIONS)
                        : Level.FALSE;
                if (ignoreContentModifications != Level.TRUE) {
                    log(CONTEXT_MODIFICATION, "Mark method object as context modified {}: {}", modified, variable.fullyQualifiedName());
                    setProperty(variable, VariableProperty.CONTEXT_MODIFIED, modified);

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
                    log(CONTEXT_MODIFICATION, "Skip marking method object as context modified: {}", variable.fullyQualifiedName());
                }
            } else {
                log(CONTEXT_MODIFICATION, "Not yet marking {} as context modified, not present", variable.fullyQualifiedName());
            }
        }

        public void variableOccursInContainerContext(Variable variable, Expression currentExpression) {
            assert evaluationContext != null;

            if (evaluationContext.isDelayed(currentExpression)) return; // not yet
            // if we already know that the variable is NOT @NotModified1, then we'll raise an error
            int container = getContainerFromInitial(currentExpression);
            if (container == Level.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.Label.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                messages.add(message);
            } else if (container == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                setProperty(variable, VariableProperty.CONTAINER, Level.TRUE);
            }
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
        Linked1Variables are not directly involved with assignments; handled later.
         */
        public Builder assignment(Variable assignmentTarget,
                                  Expression resultOfExpression,
                                  LinkedVariables staticallyAssignedVariables,
                                  LinkedVariables linkedVariables,
                                  LinkedVariables linked1Variables) {
            assert evaluationContext != null;
            boolean stateIsDelayed = evaluationContext.getConditionManager().isStateDelayedOrPreconditionDelayed();
            boolean resultOfExpressionIsDelayed = evaluationContext.isDelayed(resultOfExpression);
            // NOTE: we cannot use the @NotNull of the result in DelayedExpression.forState (Loops_1 is a good counter-example)
            boolean markAssignment = resultOfExpression != EmptyExpression.EMPTY_EXPRESSION;

            // in case both state and result of expression are delayed, we give preference to the result
            // we do NOT take the delayed state into account when the assignment target is the reason for the delay
            // see FirstThen_0 and Singleton_7
            Expression value = stateIsDelayed
                    && !resultOfExpressionIsDelayed
                    && !evaluationContext.getConditionManager().isReasonForDelay(assignmentTarget)
                    ? DelayedExpression.forState(resultOfExpression.returnType(),
                    evaluationContext.linkedVariables(resultOfExpression).variablesAsList()) : resultOfExpression;

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(assignmentTarget);
            if (ecd == null) {
                newEcd = new ChangeData(value, stateIsDelayed, markAssignment, Set.of(), linkedVariables,
                        staticallyAssignedVariables, linked1Variables, Map.of());
            } else {
                newEcd = new ChangeData(value, stateIsDelayed, ecd.markAssignment || markAssignment,
                        ecd.readAtStatementTime, linkedVariables, staticallyAssignedVariables,
                        ecd.linked1Variables, ecd.properties);
            }
            valueChanges.put(assignmentTarget, newEcd);
            return this;
        }

        // Used in transformation of parameter lists
        public void setProperty(Variable variable, VariableProperty property, int value) {
            assert evaluationContext != null;

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, false, false, Set.of(),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(property, value));
            } else {
                newEcd = new ChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables, ecd.staticallyAssignedVariables,
                        ecd.linked1Variables, mergeProperties(ecd.properties, Map.of(property, value)));
            }
            valueChanges.put(variable, newEcd);
        }

        public void eraseContextModified(Variable variable) {
            ChangeData ecd = valueChanges.get(variable);
            if (ecd != null) {
                Map<VariableProperty, Integer> propertiesWithoutContextModified = new HashMap<>(ecd.properties);
                propertiesWithoutContextModified.remove(VariableProperty.CONTEXT_MODIFIED);
                ChangeData newChangeData = new ChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables, ecd.staticallyAssignedVariables,
                        ecd.linked1Variables, Map.copyOf(propertiesWithoutContextModified));
                valueChanges.put(variable, newChangeData);
            }
        }

        private Map<VariableProperty, Integer> mergeProperties
                (Map<VariableProperty, Integer> m1, Map<VariableProperty, Integer> m2) {
            Map<VariableProperty, Integer> res = new HashMap<>(m1);
            m2.forEach((vp, v) -> res.merge(vp, v, Math::max));
            return Map.copyOf(res);
        }

        /*
        we use a null value for inScope to indicate a delay
         */
        public void link1(Variable inArgument, Variable inScope, boolean delayed) {
            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(inArgument);
            LinkedVariables linked1 = inScope == null ? LinkedVariables.DELAYED_EMPTY :
                    new LinkedVariables(Set.of(inScope), delayed);
            if (ecd == null) {
                newEcd = new ChangeData(null, false, false, Set.of(), LinkedVariables.EMPTY,
                        LinkedVariables.EMPTY, linked1, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables, ecd.staticallyAssignedVariables,
                        ecd.linked1Variables.merge(linked1), ecd.properties);
            }
            valueChanges.put(inArgument, newEcd);
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
            messages.add(Message.newMessage(new Location(parameterInfo),
                    Message.Label.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        public void addCircularCall() {
            addCircularCallOrUndeclaredFunctionalInterface = true;
        }

        public int getStatementTime() {
            return statementTime;
        }

        private Variable acceptForMarkingRemoveScopeOfFields(Variable variable, TypeInfo subType) {
            assert evaluationContext != null;
            if (evaluationContext.isPresent(variable)) return variable;
            if (variable instanceof FieldReference fieldReference &&
                    fieldReference.fieldInfo.owner != subType &&
                    fieldReference.fieldInfo.owner.primaryType() == subType.primaryType()) {
                // remove the scope, replace by "this" when our this has access to them; replace by "instance type ..."
                // when the type is static
                if (evaluationContext.getCurrentType().hasAccessToFieldsOf(evaluationContext.getAnalyserContext(),
                        fieldReference.fieldInfo.owner)) {
                    return new FieldReference(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo);
                }
                return new FieldReference(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo,
                        NewObject.genericFieldAccess(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo));
            }
            return null;
        }

        public void markVariablesFromSubMethod(MethodAnalysis methodAnalysis) {
            StatementAnalysis statementAnalysis = methodAnalysis.getLastStatement();
            if (statementAnalysis == null) return; // nothing we can do here
            statementAnalysis.variableStream()
                    .forEach(variableInfo -> {
                        Variable variable = acceptForMarkingRemoveScopeOfFields(variableInfo.variable(),
                                methodAnalysis.getMethodInfo().typeInfo);
                        if (variable != null) {
                            if (variableInfo.isRead()) {
                                markRead(variable, false);
                            }
                            if (variableInfo.isAssigned()) {
                                assignment(variable, variableInfo.getValue(), variableInfo.getStaticallyAssignedVariables(),
                                        variableInfo.getLinkedVariables(), variableInfo.getLinked1Variables());
                            }
                        }
                    });
        }

        public void markVariablesFromPrimaryTypeAnalyser(PrimaryTypeAnalyser pta) {
            pta.methodAnalyserStream().forEach(ma -> markVariablesFromSubMethod(ma.methodAnalysis));
            pta.fieldAnalyserStream().forEach(fa -> markVariablesFromSubFieldInitializers(fa.fieldAnalysis, fa.primaryType));
        }

        private void markVariablesFromSubFieldInitializers(FieldAnalysisImpl.Builder fieldAnalysis, TypeInfo subType) {
            assert evaluationContext != null;
            Expression initialValue = fieldAnalysis.getInitialValue();
            if (initialValue == EmptyExpression.EMPTY_EXPRESSION || initialValue == null) return;
            initialValue.variables().forEach(variable -> {
                Variable v = acceptForMarkingRemoveScopeOfFields(variable, subType);
                if (v != null) markRead(v);
            });
        }

        public void addDelayOnPrecondition() {
            addPrecondition(Precondition.forDelayed(DelayedExpression.forPrecondition(evaluationContext.getPrimitives())));
        }

        // can be called for multiple parameters, a value of 'true' should always survive
        public void causeOfContextModificationDelay(MethodInfo methodInfo, boolean delay) {
            if (methodInfo != null) {
                causesOfContextModificationDelays.merge(methodInfo, delay, (orig, val) -> orig || val);
            }
        }

        public LinkedVariables linked1Variables(Expression expression) {
            if (expression instanceof VariableExpression ve) {
                ChangeData cd = valueChanges.get(ve.variable());
                if (cd != null) {
                    return cd.linked1Variables;
                }
            }
            return evaluationContext.linked1Variables(expression);
        }

        public void registerLinked1(Variable variable, LinkedVariables linked1Scope) {
            for (Variable linked : linked1Scope.variables()) {
                link1(variable, linked, linked1Scope.isDelayed());
            }
        }
    }
}

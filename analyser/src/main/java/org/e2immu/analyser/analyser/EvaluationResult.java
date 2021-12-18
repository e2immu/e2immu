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
                               CausesOfDelay causes,
                               Messages messages,
                               Map<Variable, ChangeData> changeData,
                               Precondition precondition,
                               boolean addCircularCall,
                               Map<WithInspectionAndAnalysis, Boolean> causesOfContextModificationDelay) {

    public EvaluationResult {
        assert changeData.values().stream().noneMatch(ecd -> ecd.linkedVariables == null);
        boolean noMinInt = causes.causesStream().noneMatch(cause -> cause.cause() == CauseOfDelay.Cause.MIN_INT);
        assert noMinInt;
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
                             Map<Property, DV> properties) {
        public ChangeData {
            Objects.requireNonNull(linkedVariables);
            Objects.requireNonNull(readAtStatementTime);
            Objects.requireNonNull(properties);
        }

        public ChangeData merge(ChangeData other) {
            LinkedVariables combinedLinkedVariables = linkedVariables.merge(other.linkedVariables);
            Set<Integer> combinedReadAtStatementTime = SetUtil.immutableUnion(readAtStatementTime, other.readAtStatementTime);
            Map<Property, DV> combinedProperties = VariableInfoImpl.mergeIgnoreAbsent(properties, other.properties);
            return new ChangeData(other.value == null ? value : other.value,
                    delays.merge(other.delays),
                    other.stateIsDelayed, // and not a merge!
                    other.markAssignment || markAssignment,
                    combinedReadAtStatementTime,
                    combinedLinkedVariables,
                    combinedProperties);
        }

        public boolean haveContextMethodDelay() {
            return properties.getOrDefault(Property.CONTEXT_MODIFIED, Level.FALSE_DV).isDelayed();
        }

        public boolean havePropagationModificationDelay() {
            return properties.getOrDefault(Property.PROPAGATE_MODIFICATION, Level.FALSE_DV).isDelayed();
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
            return evaluationContext.getPropertyFromPreviousOrInitial(ve.variable(), property, statementTime);
        }
        return expression.getProperty(evaluationContext, property, true);
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final Messages messages = new Messages();
        private CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        private Expression value;
        private List<Expression> storedExpressions;
        private int statementTime;
        private final Map<Variable, ChangeData> valueChanges = new HashMap<>();
        private Precondition precondition;
        private boolean addCircularCallOrUndeclaredFunctionalInterface;
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
            if (value != null) {
                valueChanges.values().forEach(cd -> addCausesOfDelay(cd.delays));
                addCausesOfDelay(value.causesOfDelay());
            }
            return new EvaluationResult(evaluationContext, statementTime, value,
                    storedExpressions == null ? null : List.copyOf(storedExpressions),
                    causesOfDelay,
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

            DV notNullValue = evaluationContext.getProperty(value, Property.NOT_NULL_EXPRESSION, true, false);
            //  if (notNullValue < notNullRequired) { // also do delayed values
            // so intrinsically we can have null.
            // if context not null is already high enough, don't complain
            DV contextNotNull = getPropertyFromInitial(variable, Property.CONTEXT_NOT_NULL);
            if (contextNotNull.equals(MultiLevel.NULLABLE_DV)) {
                setProperty(variable, Property.IN_NOT_NULL_CONTEXT, Level.TRUE_DV); // so we can raise an error
            }
            setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
            //  }

        }

        private DV getContainerFromInitial(Expression expression) {
            if (expression instanceof VariableExpression variableExpression) {
                return getPropertyFromInitial(variableExpression.variable(), Property.CONTAINER);
            }
            return evaluationContext.getProperty(expression, Property.CONTAINER, true, false);
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
            return evaluationContext.getPropertyFromPreviousOrInitial(variable, property, statementTime);
        }

        public Builder markRead(Variable variable) {
            return markRead(variable, true);
        }

        private Builder markRead(Variable variable, boolean recurse) {
            ChangeData ecd = valueChanges.get(variable);
            ChangeData newEcd;
            if (ecd == null) {
                newEcd = new ChangeData(null,
                        CausesOfDelay.EMPTY, CausesOfDelay.EMPTY, false, Set.of(statementTime),
                        LinkedVariables.EMPTY, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays, ecd.stateIsDelayed, ecd.markAssignment,
                        SetUtil.immutableUnion(ecd.readAtStatementTime, Set.of(statementTime)), ecd.linkedVariables,
                        ecd.properties);
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
        private void assignInstanceToVariable(Variable variable, Expression instance, LinkedVariables linkedVariables) {
            ChangeData current = valueChanges.get(variable);
            ChangeData newVcd;
            if (current == null) {
                CausesOfDelay stateIsDelayed = evaluationContext.getConditionManager().causesOfDelay();
                newVcd = new ChangeData(instance, stateIsDelayed, stateIsDelayed, false, Set.of(), linkedVariables,
                        Map.of());
            } else {
                newVcd = new ChangeData(instance, current.delays, current.stateIsDelayed, current.markAssignment,
                        current.readAtStatementTime, linkedVariables, current.properties);
            }
            valueChanges.put(variable, newVcd);
        }

        public void variableOccursInEventuallyImmutableContext(Identifier identifier,
                                                               Variable variable,
                                                               DV requiredImmutable,
                                                               DV nextImmutable) {
            if (requiredImmutable.equals(MultiLevel.MUTABLE_DV) || requiredImmutable == DV.MIN_INT_DV) return;
            if (requiredImmutable.isDelayed()) {
                setProperty(variable, Property.CONTEXT_IMMUTABLE, requiredImmutable);
                return;
            }
            // context immutable starts at 1, but this code only kicks in once it has received a value
            // before that value (before the first eventual call, the precondition system reigns
            DV currentImmutable = getPropertyFromInitial(variable, Property.CONTEXT_IMMUTABLE);
            if (currentImmutable.ge(MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV)) {
                if (MultiLevel.isBeforeThrowWhenNotEventual(requiredImmutable)
                        && !MultiLevel.isBeforeThrowWhenNotEventual(currentImmutable)) {
                    raiseError(identifier, Message.Label.EVENTUAL_BEFORE_REQUIRED);
                } else if (MultiLevel.isAfterThrowWhenNotEventual(requiredImmutable)
                        && !MultiLevel.isAfterThrowWhenNotEventual(currentImmutable)) {
                    raiseError(identifier, Message.Label.EVENTUAL_AFTER_REQUIRED);
                }
            }
            // everything proceeds as normal
            setProperty(variable, Property.CONTEXT_IMMUTABLE, nextImmutable);
        }

        /**
         * @param variable the variable in whose context the modification takes place
         * @param modified possibly delayed
         */
        public void markContextModified(Variable variable, DV modified) {
            assert evaluationContext != null;
            DV ignoreContentModifications = variable instanceof FieldReference fr ? evaluationContext.getAnalyserContext()
                    .getFieldAnalysis(fr.fieldInfo).getProperty(Property.IGNORE_MODIFICATIONS)
                    : Level.FALSE_DV;
            if (!ignoreContentModifications.valueIsTrue()) {
                log(CONTEXT_MODIFICATION, "Mark method object as context modified {}: {}", modified, variable.fullyQualifiedName());
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
                log(CONTEXT_MODIFICATION, "Skip marking method object as context modified: {}", variable.fullyQualifiedName());
            }
        }

        public void variableOccursInContainerContext(Variable variable, Expression currentExpression, DV containerRequired) {
            assert evaluationContext != null;

            if (containerRequired.isDelayed()) {
                setProperty(variable, Property.CONTAINER, containerRequired);
                return;
            }
            if (containerRequired.valueIsFalse()) return;
            if (currentExpression.isDelayed()) return; // not yet
            // if we already know that the variable is NOT @NotModified1, then we'll raise an error
            DV container = getContainerFromInitial(currentExpression);
            if (container.valueIsFalse()) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.Label.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                messages.add(message);
            } else if (container.isDelayed()) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                // FIXME does this make sense?
                setProperty(variable, Property.CONTAINER, Level.TRUE_DV);
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
        public Builder assignment(Variable assignmentTarget, Expression resultOfExpression, LinkedVariables linkedVariables) {
            assert evaluationContext != null;
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
                        stateIsDelayed, markAssignment, Set.of(), linkedVariables, Map.of());
            } else {
                newEcd = new ChangeData(value, ecd.delays.merge(stateIsDelayed).merge(value.causesOfDelay()),
                        ecd.stateIsDelayed.merge(stateIsDelayed), ecd.markAssignment || markAssignment,
                        ecd.readAtStatementTime, linkedVariables, ecd.properties);
            }
            valueChanges.put(assignmentTarget, newEcd);
            return this;
        }

        // Used in transformation of parameter lists
        public void setProperty(Variable variable, Property property, DV value) {
            assert evaluationContext != null;

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, value.causesOfDelay(), CausesOfDelay.EMPTY, false, Set.of(),
                        LinkedVariables.EMPTY, Map.of(property, value));
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays.merge(value.causesOfDelay()), ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables,
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
                        CausesOfDelay.EMPTY, false, Set.of(), linked, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays.merge(level.causesOfDelay()),
                        ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables.merge(linked), ecd.properties);
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
                throw new UnsupportedOperationException("To implement");
                /*


                Map<VariableProperty,Integer> valueProperties = evaluationContext.getValueProperties(scope);
                Expression newScope = valueProperties.values().stream().anyMatch(v -> v < 0)
                        ? DelayedExpression.forInstance(scope)
                        : Instance.genericFieldAccess(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo, valueProperties);
                return new FieldReference(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo, newScope);

                 */
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
                                assignment(variable, variableInfo.getValue(), variableInfo.getLinkedVariables());
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
            Expression initialValue = fieldAnalysis.getInitializerValue();
            if (initialValue == EmptyExpression.EMPTY_EXPRESSION || initialValue == null) return;
            initialValue.variables().forEach(variable -> {
                Variable v = acceptForMarkingRemoveScopeOfFields(variable, subType);
                if (v != null) markRead(v);
            });
        }

        public void addDelayOnPrecondition(CausesOfDelay causes) {
            addPrecondition(Precondition.forDelayed(DelayedExpression.forPrecondition(evaluationContext.getPrimitives(), causes)));
        }

        // can be called for multiple parameters, a value of 'true' should always survive
        public void causeOfContextModificationDelay(MethodInfo methodInfo, boolean delay) {
            if (methodInfo != null) {
                causesOfContextModificationDelays.merge(methodInfo, delay, (orig, val) -> orig || val);
            }
        }
    }
}

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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Fluent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.model.MultiLevel.Effective.*;

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

Critically, the apply method will execute the value changes before executing the modifications.
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
                               Messages messages,
                               Map<Variable, ChangeData> changeData,
                               Precondition precondition) {

    public EvaluationResult {
        assert evaluationContext != null;
        assert changeData.values().stream().noneMatch(ecd -> ecd.linkedVariables == null);
        assert causesOfDelay.causesStream().noneMatch(cause -> cause.cause() == CauseOfDelay.Cause.MIN_INT)
                : "Causes of delay: " + causesOfDelay;
    }

    public EvaluationResult copy(EvaluationContext evaluationContext) {
        return new EvaluationResult(evaluationContext, statementTime, value, storedValues, causesOfDelay, messages,
                changeData, precondition);
    }

    public static EvaluationResult from(EvaluationContext evaluationContext) {
        return new EvaluationResult(evaluationContext, VariableInfoContainer.NOT_RELEVANT, null, List.of(),
                CausesOfDelay.EMPTY, Messages.EMPTY, Map.of(), Precondition.empty(evaluationContext.getPrimitives()));
    }

    public AnalyserContext getAnalyserContext() {
        return evaluationContext.getAnalyserContext();
    }

    public Primitives getPrimitives() {
        return evaluationContext.getPrimitives();
    }

    public TypeInfo getCurrentType() {
        return evaluationContext.getCurrentType();
    }

    public MethodAnalyser getCurrentMethod() {
        return evaluationContext.getCurrentMethod();
    }

    // for debugger
    public String safeMethodName() {
        return evaluationContext().safeMethodName();
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

    public DV isNotNull0(boolean useEnnInsteadOfCnn) {
        assert evaluationContext != null;
        if (value instanceof VariableExpression variableExpression) {
            ChangeData cd = changeData.get(variableExpression.variable());
            if (cd != null) {
                DV inChangeData = cd.properties.getOrDefault(Property.CONTEXT_NOT_NULL, null);
                if (inChangeData != null && inChangeData.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return DV.TRUE_DV;
            }
        }
        return evaluationContext.isNotNull0(value, useEnnInsteadOfCnn, ForwardEvaluationInfo.DEFAULT);
    }

    public Expression currentValue(Variable variable) {
        ChangeData cd = changeData.get(variable);
        if (cd != null && cd.value != null) {
            return value;
        }
        return evaluationContext.currentValue(variable);
    }

    public EvaluationResult child(Expression condition) {
        EvaluationContext child = evaluationContext.child(condition);
        return copy(child);
    }

    public EvaluationResult child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        EvaluationContext child = evaluationContext.child(condition, disableEvaluationOfMethodCallsUsingCompanionMethods);
        return copy(child);

    }

    public EvaluationResult copyToPreventAbsoluteStateComputation() {
        EvaluationContext child = evaluationContext.copyToPreventAbsoluteStateComputation();
        return copy(child);
    }

    public EvaluationResult childState(Expression state) {
        EvaluationContext child = evaluationContext.childState(state);
        return copy(child);
    }

    public EvaluationResult translate(TranslationMap translationMap) {
        Map<Variable, ChangeData> newMap = new HashMap<>();
        InspectionProvider inspectionProvider = evaluationContext.getAnalyserContext();
        for (Map.Entry<Variable, ChangeData> e : changeData().entrySet()) {
            Variable translated = translationMap.translateVariable(e.getKey());
            EvaluationResult.ChangeData newChangeData = e.getValue().translate(inspectionProvider, translationMap);
            newMap.put(translated, newChangeData);
            if (translated != e.getKey()) {
                newMap.put(e.getKey(), newChangeData);
            }
        }
        Expression translatedValue = value == null ? null : value.translate(inspectionProvider, translationMap);
        List<Expression> translatedStoredValues = storedValues == null ? null :
                storedValues.stream().map(e -> e.translate(inspectionProvider, translationMap)).toList();
        CausesOfDelay translatedCauses = causesOfDelay.translate(inspectionProvider, translationMap);
        Precondition translatedPrecondition = precondition == null ? null : precondition.translate(inspectionProvider, translationMap);
        return new EvaluationResult(evaluationContext, statementTime, translatedValue, translatedStoredValues, translatedCauses, messages, newMap, translatedPrecondition);
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

        public ChangeData translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
            Expression translatedValue = value == null ? null : value.translate(inspectionProvider, translationMap);
            CausesOfDelay translatedDelays = delays == null ? null : delays.translate(inspectionProvider, translationMap);
            CausesOfDelay translatedStateIsDelayed = stateIsDelayed == null ? null : stateIsDelayed.translate(inspectionProvider, translationMap);
            LinkedVariables translatedLv = linkedVariables == null ? null : linkedVariables.translate(translationMap);
            LinkedVariables translatedToRemove = toRemoveFromLinkedVariables == null ? null : toRemoveFromLinkedVariables.translate(translationMap);
            if (translatedValue == value && translatedDelays == delays && translatedStateIsDelayed == stateIsDelayed
                    && translatedLv == linkedVariables && translatedToRemove == toRemoveFromLinkedVariables) {
                return this;
            }
            return new ChangeData(translatedValue, translatedDelays, translatedStateIsDelayed, markAssignment,
                    readAtStatementTime, translatedLv, translatedToRemove, properties);
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
        return expression.getProperty(this, property, true);
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final EvaluationResult previousResult;
        private final Messages messages = new Messages();
        private CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        private Expression value;
        private List<Expression> storedExpressions;
        private int statementTime;
        private final Map<Variable, ChangeData> valueChanges = new HashMap<>();
        private Precondition precondition;

        public Builder(EvaluationResult evaluationResult) {
            this.previousResult = Objects.requireNonNull(evaluationResult);
            this.evaluationContext = Objects.requireNonNull(evaluationResult.evaluationContext);
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
                    EvaluationResult context = EvaluationResult.from(evaluationContext);
                    precondition = precondition.combine(context, evaluationResult.precondition);
                }
            }
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
                    causesOfDelay, messages, valueChanges, precondition);
        }

        /**
         * Primary method to generate Context Not Null on a variable.
         * <p>
         * DGSimplified_4, backupComparator line 135 shows a dilemma: bc == null ? 0 : bc.compare(...)
         * requires content not null in the "ifFalse" clause, but allows for null. Should we make the parameter
         * Nullable, or NotNull1? We prefer nullable.
         *
         * @param variable        the variable which occurs in the not null context
         * @param value           the variable's value. This can be a variable expression again (redirect).
         * @param notNullRequired the minimal not null requirement; must be > NULLABLE.
         */
        public void variableOccursInNotNullContext(Variable variable, Expression value, DV notNullRequired, ForwardEvaluationInfo forwardEvaluationInfo) {
            assert evaluationContext != null;
            assert value != null;
            if (notNullRequired.equals(MultiLevel.NULLABLE_DV)) return;
            if (variable instanceof This) return; // nothing to be done here

            for (Variable sourceOfLoop : evaluationContext.loopSourceVariables(variable)) {
                markRead(sourceOfLoop);  // TODO not correct, but done to trigger merge (no mechanism for that a t m)
                Expression sourceValue = evaluationContext.currentValue(sourceOfLoop);
                DV higher = MultiLevel.composeOneLevelMoreNotNull(notNullRequired);
                variableOccursInNotNullContext(sourceOfLoop, sourceValue, higher, forwardEvaluationInfo);
            }

            if (notNullRequired.isDelayed()) {
                // simply set the delay
                setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
                return;
            }

            CausesOfDelay cmDelays = evaluationContext.getConditionManager().causesOfDelay();
            if (cmDelays.isDelayed() && !causeOfConditionManagerDelayIsAssignmentTarget(cmDelays,
                    forwardEvaluationInfo.getAssignmentTarget(), variable)) {
                setProperty(variable, Property.CONTEXT_NOT_NULL, cmDelays);
                return;
            }
            DV effectivelyNotNull = evaluationContext.notNullAccordingToConditionManager(variable)
                    .maxIgnoreDelay(evaluationContext.notNullAccordingToConditionManager(value));
            if (effectivelyNotNull.isDelayed()) {
                setProperty(variable, Property.CONTEXT_NOT_NULL, effectivelyNotNull);
                return;
            }

            // using le() instead of equals() here is controversial. if a variable is not null according to the
            // condition manager, and we're requesting content not null, e.g., then what to do? see header of this method.
            if (MultiLevel.EFFECTIVELY_NOT_NULL_DV.le(notNullRequired) && effectivelyNotNull.valueIsTrue()) {
                return; // great, no problem, no reason to complain nor increase the property
            }
            DV contextNotNull = getPropertyFromInitial(variable, Property.CONTEXT_NOT_NULL);
            boolean complain = forwardEvaluationInfo.isComplainInlineConditional();
            if (contextNotNull.isDone() && contextNotNull.lt(notNullRequired) && complain) {
                DV nnc = effectivelyNotNull.valueIsTrue() ? notNullRequired : MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                setProperty(variable, Property.IN_NOT_NULL_CONTEXT, nnc); // so we can raise an error
            }
            setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
        }

        private static final Set<CauseOfDelay.Cause> CM_CAUSES = Set.of(CauseOfDelay.Cause.BREAK_INIT_DELAY, CauseOfDelay.Cause.INITIAL_VALUE);

        // e.g. Lazy, Loops_23_1
        // FIXME Basics_1 cannot have 3rd param, DependencyGraph goes into infinite loop without it
        // FIXME REMOVED BREAK
        private boolean causeOfConditionManagerDelayIsAssignmentTarget(CausesOfDelay cmDelays,
                                                                       Variable assignmentTarget,
                                                                       Variable variable) {
            return cmDelays.containsCausesOfDelay(CM_CAUSES, c -> c instanceof VariableCause vc &&
                    (vc.variable().equals(assignmentTarget)));// || vc.variable().equals(variable)));
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

        public void markRead(Variable variable) {
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
        }

        public void raiseError(Identifier identifier, Message.Label messageLabel) {
            assert evaluationContext != null;
            if (evaluationContext.haveCurrentStatement()) {
                Message message = Message.newMessage(evaluationContext.getEvaluationLocation(identifier), messageLabel);
                messages.add(message);
            } else { // e.g. companion analyser
                LOGGER.warn("Analyser error: {}", messageLabel);
            }
        }

        public void raiseError(Identifier identifier, Message.Label messageLabel, String extra) {
            assert evaluationContext != null;
            if (evaluationContext.haveCurrentStatement()) {
                Message message = Message.newMessage(evaluationContext.getEvaluationLocation(identifier), messageLabel, extra);
                messages.add(message);
            } else {
                LOGGER.warn("Analyser error: {}, {}", messageLabel, extra);
            }
        }

        public Expression currentExpression(Variable variable, Expression scopeValue,
                                            Expression indexValue,
                                            ForwardEvaluationInfo forwardEvaluationInfo) {
            ChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression != null && currentExpression.value != null) {
                return currentExpression.value;
            }
            assert previousResult != null;
            ChangeData inPrevious = previousResult.changeData.get(variable);
            if (inPrevious != null && inPrevious.value != null) {
                return inPrevious.value;
            }
            return evaluationContext.currentValue(variable, scopeValue, indexValue, forwardEvaluationInfo);
        }

        /*
        idea: EXT_IMM goes from statement to statement, starting with the value of field analyser (fields, params linked to fields) / type analyser (this)
        we modify along the way if the variable calls a method that changes from BEFORE to AFTER
         */

        public void variableOccursInEventuallyImmutableContext(Identifier identifier,
                                                               Variable variable,
                                                               DV requiredImmutable,
                                                               DV nextImmutable) {
            Property property;
            if (variable instanceof This || variable instanceof ParameterInfo) {
                property = Property.EXTERNAL_IMMUTABLE;
            } else if (variable instanceof FieldReference fr) {
                // assignment, or in constructor, part of construction
                property = hasBeenAssigned(fr) || evaluationContext.inConstruction() ? Property.CONTEXT_IMMUTABLE
                        : Property.EXTERNAL_IMMUTABLE;
            } else {
                property = Property.CONTEXT_IMMUTABLE;
            }
            if (requiredImmutable.isDelayed()) {
                if (property == Property.CONTEXT_IMMUTABLE) {
                    setProperty(variable, property, requiredImmutable.causesOfDelay());
                }
                return;
            }
            MultiLevel.Effective requiredEffective = MultiLevel.effective(requiredImmutable);
            if (requiredEffective != EVENTUAL_BEFORE && requiredEffective != EVENTUAL_AFTER) {
                // no reason, not a method call that changed state
                return;
            }
            DV currentImmutable = getPropertyFromInitial(variable, property);
            if (currentImmutable.isDelayed()) {
                if (property == Property.CONTEXT_IMMUTABLE) {
                    setProperty(variable, property, currentImmutable.causesOfDelay());
                }
                return; // let's wait
            }
            MultiLevel.Effective currentEffective = MultiLevel.effective(currentImmutable);

            // raise error if direction of change wring
            if (requiredEffective == EVENTUAL_BEFORE && currentEffective == EVENTUAL_AFTER) {
                raiseError(identifier, Message.Label.EVENTUAL_BEFORE_REQUIRED);
            } else if (requiredEffective == EVENTUAL_AFTER && currentEffective == EVENTUAL_BEFORE) {
                raiseError(identifier, Message.Label.EVENTUAL_AFTER_REQUIRED);
            }

            // everything proceeds as normal, we change EXTERNAL_IMMUTABLE
            assert nextImmutable.isDone();
            MultiLevel.Effective nextEffective = MultiLevel.effective(nextImmutable);
            if ((currentEffective == EVENTUAL_BEFORE || currentEffective == MultiLevel.Effective.EVENTUAL) && nextEffective == EVENTUAL_AFTER) {
                // switch from before or unknown, to after
                DV extImm = MultiLevel.afterImmutableDv(MultiLevel.level(currentImmutable));
                setProperty(variable, property, extImm);
            } else if (currentEffective == EVENTUAL && nextEffective == EVENTUAL_BEFORE) {
                DV extImm = MultiLevel.beforeImmutableDv(MultiLevel.level(currentImmutable));
                setProperty(variable, property, extImm);
            }
        }

        private boolean hasBeenAssigned(FieldReference fr) {
            ChangeData changeData = valueChanges.get(fr);
            if (changeData != null && changeData.markAssignment) return true;
            return evaluationContext.hasBeenAssigned(fr);
        }

        /**
         * @param variable the variable in whose context the modification takes place
         * @param modified possibly delayed
         */
        public void markContextModified(Variable variable, DV modified) {
            assert evaluationContext != null;
            DV ignoreContentModifications = variable instanceof FieldReference fr ? evaluationContext.getAnalyserContext()
                    .getFieldAnalysis(fr.fieldInfo).getProperty(Property.IGNORE_MODIFICATIONS)
                    : Property.IGNORE_MODIFICATIONS.falseDv;
            if (!ignoreContentModifications.equals(MultiLevel.IGNORE_MODS_DV)) {
                ChangeData cd = valueChanges.get(variable);
                // if the variable is not present yet (a field), we expect it to have been markedRead
                if (cd != null && cd.isMarkedRead() || evaluationContext.isPresent(variable)) {
                    DV currentValue = cd == null ? null : cd.properties.get(Property.CONTEXT_MODIFIED);
                    if (currentValue == null || !currentValue.valueIsTrue()) {
                        setProperty(variable, Property.CONTEXT_MODIFIED, modified);
                    }
                }
                if (variable instanceof FieldReference fr && fr.scopeVariable != null) {
                    markContextModified(fr.scopeVariable, modified);
                } else if (variable instanceof DependentVariable dv && dv.arrayVariable() != null) {
                    markContextModified(dv.arrayVariable(), modified);
                }
            }
        }

        /*
        parameters and fields use EXT_CONTAINER to raise errors; they do not wait for a valid value property.
         */
        public void variableOccursInContainerContext(Variable variable, DV containerRequired, boolean complain) {
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
                if (external.equals(MultiLevel.NOT_CONTAINER_DV) && complain) {
                    Message message = Message.newMessage(evaluationContext.getLocation(EVALUATION), Message.Label.MODIFICATION_NOT_ALLOWED, variable.simpleName());
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
            if (complain) {
                Message message = Message.newMessage(evaluationContext.getLocation(EVALUATION), Message.Label.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                messages.add(message);
            }
            setProperty(variable, Property.CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        }

        public Builder assignmentToSelfIgnored(Variable variable) {
            Message message = Message.newMessage(evaluationContext.getLocation(EVALUATION), Message.Label.ASSIGNMENT_TO_SELF, variable.fullyQualifiedName());
            messages.add(message);
            return this;
        }

        public void assignmentToCurrentValue(Variable variable) {
            Message message = Message.newMessage(evaluationContext.getLocation(EVALUATION), Message.Label.ASSIGNMENT_TO_CURRENT_VALUE, variable.fullyQualifiedName());
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
            Expression value = valueDelayWithState(assignmentTarget, resultOfExpression, stateIsDelayed);

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(assignmentTarget);
            CausesOfDelay stateDelaysFilteredForSelfReference = resultOfExpression.isDelayed() ? stateIsDelayed : breakSelfReferenceDelay(assignmentTarget, stateIsDelayed);
            if (ecd == null) {
                newEcd = new ChangeData(value, value.causesOfDelay().merge(stateDelaysFilteredForSelfReference),
                        stateDelaysFilteredForSelfReference, markAssignment, Set.of(), linkedVariables, LinkedVariables.EMPTY, Map.of());
            } else {
                CausesOfDelay mergedValueDelays = ecd.delays.merge(stateDelaysFilteredForSelfReference).merge(value.causesOfDelay());
                newEcd = new ChangeData(value, mergedValueDelays,
                        ecd.stateIsDelayed.merge(stateDelaysFilteredForSelfReference), ecd.markAssignment || markAssignment,
                        ecd.readAtStatementTime, linkedVariables, LinkedVariables.EMPTY, ecd.properties);
            }
            valueChanges.put(assignmentTarget, newEcd);
            return this;
        }

        private Expression valueDelayWithState(Variable assignmentTarget,
                                               Expression resultOfExpression,
                                               CausesOfDelay stateIsDelayed) {
            if (stateIsDelayed.isDelayed()
                    && !evaluationContext.getConditionManager().isReasonForDelay(assignmentTarget)
                    && !resultOfExpression.isInstanceOf(DelayedVariableExpression.class)) {
                return DelayedExpression.forState(Identifier.state(evaluationContext.statementIndex()),
                        resultOfExpression.returnType(),
                        resultOfExpression.variables(true),
                        stateIsDelayed);
            }
            return resultOfExpression;
        }

        private CausesOfDelay breakSelfReferenceDelay(Variable assignmentTarget, CausesOfDelay stateIsDelayed) {
            if (assignmentTarget instanceof FieldReference fieldReference) {
                boolean selfReference = stateIsDelayed.containsCauseOfDelay(CauseOfDelay.Cause.VALUES,
                        c -> c instanceof VariableCause vc && vc.variable().equals(fieldReference));
                if (selfReference) {
                    CauseOfDelay cause = new VariableCause(fieldReference, evaluationContext.getLocation(EVALUATION),
                            CauseOfDelay.Cause.BREAK_INIT_DELAY);
                    return DelayFactory.createDelay(cause).merge(stateIsDelayed);
                }
            }
            return stateIsDelayed;
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

        public void setProperty(Variable variable, Property property, DV value) {
            assert evaluationContext != null;

            CausesOfDelay causesOfDelay = property.propertyType == Property.PropertyType.CONTEXT
                    ? CausesOfDelay.EMPTY : value.causesOfDelay();

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, causesOfDelay, CausesOfDelay.EMPTY, false, Set.of(),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(property, value));
            } else {
                newEcd = new ChangeData(ecd.value, ecd.delays.merge(causesOfDelay), ecd.stateIsDelayed, ecd.markAssignment,
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
            LinkedVariables linked = LinkedVariables.of(to, level);
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

        @Fluent
        public Builder addPrecondition(Precondition newPrecondition) {
            if (precondition == null) {
                precondition = newPrecondition;
            } else {
                EvaluationResult context = EvaluationResult.from(evaluationContext);
                precondition = precondition.combine(context, newPrecondition);
            }
            return this;
        }

        public void modifyingMethodAccess(Variable variable, Expression instance, LinkedVariables linkedVariables) {
            ChangeData current = valueChanges.get(variable);
            ChangeData newVcd;
            if (current == null) {
                assert linkedVariables != null;
                CausesOfDelay stateIsDelayed = evaluationContext.getConditionManager().causesOfDelay();
                newVcd = new ChangeData(instance, stateIsDelayed, stateIsDelayed, false, Set.of(),
                        linkedVariables, LinkedVariables.EMPTY,
                        Map.of());
            } else {
                LinkedVariables lvs = linkedVariables == null ? current.linkedVariables : linkedVariables;
                newVcd = new ChangeData(instance, current.delays, current.stateIsDelayed, current.markAssignment,
                        current.readAtStatementTime, lvs, current.toRemoveFromLinkedVariables, current.properties);
            }
            valueChanges.put(variable, newVcd);
        }

        public void addErrorAssigningToFieldOutsideType(FieldInfo fieldInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(evaluationContext.getLocation(EVALUATION),
                    Message.Label.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE,
                    fieldInfo.fullyQualifiedName()));
        }

        public void addParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(parameterInfo.newLocation(),
                    Message.Label.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        public int getStatementTime() {
            return statementTime;
        }

        public DV isNotNull(Expression expression) {
            assert evaluationContext != null;
            if (value instanceof VariableExpression variableExpression) {
                ChangeData cd = valueChanges.get(variableExpression.variable());
                if (cd != null) {
                    DV inChangeData = cd.properties.getOrDefault(Property.CONTEXT_NOT_NULL, null);
                    if (inChangeData != null && inChangeData.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return DV.TRUE_DV;
                }
            }
            return evaluationContext.isNotNull0(expression, false, ForwardEvaluationInfo.DEFAULT);
        }

        // see DGSimplified_4, backupComparator. the functional interface's CNN cannot be upgraded to content not null,
        // because it is nullable
        public boolean contextNotNullIsNotNullable(Variable variable) {
            ChangeData cd = valueChanges.get(variable);
            if (cd != null) {
                DV cnn = cd.getProperty(Property.CONTEXT_NOT_NULL);
                return cnn != null && cnn.gt(MultiLevel.NULLABLE_DV);
            }
            return false;
        }

        public Map<Variable, DV> cnnMap() {
            return valueChanges.entrySet().stream()
                    .filter(e -> e.getValue().properties.containsKey(Property.CONTEXT_NOT_NULL))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                            e -> e.getValue().getProperty(Property.CONTEXT_NOT_NULL)));
        }
    }
}

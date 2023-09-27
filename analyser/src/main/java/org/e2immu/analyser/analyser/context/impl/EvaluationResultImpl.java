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

package org.e2immu.analyser.analyser.context.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.Fluent;
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.model.MultiLevel.Effective.EVENTUAL_AFTER;
import static org.e2immu.analyser.model.MultiLevel.Effective.EVENTUAL_BEFORE;

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


EvaluatedExpressionCache will be stored in StatementAnalysis.stateData() for use by CodeModernizer.

 */
public record EvaluationResultImpl(EvaluationContext evaluationContext,
                                   Expression value,
                                   List<Expression> storedValues,
                                   CausesOfDelay causesOfDelay,
                                   Messages messages,
                                   Map<Variable, ChangeData> changeData,
                                   Precondition precondition) implements EvaluationResult {

    public EvaluationResultImpl {
        assert evaluationContext != null;
        assert causesOfDelay.causesStream().noneMatch(cause -> cause.cause() == CauseOfDelay.Cause.MIN_INT)
                : "Causes of delay: " + causesOfDelay;
    }

    @Override
    public String toString() {
        return "EvaluationResult{" +
                "evaluationContext=" + evaluationContext +
                ", value=" + value +
                ", storedValues=" + storedValues +
                ", causesOfDelay=" + causesOfDelay +
                ", messages=" + messages +
                ", changeData=" + changeData.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).sorted().collect(Collectors.joining(";")) +
                ", precondition=" + precondition +
                '}';
    }

    public EvaluationResultImpl copy(EvaluationContext evaluationContext) {
        return new EvaluationResultImpl(evaluationContext, value, storedValues, causesOfDelay, messages, changeData,
                precondition);
    }

    public static EvaluationResultImpl from(EvaluationContext evaluationContext) {
        return new EvaluationResultImpl(evaluationContext, null, List.of(),
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

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationResultImpl.class);

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

    public DV isNotNull0(boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.isOnlySort()) return DV.FALSE_DV;
        assert evaluationContext != null;
        if (value instanceof VariableExpression variableExpression) {
            ChangeData cd = changeData.get(variableExpression.variable());
            if (cd != null) {
                DV inChangeData = cd.properties().getOrDefault(Property.CONTEXT_NOT_NULL, null);
                if (inChangeData != null && inChangeData.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return DV.TRUE_DV;
            }
        }
        return evaluationContext.isNotNull0(value, useEnnInsteadOfCnn, forwardEvaluationInfo);
    }

    public Expression currentValue(Variable variable) {
        ChangeData cd = changeData.get(variable);
        if (cd != null && cd.value() != null) {
            return value;
        }
        return evaluationContext.currentValue(variable);
    }

    public EvaluationResultImpl child(Expression condition, Set<Variable> conditionVariables) {
        EvaluationContext child = evaluationContext.child(condition, conditionVariables);
        return copy(child);
    }

    public EvaluationResultImpl child(Expression condition, Set<Variable> conditionVariables, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        EvaluationContext child = evaluationContext.child(condition, conditionVariables,
                disableEvaluationOfMethodCallsUsingCompanionMethods);
        return copy(child);

    }

    public EvaluationResultImpl copyToPreventAbsoluteStateComputation() {
        EvaluationContext child = evaluationContext.copyToPreventAbsoluteStateComputation();
        return copy(child);
    }

    public EvaluationResultImpl childState(Expression state, Set<Variable> stateVariables) {
        EvaluationContext child = evaluationContext.childState(state, stateVariables);
        return copy(child);
    }

    public EvaluationResultImpl translate(TranslationMap translationMap) {
        Map<Variable, ChangeData> newMap = new HashMap<>();
        InspectionProvider inspectionProvider = evaluationContext.getAnalyserContext();
        for (Map.Entry<Variable, ChangeData> e : changeData().entrySet()) {
            Variable translated = translationMap.translateVariable(evaluationContext.getAnalyserContext(), e.getKey());
            ChangeData newChangeData = e.getValue().translate(inspectionProvider, translationMap);
            newMap.put(translated, newChangeData);
            if (translated != e.getKey()) {
                /* x.i -> scope-x:2.i

                no linked variables, otherwise we end up with asymmetrical :0 and :1 arrows
                we have to keep the variable, we need its value (see e.g. VariableScope_14)
                 */
                ChangeData cd = e.getValue();
                ChangeData ncd = new ChangeData(cd.value(), cd.delays(), cd.stateIsDelayed(), cd.markAssignment(),
                        cd.readAtStatementTime(), LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(),
                        cd.modificationTimeIncrement());
                newMap.put(e.getKey(), ncd);
            }
        }
        Expression translatedValue = value == null ? null : value.translate(inspectionProvider, translationMap);
        List<Expression> translatedStoredValues = storedValues == null ? null :
                storedValues.stream().map(e -> e.translate(inspectionProvider, translationMap)).toList();
        CausesOfDelay translatedCauses = causesOfDelay.translate(inspectionProvider, translationMap);
        Precondition translatedPrecondition = precondition == null ? null : precondition.translate(inspectionProvider, translationMap);
        return new EvaluationResultImpl(evaluationContext, translatedValue, translatedStoredValues,
                translatedCauses, messages, newMap, translatedPrecondition);
    }

    public EvaluationResultImpl withNewEvaluationContext(EvaluationContext newEc) {
        return new EvaluationResultImpl(newEc, value, storedValues, causesOfDelay, messages, changeData, precondition);
    }

    public LinkedVariables linkedVariables(Variable variable) {
        ChangeData cd = changeData.get(variable);
        if (cd != null) return cd.linkedVariables();
        return null;
    }

    public EvaluationResultImpl filterChangeData(Predicate<Variable> predicate) {
        Map<Variable, ChangeData> newChangeData = changeData.entrySet().stream().filter(e -> predicate.test(e.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        CausesOfDelay newChangeDataDelays = newChangeData.values()
                .stream().map(e -> e.delays()).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        CausesOfDelay newCauses = value.causesOfDelay().merge(newChangeDataDelays);
        return new EvaluationResultImpl(evaluationContext, value, storedValues, newCauses, messages, newChangeData,
                precondition);
    }

    public DV containsModification() {
        return changeData.values().stream().map(cd -> cd.properties().getOrDefault(Property.CONTEXT_MODIFIED, DV.FALSE_DV))
                .reduce(DV.FALSE_DV, DV::max);
    }

    public int statementTime() {
        return evaluationContext.getCurrentStatementTime();
    }

    public Integer modificationTime(Variable variable) {
        ChangeData cd = changeData.get(variable);
        int increment = cd == null ? 0 : cd.modificationTimeIncrement();
        return increment + evaluationContext.initialModificationTimeOrZero(variable);
    }

    public Stream<Integer> modificationTimes(Expression expression) {
        return expression.linkedVariables(this).assignedOrDependentVariables().map(this::modificationTime);
    }

    public String modificationTimesOf(Expression... values) {
        return Arrays.stream(values).flatMap(this::modificationTimes).map(Object::toString)
                .collect(Collectors.joining(","));
    }

    public String modificationTimesOf(Expression object, List<Expression> parameters) {
        return Stream.concat(Stream.of(object), parameters.stream())
                .flatMap(this::modificationTimes)
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    public Map<Variable, Integer> modificationTimeIncrements() {
        return changeData.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> e.getValue().modificationTimeIncrement()));
    }

    public DV getProperty(Expression expression, Property property) {
        if (expression instanceof VariableExpression ve) {
            ChangeData changeData = changeData().get(ve.variable());
            if (changeData != null) {
                DV inChangeData = changeData.properties().getOrDefault(property, null);
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
            this.evaluationContext = Objects.requireNonNull(evaluationResult.evaluationContext());
            this.statementTime = evaluationContext.getCurrentStatementTime();
        }

        public Builder(EvaluationContext evaluationContext) {
            this.previousResult = null;
            this.evaluationContext = evaluationContext;
            this.statementTime = evaluationContext.getCurrentStatementTime();
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
            if (!ignoreExpression && evaluationResult.value() != null) {
                setExpression(evaluationResult.value());
            }
            this.causesOfDelay = this.causesOfDelay.merge(evaluationResult.causesOfDelay());
            this.messages.addAll(evaluationResult.getMessageStream());

            for (Map.Entry<Variable, ChangeData> e : evaluationResult.changeData().entrySet()) {
                valueChanges.merge(e.getKey(), e.getValue(), ChangeData::merge);
            }

            statementTime = Math.max(statementTime, evaluationResult.evaluationContext().getCurrentStatementTime());

            if (evaluationResult.precondition() != null) {
                if (precondition == null) {
                    precondition = evaluationResult.precondition();
                } else {
                    EvaluationResultImpl context = EvaluationResultImpl.from(evaluationContext);
                    precondition = precondition.combine(context, evaluationResult.precondition());
                }
            }
        }

        public void incrementStatementTime() {
            if (evaluationContext.allowedToIncrementStatementTime()) {
                statementTime++;
                valueChanges.forEach((v, cd) -> valueChanges.put(v, cd.incrementModificationTime()));
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

        public EvaluationResultImpl build() {
            return build(true);
        }

        public EvaluationResultImpl build(boolean includeDelaysOnValue) {
            if (value != null) {
                valueChanges.values().forEach(cd -> addCausesOfDelay(cd.delays()));
                if (includeDelaysOnValue) {
                    // this is the default; but when assigning to a variable, there is no reason to worry about
                    // the delays on that value; Container_0
                    addCausesOfDelay(value.causesOfDelay());
                }
            }
            EvaluationContext ec = evaluationContext.updateStatementTime(statementTime);
            return new EvaluationResultImpl(ec, value, storedExpressions == null ? null : List.copyOf(storedExpressions),
                    causesOfDelay, messages, valueChanges, precondition);
        }

        /**
         * Primary method to generate Context Not Null on a variable.
         * <p>
         * DGSimplified_4, backupComparator line 135 shows a dilemma: bc == null ? 0 : bc.compare(...)
         * requires content not null in the "ifFalse" clause, but allows for null. Should we make the parameter
         * Nullable, or NotNull1? We prefer nullable.
         * FIXME causes issue in Resources, _AAPI
         * <p>
         * we're breaking delays one variable at a time, so if we need to go into sources of loops, we'll do that
         * first. See e.g. Project_0
         *
         * @param variable        the variable which occurs in the not null context
         * @param value           the variable's value. This can be a variable expression again (redirect).
         * @param notNullRequired the minimal not null requirement; must be > NULLABLE.
         */
        public void variableOccursInNotNullContext(Variable variable,
                                                   Expression value,
                                                   DV notNullRequired,
                                                   ForwardEvaluationInfo forwardEvaluationInfo) {
            variableOccursInNotNullContext(variable, value, notNullRequired, forwardEvaluationInfo, false);
        }

        private void variableOccursInNotNullContext(Variable variable,
                                                    Expression value,
                                                    DV notNullRequired,
                                                    ForwardEvaluationInfo forwardEvaluationInfo,
                                                    boolean inSourceOfLoop) {
            assert evaluationContext != null;
            assert value != null;
            if (notNullRequired.equals(MultiLevel.NULLABLE_DV)) return;
            if (variable instanceof This) return; // nothing to be done here

            Either<CausesOfDelay, Set<Variable>> sourcesOfLoop = evaluationContext.loopSourceVariables(variable);
            if (sourcesOfLoop.isLeft()) {
                setProperty(variable, Property.CONTEXT_NOT_NULL, sourcesOfLoop.getLeft());
                return;
            }
            for (Variable sourceOfLoop : sourcesOfLoop.getRight()) {
                markRead(sourceOfLoop);  // TODO not correct, but done to trigger merge (no mechanism for that a t m)
                Expression sourceValue = evaluationContext.currentValue(sourceOfLoop);
                DV higher = MultiLevel.composeOneLevelMoreNotNull(notNullRequired);
                variableOccursInNotNullContext(sourceOfLoop, sourceValue, higher, forwardEvaluationInfo, true);
            }

            if (notNullRequired.isDelayed()) {
                // simply set the delay
                setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
                return;
            }

            ConditionManager cm = evaluationContext.getConditionManager();
            CausesOfDelay cmDelays = cm.causesOfDelay();
            if (cmDelays.isDelayed()) {
                if (evaluationContext.breakDelayLevel().acceptStatement()
                        && inSourceOfLoop
                        && causeOfConditionManagerDelayIsLoopNotEmpty(cmDelays, variable)) {
                    LOGGER.debug("Breaking delay on source of loop {}", variable);
                    setProperty(variable, Property.CONTEXT_NOT_NULL, notNullRequired);
                    return;
                }
                if (!causeOfConditionManagerDelayIsAssignmentTarget(cmDelays,
                        forwardEvaluationInfo.getAssignmentTarget())) {
                    setProperty(variable, Property.CONTEXT_NOT_NULL, cmDelays);
                    return;
                }
            }
            if (value.isDelayed()) {
                // cm is not delayed
                List<Variable> vars = value.variables();
                List<Variable> varsInCm = cm.variables();
                if (!Collections.disjoint(vars, varsInCm)) {
                    LOGGER.debug("Delaying CNN, delayed value shares variables with CM, {}", variable);
                    setProperty(variable, Property.CONTEXT_NOT_NULL, value.causesOfDelay());
                    return;
                }
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
                setProperty(variable, Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);
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

        private boolean causeOfConditionManagerDelayIsLoopNotEmpty(CausesOfDelay cmDelays, Variable sourceOfLoop) {
            return cmDelays.containsCauseOfDelay(CauseOfDelay.Cause.EXTERNAL_NOT_NULL, c -> c instanceof VariableCause vc &&
                    vc.variable().equals(sourceOfLoop) ||
                    sourceOfLoop instanceof FieldReference fr &&
                            c instanceof SimpleCause sc && sc.variableIsField(fr.fieldInfo) ||
                    sourceOfLoop instanceof ParameterInfo pi &&
                            c instanceof SimpleCause scPi && scPi.location().getInfo().equals(pi));
        }

        // e.g. Lazy, Loops_23_1
        // FIXME Basics_1 cannot have 3rd param, DependencyGraph goes into infinite loop without it
        // FIXME REMOVED BREAK
        private boolean causeOfConditionManagerDelayIsAssignmentTarget(CausesOfDelay cmDelays, Variable assignmentTarget) {
            return cmDelays.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY, c -> c instanceof VariableCause vc &&
                    (vc.variable().equals(assignmentTarget)));
        }

        /*
        it is important that the value is read from initial (-C), and not from evaluation (-E)
         */
        private DV getPropertyFromInitial(Variable variable, Property property) {
            ChangeData changeData = valueChanges.get(variable);
            if (changeData != null) {
                DV inChangeData = changeData.properties().getOrDefault(property, null);
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
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(), 0);
            } else {
                newEcd = new ChangeData(ecd.value(), ecd.delays(), ecd.stateIsDelayed(), ecd.markAssignment(),
                        SetUtil.immutableUnion(ecd.readAtStatementTime(), Set.of(statementTime)), ecd.linkedVariables(),
                        ecd.toRemoveFromLinkedVariables(),
                        ecd.properties(), ecd.modificationTimeIncrement());
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
                                            Identifier identifier,
                                            ForwardEvaluationInfo forwardEvaluationInfo) {
            ChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression != null && currentExpression.value() != null) {
                return currentExpression.value();
            }
            assert previousResult != null;
            ChangeData inPrevious = previousResult.changeData().get(variable);
            if (inPrevious != null && inPrevious.value() != null) {
                return inPrevious.value();
            }
            return evaluationContext.currentValue(variable, scopeValue, indexValue, identifier, forwardEvaluationInfo);
        }

        /*
        idea: EXT_IMM goes from statement to statement, starting with the value of field analyser (fields, params linked to fields) / type analyser (this)
        we modify along the way if the variable calls a method that changes from BEFORE to AFTER
         */

        public void variableOccursInEventuallyImmutableContext(Identifier identifier,
                                                               Variable variable,
                                                               DV requiredImmutable,
                                                               DV nextImmutable) {
            if (requiredImmutable.isDelayed()) {
                setProperty(variable, Property.CONTEXT_IMMUTABLE, requiredImmutable.causesOfDelay());
                return;
            }
            MultiLevel.Effective requiredEffective = MultiLevel.effective(requiredImmutable);
            if (requiredEffective != EVENTUAL_BEFORE && requiredEffective != EVENTUAL_AFTER) {
                // no reason, not a method call that changed state
                return;
            }
            // TODO use extImm to produce errors, but not to compute stuff
            //DV extImm = getPropertyFromInitial(variable, Property.EXTERNAL_IMMUTABLE);
            DV ctxImm = getPropertyFromInitial(variable, Property.CONTEXT_IMMUTABLE);
            DV imm = getPropertyFromInitial(variable, Property.IMMUTABLE);
            DV currentImmutable = ctxImm.max(imm);

            if (currentImmutable.isDelayed()) {
                setProperty(variable, Property.CONTEXT_IMMUTABLE, currentImmutable.causesOfDelay());
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
            if (nextEffective == EVENTUAL_AFTER || nextEffective == EVENTUAL_BEFORE) {
                // switch from before or unknown, to after

                // note that we cannot use IMM, because 'this' will have 'mutable', always level 0
                DV formal = evaluationContext.getAnalyserContext().typeImmutable(variable.parameterizedType());
                if (formal.isDelayed()) {
                    // we're in a self situation (formal not yet known, but imm is mutable)... waiting
                    setProperty(variable, Property.CONTEXT_IMMUTABLE, formal);
                } else {
                    int levelOfFormal = MultiLevel.level(formal);
                    DV nextImm = nextEffective == EVENTUAL_AFTER
                            ? MultiLevel.afterImmutableDv(levelOfFormal)
                            : MultiLevel.beforeImmutableDv(levelOfFormal);
                    setProperty(variable, Property.CONTEXT_IMMUTABLE, nextImm);
                }
            }

        }

        /**
         * also increases modification time!
         *
         * @param variable the variable in whose context the modification takes place
         * @param modified possibly delayed
         */
        public void markContextModified(Variable variable, DV modified) {
            assert evaluationContext != null;
            DV ignoreContentModifications = variable instanceof FieldReference fr ? evaluationContext.getAnalyserContext()
                    .getFieldAnalysis(fr.fieldInfo).getProperty(Property.EXTERNAL_IGNORE_MODIFICATIONS)
                    : Property.IGNORE_MODIFICATIONS.falseDv;
            if (!ignoreContentModifications.equals(MultiLevel.IGNORE_MODS_DV)) {
                ChangeData cd = valueChanges.get(variable);
                // if the variable is not present yet (a field), we expect it to have been markedRead
                if (cd != null && cd.isMarkedRead() || evaluationContext.isPresent(variable)) {
                    DV currentValue = cd == null ? null : cd.properties().get(Property.CONTEXT_MODIFIED);
                    if (currentValue == null || !currentValue.valueIsTrue()) {
                        int incrementModificationTime = modified.valueIsTrue() ? 1 : 0;
                        setProperty(variable, Property.CONTEXT_MODIFIED, modified, incrementModificationTime);
                    }
                }
                if (variable instanceof FieldReference fr && fr.scopeVariable != null) {
                    markRead(fr.scopeVariable);
                    markContextModified(fr.scopeVariable, modified);
                } else if (variable instanceof DependentVariable dv && dv.arrayVariable() != null) {
                    markRead(dv.arrayVariable());
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
            Variable linkedToFieldOrParameter = assignedToFieldOrParameter(evaluationContext, variable);
            if (linkedToFieldOrParameter instanceof FieldReference || linkedToFieldOrParameter instanceof ParameterInfo) {
                // will come back later
                DV external = getPropertyFromInitial(linkedToFieldOrParameter, Property.CONTAINER_RESTRICTION);
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

        /*
        Parameters' CONTAINER value cannot wait -- if the type is delayed, the value will be set to NOT_CONTAINER.
        (This is decided in the StatementAnalysisImpl.create... methods.)
        The "EXTERNAL_CONTAINER" property can make up for that.
        This method ensures that local variables linked to parameters and fields receive the same treatment.
        See InstanceOf_14 for a practical example.

        InstanceOf_10 is an example where we have to stick with the NOT_CONTAINER value.
         */
        private Variable assignedToFieldOrParameter(EvaluationContext evaluationContext, Variable variable) {
            LinkedVariables linkedVariables = evaluationContext.linkedVariables(variable);
            if (linkedVariables == null) return variable; // variable not yet known
            return linkedVariables.variablesAssigned()
                    .filter(v -> v instanceof FieldReference || v instanceof ParameterInfo)
                    .findFirst()
                    .orElse(variable);
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
                        stateDelaysFilteredForSelfReference, markAssignment, Set.of(), LinkedVariables.EMPTY,
                        LinkedVariables.EMPTY, Map.of(), 0);
            } else {
                CausesOfDelay mergedValueDelays = ecd.delays().merge(stateDelaysFilteredForSelfReference).merge(value.causesOfDelay());
                newEcd = new ChangeData(value, mergedValueDelays,
                        ecd.stateIsDelayed().merge(stateDelaysFilteredForSelfReference),
                        ecd.markAssignment() || markAssignment,
                        ecd.readAtStatementTime(), ecd.linkedVariables(), LinkedVariables.EMPTY, ecd.properties(),
                        ecd.modificationTimeIncrement());
            }
            valueChanges.put(assignmentTarget, newEcd);

            // TODO not too efficient, but for now... we want to get the symmetry right
            for (Map.Entry<Variable, DV> entry : linkedVariables) {
                link(assignmentTarget, entry.getKey(), entry.getValue());
            }
            return this;
        }

        private Expression valueDelayWithState(Variable assignmentTarget,
                                               Expression resultOfExpression,
                                               CausesOfDelay stateIsDelayed) {
            if (stateIsDelayed.isDelayed()
                    && !evaluationContext.getConditionManager().isReasonForDelay(assignmentTarget)
                    && !resultOfExpression.isInstanceOf(DelayedVariableExpression.class)) {
                return DelayedExpression.forState(Identifier.state(evaluationContext.statementIndex()),
                        resultOfExpression.returnType(), resultOfExpression, stateIsDelayed);
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
            LinkedVariables removeLv = LinkedVariables.of(assignmentTarget, LinkedVariables.LINK_ASSIGNED);
            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, CausesOfDelay.EMPTY, CausesOfDelay.EMPTY, false,
                        Set.of(), LinkedVariables.EMPTY, removeLv, Map.of(), 0);
            } else {
                newEcd = new ChangeData(ecd.value(), ecd.delays(), ecd.stateIsDelayed(), ecd.markAssignment(),
                        ecd.readAtStatementTime(), LinkedVariables.EMPTY,
                        removeLv.merge(ecd.toRemoveFromLinkedVariables()), ecd.properties(), ecd.modificationTimeIncrement());
            }
            valueChanges.put(variable, newEcd);
        }

        public void setProperty(Variable variable, Property property, DV value) {
            setProperty(variable, property, value, 0);
        }

        public void setProperty(Variable variable, Property property, DV value, int modificationTimeIncrement) {
            assert evaluationContext != null;

            CausesOfDelay causesOfDelay = property.propertyType == Property.PropertyType.CONTEXT
                    ? CausesOfDelay.EMPTY : value.causesOfDelay();

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ChangeData(null, causesOfDelay, CausesOfDelay.EMPTY, false, Set.of(),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(property, value), modificationTimeIncrement);
            } else {
                newEcd = new ChangeData(ecd.value(), ecd.delays().merge(causesOfDelay), ecd.stateIsDelayed(),
                        ecd.markAssignment(),
                        ecd.readAtStatementTime(), ecd.linkedVariables(), ecd.toRemoveFromLinkedVariables(),
                        mergeProperties(ecd.properties(), Map.of(property, value)),
                        modificationTimeIncrement + ecd.modificationTimeIncrement());
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
        delayed links must be symmetrical, until we know whether the direction is LINK_IS_HC_OF or not.
        you can never link to the return variable.
         */
        public void link(Variable from, Variable to, DV level) {
            assert !LinkedVariables.LINK_INDEPENDENT.equals(level);

            internalLink(from, to, level);
            if (!LinkedVariables.LINK_IS_HC_OF.equals(level) && !(from instanceof ReturnVariable)) {
                internalLink(to, from, level);
            }
        }

        /*
      we use a null value for inScope to indicate a delay
       */
        public void internalLink(Variable from, Variable to, DV level) {
            assert !(to instanceof ReturnVariable) : "Cannot link to return variable";

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(from);
            LinkedVariables linked = LinkedVariables.of(to, level);
            if (ecd == null) {
                newEcd = new ChangeData(null, level.causesOfDelay(),
                        CausesOfDelay.EMPTY, false, Set.of(), linked, LinkedVariables.EMPTY, Map.of(), 0);
            } else {
                newEcd = new ChangeData(ecd.value(), ecd.delays().merge(level.causesOfDelay()),
                        ecd.stateIsDelayed(), ecd.markAssignment(),
                        ecd.readAtStatementTime(), ecd.linkedVariables().merge(linked), ecd.toRemoveFromLinkedVariables(),
                        ecd.properties(), ecd.modificationTimeIncrement());
            }
            valueChanges.put(from, newEcd);
        }

        @Fluent
        public Builder addPrecondition(Precondition newPrecondition) {
            if (precondition == null) {
                precondition = newPrecondition;
            } else {
                EvaluationResultImpl context = EvaluationResultImpl.from(evaluationContext);
                precondition = precondition.combine(context, newPrecondition);
            }
            return this;
        }

        public void modifyingMethodAccess(Variable variable, Expression instance, LinkedVariables linkedVariables) {
            modifyingMethodAccess(variable, instance, linkedVariables, false);
        }

        public void modifyingMethodAccess(Variable variable,
                                          Expression instance,
                                          LinkedVariables linkedVariables,
                                          boolean markDelays) {
            ChangeData current = valueChanges.get(variable);
            ChangeData newVcd;
            if (current == null) {
                CausesOfDelay stateIsDelayed = evaluationContext.getConditionManager().causesOfDelay();
                newVcd = new ChangeData(instance,
                        markDelays ? stateIsDelayed.merge(instance.causesOfDelay()) : stateIsDelayed,
                        stateIsDelayed, false, Set.of(),
                        linkedVariables, LinkedVariables.EMPTY,
                        Map.of(), 0);
            } else {
                LinkedVariables lvs = linkedVariables == null
                        ? current.linkedVariables()
                        : linkedVariables.merge(current.linkedVariables());
                newVcd = new ChangeData(instance,
                        markDelays ? current.delays().merge(instance.causesOfDelay()) : current.delays(),
                        current.stateIsDelayed(), current.markAssignment(),
                        current.readAtStatementTime(), lvs, current.toRemoveFromLinkedVariables(), current.properties(),
                        current.modificationTimeIncrement());
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
                    DV inChangeData = cd.properties().getOrDefault(Property.CONTEXT_NOT_NULL, null);
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
                    .filter(e -> e.getValue().properties().containsKey(Property.CONTEXT_NOT_NULL))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                            e -> e.getValue().getProperty(Property.CONTEXT_NOT_NULL)));
        }

        public Builder copyChangeData(EvaluationResult result, Variable variable) {
            ChangeData cd = result.changeData().get(variable);
            if (cd != null) {
                valueChanges.put(variable, cd);
            }
            return this;
        }
    }
}

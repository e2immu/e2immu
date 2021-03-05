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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.DEBUG_MODIFY_CONTENT;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
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
                               List<ObjectFlow> objectFlows,
                               Map<Variable, ChangeData> changeData,
                               Expression precondition,
                               boolean addCircularCallOrUndeclaredFunctionalInterface) {

    public EvaluationResult {
        assert changeData.values().stream().noneMatch(ecd -> ecd.linkedVariables == null);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationResult.class);

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    public Stream<ObjectFlow> getObjectFlowStream() {
        return objectFlows.stream();
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

    public boolean isNotNull0() {
        assert evaluationContext != null;
        if (value instanceof VariableExpression variableExpression) {
            ChangeData cd = changeData.get(variableExpression.variable());
            if (cd != null) {
                Integer inChangeData = cd.properties.getOrDefault(VariableProperty.CONTEXT_NOT_NULL, null);
                if (inChangeData != null && inChangeData >= MultiLevel.EFFECTIVELY_NOT_NULL) return true;
            }
        }
        return evaluationContext.isNotNull0(value);
    }

    /**
     * Any of [value, markAssignment, linkedVariables]
     * can be used independently: possibly we want to mark assignment, but still have NO_VALUE for the value.
     * The stateOnAssignment can also still be NO_VALUE while the value is known, and vice versa.
     */
    public record ChangeData(Expression value,
                             boolean stateIsDelayed,
                             boolean markAssignment,
                             Set<Integer> readAtStatementTime,
                             LinkedVariables linkedVariables,
                             LinkedVariables staticallyAssignedVariables,
                             Map<VariableProperty, Integer> properties) {
        public ChangeData {
            Objects.requireNonNull(linkedVariables);
            Objects.requireNonNull(readAtStatementTime);
            Objects.requireNonNull(staticallyAssignedVariables);
            Objects.requireNonNull(properties);
        }

        public ChangeData merge(ChangeData other) {
            LinkedVariables combinedLinkedVariables = linkedVariables.merge(other.linkedVariables);
            LinkedVariables combinedStaticallyAssignedVariables = staticallyAssignedVariables.merge(other.staticallyAssignedVariables);
            Set<Integer> combinedReadAtStatementTime = SetUtil.immutableUnion(readAtStatementTime, other.readAtStatementTime);
            Map<VariableProperty, Integer> combinedProperties = VariableInfoImpl.mergeProperties(properties, other.properties);
            return new ChangeData(other.value == null ? value : other.value,
                    other.stateIsDelayed,
                    other.markAssignment || markAssignment,
                    combinedReadAtStatementTime,
                    combinedLinkedVariables,
                    combinedStaticallyAssignedVariables,
                    combinedProperties);
        }

        public boolean haveContextMethodDelay() {
            return properties.getOrDefault(VariableProperty.CONTEXT_MODIFIED_DELAY, Level.DELAY) == Level.TRUE;
        }

        public boolean haveDelaysCausedByMethodCalls() {
            return properties.getOrDefault(VariableProperty.SCOPE_DELAY, Level.DELAY) == Level.TRUE;
        }

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final Messages messages = new Messages();
        private List<ObjectFlow> objectFlows;
        private Expression value;
        private List<Expression> storedExpressions;
        private int statementTime;
        private final Map<Variable, ChangeData> valueChanges = new HashMap<>();
        private Expression precondition;
        private boolean addCircularCallOrUndeclaredFunctionalInterface;
        private boolean someValueWasDelayed;

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

            if (!evaluationResult.objectFlows.isEmpty()) {
                if (objectFlows == null) objectFlows = new LinkedList<>(evaluationResult.objectFlows);
                else objectFlows.addAll(evaluationResult.objectFlows);
            }
            for (Map.Entry<Variable, ChangeData> e : evaluationResult.changeData.entrySet()) {
                valueChanges.merge(e.getKey(), e.getValue(), ChangeData::merge);
            }

            statementTime = Math.max(statementTime, evaluationResult.statementTime);

            if (evaluationResult.precondition != null) {
                if (precondition == null) {
                    precondition = evaluationResult.precondition;
                } else {
                    precondition = combinePrecondition(precondition, evaluationResult.precondition);
                }
            }
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
                    storedExpressions == null ? null : ImmutableList.copyOf(storedExpressions),
                    someValueWasDelayed,
                    messages, objectFlows == null ? List.of() : objectFlows, valueChanges, precondition,
                    addCircularCallOrUndeclaredFunctionalInterface);
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

            int notNullValue = evaluationContext.getProperty(value, VariableProperty.NOT_NULL_EXPRESSION, true);
            if (notNullValue < notNullRequired) { // also do delayed values
                // so intrinsically we can have null.
                // if context not null is already high enough, don't complain
                int contextNotNull = getPropertyFromInitial(variable, VariableProperty.CONTEXT_NOT_NULL);
                int externalNotNull = getPropertyFromInitial(variable, VariableProperty.EXTERNAL_NOT_NULL);
                boolean fieldNotAssignedToParameter = variable instanceof FieldReference &&
                        !(value instanceof IsVariableExpression ve && ve.variable() instanceof ParameterInfo);
                if (fieldNotAssignedToParameter && externalNotNull == Level.DELAY) {
                    setProperty(variable, VariableProperty.EXTERNAL_NOT_NULL_DELAY, Level.TRUE);
                } else if (!evaluationContext.isDelayed(value)) {
                    boolean raiseError;
                    if (fieldNotAssignedToParameter) {
                        raiseError = externalNotNull == MultiLevel.NULLABLE && contextNotNull == MultiLevel.NULLABLE;
                    } else {
                        boolean isNotParameter = !(variable instanceof ParameterInfo) &&
                                !(value instanceof IsVariableExpression ve && ve.variable() instanceof ParameterInfo);

                        raiseError = isNotParameter &&
                                notNullValue == MultiLevel.NULLABLE && contextNotNull == MultiLevel.NULLABLE;
                    }
                    if (raiseError) {
                        Message message = Message.newMessage(evaluationContext.getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                                "Variable: " + variable.simpleName());
                        messages.add(message);
                    }
                }
                setProperty(variable, VariableProperty.CONTEXT_NOT_NULL, notNullRequired);
            }

        }

        private int getPropertyFromInitial(Expression expression, VariableProperty variableProperty) {
            if (expression instanceof VariableExpression variableExpression) {
                return getPropertyFromInitial(variableExpression.variable(), variableProperty);
            }
            return evaluationContext.getProperty(expression, variableProperty, true);
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
            ChangeData ecd = valueChanges.get(variable);
            ChangeData newEcd;
            if (ecd == null) {
                newEcd = new ChangeData(null,
                        false, false, Set.of(statementTime),
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of());
            } else {
                newEcd = new ChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        SetUtil.immutableUnion(ecd.readAtStatementTime, Set.of(statementTime)), ecd.linkedVariables,
                        ecd.staticallyAssignedVariables, ecd.properties);
            }
            valueChanges.put(variable, newEcd);

            // we do this because this. is often implicit (all other scopes will be marked read explicitly!)
            // when explicit, there may be two MarkRead modifications, which will eventually be merged
            if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof This) {
                markRead(fieldReference.scope);
            }
            return this;
        }

        public ObjectFlow createLiteralObjectFlow(ParameterizedType parameterizedType) {
            assert evaluationContext != null;

            return createInternalObjectFlow(new Location(evaluationContext.getCurrentType()), parameterizedType, Origin.LITERAL);
        }

        public ObjectFlow createInternalObjectFlow(Location location, ParameterizedType parameterizedType, Origin
                origin) {
            ObjectFlow objectFlow = new ObjectFlow(location, parameterizedType, origin);
            if (objectFlows == null) objectFlows = new LinkedList<>();
            if (!objectFlows.contains(objectFlow)) {
                objectFlows.add(objectFlow);
            }
            log(OBJECT_FLOW, "Created internal flow {}", objectFlow);
            return objectFlow;
        }

        public Builder raiseError(String messageString) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
                Message message = Message.newMessage(evaluationContext.getLocation(), messageString);
                messages.add(message);
            } else { // e.g. companion analyser
                LOGGER.warn("Analyser error: {}", messageString);
            }
            return this;
        }

        public void raiseError(String messageString, String extra) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
                Message message = Message.newMessage(evaluationContext.getLocation(), messageString, extra);
                messages.add(message);
            } else {
                LOGGER.warn("Analyser error: {}, {}", messageString, extra);
            }
        }

        /* when evaluating a variable field, a new local copy of the variable may be created
           when this happens, we need to link the field to this local copy
           this linking takes place in the value changes map, so that the linked variables can be set once, correctly.
         */
        public Expression currentExpression(Variable variable, boolean isNotAssignmentTarget) {
            ChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression == null || currentExpression.value == null) {
                assert evaluationContext != null;
                return evaluationContext.currentValue(variable, statementTime, isNotAssignmentTarget);
            }
            return currentExpression.value;
        }

        /*
        Used by MethodCall and EvaluateMethodCall
         */
        public NewObject currentInstance(Variable variable) {
            ChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression != null && currentExpression.value instanceof NewObject instance) return instance;
            assert evaluationContext != null;

            return evaluationContext.currentInstance(variable, statementTime);
        }

        // called when a new instance is needed because of a modifying method call, or when a variable doesn't have
        // an instance yet. Not called upon assignment.
        private void assignInstanceToVariable(Variable variable, NewObject instance, LinkedVariables
                linkedVariables) {
            ChangeData current = valueChanges.get(variable);
            ChangeData newVcd;
            if (current == null) {
                boolean stateIsDelayed = evaluationContext.getConditionManager().isDelayed();
                newVcd = new ChangeData(instance, stateIsDelayed, false, Set.of(), linkedVariables,
                        LinkedVariables.EMPTY, Map.of());
            } else {
                newVcd = new ChangeData(instance, current.stateIsDelayed, current.markAssignment,
                        current.readAtStatementTime, linkedVariables, current.staticallyAssignedVariables, current.properties);
            }
            valueChanges.put(variable, newVcd);
        }

        public void markContextModifiedDelay(Variable variable) {
            setProperty(variable, VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE);
        }

        public void markContextNotNullDelay(Variable variable) {
            setProperty(variable, VariableProperty.CONTEXT_NOT_NULL_DELAY, Level.TRUE);
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
                int ignoreContentModifications = getPropertyFromInitial(variable, VariableProperty.IGNORE_MODIFICATIONS);
                if (ignoreContentModifications != Level.TRUE) {
                    log(DEBUG_MODIFY_CONTENT, "Mark method object as context modified {}: {}", modified, variable.fullyQualifiedName());
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
                    log(DEBUG_MODIFY_CONTENT, "Skip marking method object as context modified: {}", variable.fullyQualifiedName());
                }
            } else {
                log(DEBUG_MODIFY_CONTENT, "Not yet marking {} as context modified, not present", variable.fullyQualifiedName());
            }
        }

        public void variableOccursInNotModified1Context(Variable variable, Expression currentExpression) {
            assert evaluationContext != null;

            if (evaluationContext.isDelayed(currentExpression)) return; // not yet
            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notModified1 = getPropertyFromInitial(currentExpression, VariableProperty.NOT_MODIFIED_1);
            if (notModified1 == Level.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                messages.add(message);
            } else if (notModified1 == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                setProperty(variable, VariableProperty.NOT_MODIFIED_1, Level.TRUE);
            }
        }

        /*
        Called from Assignment and from LocalVariableCreation.
         */
        public Builder assignment(Variable assignmentTarget,
                                  Expression resultOfExpression,
                                  LinkedVariables linkedVariables,
                                  LinkedVariables staticallyAssignedVariables) {
            assert evaluationContext != null;
            boolean stateIsDelayed = evaluationContext.getConditionManager().isDelayed();
            boolean resultOfExpressionIsDelayed = evaluationContext.isDelayed(resultOfExpression);
            boolean markAssignment = resultOfExpression != EmptyExpression.EMPTY_EXPRESSION;

            // in case both state and result of expression are delayed, we give preference to the result
            Expression value = stateIsDelayed && !resultOfExpressionIsDelayed
                    ? DelayedExpression.forState(resultOfExpression.returnType()) : resultOfExpression;

            ChangeData newEcd;
            ChangeData ecd = valueChanges.get(assignmentTarget);
            if (ecd == null) {
                newEcd = new ChangeData(value, stateIsDelayed,
                        markAssignment, Set.of(), linkedVariables, staticallyAssignedVariables, Map.of());
            } else {
                newEcd = new ChangeData(value, stateIsDelayed, ecd.markAssignment || markAssignment,
                        ecd.readAtStatementTime, linkedVariables, staticallyAssignedVariables, ecd.properties);
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
                        LinkedVariables.EMPTY, LinkedVariables.EMPTY, Map.of(property, value));
            } else {
                newEcd = new ChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables, ecd.staticallyAssignedVariables,
                        mergeProperties(ecd.properties, Map.of(property, value)));
            }
            valueChanges.put(variable, newEcd);
        }

        private Map<VariableProperty, Integer> mergeProperties
                (Map<VariableProperty, Integer> m1, Map<VariableProperty, Integer> m2) {
            Map<VariableProperty, Integer> res = new HashMap<>(m1);
            m2.forEach((vp, v) -> res.merge(vp, v, Math::max));
            return ImmutableMap.copyOf(res);
        }

        public void addPrecondition(Expression expression) {
            if (precondition == null) {
                precondition = expression;
            } else {
                precondition = combinePrecondition(precondition, expression);
            }
        }

        private Expression combinePrecondition(Expression e1, Expression e2) {
            return new And(evaluationContext.getPrimitives()).append(evaluationContext, e1, e2);
        }

        public void addCallOut(boolean b, ObjectFlow destination, Expression parameterExpression) {
            // TODO part of object flow
        }

        public void addAccess(boolean b, MethodAccess methodAccess, Expression object) {
            // TODO part of object flow
        }

        public void modifyingMethodAccess(Variable variable, NewObject newInstance, LinkedVariables linkedVariables) {
            //add(new StateData.RemoveVariableFromState(evaluationContext, variable)); TODO replace by other code
            assignInstanceToVariable(variable, newInstance, linkedVariables);
        }

        public void addErrorAssigningToFieldOutsideType(FieldInfo fieldInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(evaluationContext.getLocation(),
                    Message.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE, "Field " + fieldInfo.fullyQualifiedName()));
        }

        public void addParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(new Location(parameterInfo), Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        public void addCircularCallOrUndeclaredFunctionalInterface() {
            addCircularCallOrUndeclaredFunctionalInterface = true;
        }

        public int getStatementTime() {
            return statementTime;
        }

        private boolean acceptForMarking(Variable variable, TypeInfo subType) {
            assert evaluationContext != null;
            return evaluationContext.isPresent(variable) ||
                    variable instanceof FieldReference fieldReference &&
                            fieldReference.fieldInfo.owner != subType &&
                            fieldReference.fieldInfo.owner.primaryType() == subType.primaryType();
        }

        public void markVariablesFromSubMethod(MethodAnalysis methodAnalysis) {
            StatementAnalysis statementAnalysis = methodAnalysis.getLastStatement();
            if (statementAnalysis == null) return; // nothing we can do here
            statementAnalysis.variableStream()
                    // parameters are already present, fields should become present if they're not local
                    .filter(variableInfo -> acceptForMarking(variableInfo.variable(), methodAnalysis.getMethodInfo().typeInfo))
                    .forEach(variableInfo -> {
                        if (variableInfo.getReadId().compareTo(VariableInfoContainer.NOT_YET_READ) > 0) {
                            markRead(variableInfo.variable());
                        }
                        if (variableInfo.getAssignmentId().compareTo(VariableInfoContainer.NOT_YET_READ) > 0) {
                            assignment(variableInfo.variable(), variableInfo.getValue(), variableInfo.getLinkedVariables(),
                                    variableInfo.getStaticallyAssignedVariables());
                        }
                    });
        }

        public void markVariablesFromPrimaryTypeAnalyser(PrimaryTypeAnalyser pta) {
            pta.methodAnalyserStream().forEach(ma -> markVariablesFromSubMethod(ma.methodAnalysis));
            pta.fieldAnalyserStream().forEach(fa -> markVariablesFromSubFieldInitialisers(fa.fieldAnalysis, pta.primaryType));
        }

        private void markVariablesFromSubFieldInitialisers(FieldAnalysisImpl.Builder fieldAnalysis, TypeInfo
                subType) {
            assert evaluationContext != null;
            Expression initialValue = fieldAnalysis.getInitialValue();
            if (initialValue == EmptyExpression.EMPTY_EXPRESSION || initialValue == null) return;
            initialValue.variables().stream()
                    .filter(variable -> acceptForMarking(variable, subType))
                    .forEach(this::markRead);
        }

        public void addDelayOnPrecondition() {
            addPrecondition(DelayedExpression.forPrecondition(evaluationContext.getPrimitives()));
        }
    }
}

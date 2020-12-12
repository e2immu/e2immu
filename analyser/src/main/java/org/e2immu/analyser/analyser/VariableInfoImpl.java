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
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.SetOnce;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;

class VariableInfoImpl implements VariableInfo {
    public final Variable variable;
    /**
     * Generally the variable's fully qualified name, but can be more complicated (see DependentVariable)
     */
    public final String name;

    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

    private final SetOnce<Expression> value = new SetOnce<>(); // value from step 3 (initialisers)

    public final SetOnce<Expression> stateOnAssignment = new SetOnce<>();

    public final SetOnce<ObjectFlow> objectFlow = new SetOnce<>();
    public final SetOnce<Set<Variable>> linkedVariables = new SetOnce<>();

    VariableInfoImpl(Variable variable) {
        this.variable = Objects.requireNonNull(variable);
        this.name = variable.fullyQualifiedName();
    }

    VariableInfoImpl(VariableInfoImpl previous) {
        this.name = previous.name();
        this.variable = previous.variable();
        this.properties.copyFrom(previous.properties);
        this.value.copy(previous.value);
        this.stateOnAssignment.copy(previous.stateOnAssignment);
        this.linkedVariables.copy(previous.linkedVariables);
        this.objectFlow.copy(previous.objectFlow);
    }

    @Override
    public boolean isVariableField() {
        return variable instanceof FieldReference; // FIXME and some other criterion
    }

    @Override
    public Stream<Map.Entry<VariableProperty, Integer>> propertyStream() {
        return properties.stream();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Variable variable() {
        return variable;
    }

    @Override
    public Set<Variable> getLinkedVariables() {
        return linkedVariables.getOrElse(null);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow.getOrElse(ObjectFlow.NO_FLOW);
    }

    @Override
    public Expression getValue() {
        return value.getOrElse(NO_VALUE);
    }

    @Override
    public Expression getStateOnAssignment() {
        return stateOnAssignment.getOrElse(NO_VALUE);
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public int getProperty(VariableProperty variableProperty, int defaultValue) {
        return properties.getOrDefault(variableProperty, defaultValue);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[name=").append(name).append(", props=").append(properties);
        if (value.isSet()) {
            sb.append(", value=").append(value.get());
        }
        return sb.append("]").toString();
    }

    public boolean setProperty(VariableProperty variableProperty, int value) {
        Integer prev = properties.put(variableProperty, value);
        return prev == null || prev < value;
    }

    public boolean remove() {
        Integer prev = properties.put(VariableProperty.REMOVED, Level.TRUE);
        return prev == null || prev < Level.TRUE;

    }

    public void setObjectFlow(ObjectFlow objectFlow) {
        this.objectFlow.set(objectFlow);
    }

    @Override
    public Map<VariableProperty, Integer> getProperties() {
        return properties.toImmutableMap();
    }

    @Override
    public VariableInfo freeze() {
        return null;
    }

    @Override
    public boolean hasProperty(VariableProperty variableProperty) {
        return properties.isSet(variableProperty);
    }

    public void writeValue(Expression value) {
        if (value != NO_VALUE) {
            setValue(value);
        }
    }

    // we essentially compute the union
    public void mergeLinkedVariables(boolean existingValuesWillBeOverwritten, VariableInfoImpl existing, List<VariableInfo> merge) {
        Set<Variable> merged = new HashSet<>();
        if (!existingValuesWillBeOverwritten) {
            if (existing.linkedVariablesIsSet()) {
                merged.addAll(existing.getLinkedVariables());
            } //else
            // typical situation: int a; if(x) { a = 5; }. Existing has not been assigned
            // this will end up an error when the variable is read before being assigned
        }
        for (VariableInfo vi : merge) {
            if (!vi.linkedVariablesIsSet()) return;
            merged.addAll(vi.getLinkedVariables());
        }
        if (!linkedVariablesIsSet() || !getLinkedVariables().equals(merged)) {
            linkedVariables.set(merged);
        }
    }

    private record MergeOp(VariableProperty variableProperty, IntBinaryOperator operator, int initial) {
    }

    // it is important to note that the properties are NOT read off the value, but from the properties map
    // this copying has to have taken place earlier; for each of the variable properties below:

    private static final List<MergeOp> MERGE = List.of(
            new MergeOp(VariableProperty.NOT_NULL, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IMMUTABLE, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.CONTAINER, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.IDENTITY, Math::min, Integer.MAX_VALUE),
            new MergeOp(VariableProperty.READ, Math::max, Level.DELAY),
            new MergeOp(VariableProperty.ASSIGNED, Math::max, Level.DELAY)
    );

    /**
     * Merge the value of this object with the values of a list of other variables.
     * <p>
     * This method has to decide whether a new variableInfo object should be created.
     * <p>
     * As soon as there is a need for overwriting the value or the state, either the provided newObject must be used
     * (where we assume we can write) or a completely new object must be returned. This new object then starts as a copy
     * of this object.
     */
    public VariableInfoImpl merge(EvaluationContext evaluationContext,
                                  VariableInfoImpl newObject,
                                  boolean existingValuesWillBeOverwritten,
                                  List<VariableInfo> merge) {
        Expression mergedValue = mergeValue(evaluationContext, existingValuesWillBeOverwritten, merge);
        Expression currentValue = getValue();
        if (mergedValue == NO_VALUE || currentValue.equals(mergedValue))
            return newObject == null ? this : newObject; // no need to create
        if (newObject == null) {
            if (!value.isSet()) {
                setValue(mergedValue);
                return this;
            }
            VariableInfoImpl newVi = new VariableInfoImpl(variable);
            //if (!existingValuesWillBeOverwritten) newVi.properties.putAll(properties);
            newVi.setValue(mergedValue);
            newVi.stateOnAssignment.copy(stateOnAssignment);
            return newVi;
        }
        if (!newObject.value.isSet() || !newObject.value.get().equals(mergedValue)) {
            newObject.setValue(mergedValue); // will cause severe problems if the value already there is different :-)
        }
        return newObject;
    }

    void setValue(Expression value) {
        if (value instanceof VariableExpression variableValue && variableValue.variable() == variable) {
            throw new UnsupportedOperationException("Cannot redirect to myself");
        }
        this.value.set(value);
    }

    public void mergeProperties(boolean existingValuesWillBeOverwritten, VariableInfo previous, List<VariableInfo> merge) {
        VariableInfo[] list = merge
                .stream().filter(vi -> vi.getValue().isComputeProperties())
                .toArray(n -> new VariableInfo[n + 1]);
        if (!existingValuesWillBeOverwritten && getValue().isComputeProperties()) {
            list[list.length - 1] = previous;
        }
        for (MergeOp mergeOp : MERGE) {
            int commonValue = mergeOp.initial;

            for (VariableInfo vi : list) {
                if (vi != null) {
                    int value = vi.getProperty(mergeOp.variableProperty);
                    commonValue = mergeOp.operator.applyAsInt(commonValue, value);
                }
            }
            if (commonValue != mergeOp.initial) {
                setProperty(mergeOp.variableProperty, commonValue);
            }
        }
    }

    private Expression mergeValue(EvaluationContext evaluationContext,
                                  boolean existingValuesWillBeOverwritten,
                                  List<VariableInfo> merge) {
        Expression currentValue = getValue();
        if (!existingValuesWillBeOverwritten) {
            if (currentValue.isUnknown()) return currentValue;
        }
        boolean haveANoValue = merge.stream().anyMatch(v -> !v.stateOnAssignmentIsSet());
        if (haveANoValue) return NO_VALUE;

        if (merge.isEmpty()) {
            if (existingValuesWillBeOverwritten) throw new UnsupportedOperationException();
            return currentValue;
        }

        boolean allValuesIdentical = merge.stream().allMatch(v -> currentValue.equals(v.getValue()));
        if (allValuesIdentical) return currentValue;


        if (merge.size() == 1) {
            if (existingValuesWillBeOverwritten) return merge.get(0).getValue();
            Expression result = oneNotOverwritten(evaluationContext, currentValue, merge.get(0));
            if (result != null) return result;
        }

        if (merge.size() == 2) {
            Expression result = existingValuesWillBeOverwritten ? twoOverwritten(evaluationContext, merge.get(0), merge.get(1))
                    : two(evaluationContext, currentValue, merge.get(0), merge.get(1));
            if (result != null) return result;
        }
/*
        boolean noneEmpty = merge.stream().noneMatch(vi -> vi.getStateOnAssignment().isBoolValueTrue());
        if (noneEmpty) {
            Variable variable = allInvolveConstantsEqualToAVariable(merge);
            if (variable != null) {
                return inlineSwitch(existingValuesWillBeOverwritten, currentValue, variable, merge);
            }
        }
*/
        // no clue
        return noConclusion(evaluationContext.getPrimitives());
    }

    private Expression noConclusion(Primitives primitives) {
        return new NewObject(primitives, variable.parameterizedType(), getObjectFlow());
    }


    private Expression oneNotOverwritten(EvaluationContext evaluationContext, Expression a, VariableInfo vi) {
        Expression b = vi.getValue();
        Expression x = vi.getStateOnAssignment();

        // int c = a; if(x) c = b;  --> c = x?b:a
        if (!x.isBoolValueTrue()) {
            return safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, x, b, a, ObjectFlow.NO_FLOW),
                    evaluationContext.getPrimitives());
        }

        return noConclusion(evaluationContext.getPrimitives());
    }

    private Expression two(EvaluationContext evaluationContext, Expression x, VariableInfo vi1, VariableInfo vi2) {
        Expression s1 = vi1.getStateOnAssignment();
        Expression s2 = vi2.getStateOnAssignment();

        // silly situation, twice the same condition
        // int c = ex; if(s1) c = a; if(s1) c =b;
        if (s1.equals(s2) && !s1.isBoolValueTrue()) {
            return safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, s1, vi2.getValue(), x, ObjectFlow.NO_FLOW),
                    evaluationContext.getPrimitives());
        }
        // int c = x; if(s1) c = a; if(s2) c = b; --> s1?a:(s2?b:x)
        if (!s1.isBoolValueTrue() && !s2.isBoolValueTrue()) {
            Expression s2bx = safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, s2, vi2.getValue(), x, ObjectFlow.NO_FLOW),
                    evaluationContext.getPrimitives());
            return safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, s1, vi1.getValue(), s2bx, ObjectFlow.NO_FLOW),
                    evaluationContext.getPrimitives());
        }
        return noConclusion(evaluationContext.getPrimitives());
    }

    private Expression twoOverwritten(EvaluationContext evaluationContext, VariableInfo vi1, VariableInfo vi2) {
        Expression s1 = vi1.getStateOnAssignment();
        Expression s2 = vi2.getStateOnAssignment();

        if (!s1.isBoolValueTrue() && !s2.isBoolValueTrue()) {
            // int c; if(s1) c = a; else c = b;
            if (Negation.negate(evaluationContext, s1).equals(s2)) {
                return safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, s1, vi1.getValue(), vi2.getValue(), ObjectFlow.NO_FLOW),
                        evaluationContext.getPrimitives());
            } else throw new UnsupportedOperationException("? impossible situation");
        }
        return noConclusion(evaluationContext.getPrimitives());
    }

    private Expression safe(EvaluationResult result, Primitives primitives) {
        if (result.getModificationStream().anyMatch(m -> m instanceof StatementAnalyser.RaiseErrorMessage)) {
            // something gone wrong, retreat
            return noConclusion(primitives);
        }
        return result.value;
    }
}

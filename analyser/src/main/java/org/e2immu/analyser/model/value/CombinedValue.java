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

package org.e2immu.analyser.model.value;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// a combined value is NOT a variable value. that means it should not be assigned to a variable
public class CombinedValue implements Value {

    public final List<Value> values;
    private final ParameterizedType commonType;

    private CombinedValue(ParameterizedType commonType, List<Value> values) {
        this.values = values;
        this.commonType = commonType;
    }

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    public static Value create(Primitives primitives, List<Value> values) {
        if (values.isEmpty()) throw new UnsupportedOperationException();
        ParameterizedType commonType = commonType(InspectionProvider.defaultFrom(primitives), values);
        return new CombinedValue(commonType, ImmutableList.copyOf(values));
    }

    private static ParameterizedType commonType(InspectionProvider inspectionProvider, List<Value> values) {
        ParameterizedType commonType = values.get(0).type();
        for (int i = 1; i < values.size(); i++) {
            if (commonType == null) return null;
            commonType = commonType.commonType(inspectionProvider, values.get(i).type());
        }
        return commonType;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return values.stream()
                .filter(Value::isComputeProperties)
                .mapToInt(value -> evaluationContext.getProperty(value, variableProperty)).min().orElse(Level.DELAY);
    }

    @Override
    public int order() {
        return ORDER_COMBINED;
    }

    @Override
    public int internalCompareTo(Value v) {
        return ListUtil.compare(values, ((CombinedValue) v).values);
    }

    @Override
    public Set<Variable> variables() {
        return values.stream().flatMap(v -> v.variables().stream()).collect(Collectors.toSet());
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        Set<Variable> result = new HashSet<>();
        for (Value value : values) {
            Set<Variable> sub = evaluationContext.linkedVariables(value);
            if (sub == null) return null; // DELAY
            result.addAll(sub);
        }
        return result;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if(predicate.test(this)) {
            values.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public String print(PrintMode printMode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        if (Primitives.isPrimitiveExcludingVoid(type())) return null;
        return new Instance(type(), getObjectFlow(), UnknownValue.EMPTY);
    }

    @Override
    public ParameterizedType type() {
        return commonType;
    }
}

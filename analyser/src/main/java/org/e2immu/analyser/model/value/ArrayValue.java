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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// {1,2,3}, {a, b, {1,3,3}}, ...
public class ArrayValue implements Value {

    public final Value combinedValue; // NO_VALUE when no values
    public final List<Value> values;
    public final ObjectFlow objectFlow;

    public ArrayValue(Primitives primitives, ObjectFlow objectFlow, List<Value> values) {
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.values = ImmutableList.copyOf(values);
        combinedValue = values.isEmpty() ? UnknownValue.NO_VALUE : CombinedValue.create(primitives, values);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        List<EvaluationResult> reClauseERs = values.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        List<Value> reValues = reClauseERs.stream().map(er -> er.value).collect(Collectors.toList());
        return new EvaluationResult.Builder()
                .compose(reClauseERs)
                .setValue(new ArrayValue(evaluationContext.getPrimitives(), objectFlow, reValues))
                .build();
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "{" + values.stream().map(v -> v.print(printMode)).collect(Collectors.joining(",")) + "}";
    }

    @Override
    public int order() {
        return ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Value v) {
        return ListUtil.compare(values, ((ArrayValue) v).values);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) {
            int notNull = evaluationContext.getProperty(combinedValue, variableProperty);
            return MultiLevel.shift(MultiLevel.EFFECTIVE, notNull); // default = @NotNull level 0
        }
        // default is to refer to each of the components
        return evaluationContext.getProperty(combinedValue, variableProperty);
    }

    @Override
    public Set<Variable> variables() {
        return combinedValue.variables();
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayValue that = (ArrayValue) o;
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if(predicate.test(this)) {
            values.forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return new Instance(type(), getObjectFlow(), UnknownValue.EMPTY);
    }
}
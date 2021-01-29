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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NoValue implements Expression, NullState {

    public static final NoValue EMPTY = new NoValue();
    public static final String NO_VALUE = "<no value>";

    private final Map<Variable, Integer> map = new HashMap<>();

    public NoValue() {
    }

    public NoValue(Map<Variable, Integer> map) {
        this.map.putAll(map);
    }

    public NoValue merge(NoValue other) {
        Map<Variable, Integer> map = new HashMap<>(this.map);
        other.map.forEach((k, v) -> {
            Integer inMap = map.get(k);
            Integer newValue = inMap == null ? v : Math.max(inMap, v);
            map.put(k, newValue);
        });
        return new NoValue(map);
    }

    @Override
    public Integer get(Variable variable) {
        return map.get(variable);
    }

    @Override
    public Stream<Map.Entry<Variable, Integer>> getStream() {
        return map.entrySet().stream();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return NO_VALUE;
    }

    @Override
    public String debugOutput() {
        return "<no value: " + map.entrySet().stream()
                .map(e -> e.getKey().simpleName() + ":" + e.getValue())
                .sorted()
                .collect(Collectors.joining(", ")) + ">";
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(new Text(NO_VALUE, debugOutput()));
    }

    @Override
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder().build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return Level.FALSE;
    }

    @Override
    public int unknownOrder() {
        return 1;
    }
}

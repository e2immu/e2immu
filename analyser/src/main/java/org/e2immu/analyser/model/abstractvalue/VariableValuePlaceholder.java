/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

// used in a return statement, to freeze the properties
// also in @Mark @Only computation to hold the approved precondition values; in this case the properties are not computed

public class VariableValuePlaceholder extends ValueWithVariable {
    @NotNull
    public final String name; // the name in the variable properties; this will speed up grabbing the variable properties

    public final ObjectFlow objectFlow;
    private final Map<VariableProperty, Integer> properties;
    private final Set<Variable> linkedVariables;

    public VariableValuePlaceholder(Value valueForProperties, VariableValue original, EvaluationContext evaluationContext, ObjectFlow objectFlow) {

        // IMPORTANT: the null here indicates that FieldReferences of MULTI_COPY type will be seen as normal rather than MULTI_COPY
        super(original.variable, null);

        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.name = original.name;
        ImmutableMap.Builder<VariableProperty, Integer> builder = new ImmutableMap.Builder<>();
        if (evaluationContext != null) {
            for (VariableProperty property : VariableProperty.RETURN_VALUE_PROPERTIES) {
                builder.put(property, evaluationContext.getProperty(valueForProperties, property));
            }
        }
        properties = builder.build();
        if (evaluationContext != null) {
            linkedVariables = original.linkedVariables(evaluationContext);
        } else {
            linkedVariables = Set.of();
        }
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return linkedVariables;
    }
}

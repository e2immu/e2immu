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

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableContext {
    public final VariableContext parentContext;

    public static VariableContext initialVariableContext(VariableContext parentContext, Map<String, FieldReference> staticallyImportedFields) {
        return new VariableContext(parentContext, staticallyImportedFields);
    }

    public static VariableContext dependentVariableContext(VariableContext parentContext) {
        return new VariableContext(parentContext, new HashMap<>());
    }

    private VariableContext(VariableContext parentContext, Map<String, FieldReference> staticallyImportedFields) {
        this.fields = new HashMap<>(staticallyImportedFields);
        this.parentContext = parentContext;
    }

    private final Map<String, FieldReference> fields;
    private final Map<String, LocalVariableReference> localVars = new HashMap<>();
    private final Map<String, ParameterInfo> parameters = new HashMap<>();

    public Variable get(String name, boolean complain) {
        Variable variable = localVars.get(name);
        if (variable != null) return variable;
        variable = parameters.get(name);
        if (variable != null) return variable;
        variable = fields.get(name);
        if (variable != null) return variable;
        if (parentContext != null) {
            variable = parentContext.get(name, false);
        }
        if (variable == null && complain) {
            throw new UnsupportedOperationException("Unknown variable in context: '" + name + "'");
        }
        return variable;
    }

    /**
     * we'll add them in the correct order, so no overwriting!
     *
     * @param variable the variable to be added
     */
    public void add(FieldReference variable) {
        String name = variable.simpleName();
        if (!fields.containsKey(name)) {
            fields.put(name, variable);
        }
    }

    public void add(ParameterInfo variable) {
        parameters.put(variable.simpleName(), variable);
    }

    public void add(InspectionProvider inspectionProvider, LocalVariable variable, List<Expression> assignmentExpressions) {
        localVars.put(variable.name(), new LocalVariableReference(inspectionProvider, variable, assignmentExpressions));
    }

    public void addAll(List<LocalVariableReference> localVariableReferences) {
        localVariableReferences.forEach(lvr -> {
            localVars.put(lvr.variable.name(), lvr);
        });
    }

    @Override
    public String toString() {
        return "VariableContext{" +
                parentContext +
                ", local " + localVars.keySet() + ", fields " + fields.keySet() +
                '}';
    }
}

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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableContext {
    public final VariableContext parentContext;

    public static VariableContext initialVariableContext(Map<String, FieldReference> staticallyImportedFields) {
        return new VariableContext(staticallyImportedFields, null);
    }

    public static VariableContext dependentVariableContext(VariableContext parentContext) {
        return new VariableContext(new HashMap<>(), parentContext);
    }

    private VariableContext(Map<String, FieldReference> staticallyImportedFields, VariableContext parentContext) {
        this.fields = new HashMap<>(staticallyImportedFields);
        this.parentContext = parentContext;
    }

    private Map<String, FieldReference> fields;
    private Map<String, LocalVariableReference> localVars = new HashMap<>();
    private Map<String, ParameterInfo> parameters = new HashMap<>();

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
            throw new UnsupportedOperationException("Unknown variable in context " + name);
        }
        return variable;
    }

    /**
     * we'll add them in the correct order, so no overwriting!
     *
     * @param variable the variable to be added
     */
    public void add(FieldReference variable) {
        String name = variable.name();
        if(!fields.containsKey(name)) {
            fields.put(name, variable);
        }
    }

    public void add(ParameterInfo variable) {
        parameters.put(variable.name(), variable);
    }

    public void add(LocalVariable variable, List<Expression> assignmentExpressions) {
        localVars.put(variable.name, new LocalVariableReference(variable, assignmentExpressions));
    }

    public void addAll(List<LocalVariableReference> localVariableReferences) {
        localVariableReferences.forEach(lvr -> {
            localVars.put(lvr.variable.name, lvr);
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

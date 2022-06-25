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

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;

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

    public void add(LocalVariableReference variable) {
        localVars.put(variable.simpleName(), variable);
    }

    public void add(LocalVariable variable, Expression assignmentExpression) {
        localVars.put(variable.name(), new LocalVariableReference(variable, assignmentExpression));
    }

    public void addAll(List<LocalVariableReference> localVariableReferences) {
        localVariableReferences.forEach(lvr -> localVars.put(lvr.variable.name(), lvr));
    }

    @Override
    public String toString() {
        return "VariableContext{" +
                parentContext +
                ", local " + localVars.keySet() + ", fields " + fields.keySet() +
                '}';
    }
}

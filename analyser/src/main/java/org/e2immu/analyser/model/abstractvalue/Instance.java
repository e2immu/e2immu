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

import org.e2immu.analyser.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.log;

// an object, instance of a certain class, potentially after casting
public class Instance implements Value {
    public final ParameterizedType parameterizedType;
    public final List<Value> constructorParameterValues;
    public final MethodInfo constructor;

    public Instance(ParameterizedType parameterizedType) {
        this(parameterizedType, null, null);
    }

    public Instance(ParameterizedType parameterizedType, MethodInfo constructor, List<Value> parameterValues) {
        this.parameterizedType = parameterizedType;
        this.constructor = constructor;
        this.constructorParameterValues = parameterValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance that = (Instance) o;
        return parameterizedType.equals(that.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType);
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof Instance) {
            return parameterizedType.detailedString()
                    .compareTo(((Instance) o).parameterizedType.detailedString());
        }
        if (o instanceof VariableValue) return 1;
        if (o instanceof MethodValue) return -1;
        return -1;
    }

    @Override
    public String toString() {
        return "instanceof " + parameterizedType.detailedString()
                + (constructorParameterValues != null ? "(" + constructorParameterValues.stream()
                .map(Value::toString)
                .collect(Collectors.joining(", ")) + ")" : "");
    }

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        if (constructorParameterValues == null || constructor == null) return Set.of();
        if (constructorParameterValues.isEmpty() && constructor.typeInfo.isStatic()) {
            return Set.of(); // independent!
        }
        boolean differentType = constructor.typeInfo != evaluationContext.getCurrentMethod().typeInfo;
        if ((bestCase || differentType) && constructor.isIndependent(evaluationContext.getTypeContext()) == Boolean.TRUE) {
            return Set.of();
        }
        Set<Variable> result = new HashSet<>();
        constructorParameterValues.stream().map(v -> v.linkedVariables(bestCase, evaluationContext)).forEach(result::addAll);

        // TODO  not modified, but should be on methods! constructors don't return values
        if (constructor.methodAnalysis.variablesLinkedToMethodResult.isSet()) {
            Set<Variable> links = constructor.methodAnalysis.variablesLinkedToMethodResult.get();
            for (Variable link : links) {
                if (link instanceof ParameterInfo) {
                    MethodInfo owner = ((ParameterInfo) link).parameterInspection.get().owner;
                    if (owner.isConstructor) {
                        // TODO add restriction on @Final for the fields that do the transfer, OR somehow clear 'constructorParameterValues' on instance
                        Value value = constructorParameterValues.get(((ParameterInfo) link).index);
                        Set<Variable> toAdd = value.linkedVariables(bestCase, evaluationContext);
                        log(LINKED_VARIABLES, "Via constructor, add {} for result of {}", Variable.detailedString(toAdd), constructor.fullyQualifiedName());
                        result.addAll(toAdd);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return true;
    }
}

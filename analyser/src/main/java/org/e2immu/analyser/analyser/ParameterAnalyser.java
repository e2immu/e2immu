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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Lazy;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NullNotAllowed;

import java.util.Map;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private final TypeContext typeContext;

    public ParameterAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public void check(ParameterInfo parameterInfo) {
        log(ANALYSER, "Checking parameter {}", parameterInfo.detailedString());

        Lazy<String> where = new Lazy<>(() -> "In method " +
                parameterInfo.parameterInspection.get().owner.fullyQualifiedName() + ", " +
                parameterInfo.detailedString());

        parameterInfo.error(NotModified.class, typeContext.notModified.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, where.get() +
                        ": parameter should " + (mustBeAbsent ? "not " : "") + "be marked @NotModified"));

        parameterInfo.error(NullNotAllowed.class, typeContext.nullNotAllowed.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, where.get() +
                        ": parameter should " + (mustBeAbsent ? "not " : "") + "be marked @NullNotAllowed"));
    }

    public boolean notModified(ParameterInfo parameterInfo, Boolean directContentModification) {
        if (!parameterInfo.isNotModifiedByDefinition(typeContext)) {
            if (directContentModification != null) {
                boolean notModified = !directContentModification;
                if (!parameterInfo.parameterAnalysis.annotations.isSet(typeContext.notModified.get())) {
                    log(MODIFY_CONTENT, "Mark {} of {} " + (notModified ? "" : "NOT ") + " @NotModified",
                            parameterInfo.detailedString(),
                            parameterInfo.parameterInspection.get().owner.distinguishingName());
                    parameterInfo.parameterAnalysis.annotations.put(typeContext.notModified.get(), notModified);
                    return true;
                }
            } else {
                log(DELAYED, "Delaying setting parameter not modified on {}", parameterInfo.detailedString());
            }
        }
        return false;
    }

    /*
     computation is based on the premise that if a parameter ends up with the property PERMANENTLY NOT NULL, then
     it should get a @NullNotAllowed annotation
     the code is more complex because there may have been indirect assignments

     String method(String s) {
       String t = s;
       return t.trim();
     }

     Here `t` will get the PERMANENTLY_NOT_NULL, but the value of `t` will be the VariableValue `s`
    */

    public boolean isNullNotAllowed(VariableProperties methodProperties) {
        boolean changes = false;
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : methodProperties.variableProperties.entrySet()) {
            Variable variable = entry.getKey();
            ParameterInfo parameterInfo = null;
            boolean isPermanentlyNotNull = false;
            if (variable instanceof ParameterInfo) {
                parameterInfo = (ParameterInfo) variable;
                isPermanentlyNotNull = entry.getValue().properties.contains(VariableProperty.PERMANENTLY_NOT_NULL);
            } else {
                Value value = entry.getValue().getCurrentValue();
                if (value instanceof VariableValue) {
                    Variable assignedVariable = ((VariableValue) value).value;
                    if (assignedVariable instanceof ParameterInfo) {
                        parameterInfo = (ParameterInfo) assignedVariable;
                        VariableProperties.AboutVariable aboutVariable = methodProperties.variableProperties.get(parameterInfo);
                        Objects.requireNonNull(aboutVariable);
                        isPermanentlyNotNull = entry.getValue().properties.contains(VariableProperty.PERMANENTLY_NOT_NULL);
                    }
                }
            }
            if (parameterInfo != null && isPermanentlyNotNull && !parameterInfo.parameterAnalysis.annotations.isSet(typeContext.nullNotAllowed.get())) {
                log(NULL_NOT_ALLOWED, "Adding implicit null not allowed on {}", parameterInfo.detailedString());
                parameterInfo.parameterAnalysis.annotations.put(typeContext.nullNotAllowed.get(), true);
                changes = true;
            }
        }
        return changes;
    }
}

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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

public class ParameterAnalysis extends Analysis {

    private final ParameterizedType parameterizedType;
    private final TypeContext typeContext; // can be null, for primitives
    private final MethodInfo owner; // can be null, for lambda expressions

    public ParameterAnalysis(@NotNull ParameterizedType parameterizedType,
                             @NotNull TypeContext typeContext,
                             MethodInfo owner) {
        this.owner = owner;
        this.parameterizedType = parameterizedType;
        this.typeContext = typeContext;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                if (owner != null && Level.value(owner.typeInfo.typeAnalysis.
                        getProperty(VariableProperty.NOT_NULL_PARAMETERS), Level.NOT_NULL) == Level.TRUE)
                    return Level.TRUE;
                break;
            case NOT_MODIFIED:
                if (parameterizedType.isNotModifiedByDefinition(typeContext)) return Level.TRUE;
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (bestType != null && (Level.value(bestType.typeAnalysis.getProperty(VariableProperty.IMMUTABLE),
                        Level.E2IMMUTABLE) == Level.TRUE ||
                        bestType.typeAnalysis.getProperty(VariableProperty.CONTAINER) == Level.TRUE)) {
                    return Level.TRUE;
                }
            default:
        }
        return super.getProperty(variableProperty);
    }
}

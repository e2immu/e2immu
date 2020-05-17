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

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalysis extends Analysis {

    private final ParameterizedType parameterizedType;
    private final MethodInfo owner; // can be null, for lambda expressions
    private final String logName;

    public ParameterAnalysis(String logName, @NotNull ParameterizedType parameterizedType, MethodInfo owner) {
        this.owner = owner;
        this.logName = logName;
        this.parameterizedType = parameterizedType;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                if (owner != null && Level.haveTrueAt(owner.typeInfo.typeAnalysis.get()
                        .getProperty(VariableProperty.NOT_NULL_PARAMETERS), Level.NOT_NULL))
                    return Level.TRUE; // we've already marked our owning type with @NotNull...
                break;
            case NOT_MODIFIED:
                if (parameterizedType.isNotModifiedByDefinition()) return Level.TRUE;
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (bestType != null && (Level.haveTrueAt(bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
                        Level.E2IMMUTABLE) ||
                        bestType.typeAnalysis.get().getProperty(VariableProperty.CONTAINER) == Level.TRUE)) {
                    return Level.TRUE;
                }
            case IMMUTABLE:
            case CONTAINER:
                return Level.FALSE; // no assignment, so no way of knowing
            default:
        }
        return super.getProperty(variableProperty);
    }


    public boolean notModified(Boolean directContentModification) {
        if (directContentModification != null) {
            boolean notModified = !directContentModification;
            if (getProperty(VariableProperty.NOT_MODIFIED) == Level.DELAY) {
                log(NOT_MODIFIED, "Mark {} " + (notModified ? "" : "NOT") + " @NotModified",
                        logName);
                setProperty(VariableProperty.NOT_MODIFIED, notModified);
                return true;
            }
        } else {
            log(DELAYED, "Delaying setting parameter @NotModified on {}", logName);
        }

        return false;
    }

    public boolean notNull(Boolean notNull) {
        if (notNull != null) {
            if (getProperty(VariableProperty.NOT_NULL) == Level.DELAY) {
                log(NOT_NULL, "Mark {}  " + (notNull ? "" : "NOT") + " @NotNull", logName);
                setProperty(VariableProperty.NOT_NULL, notNull);
                return true;
            }
        } else {
            log(DELAYED, "Delaying setting parameter @NotNull on {}", logName);
        }
        return false;
    }
}

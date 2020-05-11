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
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.Set;

public class FieldAnalysis extends Analysis {

    public final TypeInfo bestType;
    public final TypeInfo owner;

    public FieldAnalysis(TypeInfo bestType, TypeInfo owner) {
        this.owner = owner;
        this.bestType = bestType;
    }

    // if the field turns out to be effectively final, it can have a value
    public final SetOnce<Value> effectivelyFinalValue = new SetOnce<>();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // here, the key of the map are fields; the local variables and parameters are stored in method analysis
    // the values are either other fields (in which case these other fields are not linked to parameters)
    // or parameters
    public final SetOnce<Set<Variable>> variablesLinkedToMe = new SetOnce<>();

    public final SetOnceMap<MethodInfo, Boolean> errorsForAssignmentsOutsidePrimaryType = new SetOnceMap<>();

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_MODIFIED:
                if (bestType == null) return Level.TRUE; // we cannot modify because we cannot even execute a method
                if (bestType.isPrimitive()) return Level.TRUE;
                int e2Immutable = Level.value(getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE);
                if (e2Immutable != Level.FALSE) return e2Immutable;
                break;

            case NOT_NULL:
                if (bestType != null && bestType.isPrimitive()) return Level.TRUE;
                int notNullFields = owner.typeAnalysis.getProperty(VariableProperty.NOT_NULL_FIELDS);
                return Level.best(notNullFields, super.getProperty(VariableProperty.NOT_NULL));

            case FINAL:
                int e1ImmutableOwner = Level.value(owner.typeAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E1IMMUTABLE);
                if (e1ImmutableOwner == Level.TRUE) return Level.TRUE;
                break;

            case IMMUTABLE:
                int immutableType = owner == bestType || bestType == null ? Level.FALSE :
                        bestType.typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                return Level.best(immutableType, super.getProperty(variableProperty));

            default:
        }
        return super.getProperty(variableProperty);
    }
}

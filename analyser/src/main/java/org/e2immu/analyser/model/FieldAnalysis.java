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
import org.e2immu.analyser.objectflow.origin.CallOutsArgumentToParameter;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FieldAnalysis extends Analysis {

    public final TypeInfo bestType;
    public final TypeInfo owner;
    public final boolean isExplicitlyFinal;
    public final ParameterizedType type;
    public final Location location;
    public final FieldInfo fieldInfo;

    public FieldAnalysis(FieldInfo fieldInfo) {
        super(fieldInfo.hasBeenDefined(), fieldInfo.name);
        this.owner = fieldInfo.owner;
        this.bestType = fieldInfo.type.bestTypeInfo();
        isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
        type = fieldInfo.type;
        location = new Location(fieldInfo);
        this.fieldInfo = fieldInfo;
        objectFlow = new ObjectFlow(new org.e2immu.analyser.objectflow.Location(fieldInfo), type, new CallOutsArgumentToParameter());
    }

    @Override
    protected Location location() {
        return location;
    }

    @Override
    public AnnotationMode annotationMode() {
        return owner.typeAnalysis.get().annotationMode();
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

    public final SetOnce<Boolean> fieldError = new SetOnce<>();

    private ObjectFlow objectFlow;

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case MODIFIED:
                if (bestType == null) return Level.FALSE; // we cannot modify because we cannot even execute a method
                int e2Immutable = Level.value(getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE);
                if (e2Immutable == Level.DELAY) return e2Immutable;
                if (e2Immutable == Level.TRUE) return Level.FALSE;
                break;

            case NOT_NULL:
                if (bestType != null && bestType.isPrimitive()) return Level.TRUE;
                int notNullFields = owner.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL_FIELDS);
                return Level.best(notNullFields, super.getProperty(VariableProperty.NOT_NULL));

            case FINAL:
                int e1ImmutableOwner = Level.value(owner.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E1IMMUTABLE);
                if (e1ImmutableOwner == Level.TRUE) return Level.TRUE;
                break;

            case IMMUTABLE:
                int immutableType = owner == bestType || bestType == null ? Level.FALSE :
                        bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
                int myProperty = super.getProperty(variableProperty);
                if (myProperty == Level.DELAY) return Level.DELAY;
                return Level.best(immutableType, myProperty);

            default:
        }
        return super.getProperty(variableProperty);
    }


    @Override
    public int maximalValue(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.MODIFIED) {
            if (type.isUnboundParameterType()) return Level.TRUE;
            if (bestType != null && Level.haveTrueAt(bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
                    Level.E2IMMUTABLE)) {
                return Level.TRUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public int minimalValue(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                if (type.isPrimitive()) return Level.TRUE;
                break;

            case MODIFIED:
                if (type.isUnboundParameterType()) return Integer.MAX_VALUE;
                if (bestType != null && Level.haveTrueAt(bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
                        Level.E2IMMUTABLE)) {
                    return Integer.MAX_VALUE;
                }
                return Level.UNDEFINED;

            case FINAL:
                if (isExplicitlyFinal) return Level.TRUE;
                if (Level.haveTrueAt(owner.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E1IMMUTABLE)) {
                    // in an @E1Immutable class, all fields are effectively final, so no need to write this
                    return Level.TRUE;
                }
                break;
            default:

            case IMMUTABLE:
                if (Level.haveTrueAt(type.getProperty(variableProperty), Level.E2IMMUTABLE))
                    return variableProperty.best;
                break;

            case CONTAINER:
                if (type.getProperty(variableProperty) == Level.TRUE) return Level.TRUE;
                break;

            case SIZE:
                return 1;

        }
        return Level.UNDEFINED;
    }

    @Override
    public Map<VariableProperty, AnnotationExpression> oppositesMap(TypeContext typeContext) {
        return Map.of(
                VariableProperty.MODIFIED, typeContext.notModified.get(),
                VariableProperty.FINAL, typeContext.variableField.get());
    }

    public ObjectFlow ensureObjectFlow(ObjectFlow objectFlow) {
        this.objectFlow = objectFlow.merge(this.objectFlow);
        return this.objectFlow;
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    private final Set<ObjectFlow> internalObjectFlows = new HashSet<>();

    public Iterable<ObjectFlow> getInternalObjectFlows() {
        return internalObjectFlows;
    }

    public void addInternalObjectFlow(ObjectFlow objectFlow) {
        if (objectFlow == ObjectFlow.NO_FLOW) throw new UnsupportedOperationException();
        internalObjectFlows.add(objectFlow);
    }

}

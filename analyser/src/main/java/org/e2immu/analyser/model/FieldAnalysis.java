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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

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
        ObjectFlow initialObjectFlow = new ObjectFlow(new org.e2immu.analyser.objectflow.Location(fieldInfo), type,
                Origin.INITIAL_FIELD_FLOW);
        objectFlow = new FirstThen<>(initialObjectFlow);
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

    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

    public final SetOnce<Boolean> supportData = new SetOnce<>();

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case MODIFIED:
                if (bestType == null) return Level.FALSE; // we cannot modify because we cannot even execute a method
                int immutable = getProperty(VariableProperty.IMMUTABLE);
                if (immutable == Level.DELAY) return Level.DELAY;
                if (MultiLevel.isE2Immutable(immutable)) return Level.FALSE;
                break;

            case FINAL:
                int immutableOwner = owner.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
                if (MultiLevel.isEffectivelyE1Immutable(immutableOwner)) return Level.TRUE;
                break;

            case IMMUTABLE:
                int immutableType = owner == bestType || bestType == null ? Level.FALSE :
                        bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
                int myProperty = super.getProperty(variableProperty);
                if (myProperty == Level.DELAY) return Level.DELAY;
                return Level.best(immutableType, myProperty);

            // container is, for fields, a property purely on the type
            case CONTAINER:
                return bestType == null ? Level.TRUE : bestType.typeAnalysis.get().getProperty(VariableProperty.CONTAINER);

            case NOT_NULL:
                if (bestType != null && bestType.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;

            default:
        }
        return super.getProperty(variableProperty);
    }

    @Override
    public Pair<Boolean, Integer> getImmutablePropertyAndBetterThanFormal() {
        return new Pair<>(false, getProperty(VariableProperty.IMMUTABLE));
    }

    @Override
    public int maximalValue(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.MODIFIED) {
            if (type.isUnboundParameterType()) return Level.TRUE;
            if (bestType != null && MultiLevel.isE2Immutable(getProperty(VariableProperty.IMMUTABLE))) {
                return Level.TRUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public int minimalValue(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                if (type.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;
                break;

            case MODIFIED:
                if (type.cannotBeModified()) return Level.TRUE; // never
                break;

            case FINAL:
                if (isExplicitlyFinal) return Level.TRUE; // never
                if (type.cannotBeModified()) return Level.UNDEFINED; // always
                int modified = getProperty(VariableProperty.MODIFIED);
                if (modified == Level.FALSE) return Level.TRUE; // never, is already @NotModified
                break;


            // dynamic type annotations

            case IMMUTABLE:
                if (MultiLevel.isE2Immutable(type.getProperty(VariableProperty.IMMUTABLE)))
                    return variableProperty.best; // never
                break;

            case CONTAINER:
                if (type.getProperty(variableProperty) == Level.TRUE) return Level.TRUE; // never
                break;

            case SIZE:
                return 1;

            default:
        }
        return Level.UNDEFINED;
    }

    @Override
    public Map<VariableProperty, AnnotationExpression> oppositesMap(E2ImmuAnnotationExpressions typeContext) {
        return Map.of(
                VariableProperty.MODIFIED, typeContext.notModified.get(),
                VariableProperty.FINAL, typeContext.variableField.get());
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }

}

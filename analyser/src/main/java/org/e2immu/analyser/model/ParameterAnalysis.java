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
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.AnnotationMode;

import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalysis extends Analysis {

    private final ParameterizedType parameterizedType;
    private final MethodInfo owner; // can be null, for lambda expressions
    private final String logName;

    public final SetOnce<FieldInfo> assignedToField = new SetOnce<>();
    public final SetOnce<Boolean> copiedFromFieldToParameters = new SetOnce<>();
    public final Location location;

    // initial flow object, used to collect call-outs
    // at the end of the method analysis replaced by a "final" flow object
    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    public ParameterAnalysis(ParameterInfo parameterInfo) {
        super(parameterInfo.hasBeenDefined(), parameterInfo.name);
        this.owner = parameterInfo.parameterInspection.get().owner;
        this.logName = parameterInfo.detailedString() + (owner == null ? " in lambda" : " in " + owner.distinguishingName());
        this.parameterizedType = parameterInfo.parameterizedType;
        this.location = new Location(parameterInfo);
        ObjectFlow initialObjectFlow = new ObjectFlow(new org.e2immu.analyser.objectflow.Location(parameterInfo), parameterizedType, Origin.INITIAL_PARAMETER_FLOW);
        objectFlow = new FirstThen<>(initialObjectFlow);
    }

    @Override
    protected Location location() {
        return location;
    }

    @Override
    public AnnotationMode annotationMode() {
        return owner.typeInfo.typeAnalysis.get().annotationMode();
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                return Math.max(super.getProperty(VariableProperty.NOT_NULL), notNullFromOwner());

            case MODIFIED: {
                // if the parameter is either formally or actually immutable, it cannot be modified
                if (notModifiedBecauseOfImmutableStatus()) return Level.FALSE;
                // now we rely on the computed value
                break;
            }

            // the only way to have a container is for the type to be a container, or for the user to have
            // contract annotated the parameter with @Container
            case CONTAINER: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (bestType != null) return bestType.typeAnalysis.get().getProperty(VariableProperty.CONTAINER);
                return Level.best(super.getProperty(VariableProperty.CONTAINER), Level.FALSE);
            }
            // when can a parameter be immutable? it cannot be computed in the method
            // (1) if the type is effectively immutable
            // (2) if all the flows leading up to this parameter are flows where the mark has been set; but this
            // we can only know when the method is private and the flows are known
            // (3) when the user has contract-annotated the parameter with @E2Immutable
            case IMMUTABLE: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                int immutableFromType;
                if (bestType != null) {
                    int immutable = bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
                    boolean objectFlowCondition = owner.isPrivate() && objectFlow.isSet() && objectFlow.get().getPrevious().allMatch(of -> of.conditionsMetForEventual(bestType));
                    immutableFromType = MultiLevel.eventual(immutable, objectFlowCondition);
                } else {
                    immutableFromType = MultiLevel.MUTABLE;
                }
                return Math.max(immutableFromType, MultiLevel.delayToFalse(super.getProperty(VariableProperty.IMMUTABLE)));
            }

            default:
        }
        return super.getProperty(variableProperty);
    }

    private int notNullFromOwner() {
        if (owner == null) return MultiLevel.DELAY;
        return owner.typeInfo.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL_PARAMETERS);
    }

    private boolean notModifiedBecauseOfImmutableStatus() {
        if (MultiLevel.value(getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE) >= MultiLevel.EVENTUAL_AFTER)
            return true;
        // if the parameter is an unbound generic, the same holds
        if (parameterizedType.isUnboundParameterType()) return true;
        // if we're inside a container and the method is not private, the parameter cannot be modified
        return owner != null && !owner.isPrivate() &&
                owner.typeInfo.typeAnalysis.get().getProperty(VariableProperty.CONTAINER) == Level.TRUE;
    }

    @Override
    public int minimalValue(VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL:
                return notNullFromOwner();

            case SIZE:
                int modified = getProperty(VariableProperty.MODIFIED);
                if (modified != Level.FALSE) return Integer.MAX_VALUE; // only annotation when also @NotModified!
                return Math.max(1, parameterizedType.getProperty(variableProperty));

            case MODIFIED:
                if (notModifiedBecauseOfImmutableStatus()) return Integer.MAX_VALUE;
                return Level.UNDEFINED;

            case CONTAINER:
            case IMMUTABLE:
                // TODO
                return parameterizedType.getProperty(variableProperty);

            default:
        }
        return Level.UNDEFINED;
    }

    @Override
    public int maximalValue(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.MODIFIED && notModifiedBecauseOfImmutableStatus()) return Level.TRUE;

        if (variableProperty == VariableProperty.NOT_NULL) {
            if (parameterizedType.isPrimitive()) return MultiLevel.NULLABLE;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public Map<VariableProperty, AnnotationExpression> oppositesMap(TypeContext typeContext) {
        return Map.of(VariableProperty.MODIFIED, typeContext.notModified.get());
    }

    public boolean notNull(Boolean notNull) {
        if (notNull != null) {
            if (getProperty(VariableProperty.NOT_NULL) == Level.DELAY) {
                log(NOT_NULL, "Mark {}  " + (notNull ? "" : "NOT") + " @NotNull", logName);
                setProperty(VariableProperty.NOT_NULL, notNull ? MultiLevel.EFFECTIVE : MultiLevel.FALSE);
                return true;
            }
        } else {
            log(DELAYED, "Delaying setting parameter @NotNull on {}", logName);
        }
        return false;
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }
}

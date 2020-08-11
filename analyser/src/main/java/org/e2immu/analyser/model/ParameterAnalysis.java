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
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.AnnotationMode;

import java.util.Map;
import java.util.stream.IntStream;

public class ParameterAnalysis extends Analysis {

    private final ParameterInfo parameterInfo;
    public final SetOnce<FieldInfo> assignedToField = new SetOnce<>();
    public final SetOnce<Boolean> copiedFromFieldToParameters = new SetOnce<>();
    public final Location location;

    // initial flow object, used to collect call-outs
    // at the end of the method analysis replaced by a "final" flow object
    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    public ParameterAnalysis(ParameterInfo parameterInfo) {
        super(parameterInfo.hasBeenDefined(), parameterInfo.name);
        this.parameterInfo = parameterInfo;
        this.location = new Location(parameterInfo);
        ObjectFlow initialObjectFlow = new ObjectFlow(new org.e2immu.analyser.objectflow.Location(parameterInfo),
                parameterInfo.parameterizedType, Origin.INITIAL_PARAMETER_FLOW);
        objectFlow = new FirstThen<>(initialObjectFlow);
    }

    @Override
    protected Location location() {
        return location;
    }

    @Override
    public AnnotationMode annotationMode() {
        return parameterInfo.owner.typeInfo.typeAnalysis.get().annotationMode();
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case MODIFIED: {
                // if the parameter is either formally or actually immutable, it cannot be modified
                if (notModifiedBecauseOfImmutableStatus()) return Level.FALSE;
                // now we rely on the computed value
                break;
            }

            // the only way to have a container is for the type to be a container, or for the user to have
            // contract annotated the parameter with @Container
            case CONTAINER: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                if (bestType != null) return bestType.typeAnalysis.get().getProperty(VariableProperty.CONTAINER);
                return Level.best(super.getProperty(VariableProperty.CONTAINER), Level.FALSE);
            }
            // when can a parameter be immutable? it cannot be computed in the method
            // (1) if the type is effectively immutable
            // (2) if all the flows leading up to this parameter are flows where the mark has been set; but this
            // we can only know when the method is private and the flows are known
            // (3) when the user has contract-annotated the parameter with @E2Immutable
            case IMMUTABLE: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                int immutableFromType;
                if (bestType != null) {
                    int immutable = bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
                    boolean objectFlowCondition = parameterInfo.owner.isPrivate() && objectFlow.isSet() && objectFlow.get().getPrevious().allMatch(of -> of.conditionsMetForEventual(bestType));
                    immutableFromType = MultiLevel.eventual(immutable, objectFlowCondition);
                } else {
                    immutableFromType = MultiLevel.MUTABLE;
                }
                return Math.max(immutableFromType, MultiLevel.delayToFalse(super.getProperty(VariableProperty.IMMUTABLE)));
            }

            case NOT_NULL: {
                TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
                if (bestType != null && bestType.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;
                return getPropertyCheckOverrides(variableProperty);
            }

            case SIZE:
                return getPropertyCheckOverrides(variableProperty);

            default:
        }
        return super.getProperty(variableProperty);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(super.getPropertyAsIs(variableProperty));
        IntStream overrideValues = parameterInfo.owner.methodAnalysis.get().overrides.stream()
                .mapToInt(mi -> mi.methodInspection.get().parameters.get(parameterInfo.index).parameterAnalysis.get().getPropertyAsIs(variableProperty));
        int max = IntStream.concat(mine, overrideValues).max().orElse(Level.DELAY);
        if (max == Level.DELAY && !hasBeenDefined) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    @Override
    public Pair<Boolean, Integer> getImmutablePropertyAndBetterThanFormal() {
        // TODO can be better!
        return new Pair<>(false, getProperty(VariableProperty.IMMUTABLE));
    }

    private boolean notModifiedBecauseOfImmutableStatus() {
        if (MultiLevel.value(getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE) >= MultiLevel.EVENTUAL_AFTER)
            return true;
        // if the parameter is an unbound generic, the same holds
        if (parameterInfo.parameterizedType.isUnboundParameterType()) return true;
        // if we're inside a container and the method is not private, the parameter cannot be modified
        return !parameterInfo.owner.isPrivate() &&
                parameterInfo.owner.typeInfo.typeAnalysis.get().getProperty(VariableProperty.CONTAINER) == Level.TRUE;
    }

    @Override
    public int minimalValue(VariableProperty variableProperty) {
        switch (variableProperty) {
            case SIZE:
                int modified = getProperty(VariableProperty.MODIFIED);
                if (modified != Level.FALSE) return Integer.MAX_VALUE; // only annotation when also @NotModified!
                return Level.bestSize(1, parameterInfo.parameterizedType.getProperty(variableProperty));

            case MODIFIED:
                if (notModifiedBecauseOfImmutableStatus()) return Integer.MAX_VALUE;
                return Level.UNDEFINED;

            case CONTAINER:
            case IMMUTABLE:
                throw new UnsupportedOperationException();

            default:
        }
        return Level.UNDEFINED;
    }

    @Override
    public int maximalValue(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.MODIFIED && notModifiedBecauseOfImmutableStatus()) return Level.TRUE;

        if (variableProperty == VariableProperty.NOT_NULL) {
            if (parameterInfo.parameterizedType.isPrimitive()) return MultiLevel.NULLABLE;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public Map<VariableProperty, AnnotationExpression> oppositesMap(E2ImmuAnnotationExpressions typeContext) {
        return Map.of(VariableProperty.MODIFIED, typeContext.notModified.get());
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }
}

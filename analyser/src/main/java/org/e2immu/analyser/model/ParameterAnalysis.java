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
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.stream.IntStream;

public class ParameterAnalysis extends Analysis {

    private final ParameterInfo parameterInfo;
    public final SetOnce<FieldInfo> assignedToField = new SetOnce<>();
    public final SetOnce<Boolean> copiedFromFieldToParameters = new SetOnce<>();
    public final Location location;

    // initial flow object, used to collect call-outs
    // at the end of the method analysis replaced by a "final" flow object
    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    // associated with the parameter is a list of other parameter indices it exposes
    // this list is only filled in when the EXPOSED property is Level.TRUE.
    public static final int FIELDS_EXPOSED = -1;
    public final SetOnceMap<Integer, Boolean> exposed = new SetOnceMap<>();

    public ParameterAnalysis(ParameterInfo parameterInfo) {
        super(parameterInfo.hasBeenDefined(), parameterInfo.name);
        this.parameterInfo = parameterInfo;
        this.location = new Location(parameterInfo);
        ObjectFlow initialObjectFlow = new ObjectFlow(new Location(parameterInfo),
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

    public void setProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, int value) {
        // raise error if the situation gets worse
        int valueFromOverrides = parameterInfo.owner.methodAnalysis.get().overrides.stream()
                .map(mi -> mi.methodInspection.get().parameters.get(parameterInfo.index))
                .mapToInt(pi -> pi.parameterAnalysis.get().getProperty(variableProperty)).max().orElse(Level.DELAY);
        if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
            boolean complain = variableProperty == VariableProperty.MODIFIED ? value > valueFromOverrides : value < valueFromOverrides;
            if (complain) {
                evaluationContext.raiseError(Message.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER, variableProperty.name + ", parameter " + parameterInfo.name);
            }
        }
        super.setProperty(variableProperty, value);
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case MODIFIED: {
                // if the parameter is level 2 immutable, it cannot be modified
                Boolean e2immu = parameterInfo.parameterizedType.isAtLeastEventuallyE2Immutable();
                if (e2immu == Boolean.TRUE) return Level.FALSE;
                if (parameterInfo.parameterizedType.isFunctionalInterface()) {
                    return Level.FALSE; // by definition, see manual
                }
                if (!parameterInfo.owner.isPrivate() &&
                        parameterInfo.owner.typeInfo.typeAnalysis.get().getProperty(VariableProperty.CONTAINER) == Level.TRUE) {
                    return Level.FALSE;
                }
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

            case NOT_MODIFIED_1:
                if (!parameterInfo.parameterizedType.isFunctionalInterface()) {
                    return Level.FALSE;
                }
                break;

            default:
        }
        return super.getProperty(variableProperty);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(super.getPropertyAsIs(variableProperty));
        IntStream theStream;
        if (hasBeenDefined) {
            theStream = mine;
        } else {
            IntStream overrideValues = parameterInfo.owner.methodAnalysis.get().overrides.stream()
                    .mapToInt(mi -> mi.methodInspection.get().parameters.get(parameterInfo.index).parameterAnalysis.get().getPropertyAsIs(variableProperty));
            theStream = IntStream.concat(mine, overrideValues);
        }
        int max = theStream.max().orElse(Level.DELAY);
        if (max == Level.DELAY && !hasBeenDefined) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

        // no annotations can be added to primitives
        if (parameterInfo.parameterizedType.isPrimitive()) return;

        // @NotModified, @Modified
        // implicitly @NotModified when E2Immutable, or functional interface
        int modified = getProperty(VariableProperty.MODIFIED);
        if (!parameterInfo.parameterizedType.isFunctionalInterface() &&
                parameterInfo.parameterizedType.isAtLeastEventuallyE2Immutable() != Boolean.TRUE) {
            AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified.get() :
                    e2ImmuAnnotationExpressions.modified.get();
            annotations.put(ae, true);
        }

        // @NotModified1
        doNotModified1(e2ImmuAnnotationExpressions);

        // @NotNull, @Size
        doNotNull(e2ImmuAnnotationExpressions);
        doSize(e2ImmuAnnotationExpressions);
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }
}

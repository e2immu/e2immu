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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.Map;

public class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {

    private final ParameterInfo parameterInfo;
    public final ObjectFlow objectFlow;
    public final FieldInfo assignedToField;
    public final boolean copiedFromFieldToParameters;

    private ParameterAnalysisImpl(ParameterInfo parameterInfo,
                                  Map<VariableProperty, Integer> properties,
                                  Map<AnnotationExpression, Boolean> annotations,
                                  ObjectFlow objectFlow,
                                  FieldInfo assignedToField,
                                  boolean copiedFromFieldToParameters) {
        super(parameterInfo.hasBeenDefined(), properties, annotations);
        this.parameterInfo = parameterInfo;
        this.objectFlow = objectFlow;
        this.assignedToField = assignedToField;
        this.copiedFromFieldToParameters = copiedFromFieldToParameters;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getParameterProperty(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, getObjectFlow(), variableProperty);
    }

    @Override
    public boolean isCopiedFromFieldToParameters() {
        return copiedFromFieldToParameters;
    }

    @Override
    public Location location() {
        return new Location(parameterInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return parameterInfo.owner.typeInfo.typeInspection.get().annotationMode;
    }

    @Override
    public FieldInfo getAssignedToField() {
        return assignedToField;
    }

    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    public static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {

        private final ParameterInfo parameterInfo;
        public final SetOnce<FieldInfo> assignedToField = new SetOnce<>();
        public final SetOnce<Boolean> copiedFromFieldToParameters = new SetOnce<>();
        public final Location location;
        private final AnalysisProvider analysisProvider;

        // initial flow object, used to collect call-outs
        // at the end of the method analysis replaced by a "final" flow object
        public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, ParameterInfo parameterInfo) {
            super(primitives, parameterInfo.hasBeenDefined(), parameterInfo.simpleName());
            this.parameterInfo = parameterInfo;
            this.location = new Location(parameterInfo);
            ObjectFlow initialObjectFlow = new ObjectFlow(new Location(parameterInfo),
                    parameterInfo.parameterizedType, Origin.INITIAL_PARAMETER_FLOW);
            objectFlow = new FirstThen<>(initialObjectFlow);
            this.analysisProvider = analysisProvider;
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getParameterProperty(analysisProvider, parameterInfo, getObjectFlow(), variableProperty);
        }

        @Override
        public boolean isHasBeenDefined() {
            return parameterInfo.owner.typeInfo.hasBeenDefined();
        }

        @Override
        public Location location() {
            return location;
        }

        @Override
        public AnnotationMode annotationMode() {
            return parameterInfo.owner.typeInfo.typeInspection.get().annotationMode;
        }

        @Override
        public ObjectFlow getObjectFlow() {
            return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
        }

        @Override
        public FieldInfo getAssignedToField() {
            return assignedToField.isSet() ? assignedToField.get() : null;
        }

        @Override
        public boolean isCopiedFromFieldToParameters() {
            return copiedFromFieldToParameters.getOrElse(false);
        }

        @Override
        public Analysis build() {
            return new ParameterAnalysisImpl(parameterInfo,
                    properties.toImmutableMap(),
                    annotations.toImmutableMap(), getObjectFlow(), getAssignedToField(), isCopiedFromFieldToParameters());
        }

        @Override
        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // no annotations can be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) return;

            // @NotModified, @Modified
            // implicitly @NotModified when E2Immutable, or functional interface
            int modified = getProperty(VariableProperty.MODIFIED);
            if (!parameterInfo.parameterizedType.isFunctionalInterface() &&
                    parameterInfo.parameterizedType.isAtLeastEventuallyE2Immutable(analysisProvider) != Boolean.TRUE) {
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
    }

}

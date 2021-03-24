/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.AnalysisImpl;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.support.FirstThen;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnceMap;

import java.util.Map;

public class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {

    private final ParameterInfo parameterInfo;
    public final ObjectFlow objectFlow;
    public final Map<FieldInfo, AssignedOrLinked> assignedToField;

    private ParameterAnalysisImpl(ParameterInfo parameterInfo,
                                  Map<VariableProperty, Integer> properties,
                                  Map<AnnotationExpression, AnnotationCheck> annotations,
                                  ObjectFlow objectFlow,
                                  Map<FieldInfo, AssignedOrLinked> assignedToField) {
        super(properties, annotations);
        this.parameterInfo = parameterInfo;
        this.objectFlow = objectFlow;
        this.assignedToField = assignedToField;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getParameterProperty(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, getObjectFlow(), variableProperty);
    }

    @Override
    public Location location() {
        return new Location(parameterInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return parameterInfo.owner.typeInfo.typeInspection.get().annotationMode();
    }


    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    public static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {

        private final ParameterInfo parameterInfo;
        private final SetOnceMap<FieldInfo, AssignedOrLinked> assignedToField = new SetOnceMap<>();
        private final FlipSwitch delaysOnFieldsResolved = new FlipSwitch();
        public final Location location;
        private final AnalysisProvider analysisProvider;

        // initial flow object, used to collect call-outs
        // at the end of the method analysis replaced by a "final" flow object
        public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, ParameterInfo parameterInfo) {
            super(primitives, parameterInfo.simpleName());
            this.parameterInfo = parameterInfo;
            this.location = new Location(parameterInfo);
            ObjectFlow initialObjectFlow = new ObjectFlow(new Location(parameterInfo),
                    parameterInfo.parameterizedType, Origin.INITIAL_PARAMETER_FLOW);
            objectFlow = new FirstThen<>(initialObjectFlow);
            this.analysisProvider = analysisProvider;
        }

        @Override
        public boolean isAssignedToFieldDelaysResolved() {
            return delaysOnFieldsResolved.isSet();
        }

        public void resolveFieldDelays() {
            if (!delaysOnFieldsResolved.isSet()) delaysOnFieldsResolved.set();
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getParameterProperty(analysisProvider, parameterInfo, getObjectFlow(), variableProperty);
        }

        @Override
        public Location location() {
            return location;
        }

        @Override
        public AnnotationMode annotationMode() {
            return parameterInfo.owner.typeInfo.typeInspection.get().annotationMode();
        }

        @Override
        public ObjectFlow getObjectFlow() {
            return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
        }

        @Override
        public Map<FieldInfo, AssignedOrLinked> getAssignedToField() {
            return assignedToField.toImmutableMap();
        }

        @Override
        public Analysis build() {
            return new ParameterAnalysisImpl(parameterInfo, properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(), getObjectFlow(), getAssignedToField());
        }

        @Override
        public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // no annotations can be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) return;

            // @NotModified, @Modified
            // implicitly @NotModified when E2Immutable, or functional interface
            int modified = getProperty(VariableProperty.MODIFIED_VARIABLE);
            if (!parameterInfo.parameterizedType.isFunctionalInterface() &&
                    parameterInfo.parameterizedType.isE2Immutable(analysisProvider) != Boolean.TRUE) {
                AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                        e2ImmuAnnotationExpressions.modified;
                annotations.put(ae, true);
            }

            // @NotModified1
            doNotModified1(e2ImmuAnnotationExpressions);

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions, getProperty(VariableProperty.NOT_NULL_PARAMETER));

            // @PropagateModification
            doPropagateModification(e2ImmuAnnotationExpressions);
        }

        public boolean addAssignedToField(FieldInfo fieldInfo, AssignedOrLinked assignedOrLinked) {
            if (!assignedToField.isSet(fieldInfo)) {
                assignedToField.put(fieldInfo, assignedOrLinked);
                return true;
            }
            return false;
        }

        public void freezeAssignedToField() {
            assignedToField.freeze();
        }

        public boolean assignedToFieldIsFrozen() {
            return assignedToField.isFrozen();
        }
    }

}

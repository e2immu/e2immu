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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.NotModified;

import java.util.Map;
import java.util.Set;

public class FieldAnalysisImpl extends AnalysisImpl implements FieldAnalysis {

    private final FieldInfo fieldInfo;
    public final boolean isOfImplicitlyImmutableDataType;
    public final Set<ObjectFlow> internalObjectFlows;
    public final ObjectFlow objectFlow;
    public final boolean fieldError;
    public final Set<Variable> variablesLinkedToMe;
    public final Value effectivelyFinalValue;

    private FieldAnalysisImpl(FieldInfo fieldInfo,
                              boolean isOfImplicitlyImmutableDataType,
                              ObjectFlow objectFlow,
                              Set<ObjectFlow> internalObjectFlows,
                              boolean fieldError,
                              Set<Variable> variablesLinkedToMe,
                              Value effectivelyFinalValue,
                              Map<VariableProperty, Integer> properties,
                              Map<AnnotationExpression, Boolean> annotations) {
        super(fieldInfo.hasBeenDefined(), properties, annotations);
        this.fieldInfo = fieldInfo;
        this.isOfImplicitlyImmutableDataType = isOfImplicitlyImmutableDataType;
        this.objectFlow = objectFlow;
        this.internalObjectFlows = internalObjectFlows;
        this.fieldError = fieldError;
        this.variablesLinkedToMe = variablesLinkedToMe;
        this.effectivelyFinalValue = effectivelyFinalValue;
    }

    @Override
    public Value getEffectivelyFinalValue() {
        return effectivelyFinalValue;
    }

    @Override
    public Set<Variable> getVariablesLinkedToMe() {
        return variablesLinkedToMe;
    }

    @Override
    public Boolean getFieldError() {
        return fieldError;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public Set<ObjectFlow> getInternalObjectFlows() {
        return internalObjectFlows;
    }

    @Override
    public Boolean isOfImplicitlyImmutableDataType() {
        return isOfImplicitlyImmutableDataType;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getFieldProperty(AnalysisProvider.DEFAULT_PROVIDER, fieldInfo, fieldInfo.type.bestTypeInfo(), variableProperty);
    }

    @Override
    public Location location() {
        return new Location(fieldInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return fieldInfo.owner.typeInspection.get().annotationMode;
    }

    public static class Builder extends AbstractAnalysisBuilder implements FieldAnalysis {
        public final TypeInfo bestType;
        public final boolean isExplicitlyFinal;
        public final ParameterizedType type;
        public final FieldInfo fieldInfo;
        public final MethodInfo sam;
        private final TypeAnalysis typeAnalysisOfOwner;

        public Builder(AnalyserContext analyserContext, @NotModified FieldInfo fieldInfo, TypeAnalysis typeAnalysisOfOwner) {
            super(analyserContext, fieldInfo.hasBeenDefined(), fieldInfo.name);
            this.typeAnalysisOfOwner = typeAnalysisOfOwner;
            this.bestType = fieldInfo.type.bestTypeInfo();
            isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
            type = fieldInfo.type;
            this.sam = !fieldInfo.fieldInspection.get().initialiser.isSet() ? null :
                    fieldInfo.fieldInspection.get().initialiser.get().implementationOfSingleAbstractMethod;
            ObjectFlow initialObjectFlow = new ObjectFlow(new Location(fieldInfo), type,
                    Origin.INITIAL_FIELD_FLOW);
            objectFlow = new FirstThen<>(initialObjectFlow);
            this.fieldInfo = fieldInfo;
        }

        @Override
        public boolean isHasBeenDefined() {
            return fieldInfo.owner.hasBeenDefined();
        }

        @Override
        public Location location() {
            return new Location(fieldInfo);
        }

        @Override
        public AnnotationMode annotationMode() {
            return typeAnalysisOfOwner.annotationMode();
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

        public final SetOnce<Boolean> fieldError = new SetOnce<>();

        public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

        public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

        public final SetOnce<Boolean> isOfImplicitlyImmutableDataType = new SetOnce<>();

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getFieldProperty(analyserContext, fieldInfo, bestType, variableProperty);
        }

        @Override
        public Value getEffectivelyFinalValue() {
            return effectivelyFinalValue.getOrElse(null);
        }

        @Override
        public Set<Variable> getVariablesLinkedToMe() {
            return variablesLinkedToMe.getOrElse(null);
        }

        @Override
        public Boolean getFieldError() {
            return fieldError.getOrElse(null);
        }

        @Override
        public ObjectFlow getObjectFlow() {
            return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
        }

        @Override
        public Set<ObjectFlow> getInternalObjectFlows() {
            return internalObjectFlows.getOrElse(null);
        }

        @Override
        public Boolean isOfImplicitlyImmutableDataType() {
            return isOfImplicitlyImmutableDataType.getOrElse(null);
        }

        @Override
        public Analysis build() {
            return new FieldAnalysisImpl(fieldInfo,
                    isOfImplicitlyImmutableDataType.getOrElse(false),
                    getObjectFlow(),
                    internalObjectFlows.getOrElse(Set.of()),
                    fieldError.getOrElse(false),
                    variablesLinkedToMe.getOrElse(Set.of()),
                    effectivelyFinalValue.getOrElse(null),
                    properties.toImmutableMap(),
                    annotations.toImmutableMap());
        }

        @Override
        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            int effectivelyFinal = getProperty(VariableProperty.FINAL);
            int ownerImmutable = typeAnalysisOfOwner.getProperty(VariableProperty.IMMUTABLE);
            int modified = getProperty(VariableProperty.MODIFIED);

            // @Final(after=), @Final, @Variable
            if (effectivelyFinal == Level.FALSE && MultiLevel.isEventuallyE1Immutable(ownerImmutable)) {
                String labels = typeAnalysisOfOwner.allLabelsRequiredForImmutable();
                annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal.get()
                        .copyWith(analyserContext.getPrimitives(), "after", labels), true);
            } else {
                if (effectivelyFinal == Level.TRUE && !isExplicitlyFinal) {
                    annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal.get(), true);
                }
                if (effectivelyFinal == Level.FALSE) {
                    annotations.put(e2ImmuAnnotationExpressions.variableField.get(), true);
                }
            }

            // all other annotations cannot be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(type)) return;

            // @NotModified(after=), @NotModified, @Modified
            if (modified == Level.TRUE && MultiLevel.isEventuallyE2Immutable(ownerImmutable)) {
                String marks = String.join(",", typeAnalysisOfOwner.marksRequiredForImmutable());
                annotations.put(e2ImmuAnnotationExpressions.notModified.get()
                        .copyWith(analyserContext.getPrimitives(), "after", marks), true);
            } else if (allowModificationAnnotation(analyserContext, effectivelyFinal)) {
                AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified.get() :
                        e2ImmuAnnotationExpressions.modified.get();
                annotations.put(ae, true);
            }

            doNotModified1(e2ImmuAnnotationExpressions);

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions);

            // @Size
            doSize(e2ImmuAnnotationExpressions);

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            int typeImmutable = typeImmutable();
            int fieldImmutable = super.getProperty(VariableProperty.IMMUTABLE);
            if (MultiLevel.isBetterImmutable(fieldImmutable, typeImmutable)) {
                doImmutableContainer(e2ImmuAnnotationExpressions, fieldImmutable, true);
            }
        }

        private boolean allowModificationAnnotation(AnalysisProvider analysisProvider, int effectivelyFinal) {
            if (effectivelyFinal <= Level.FALSE) return false;
            if (type.isAtLeastEventuallyE2Immutable(analysisProvider) == Boolean.TRUE) return false;
            if (type.isFunctionalInterface()) {
                return sam != null;
            }
            return true;
        }

        private int typeImmutable() {
            return fieldInfo.owner == bestType || bestType == null ? MultiLevel.FALSE :
                    bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
        }

        public boolean isDeclaredFunctionalInterface() {
            return false; // TODO
        }
    }
}
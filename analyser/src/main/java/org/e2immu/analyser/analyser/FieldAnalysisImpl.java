/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NoValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.FlipSwitch;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.NotModified;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FieldAnalysisImpl extends AnalysisImpl implements FieldAnalysis {

    private final FieldInfo fieldInfo;
    public final boolean isOfImplicitlyImmutableDataType;
    public final Set<ObjectFlow> internalObjectFlows;
    public final ObjectFlow objectFlow;
    public final boolean fieldError;
    public final LinkedVariables variablesLinkedToMe;
    public final Expression effectivelyFinalValue;
    public final Expression initialValue;  // value from the initialiser
    public final Expression stateOfEffectivelyFinalValue;

    private FieldAnalysisImpl(FieldInfo fieldInfo,
                              boolean isOfImplicitlyImmutableDataType,
                              ObjectFlow objectFlow,
                              Set<ObjectFlow> internalObjectFlows,
                              boolean fieldError,
                              LinkedVariables variablesLinkedToMe,
                              Expression effectivelyFinalValue,
                              Expression initialValue,
                              Expression stateOfEffectivelyFinalValue,
                              Map<VariableProperty, Integer> properties,
                              Map<AnnotationExpression, AnnotationCheck> annotations) {
        super(properties, annotations);
        this.fieldInfo = fieldInfo;
        this.isOfImplicitlyImmutableDataType = isOfImplicitlyImmutableDataType;
        this.objectFlow = objectFlow;
        this.internalObjectFlows = internalObjectFlows;
        this.fieldError = fieldError;
        this.variablesLinkedToMe = variablesLinkedToMe;
        this.effectivelyFinalValue = effectivelyFinalValue;
        this.initialValue = initialValue;
        this.stateOfEffectivelyFinalValue = stateOfEffectivelyFinalValue; // null when there is no EFV
    }

    @Override
    public Expression getStateOfEffectivelyFinalValue() {
        return stateOfEffectivelyFinalValue;
    }

    @Override
    public Expression getEffectivelyFinalValue() {
        return effectivelyFinalValue;
    }

    @Override
    public LinkedVariables getLinkedVariables() {
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
    public Expression getInitialValue() {
        return initialValue;
    }

    @Override
    public AnnotationMode annotationMode() {
        return fieldInfo.owner.typeInspection.get().annotationMode();
    }

    public static class Builder extends AbstractAnalysisBuilder implements FieldAnalysis {
        public final TypeInfo bestType;
        public final boolean isExplicitlyFinal;
        public final ParameterizedType type;
        public final FieldInfo fieldInfo;
        public final MethodInfo sam;
        private final TypeAnalysis typeAnalysisOfOwner;
        private final AnalysisProvider analysisProvider;
        public final SetOnce<Expression> initialValue = new SetOnce<>();
        public Expression stateOfEffectivelyFinalValue;

        public final SetOnce<List<Expression>> values = new SetOnce<>();
        public final FlipSwitch allLinksHaveBeenEstablished = new FlipSwitch();

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, @NotModified FieldInfo fieldInfo, TypeAnalysis typeAnalysisOfOwner) {
            super(primitives, fieldInfo.name);
            this.typeAnalysisOfOwner = typeAnalysisOfOwner;
            this.bestType = fieldInfo.type.bestTypeInfo();
            isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
            this.analysisProvider = analysisProvider;
            type = fieldInfo.type;
            this.sam = !fieldInfo.fieldInspection.get().fieldInitialiserIsSet() ? null :
                    fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
            ObjectFlow initialObjectFlow = new ObjectFlow(new Location(fieldInfo), type,
                    Origin.INITIAL_FIELD_FLOW);
            objectFlow = new FirstThen<>(initialObjectFlow);
            this.fieldInfo = fieldInfo;
        }

        @Override
        public Expression getInitialValue() {
            return initialValue.getOrElse(NoValue.EMPTY);
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
        public final SetOnce<Expression> effectivelyFinalValue = new SetOnce<>();

        // end product of the dependency analysis of linkage between the variables in a method
        // if A links to B, and A is modified, then B must be too.
        // In other words, if A->B, then B cannot be @NotModified unless A is too

        // here, the key of the map are fields; the local variables and parameters are stored in method analysis
        // the values are either other fields (in which case these other fields are not linked to parameters)
        // or parameters
        public final SetOnce<LinkedVariables> linkedVariables = new SetOnce<>();

        public final SetOnce<Boolean> fieldError = new SetOnce<>();

        public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

        public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

        public final SetOnce<Boolean> isOfImplicitlyImmutableDataType = new SetOnce<>();

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getFieldProperty(analysisProvider, fieldInfo, bestType, variableProperty);
        }

        @Override
        public Expression getEffectivelyFinalValue() {
            return effectivelyFinalValue.getOrElse(null);
        }

        @Override
        public LinkedVariables getLinkedVariables() {
            return linkedVariables.getOrElse(LinkedVariables.DELAY);
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
                    linkedVariables.getOrElse(LinkedVariables.EMPTY),
                    getEffectivelyFinalValue(),
                    getInitialValue(),
                    getStateOfEffectivelyFinalValue(),
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap());
        }

        @Override
        public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider,
                                                    E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            int effectivelyFinal = getProperty(VariableProperty.FINAL);
            int ownerImmutable = typeAnalysisOfOwner.getProperty(VariableProperty.IMMUTABLE);
            int modified = getProperty(VariableProperty.MODIFIED);

            // @Final(after=), @Final, @Variable
            if (effectivelyFinal == Level.FALSE && MultiLevel.isEventuallyE1Immutable(ownerImmutable)) {
                String labels = typeAnalysisOfOwner.allLabelsRequiredForImmutable();
                annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal.copyWith(primitives, "after", labels), true);
            } else {
                if (effectivelyFinal == Level.TRUE && !isExplicitlyFinal) {
                    annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal, true);
                }
                if (effectivelyFinal == Level.FALSE) {
                    annotations.put(e2ImmuAnnotationExpressions.variableField, true);
                }
            }

            // all other annotations cannot be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(type)) return;

            // @NotModified(after=), @NotModified, @Modified
            if (modified == Level.TRUE && MultiLevel.isEventuallyE2Immutable(ownerImmutable)) {
                String marks = String.join(",", typeAnalysisOfOwner.marksRequiredForImmutable());
                annotations.put(e2ImmuAnnotationExpressions.notModified.copyWith(primitives, "after", marks), true);
            } else if (allowModificationAnnotation(effectivelyFinal)) {
                AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                        e2ImmuAnnotationExpressions.modified;
                annotations.put(ae, true);
            }

            doNotModified1(e2ImmuAnnotationExpressions);

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions);

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            int typeImmutable = typeImmutable();
            int fieldImmutable = super.getProperty(VariableProperty.IMMUTABLE);
            if (MultiLevel.isBetterImmutable(fieldImmutable, typeImmutable)) {
                doImmutableContainer(e2ImmuAnnotationExpressions, fieldImmutable, true);
            }
        }

        private boolean allowModificationAnnotation(int effectivelyFinal) {
            if (effectivelyFinal <= Level.FALSE) return false;
            if (type.isAtLeastEventuallyE2Immutable(analysisProvider) == Boolean.TRUE) return false;
            if (type.isFunctionalInterface()) {
                return sam != null;
            }
            return true;
        }

        private int typeImmutable() {
            return fieldInfo.owner == bestType || bestType == null ? MultiLevel.FALSE :
                    analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
        }

        public boolean isDeclaredFunctionalInterface() {
            return false; // TODO
        }

        @Override
        public Expression getStateOfEffectivelyFinalValue() {
            return stateOfEffectivelyFinalValue;
        }

        public void setStateOfEffectivelyFinalValue(Expression stateOfEffectivelyFinalValue) {
            this.stateOfEffectivelyFinalValue = Objects.requireNonNull(stateOfEffectivelyFinalValue);
        }
    }
}

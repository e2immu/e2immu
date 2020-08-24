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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.ContractMark;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.*;
import java.util.stream.IntStream;

public class MethodAnalysis extends Analysis {

    public final Set<MethodInfo> overrides;
    public final ParameterizedType returnType;
    public final TypeInfo typeInfo;
    public final Location location;
    public final MethodInfo methodInfo;

    public MethodAnalysis(MethodInfo methodInfo) {
        this(methodInfo, methodInfo.typeInfo.overrides(methodInfo, true));
    }

    public static MethodAnalysis newMethodAnalysisForLambdaBlocks(MethodInfo methodInfo) {
        return new MethodAnalysis(methodInfo, Set.of());
    }

    private MethodAnalysis(MethodInfo methodInfo, Set<MethodInfo> overrides) {
        super(methodInfo.hasBeenDefined(), methodInfo.name);
        this.overrides = overrides;
        this.methodInfo = methodInfo;
        this.typeInfo = methodInfo.typeInfo;
        this.returnType = methodInfo.returnType();
        location = new Location(methodInfo);
        if (methodInfo.isConstructor || methodInfo.isVoid()) {
            // we set a NO_FLOW, non-modifiable
            objectFlow = new FirstThen<>(ObjectFlow.NO_FLOW);
            objectFlow.set(ObjectFlow.NO_FLOW);
        } else {
            ObjectFlow initialObjectFlow = new ObjectFlow(new org.e2immu.analyser.objectflow.Location(methodInfo), returnType, Origin.INITIAL_METHOD_FLOW);
            objectFlow = new FirstThen<>(initialObjectFlow);
        }
    }

    @Override
    protected Location location() {
        return location;
    }

    @Override
    public AnnotationMode annotationMode() {
        return typeInfo.typeAnalysis.get().annotationMode();
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case MODIFIED:
                // all methods in java.lang.String are @NotModified, but we do not bother writing that down
                // we explicitly check on EFFECTIVE, because in an eventually E2IMMU we want the methods to remain @Modified
                if (!methodInfo.isConstructor &&
                        typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE) == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                    return Level.FALSE;
                }
                return getPropertyCheckOverrides(VariableProperty.MODIFIED);

            case FLUENT:
            case IDENTITY:
            case INDEPENDENT:
            case SIZE:
                return getPropertyCheckOverrides(variableProperty);

            case NOT_NULL:
                if (returnType.isPrimitive()) return MultiLevel.EFFECTIVELY_NOT_NULL;
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                        typeInfo.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                return getPropertyCheckOverrides(VariableProperty.NOT_NULL);

            case IMMUTABLE:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR || returnType.isVoid())
                    throw new UnsupportedOperationException(); //we should not even be asking

                int immutableType = formalImmutableProperty();
                int immutableDynamic = dynamicImmutableProperty(immutableType);
                return MultiLevel.bestImmutable(immutableType, immutableDynamic);

            case CONTAINER:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR || returnType.isVoid())
                    throw new UnsupportedOperationException(); //we should not even be asking
                int container = returnType.getProperty(VariableProperty.CONTAINER);
                if (container == Level.DELAY) return Level.DELAY;
                return Level.best(getPropertyCheckOverrides(VariableProperty.CONTAINER), container);

            default:
        }
        return super.getProperty(variableProperty);
    }

    private int dynamicImmutableProperty(int formalImmutableProperty) {
        int immutableTypeAfterEventual = MultiLevel.eventual(formalImmutableProperty, getObjectFlow().conditionsMetForEventual(returnType));
        return Level.best(super.getProperty(VariableProperty.IMMUTABLE), immutableTypeAfterEventual);
    }

    private int formalImmutableProperty() {
        return returnType.getProperty(VariableProperty.IMMUTABLE);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(super.getPropertyAsIs(variableProperty));
        IntStream overrideValues = overrides.stream().mapToInt(mi -> mi.methodAnalysis.get().getPropertyAsIs(variableProperty));
        int max = IntStream.concat(mine, overrideValues).max().orElse(Level.DELAY);
        if (max == Level.DELAY && !hasBeenDefined) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        int modified = getProperty(VariableProperty.MODIFIED);

        // @Precondition
        if (precondition.isSet()) {
            Value value = precondition.get();
            if (value != UnknownValue.EMPTY) {
                AnnotationExpression ae = e2ImmuAnnotationExpressions.precondition.get()
                        .copyWith("value", value.toString());
                annotations.put(ae, true);
            }
        }

        // @Dependent @Independent
        boolean haveSupportData = typeInfo.typeAnalysis.get().haveSupportData();
        if (haveSupportData && (methodInfo.isConstructor || modified == Level.FALSE && allowIndependentOnMethod())) {
            int independent = getProperty(VariableProperty.INDEPENDENT);
            AnnotationExpression ae = independent == Level.FALSE ? e2ImmuAnnotationExpressions.dependent.get() :
                    e2ImmuAnnotationExpressions.independent.get();
            annotations.put(ae, true);
        }

        if (methodInfo.isConstructor) return;

        // @NotModified, @Modified
        AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified.get() :
                e2ImmuAnnotationExpressions.modified.get();
        annotations.put(ae, true);

        if (returnType.isVoid()) return;

        // @Identity
        if (getProperty(VariableProperty.IDENTITY) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.identity.get(), true);
        }

        // all other annotations cannot be added to primitives
        if (returnType.isPrimitive()) return;

        // @Fluent
        if (getProperty(VariableProperty.FLUENT) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.fluent.get(), true);
        }

        // @NotNull
        doNotNull(e2ImmuAnnotationExpressions);

        // @Size
        doSize(e2ImmuAnnotationExpressions);

        // dynamic type annotations for functional interface types: @NotModified1
        doNotModified1(e2ImmuAnnotationExpressions);

        // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
        int formallyImmutable = formalImmutableProperty();
        int dynamicallyImmutable = dynamicImmutableProperty(formallyImmutable);
        if (MultiLevel.isBetterImmutable(dynamicallyImmutable, formallyImmutable)) {
            doImmutableContainer(e2ImmuAnnotationExpressions, false, dynamicallyImmutable, true);
        }
    }

    private boolean allowIndependentOnMethod() {
        return !returnType.isPrimitive() && !returnType.isVoid() && !returnType.isAtLeastEventuallyE2Immutable();
    }

    // ************** LOCAL STORAGE

    // not to be stored. later, move to separate class...
    public final SetOnce<List<NumberedStatement>> numberedStatements = new SetOnce<>();

    /**
     * this one contains all own methods called from this method, and the transitive closure.
     * we use this to compute effective finality: some methods are only called from constructors,
     * they form part of the construction aspect of the class
     */
    public final SetOnce<Set<MethodInfo>> methodsOfOwnClassReached = new SetOnce<>();
    public final SetOnce<Boolean> partOfConstruction = new SetOnce<>();

    // ************** VARIOUS ODDS AND ENDS
    // used to check that in a utility class, no objects of the class itself are created
    public final SetOnce<Boolean> createObjectOfSelf = new SetOnce<>();

    // if true, the method has no (non-static) method calls on the "this" scope
    public final SetOnce<Boolean> staticMethodCallsOnly = new SetOnce<>();


    // ************** ERRORS

    public final SetOnce<Boolean> complainedAboutMissingStaticModifier = new SetOnce<>();
    public final SetOnceMap<ParameterInfo, Boolean> parameterAssignments = new SetOnceMap<>();
    public final SetOnceMap<LocalVariable, Boolean> unusedLocalVariables = new SetOnceMap<>();
    public final SetOnceMap<Variable, Boolean> uselessAssignments = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, Boolean> errorAssigningToFieldOutsideType = new SetOnceMap<>();
    public final SetOnceMap<MethodInfo, Boolean> errorCallingModifyingMethodOutsideType = new SetOnceMap<>();

    // ************** SUMMARIES
    // in combination with the properties in the super class, this forms the knowledge about the method itself
    public final SetOnce<Value> singleReturnValue = new SetOnce<>();

    public final SetOnce<TransferValue> thisSummary = new SetOnce<>();
    public final SetOnceMap<String, TransferValue> returnStatementSummaries = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, TransferValue> fieldSummaries = new SetOnceMap<>();

    // ************** LINKING

    // this one is the marker that says that links have been established
    public final SetOnce<Map<Variable, Set<Variable>>> variablesLinkedToFieldsAndParameters = new SetOnce<>();

    public final SetOnce<Set<Variable>> variablesLinkedToMethodResult = new SetOnce<>();

    // ************** PRECONDITION

    public final SetOnce<Value> precondition = new SetOnce<>();

    protected void writeMarkAndOnly(MarkAndOnly markAndOnly) {
        ContractMark contractMark = new ContractMark(markAndOnly.markLabel);
        preconditionForMarkAndOnly.set(contractMark);
        this.markAndOnly.set(markAndOnly);
    }

    // the name refers to the @Mark and @Only annotations. It is the data for this annotation.

    public static class MarkAndOnly {
        public final Value precondition;
        public final String markLabel;
        public final boolean mark;
        public final Boolean after; // null for a @Mark without @Only

        public MarkAndOnly(Value precondition, String markLabel, boolean mark, Boolean after) {
            this.precondition = precondition;
            this.mark = mark;
            this.markLabel = markLabel;
            this.after = after;
        }

        @Override
        public String toString() {
            return markLabel + "=" + precondition + "; after? " + after + "; @Mark? " + mark;
        }
    }

    // the value here (size will be one)
    public final SetOnce<Value> preconditionForMarkAndOnly = new SetOnce<>();
    public final SetOnce<MarkAndOnly> markAndOnly = new SetOnce<>();

    // ************* object flow

    public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }
}

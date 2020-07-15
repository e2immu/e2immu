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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.IntStream;

@NotNull
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
                //if (!methodInfo.isConstructor && Level.haveTrueAt(typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE)
                //        && getObjectFlow().conditionsMetForEventual(typeInfo)) {
                //    return Level.FALSE;
                //}
                //return getPropertyCheckOverrides(variableProperty);

            case FLUENT:
            case IDENTITY:
            case INDEPENDENT:
            case SIZE:
                return getPropertyCheckOverrides(variableProperty);

            case NOT_NULL:
                if (returnType.isPrimitive()) return Level.TRUE;
                int notNullMethods = typeInfo.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL_METHODS);
                int fluent = getProperty(VariableProperty.FLUENT);
                if (fluent == Level.TRUE) return Level.best(Level.TRUE,
                        typeInfo.typeAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                return Level.best(notNullMethods, getPropertyCheckOverrides(VariableProperty.NOT_NULL));

            case IMMUTABLE:
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return Level.FALSE;
                int fromReturnType = Level.haveTrueAt(returnType.getProperty(variableProperty), Level.E2IMMUTABLE) &&
                        getObjectFlow().conditionsMetForEventual(returnType) ? variableProperty.best : Level.DELAY;
                return Level.best(super.getProperty(VariableProperty.IMMUTABLE), fromReturnType);

            case CONTAINER:
                // don't understand why this code is here... return type @E2Immutable does NOT imply container
                //  if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return Level.FALSE;
                //  if (Level.haveTrueAt(returnType.getProperty(variableProperty), Level.E2IMMUTABLE)) {
                //      return variableProperty.best;
                //   }
                //  return super.getProperty(variableProperty);
            default:
        }
        return super.getProperty(variableProperty);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(super.getPropertyAsIs(variableProperty, Level.DELAY));
        IntStream overrideValues = overrides.stream().mapToInt(mi -> mi.methodAnalysis.get().getPropertyAsIs(variableProperty, Level.DELAY));
        int max = IntStream.concat(mine, overrideValues).max().orElse(Level.DELAY);
        if (max == Level.DELAY && !hasBeenDefined) {
            // no information found in the whole hierarchy
            return variableProperty.valueWhenAbsent(annotationMode());
        }
        return max;
    }

    @Override
    public int minimalValue(VariableProperty variableProperty) {
        switch (variableProperty) {
            case INDEPENDENT:
                // implicit on non-eventual @E2Immutable
                if (Level.haveTrueAt(typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE) &&
                        !typeInfo.isEventual()) {
                    return Level.TRUE;
                }
                break;

            case NOT_NULL:
                // don't show @NotNull on primitive types
                if (returnType.isPrimitive()) return Level.TRUE;
                break;

            case SIZE:
                // do not show @Size(min = 0) on a method
                return 1;

            case IMMUTABLE:
                // we show @ExImmutable on the method only when it is eventual, and the conditions have been met
                return returnType.isEventual() && getObjectFlow().conditionsMetForEventual(returnType)
                        ? Level.UNDEFINED : returnType.getProperty(variableProperty);

            case CONTAINER:
                // we don't show @Container on the method
                return returnType.getProperty(variableProperty);
            default:
        }
        return Level.UNDEFINED;
    }

    @Override
    public int maximalValue(VariableProperty variableProperty) {
        switch (variableProperty) {
            case INDEPENDENT:
            case MODIFIED:
                IntStream overrideValues = overrides.stream().mapToInt(mi -> mi.methodAnalysis.get().getPropertyAsIs(variableProperty, Level.DELAY));
                IntStream overrideTypes = overrides.stream().mapToInt(mi -> !mi.isPrivate() && Level.haveTrueAt(mi.typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE) ? Level.FALSE : Level.DELAY);
                int max = IntStream.concat(overrideTypes, overrideValues).max().orElse(Level.DELAY);
                if (max == Level.FALSE) return Level.TRUE;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public Map<VariableProperty, AnnotationExpression> oppositesMap(TypeContext typeContext) {
        return Map.of(
                VariableProperty.MODIFIED, typeContext.notModified.get(),
                VariableProperty.INDEPENDENT, typeContext.dependent.get());
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

    /*
    // used in the computation of effectively final fields
    public final SetOnceMap<FieldInfo, Boolean> fieldAssignments = new SetOnceMap<>(); // -> ASSIGNED in transfer value
    public final SetOnceMap<FieldInfo, Value> fieldAssignmentValues = new SetOnceMap<>(); // -> Value in transfer value

    // used in the computation of content modification of fields
    public final SetOnceMap<Variable, Boolean> contentModifications = new SetOnceMap<>(); // -> VP.NOT_MODIFIED
    // ignoring field assignments for @NotNull computation because of breaking symmetry field <-> parameter
    public final SetOnceMap<FieldInfo, Boolean> ignoreFieldAssignmentForNotNull = new SetOnceMap<>(); // should disappear

    public final SetOnceMap<FieldInfo, Boolean> fieldRead = new SetOnceMap<>(); // -> READ in transfer value
*/

    // ************** LINKING

    // this one is the marker that says that links have been established
    public final SetOnce<Map<Variable, Set<Variable>>> variablesLinkedToFieldsAndParameters = new SetOnce<>();

    public final SetOnce<Set<Variable>> variablesLinkedToMethodResult = new SetOnce<>();

    // ************** PRECONDITION

    public final SetOnce<Value> precondition = new SetOnce<>();

    @Override
    protected void writePrecondition(String value) {
        throw new UnsupportedOperationException(); //TODO not yet implemented--we'd have to parse our "value" language
    }

    @Override
    protected void preconditionFromAnalysisToAnnotation(TypeContext typeContext) {
        if (precondition.isSet()) {
            AnnotationExpression ae = typeContext.precondition.get().copyWith("value", precondition.get().toString());
            annotations.put(ae, true);
        }
    }

    public static class OnlyData {
        public final Value precondition;
        public final String markLabel;
        public final boolean mark;
        public final boolean after; // default before

        public OnlyData(Value precondition, String markLabel, boolean mark, boolean after) {
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
    public final SetOnce<Value> preconditionForOnlyData = new SetOnce<>();
    public final SetOnce<OnlyData> onlyData = new SetOnce<>();

    public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

    public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

    public ObjectFlow getObjectFlow() {
        return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
    }

}

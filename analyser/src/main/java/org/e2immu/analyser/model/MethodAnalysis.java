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
import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@NotNull
public class MethodAnalysis extends Analysis {

    private final Supplier<Stream<MethodInfo>> overrides;
    private final Supplier<ParameterizedType> returnType;
    public final TypeInfo typeInfo;

    public MethodAnalysis(TypeInfo typeInfo, Supplier<ParameterizedType> returnType, Supplier<Stream<MethodInfo>> overrides) {
        this.overrides = overrides;
        this.typeInfo = typeInfo;
        this.returnType = returnType;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case FLUENT:
            case IDENTITY:
            case INDEPENDENT:
                return getPropertyCheckOverrides(variableProperty);
            case NOT_MODIFIED:
                int typeNotModified = typeInfo.typeAnalysis.getProperty(variableProperty);
                if (typeNotModified == Level.TRUE) return typeNotModified;
                return getPropertyCheckOverrides(variableProperty);
            case NOT_NULL:
                int notNullMethods = typeInfo.typeAnalysis.getProperty(VariableProperty.NOT_NULL_METHODS);
                return Level.best(notNullMethods, getPropertyCheckOverrides(VariableProperty.NOT_NULL));
            case IMMUTABLE:
            case CONTAINER:
                ParameterizedType returnType = this.returnType.get();
                if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return Level.FALSE;
                if (returnType.isE2ContainerByDefinition()) {
                    return variableProperty.best;
                }
                return super.getProperty(variableProperty);
            default:
        }
        return super.getProperty(variableProperty);
    }

    private int getPropertyCheckOverrides(VariableProperty variableProperty) {
        IntStream mine = IntStream.of(super.getProperty(variableProperty));
        IntStream overrideValues = overrides.get().mapToInt(mi -> mi.methodAnalysis.getProperty(variableProperty));
        return IntStream.concat(mine, overrideValues).max().orElse(Level.DELAY);
    }


    // used to check that in a utility class, no objects of the class itself are created
    public final SetOnce<Boolean> createObjectOfSelf = new SetOnce<>();

    // not to be stored. later, move to separate class...
    public final SetOnce<List<NumberedStatement>> numberedStatements = new SetOnce<>();

    // if true, the method has no (non-static) method calls on the "this" scope
    public final SetOnce<Boolean> staticMethodCallsOnly = new SetOnce<>();
    public final SetOnce<Boolean> complainedAboutMissingStaticStatement = new SetOnce<>();

    // produces an error
    public final SetOnceMap<ParameterInfo, Boolean> parameterAssignments = new SetOnceMap<>();

    // used in the computation of effectively final fields
    public final SetOnceMap<FieldInfo, Boolean> fieldAssignments = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, Value> fieldAssignmentValues = new SetOnceMap<>();

    // used in the computation of content modification of fields
    public final SetOnceMap<Variable, Boolean> contentModifications = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, Boolean> ignoreFieldAssignmentForNotNull = new SetOnceMap<>();

    public final SetOnceMap<FieldInfo, Boolean> fieldRead = new SetOnceMap<>();
    public final SetOnce<Boolean> thisRead = new SetOnce<>();

    // produces a warning
    public final SetOnceMap<LocalVariable, Boolean> unusedLocalVariables = new SetOnceMap<>();
    public final SetOnceMap<Variable, Boolean> uselessAssignments = new SetOnceMap<>();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // valid as soon as fieldAssignments is set, and currently only used to compute @Linked annotations
    public final SetOnceMap<Variable, Set<Variable>> fieldsLinkedToFieldsAndVariables = new SetOnceMap<>();

    // this one is the marker that says that links have been established
    public final SetOnce<Map<Variable, Set<Variable>>> variablesLinkedToFieldsAndParameters = new SetOnce<>();

    public final SetOnce<Set<Variable>> variablesLinkedToMethodResult = new SetOnce<>();

    public final SetOnce<Value> singleReturnValue = new SetOnce<>();

    /**
     * this one contains all own methods called from this method, and the transitive closure.
     * we use this to compute effective finality: some methods are only called from constructors,
     * they form part of the construction aspect of the class
     */
    public final SetOnce<Set<MethodInfo>> methodsOfOwnClassReached = new SetOnce<>();
    public final SetOnce<Boolean> partOfConstruction = new SetOnce<>();

    // once we know the dynamic type annotations, we can convert this to annotations on the fields
    public final SetOnce<Boolean> dynamicTypeAnnotationsAdded = new SetOnce<>();

    // when an assignment to a field is blocked
    public final SetOnceMap<FieldInfo, Boolean> errorAssigningToFieldOutsideType = new SetOnceMap<>();
    public final SetOnceMap<MethodInfo, Boolean> errorCallingModifyingMethodOutsideType = new SetOnceMap<>();

    public final SetOnce<Set<MethodInfo>> localMethodsCalled = new SetOnce<>();
    public final SetOnce<List<NumberedStatement>> returnStatements = new SetOnce<>();
}

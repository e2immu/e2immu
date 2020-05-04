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
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

@NotNull
public class MethodAnalysis extends Analysis {

    // used to check that in a utility class, no objects of the class itself are created
    public final SetOnce<Boolean> createObjectOfSelf = new SetOnce<>();

    // not to be stored. later, move to separate class...
    // TODO
    public final SetOnce<SideEffect> sideEffect = new SetOnce<>();
    // TODO ditto
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
    public final SetOnceMap<FieldInfo, Boolean> fieldRead = new SetOnceMap<>();
    public final SetOnce<Boolean> thisRead = new SetOnce<>();

    // produces a warning
    public final SetOnceMap<LocalVariable, Boolean> unusedLocalVariables = new SetOnceMap<>();

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


    // once we know the dynamic type annotations, we can convert this to annotations on the fields
    public final SetOnce<Boolean> dynamicTypeAnnotationsAdded = new SetOnce<>();

    // when an assignment to a field is blocked
    public final SetOnceMap<FieldInfo, Boolean> errorAssigningToFieldOutsideType = new SetOnceMap<>();
    public final SetOnceMap<MethodInfo, Boolean> errorCallingModifyingMethodOutsideType = new SetOnceMap<>();

    public final SetOnce<Set<MethodInfo>> localMethodsCalled = new SetOnce<>();
    public final SetOnce<List<NumberedStatement>> returnStatements = new SetOnce<>();
}

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

import org.e2immu.analyser.model.statement.Block;

import java.util.List;
import java.util.Map;

public interface MethodInspection extends Inspection {

    String getFullyQualifiedName();

    String getDistinguishingName();

    MethodInfo getMethodInfo(); // backlink, container... will become contextclass+immutable eventually

    ParameterizedType getReturnType(); // ContextClass

    Block getMethodBody();

    //@Immutable(level = 2, after="MethodAnalyzer.analyse()")
    //@Immutable
    List<ParameterInfo> getParameters();

    //@Immutable
    List<MethodModifier> getModifiers();

    //@Immutable
    List<TypeParameter> getTypeParameters();

    //@Immutable
    List<ParameterizedType> getExceptionTypes();

    // if our type implements a number of interfaces, then the method definitions in these interfaces
    // that this method implements, are represented in this variable
    // this is used to check inherited annotations on methods
    //@Immutable
    List<MethodInfo> getImplementationOf();

    Map<CompanionMethodName, MethodInfo> getCompanionMethods();

    boolean isStatic();

    boolean isDefault();

    boolean isVarargs();
}

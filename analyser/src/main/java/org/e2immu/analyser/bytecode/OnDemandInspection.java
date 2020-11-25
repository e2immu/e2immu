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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.inspector.TypeContext;

import java.util.List;
import java.util.Stack;

public interface OnDemandInspection {

    List<TypeInfo> inspectFromPath(String path); // org/junit/Assert

    TypeInfo inspectFromPath(String name, Stack<TypeInfo> enclosingTypes, TypeContext typeContext);
}

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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.PackagePrefix;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface TypeMap extends  InspectionProvider {

    TypeInfo get(Class<?> clazz);

    TypeInfo get(String fullyQualifiedName);

    boolean isPackagePrefix(PackagePrefix packagePrefix);

    void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer);

    void visitLeaves(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer);

}
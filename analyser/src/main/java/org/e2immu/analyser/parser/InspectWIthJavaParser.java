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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.TypeInfo;

/**
 * Interface to avoid a circular dependency between the ParseAndInspect type and the TypeMapImpl.Builder.
 * The builder has to be able to inspect types on demand, while inspecting another type.
 * <p>
 * In a similar way, the byte code inspector is known to the TypeMapImpl.Builder, whilst the ByteCodeInspector needs the TypeMapImpl.Builder.
 */
public interface InspectWIthJavaParser {

    void inspect(TypeInfo typeInfo);
}

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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashSet;
import java.util.Set;

public class CallsToOwnMethods {

    private final InspectionProvider inspectionProvider;
    private final Set<MethodInfo> methods = new HashSet<>();

    public CallsToOwnMethods(InspectionProvider inspectionProvider) {
        this.inspectionProvider = inspectionProvider;
    }

    public CallsToOwnMethods visit(Element element) {
        element.visit(e -> {
            if (e instanceof MethodCall methodCall && methodCall.objectIsThisOrSuper(inspectionProvider)) {
                methods.add(methodCall.methodInfo);
            } else if (e instanceof MethodReference methodReference && methodReference.objectIsThisOrSuper(inspectionProvider)) {
                methods.add(methodReference.methodInfo);
            }
        });
        return this;
    }

    public Set<MethodInfo> getMethods() {
        return methods;
    }
}

/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

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

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.statement.SynchronizedStatement;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/*
Try to determine if the method allows for interrupts.
We already know of local method calls; so we look for "foreign" method calls,
or explicit synchronisation.
 */
public class AllowInterruptVisitor {

    static boolean allowInterrupts(Element element, Set<MethodInfo> exclude) {
        AtomicBoolean allowInterrupts = new AtomicBoolean();
        element.visit(e -> {
            MethodCall methodCall;
            MethodReference methodReference;
            ConstructorCall constructorCall;
            if (e instanceof SynchronizedStatement) {
                allowInterrupts.set(true);
            } else if ((methodCall = e.asInstanceOf(MethodCall.class)) != null) {
                if (verify(methodCall.methodInfo, exclude)) allowInterrupts.set(true);
            } else if ((methodReference = e.asInstanceOf(MethodReference.class)) != null) {
                if (verify(methodReference.methodInfo, exclude)) allowInterrupts.set(true);
            } else if ((constructorCall = e.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
                if (verify(constructorCall.constructor(), exclude)) allowInterrupts.set(true);
            }
        });
        return allowInterrupts.get();
    }

    private static boolean verify(MethodInfo methodInfo, Set<MethodInfo> exclude) {
        if (exclude.contains(methodInfo)) return false;

        return !methodInfo.methodResolution.isSet() || methodInfo.methodResolution.get().allowsInterrupts();
    }
}

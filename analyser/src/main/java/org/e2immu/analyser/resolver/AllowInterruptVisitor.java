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

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.NewObject;
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
            if (e instanceof SynchronizedStatement) {
                allowInterrupts.set(true);
            } else if (e instanceof MethodCall methodCall) {
                if (verify(methodCall.methodInfo, exclude)) allowInterrupts.set(true);
            } else if (e instanceof MethodReference methodReference) {
                if (verify(methodReference.methodInfo, exclude)) allowInterrupts.set(true);
            } else if (e instanceof NewObject newObject && newObject.constructor() != null) {
                if (verify(newObject.constructor(), exclude)) allowInterrupts.set(true);
            }
        });
        return allowInterrupts.get();
    }

    private static boolean verify(MethodInfo methodInfo, Set<MethodInfo> exclude) {
        if (exclude.contains(methodInfo)) return false;

        return !methodInfo.methodResolution.isSet() || methodInfo.methodResolution.get().allowsInterrupts();
    }
}

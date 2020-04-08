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

package org.e2immu.analyser.util;

import org.e2immu.annotation.*;

import java.util.Objects;
import java.util.function.Supplier;

import static org.e2immu.annotation.AnnotationType.VERIFY_ABSENT;

/**
 * Implementation of a lazy value, where <code>null</code> is used to indicate that the value has not been
 * evaluated yet.
 * <p>
 * Typical {@link Container}, eventually a {@link E2Final}.
 *
 * @param <T> the container's content
 */

@E2Final(after = "get")
@Container
public class Lazy<T> {
    @NotModified
    private final Supplier<T> supplier;

    @Final(type = VERIFY_ABSENT)
    @Linked // for now, we link t to supplier (nothing that rules it out)
    private volatile T t;

    /**
     * Construct the lazy object by storing a supplier.
     *
     * @param supplier the supplier that will compute the value
     * @throws NullPointerException when the argument is <code>null</code>
     */
    public Lazy(@NullNotAllowed @NotModified Supplier<T> supplier) {
        if (supplier == null) throw new NullPointerException("Null not allowed");
        this.supplier = supplier;
    }

    /**
     * Obtain the value, either by evaluation, if this is the first call, or from the cached field.
     *
     * @return the value
     * @throws NullPointerException if the evaluation returns <code>null</code>
     */
    @NotNull
    @Mark("get")
    public T get() {
        if (t == null) {
            synchronized (this) {
                if (t == null) {
                    t = Objects.requireNonNull(supplier.get());
                }
            }
        }
        return t;
    }

    /**
     * @return true when the lazy object has been evaluated
     */
    @NotModified
    public boolean hasBeenEvaluated() {
        return t != null;
    }

}

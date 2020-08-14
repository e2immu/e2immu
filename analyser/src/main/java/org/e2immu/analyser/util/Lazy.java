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

/**
 * Implementation of a lazy value, where <code>null</code> is used to indicate that the value has not been
 * evaluated yet.
 * <p>
 * Currently there is no means of detecting the @Mark annotation, which is why we have added it as a contract.
 *
 * @param <T> the container's content type
 */

@E2Immutable(after = "get")
public class Lazy<T> {
    @NotNull1
    @Linked(to = "supplierParam")
    @NotModified(after = "get")
    private final Supplier<T> supplier;

    @Final(after = "get")
    private volatile T t;

    /**
     * Construct the lazy object by storing a supplier.
     *
     * @param supplierParam the supplier that will compute the value; it should not produce a null value
     * @throws NullPointerException when the argument is <code>null</code>
     */
    public Lazy(@NotNull1 Supplier<T> supplierParam) {
        if (supplierParam == null) throw new NullPointerException("Null not allowed");
        this.supplier = supplierParam;
    }

    /**
     * Obtain the value, either by evaluation, if this is the first call, or from the cached field.
     *
     * @return the value
     * @throws NullPointerException if the evaluation returns <code>null</code>
     */
    @NotNull
    @Mark(value = "get", type = AnnotationType.CONTRACT)
    public T get() {
        T localT = t;
        if (localT != null) return localT;

        synchronized (this) {
            if (t == null) {
                t = Objects.requireNonNull(supplier.get()); // this statement causes @NotNull1 on supplier
            }
            return t;
        }
    }

    /**
     * @return true when the lazy object has been evaluated
     */
    @NotModified
    public boolean hasBeenEvaluated() {
        return t != null;
    }

}

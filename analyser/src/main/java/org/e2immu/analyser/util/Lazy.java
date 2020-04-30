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
 * Typical {@link Container}, eventually a {@link E1Immutable}.
 *
 * @param <T> the container's content
 */

@E1Container(type = AnnotationType.VERIFY_ABSENT) //(after = "get")
@E1Immutable(type = AnnotationType.VERIFY_ABSENT) // remove me later
@Container // this one is safe
public class Lazy<T> {
    @NotModified
    @NotNull
    @Final
    @Linked(to = "supplierParam") // the parameter
    private final Supplier<T> supplier;

    @Linked(to = "supplier")// for now, we link t to supplier (nothing that rules it out)
    @Final(type = AnnotationType.VERIFY_ABSENT) // later after mark
    private volatile T t;

    /**
     * Construct the lazy object by storing a supplier.
     *
     * @param supplierParam the supplier that will compute the value
     * @throws NullPointerException when the argument is <code>null</code>
     */
    public Lazy(@NotNull Supplier<T> supplierParam) {
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
    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    //@Mark("get")
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

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

package org.e2immu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used for functional types, as a dynamic type annotation, to indicate that the single abstract method
 * exposes part of the fields' object graph to the outside world.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Exposed {
    AnnotationType type() default AnnotationType.VERIFY;

    /*
    Other parameters that are exposed by this parameter.
    The value -1 indicates that fields are being exposed.
    Exposure of fields is the default, so {-1} and {} is equivalent.

    On a field, only {-1} or {} is allowed.
     */
    int[] value() default {};
}

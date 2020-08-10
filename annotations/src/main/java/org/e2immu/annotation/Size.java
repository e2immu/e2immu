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
 * Annotation involving sizes of collections.
 * <p>
 * On: types: NO; a type becomes "@Size"-able when it has a @Size method
 * <p>
 * On: methods returning int or long: @Size means that this method returns the size of the collection
 * On: methods returning boolean:
 * - @Size(min = 1) returns true when size >= 1;
 * - @Size(equals = 0) returns true when isEmpty
 * On: methods returning collections:
 * - @Size(copy = true) returns the same size as the collection
 * On: all modifying methods: this becomes the new @Size
 * <p>
 * On: methods returning collection: @Size(min = 1) means that size >= 1 for the returned object
 * On: parameters:
 * - @Size(min = 1) means that these are the size requirements for a collection
 * - @Size(copy = true) on
 * -- constructors: transfer the property to the object
 * -- methods returning a collection: transfer the property to the return type
 * <p>
 * Calling a @NotModified method on an object maintains the size.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface Size {
    AnnotationType type() default AnnotationType.VERIFY;

    int min() default -1;

    int equals() default -1;

    boolean copy() default false;

    boolean copyMin() default false;
}

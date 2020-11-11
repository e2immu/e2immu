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
 * There are no @Size annotations on types; a type becomes "@Size"-able when it has a @Size method
 * <p>
 * On methods, we distinguish between:
 * non-modifying: @Size describes the return value
 * modifying or constructor: @Size describes the object, regardless the return value
 * The presence of a @Size annotation on the method is mutually exclusive with the presence of @Size(copy =)
 * annotations on parameters of the method. The @Size(copy=true) annotations copy the size characteristics of the argument
 * into the return value (non-modifying method) or the object (modifying method, constructor) in an additive way.
 *
 * <p>
 * On non-modifying methods returning int or long without parameters marked @Size(copy=):
 * means that this method returns the size of the collection
 * The presence of such a method marks that the type is @Size-able.
 * There are no provisions for a maximal size.
 * - @Size(min = 0) means that the result is the size of the object, but nothing more is known (>= 0)
 * - @Size(min = 2) means that the object has at least size 2
 * - @Size(equals = 3) means that the object has 3 elements
 * <p>
 * On non-modifying methods of a @Size-able type returning boolean without parameters marked @Size(copy=):
 * - @Size(min = 1) returns true when size >= 1;
 * - @Size(equals = 0) returns true when isEmpty
 * <p>
 * On non-modifying methods returning collections without parameters marked @Size(copy=):
 * - @Size(copy = true) on a @Size-able type: returns the same size (copyMin = at least the same size) as the collection object
 * - @Size(min =, equals = ) returns information about the size of the collection returned.
 * This annotation can also occur on methods in non-@Size-able types.
 * <p>
 * On modifying methods and constructors, the @Size(min=, equals=) describes characteristics of the size of the object.
 * The copy= is not allowed.
 *
 * <p>
 * On parameters, there are generally 3 types of annotations:
 * - restrictions (min=, equals=) that describe the in-flow (properties required of the argument)
 * - copy into the method result or object (copy=, resp. non-modifying or modifying/constructor)
 * - output properties (outMin=, outEquals=), on modifying parameters (and therefore never in containers)
 * Important: All three annotations can technically occur at the same time.
 * However, only one parameter can have a copy=true or copyMin=true; and this is exclusive with an annotation on the method
 * or constructor.
 * <p>
 * This obviously does not cover all combinations possible... we could imagine a copyOut, copyOutMin from the object, or
 * even from another parameter.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface Size {
    AnnotationType type() default AnnotationType.VERIFY;

    int min() default -1;

    int equals() default -1;

    boolean copy() default false;

    boolean copyMin() default false;

    int outMin() default -1;

    int outEquals() default -1;

}

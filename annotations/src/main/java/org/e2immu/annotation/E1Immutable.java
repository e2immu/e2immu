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
 * Annotation marking that the type is effectively or eventually level 1 immutable (all fields are (eventually) @Final).
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface E1Immutable {
    AnnotationType type() default AnnotationType.VERIFY;

    /**
     * @return when the type is effectively level 1 immutable, set the empty string.
     * When it is eventually level 1 immutable, return a comma-separated list of strings from <code>@Mark</code>
     * values on some of the modifying methods of the type. After these have been called, the
     * type will become effectively level 1 immutable.
     */
    String after() default "";

    /**
     * @return when true, the type is eventually level 1 immutable after the framework has made all modifications.
     * <p>
     * This is a short-hand for adding <code>@Only(framework=true) @Mark("framework")</code> on all modifying methods,
     * and setting <code>after="framework"</code> on this annotation.
     */
    boolean framework() default false;

}

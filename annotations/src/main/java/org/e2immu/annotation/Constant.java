/*
 * e2immu-annot: annotations for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * Field has been assigned, or method returns, a constant value.
 *
 * This happens more than you think, esp. in class hierarchies.
 * This annotation is there mostly to test the value of the constants.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Constant {
    AnnotationType type() default AnnotationType.VERIFY;

    // if test is true, then the value of int, string or bool will be verified
    // the same will happen when the value is different from the default.
    boolean test() default false;

    int intValue() default 0;
    String stringValue() default "";
    boolean boolValue() default false;
}

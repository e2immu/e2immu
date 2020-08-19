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

public enum AnnotationType {
    /**
     * annotation present in the code, added by hand,
     * to ensure that an error is raised in case the code analyser
     * fails to compute this annotation.
     *
     * This is the default for annotations in Java classes.
     */
    VERIFY,

    /**
     * annotation present in the code, added by hand, to ensure
     * that an error is raised in case the code analyser would
     * compute this annotation.
     *
     * Cannot be used on interfaces.
     */
    VERIFY_ABSENT,

    /**
     * an annotation produced by the code analyser; visible only on code
     * produced by the code analyser
     */
    COMPUTED,

    /**
     * The default value for annotated_api files and interfaces: not computed
     * but set by hand.
     */
    CONTRACT,
}

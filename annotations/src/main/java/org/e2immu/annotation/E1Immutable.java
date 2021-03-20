/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
    boolean absent() default false;

    boolean contract() default false;

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
    /*
     IMPROVE
     boolean framework() default false;
     */

}

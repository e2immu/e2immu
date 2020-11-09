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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.annotation.AnnotationMode;

import java.util.Set;

public enum VariableProperty {
    // can be read multiple times
    READ("read", true, Level.FALSE, Level.READ_ASSIGN_MULTIPLE_TIMES, Level.FALSE, Level.FALSE),

    // assigned multiple times
    ASSIGNED("assigned", true, Level.FALSE, Level.READ_ASSIGN_MULTIPLE_TIMES, Level.FALSE, Level.FALSE),

    // in a block, are we guaranteed to reach the last assignment?
    // we focus on last assignment because that is what the 'currentValue' holds
    LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED("reached"),

    // this variable is changing inside a loop; do not read its value
    ASSIGNED_IN_LOOP("in loop"),

    // single simple purpose: on `this` to see if local methods have been called
    METHOD_CALLED("method called"),

    // purpose: goes to true when all methods involved in the computation have been "evaluated"
    METHOD_DELAY("method delay"),
    // continuation of METHOD_DELAY from variable properties into field summaries
    METHOD_DELAY_RESOLVED("method delay"),

    // purpose: goes to false when a parameter occurs in a not_null context, but there is a delay
    // goes to true when that delay has been resolved
    NOT_NULL_DELAYS_RESOLVED("notnull delay"),

    // removed after detection of "not used"
    REMOVED("removed"),

    // the ones corresponding to annotations

    NOT_NULL("@NotNull", true,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL, MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),

    FINAL("@Final", false, Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE),
    CONTAINER("@Container", false, Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE),
    IMMUTABLE("@Immutable", true, MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE, MultiLevel.MUTABLE),
    MODIFIED("@Modified", true, Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),
    INDEPENDENT("@Independent", true, MultiLevel.FALSE, MultiLevel.EFFECTIVE, MultiLevel.FALSE, MultiLevel.EFFECTIVE),
    CONSTANT("@Constant"),
    EXTENSION_CLASS("@ExtensionClass"),
    FLUENT("@Fluent"),
    IDENTITY("@Identity"),
    IGNORE_MODIFICATIONS("@IgnoreModifications"),
    LINKED("@Linked"),
    MARK("@Mark"),
    ONLY("@Only"),
    SINGLETON("@Singleton"),
    NOT_MODIFIED_1("@NotModified1"),
    SIZE("@Size", true, Level.NOT_A_SIZE, Integer.MAX_VALUE, Level.FALSE, Level.FALSE), // the int value is for "min"+"equals", not for "max"
    SIZE_COPY("@Size copy"), // the int value is associated with the @Size(copy, copyMin)
    UTILITY_CLASS("@UtilityClass");

    public final String name;
    public final boolean canImprove;
    public final int best;
    public final int falseValue;
    private final int valueWhenAbsentInDefensiveMode;
    private final int valueWhenAbsentInOffensiveMode;

    private VariableProperty(String name) {
        this(name, false, Level.FALSE, Level.TRUE, Level.FALSE, Level.FALSE);
    }

    private VariableProperty(String name, boolean canImprove, int falseValue, int best, int valueWhenAbsentInDefensiveMode, int valueWhenAbsentInOffensiveMode) {
        this.name = name;
        this.canImprove = canImprove;
        this.best = best;
        this.falseValue = falseValue;
        this.valueWhenAbsentInDefensiveMode = valueWhenAbsentInDefensiveMode;
        this.valueWhenAbsentInOffensiveMode = valueWhenAbsentInOffensiveMode;
    }

    @Override
    public String toString() {
        return name;
    }

    public int valueWhenAbsent(AnnotationMode annotationMode) {
        if (annotationMode == AnnotationMode.DEFENSIVE) return valueWhenAbsentInDefensiveMode;
        if (annotationMode == AnnotationMode.OFFENSIVE) return valueWhenAbsentInOffensiveMode;
        throw new UnsupportedOperationException();
    }

    public final static Set<VariableProperty> FORWARD_PROPERTIES_ON_PARAMETERS = Set.of(NOT_NULL, MODIFIED, NOT_MODIFIED_1); // TODO add SIZE
    public final static Set<VariableProperty> FROM_FIELD_TO_PARAMETER = Set.of(NOT_NULL, MODIFIED, SIZE);
    public final static Set<VariableProperty> PROPERTIES_IN_METHOD_RESULT_WRAPPER = Set.of(NOT_NULL, SIZE, IMMUTABLE);
    public final static Set<VariableProperty> READ_FROM_RETURN_VALUE_PROPERTIES = Set.of(IMMUTABLE, CONTAINER, NOT_NULL);
    public final static Set<VariableProperty> METHOD_PROPERTIES_IN_INLINE_SAM = Set.of(MODIFIED, INDEPENDENT);
    public static final Set<VariableProperty> CHECK_WORSE_THAN_PARENT = Set.of(SIZE, NOT_NULL, MODIFIED);
    public static final Set<VariableProperty> FROM_PARAMETER_TO_PROPERTIES = Set.of(NOT_NULL, SIZE, SIZE_COPY, IMMUTABLE, CONTAINER, NOT_MODIFIED_1);
    public static final Set<VariableProperty> VALUE_PROPERTIES = Set.of(IMMUTABLE, CONTAINER, NOT_NULL, SIZE, SIZE_COPY);

}

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
import org.e2immu.annotation.AnnotationMode;

import java.util.List;
import java.util.Set;

public enum VariableProperty {
    // can be read multiple times
    READ("read", true, Level.TRUE_LEVEL_1, 0, 0),

    NOT_YET_READ_AFTER_ASSIGNMENT("not yet read"),

    // assigned multiple times
    ASSIGNED("assigned", true, Level.TRUE_LEVEL_1, 0, 0),

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

    // the ones corresponding to annotations

    NOT_NULL("@NotNull", true, Level.TRUE_LEVEL_2, 0, 1),

    // the following three are on types only
    NOT_NULL_FIELDS("@NotNull(where=FIELDS)", true, Level.TRUE_LEVEL_2, 0, 1),
    NOT_NULL_METHODS("@NotNull(where=METHODS)", true, Level.TRUE_LEVEL_2, 0, 1),
    NOT_NULL_PARAMETERS("@NotNull(where=PARAMETERS)", true, Level.TRUE_LEVEL_2, 0, 1),

    FINAL("@Final", false, Level.TRUE, 0, 1),
    CONTAINER("@Container", false, 1, 0, 1),
    IMMUTABLE("@Immutable", true, Level.compose(Level.TRUE, Level.E2IMMUTABLE), 0, 1),
    MODIFIED("@Modified", true, Level.TRUE, 1, 0),
    INDEPENDENT("@Independent", false, Level.TRUE, 0, 1),
    CONSTANT("@Constant"),
    EXTENSION_CLASS("@ExtensionClass"),
    FLUENT("@Fluent"),
    IDENTITY("@Identity"),
    IGNORE_MODIFICATIONS("@IgnoreModifications"),
    LINKED("@Linked"),
    MARK("@Mark"),
    ONLY("@Only"),
    OUTPUT("@Output"),
    SINGLETON("@Singleton"),
    SIZE("@Size", true, Integer.MAX_VALUE, 0, 0), // the int value is for "min"+"equals", not for "max"
    SIZE_COPY("@Size copy"), // the int value is associated with the @Size(copy, copyMin)
    UTILITY_CLASS("@UtilityClass");

    public final String name;
    public final boolean canImprove;
    public final int best;
    private final int valueWhenAbsentInDefensiveMode;
    private final int valueWhenAbsentInOffensiveMode;

    private VariableProperty(String name) {
        this(name, false, Level.TRUE, 0, 0);
    }

    private VariableProperty(String name, boolean canImprove, int best, int valueWhenAbsentInDefensiveMode, int valueWhenAbsentInOffensiveMode) {
        this.name = name;
        this.canImprove = canImprove;
        this.best = best;
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

    public final static Set<VariableProperty> NO_DELAY_FROM_STMT_TO_METHOD = Set.of(READ, ASSIGNED, METHOD_CALLED);
    public final static Set<VariableProperty> CONTEXT_PROPERTIES_FROM_STMT_TO_METHOD = Set.of(SIZE, NOT_NULL);

    // it is important that NOT_MODIFIED is copied BEFORE SIZE, because SIZE on Parameters is only copied when
    // NOT_MODIFIED == TRUE
    public final static Set<VariableProperty> FORWARD_PROPERTIES_ON_PARAMETERS = Set.of(NOT_NULL, MODIFIED, SIZE);
    public final static Set<VariableProperty> FROM_FIELD_TO_PARAMETER = Set.of(NOT_NULL, MODIFIED, SIZE);

    public final static Set<VariableProperty> FIELD_ANALYSER_MIN_OVER_ASSIGNMENTS = Set.of(IMMUTABLE, CONTAINER);

    public final static Set<VariableProperty> DYNAMIC_TYPE_PROPERTY = Set.of(IMMUTABLE, CONTAINER);
    public final static Set<VariableProperty> FIELD_AND_METHOD_PROPERTIES = Set.of(NOT_NULL, SIZE, SIZE_COPY);
    public final static Set<VariableProperty> PROPERTIES_IN_METHOD_RESULT_WRAPPER = Set.of(NOT_NULL, SIZE);

    public final static Set<VariableProperty> INSTANCE_PROPERTIES = Set.of(IMMUTABLE, CONTAINER, NOT_NULL, SIZE);
    public final static Set<VariableProperty> FROM_FIELD_TO_PROPERTIES = Set.of(IMMUTABLE, CONTAINER, NOT_NULL, SIZE, IGNORE_MODIFICATIONS);
    public final static Set<VariableProperty> FROM_PARAMETER_TO_PROPERTIES = Set.of(IMMUTABLE, CONTAINER); // from the type


    public final static Set<VariableProperty> RETURN_VALUE_PROPERTIES = Set.of(IMMUTABLE, CONTAINER, NOT_NULL, MODIFIED);

    public final static Set<VariableProperty> INTO_RETURN_VALUE_SUMMARY = Set.of( NOT_NULL, SIZE, SIZE_COPY);
    public final static Set<VariableProperty> INTO_RETURN_VALUE_SUMMARY_DEFAULT_FALSE = Set.of(IMMUTABLE, CONTAINER);

    public final static Set<VariableProperty> RETURN_VALUE_PROPERTIES_IN_METHOD_ANALYSER =
            Set.of(IMMUTABLE, CONTAINER, NOT_NULL, IDENTITY, FLUENT); // but not CONTENT_MODIFIED, SIZE, have separate computation

    public final static Set<VariableProperty> REMOVE_AFTER_ASSIGNMENT = Set.of(NOT_NULL, SIZE);
}

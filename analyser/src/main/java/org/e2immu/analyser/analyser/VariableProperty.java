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

    // single simple purpose: on `this` to see if local methods have been called
    METHOD_CALLED("method called"),

    // purpose: goes to true when all methods involved in the computation have been "evaluated"
    METHOD_DELAY("method delay"),
    // continuation of METHOD_DELAY from variable properties into field summaries
    METHOD_DELAY_RESOLVED("method delay resolved"),

    SCOPE_DELAY("scope delay"),

    // purpose: goes to false when a parameter occurs in a not_null context, but there is a delay
    // goes to true when that delay has been resolved
    CONTEXT_NOT_NULL("not null in context", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL, new VariableProperty[0]),
    CONTEXT_NOT_NULL_DELAY("not null in context delay"),
    CONTEXT_NOT_NULL_DELAY_RESOLVED("not null in context delay resolved"),

    /*
    in fields, external not null is the truth
    in statements in a method, external not null needs combined with context not null (not null variable)
    the method result is stored in not null expression

     */

    // or: not null from a value/assignment
    EXTERNAL_NOT_NULL("not null from outside method", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL, new VariableProperty[0]),
    EXTERNAL_NOT_NULL_DELAY("not null from outside method delay"),
    EXTERNAL_NOT_NULL_DELAY_RESOLVED("not null from outside method delay resolved"),

    CONTEXT_MODIFIED("modified in context"),
    MODIFIED_OUTSIDE_METHOD("modified outside method"),

    // the ones corresponding to annotations

    NOT_NULL_VARIABLE("@NotNull variable", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL,
            new VariableProperty[]{EXTERNAL_NOT_NULL, CONTEXT_NOT_NULL}),
    NOT_NULL_EXPRESSION("@NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL, new VariableProperty[0]),

    FINAL("@Final", Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE, new VariableProperty[0]),

    CONTAINER("@Container", Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE, new VariableProperty[0]),

    IMMUTABLE("@Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE,
            MultiLevel.MUTABLE, new VariableProperty[0]),

    MODIFIED_VARIABLE("@Modified variable", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE,
            new VariableProperty[]{MODIFIED_OUTSIDE_METHOD, CONTEXT_MODIFIED}),
    MODIFIED_METHOD("@Modified method", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE,
            new VariableProperty[0]),

    INDEPENDENT("@Independent", MultiLevel.FALSE, MultiLevel.EFFECTIVE, MultiLevel.FALSE, MultiLevel.EFFECTIVE,
            new VariableProperty[0]),

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
    UTILITY_CLASS("@UtilityClass");

    public final String name;
    public final int best;
    public final int falseValue;
    private final int valueWhenAbsentInDefensiveMode;
    private final int valueWhenAbsentInOffensiveMode;
    public final VariableProperty[] combinationOf;

    VariableProperty(String name) {
        this(name, Level.FALSE, Level.TRUE, Level.FALSE, Level.FALSE, new VariableProperty[0]);
    }

    VariableProperty(String name,
                     int falseValue,
                     int best,
                     int valueWhenAbsentInDefensiveMode,
                     int valueWhenAbsentInOffensiveMode,
                     VariableProperty[] combinationOf) {
        this.name = name;
        this.best = best;
        this.falseValue = falseValue;
        this.valueWhenAbsentInDefensiveMode = valueWhenAbsentInDefensiveMode;
        this.valueWhenAbsentInOffensiveMode = valueWhenAbsentInOffensiveMode;
        this.combinationOf = combinationOf;
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

    public final static Set<VariableProperty> PROPERTIES_IN_METHOD_RESULT_WRAPPER = Set.of(NOT_NULL_EXPRESSION, IMMUTABLE);
    public final static Set<VariableProperty> READ_FROM_RETURN_VALUE_PROPERTIES = Set.of(IDENTITY, IMMUTABLE, CONTAINER, NOT_NULL);
    public final static Set<VariableProperty> METHOD_PROPERTIES_IN_INLINE_SAM = Set.of(MODIFIED, INDEPENDENT);
    public static final Set<VariableProperty> CHECK_WORSE_THAN_PARENT = Set.of(NOT_NULL_VARIABLE, MODIFIED_VARIABLE);
    public static final Set<VariableProperty> FROM_ANALYSER_TO_PROPERTIES = Set.of(IDENTITY, FINAL, NOT_NULL, IMMUTABLE, CONTAINER, NOT_MODIFIED_1);
    public static final Set<VariableProperty> VALUE_PROPERTIES = Set.of(IDENTITY, IMMUTABLE, CONTAINER, NOT_NULL);

}

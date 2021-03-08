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
    CONTEXT_MODIFIED_DELAY("method delay"),
    // continuation of METHOD_DELAY from variable properties into field summaries

    SCOPE_DELAY("scope delay"),

    // only lives in change map
    IN_NOT_NULL_CONTEXT("in not-null context"),

    // purpose: goes to false when a parameter occurs in a not_null context, but there is a delay
    // goes to true when that delay has been resolved
    CONTEXT_NOT_NULL("not null in context", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    CONTEXT_NOT_NULL_DELAY("not null in context delay"),

    /*
    transfer property from a block that escapes to its parent; see StatementAnalyser.checkNotNullEscapesAndPreconditions
    and the corresponding code in StatementAnalysis.copyBackLocalCopies
     */
    CONTEXT_NOT_NULL_FOR_PARENT("not null in context for parent", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    CONTEXT_NOT_NULL_FOR_PARENT_DELAY("cnn4parent delay"),
    CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED("cnn4parent delay resolved"),

    /*
    in fields, external not null is the truth
    in statements in a method, external not null needs combined with context not null (not null variable) and not null expression
    the method result is stored in not null expression

     */

    CONTEXT_MODIFIED("modified in context"),
    MODIFIED_OUTSIDE_METHOD("modified outside method"),

    EXTERNAL_NOT_NULL("external @NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),

    NOT_NULL_EXPRESSION("@NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),

    // in parameter analyser, combination
    NOT_NULL_PARAMETER("@NotNull parameter", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),

    FINAL("@Final", Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE),

    CONTAINER("@Container", Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE),

    IMMUTABLE("@Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE,
            MultiLevel.MUTABLE),

    MODIFIED_VARIABLE("@Modified variable", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),
    MODIFIED_METHOD("@Modified method", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),
    TEMP_MODIFIED_METHOD("@Modified method, temp", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),

    INDEPENDENT("@Independent", MultiLevel.FALSE, MultiLevel.EFFECTIVE, MultiLevel.FALSE, MultiLevel.EFFECTIVE),

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

    /*
    Properties of return variables, initially set to false, finally copied to the method's properties.
    NotNull is handled separately, because the property changes from NOT_NULL_EXPRESSION to NOT_NULL_EXPRESSION
     */
    public final static Set<VariableProperty> READ_FROM_RETURN_VALUE_PROPERTIES = Set.of(IDENTITY, IMMUTABLE, CONTAINER); // +NOT_NULL by hand
    /*
    copy from field, parameter, this/type to variable, once a value has been determined.
     */
    public static final Set<VariableProperty> FROM_ANALYSER_TO_PROPERTIES
            = Set.of(IDENTITY, FINAL, EXTERNAL_NOT_NULL, MODIFIED_OUTSIDE_METHOD, IMMUTABLE, CONTAINER, NOT_MODIFIED_1);

    public static final Set<VariableProperty> GROUP_PROPERTIES = Set.of(CONTEXT_NOT_NULL, EXTERNAL_NOT_NULL, CONTEXT_MODIFIED);

    public final String name;
    public final int best;
    public final int falseValue;
    private final int valueWhenAbsentInDefensiveMode;
    private final int valueWhenAbsentInOffensiveMode;

    VariableProperty(String name) {
        this(name, Level.FALSE, Level.TRUE, Level.FALSE, Level.FALSE);
    }

    VariableProperty(String name,
                     int falseValue,
                     int best,
                     int valueWhenAbsentInDefensiveMode,
                     int valueWhenAbsentInOffensiveMode) {
        this.name = name;
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

}

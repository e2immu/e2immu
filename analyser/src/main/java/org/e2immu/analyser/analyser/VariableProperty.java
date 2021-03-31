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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.annotation.AnnotationMode;

import java.util.Set;

public enum VariableProperty {

    /*
    "short-lived" properties of the EvaluationResult
     */

    // single simple purpose: on `this` to see if local methods have been called
    METHOD_CALLED("method called"),

    // purpose: goes to true when all methods involved in the computation have been "evaluated"
    CONTEXT_MODIFIED_DELAY("method delay"),
    // continuation of METHOD_DELAY from variable properties into field summaries

    SCOPE_DELAY("scope delay"),

    // only lives in change map
    IN_NOT_NULL_CONTEXT("in not-null context"),
    CANDIDATE_FOR_NULL_PTR_WARNING("candidate for null pointer warning"),

    /*
    @NotNull, @Nullable property.
    Multiple aspects worth mentioning. See MultiLevel for the different values this property can take.

    NOT_NULL_PARAMETER: overarching property for parameters
    EXTERNAL_NOT_NULL: in statement analyser, value coming in from field analyser; std in field analyser
    NOT_NULL_EXPRESSION: the value in expressions, assignments
    CONTEXT_NOT_NULL: the value caused by context (scope or arg of method calls)

    NotNull values can be delayed: CONTEXT_NOT_NULL_DELAY

    CONTEXT_NOT_NULL_FOR_PARENT and associated:  transfer property from a block that escapes to its parent;
    see StatementAnalyser.checkNotNullEscapesAndPreconditions
    and the corresponding code in StatementAnalysis.copyBackLocalCopies
     */
    NOT_NULL_PARAMETER("@NotNull parameter", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    EXTERNAL_NOT_NULL("external @NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    NOT_NULL_EXPRESSION("@NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    CONTEXT_NOT_NULL("not null in context", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    CONTEXT_NOT_NULL_DELAY("not null in context delay"),

    CONTEXT_NOT_NULL_FOR_PARENT("not null in context for parent", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_NOT_NULL),
    CONTEXT_NOT_NULL_FOR_PARENT_DELAY("cnn4parent delay"),
    CONTEXT_NOT_NULL_FOR_PARENT_DELAY_RESOLVED("cnn4parent delay resolved"),

    /*
    @E2Immutable, @E1Immutable property.
    Multiple aspects worth mentioning. See MultiLevel for the different values this property can take.

    In the case of parameters and fields of eventually immutable types, the current state (before or after) is held
    by various properties in the same way as we hold @NotNull

    IMMUTABLE: overarching property used in parameter analyser; expressions and assignments, std in method analyser
    CONTEXT_IMMUTABLE: context property in statement analyser; we'll see increments from EVENTUAL to EVENTUAL_BEFORE and _AFTER
    EXTERNAL_IMMUTABLE: external property in the statement analyser; std in the field analyser

    immutable is computed at the static assignment level.
     */

    IMMUTABLE("@Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE,
            MultiLevel.MUTABLE),
    CONTEXT_IMMUTABLE("context @Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE,
            MultiLevel.MUTABLE),
    EXTERNAL_IMMUTABLE("external @Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE,
            MultiLevel.MUTABLE),

    /*
    Modification.

    MODIFIED_VARIABLE: overarching value in parameters, combining MOM and CM
    MODIFIED_OUTSIDE_METHOD: modification of parameter coming from fields (~EXTERNAL_NOT_NULL)
    CONTEXT_MODIFIED: independent modification computation in the statement analyser

    MODIFIED_METHOD: modification of methods
    TEMP_MODIFIED_METHOD: used in the method analyser for methods that call each other in a cyclic way

    Modification is computed on linked variables.
    Delays on modification are governed by CONTEXT_MODIFIED_DELAY
     */
    MODIFIED_VARIABLE("@Modified variable", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),
    MODIFIED_OUTSIDE_METHOD("modified outside method"),
    CONTEXT_MODIFIED("modified in context"),
    MODIFIED_METHOD("@Modified method", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),
    TEMP_MODIFIED_METHOD("@Modified method, temp", Level.FALSE, Level.TRUE, Level.TRUE, Level.FALSE),

    /*
    Higher level modifications @NotModified1 @Modified1
    TODO: implement
     */
    NOT_MODIFIED_1("@NotModified1"),

    /*
    propagate modification: a parameter of abstract type can have this property when one of its abstract methods
    has been called in the method. There are no delays, we know of abstract vs concrete immediately.
    This parameter can be linked to a field, which then gets used in other methods. For that reason, similar to
    modification and not null, we use the following properties:

    PROPAGATE_MODIFICATION: as overarching property in the parameter analyser
    CONTEXT_PROPAGATE_MOD: context property in statement analyser
    EXTERNAL_PROPAGATE_MOD: external property in statement analyser, std in field analyser

    propagation of modification is computed on linked variables; method return values are not relevant
    @PropagateModification occurs on parameters and fields.
    */
    PROPAGATE_MODIFICATION("@PropagateModification"),
    CONTEXT_PROPAGATE_MOD("context propagate modification"),
    EXTERNAL_PROPAGATE_MOD("external propagate modification")

    /*
    @Dependent, @Independent, @Dependent1, @Dependent2

    INDEPENDENT: overarching value at parameters and methods
        param: not linked to field? independent;  when assigned: dependent, dependent1 when Impl Imm, dependent2 when  II parts of me linked
        constructor: independent if all parameters independent
        method: @Mod+assignment to fields: independent when return value and parameters independent
                @NM: computed on return value only (there can be no assignments, so no linked params to fields)

        static method: TODO List.of(), List.copyOf()
    CONTEXT_DEPENDENT: context property in the statement analyser
        propagation: same assignment takes place via methods to (field.add(parameter))
     */,
    INDEPENDENT("@Independent", MultiLevel.DEPENDENT, MultiLevel.INDEPENDENT, MultiLevel.DEPENDENT, MultiLevel.INDEPENDENT),
    CONTEXT_DEPENDENT("context dependent", MultiLevel.DEPENDENT, MultiLevel.INDEPENDENT, MultiLevel.DEPENDENT, MultiLevel.INDEPENDENT),

    /*
    group of more simple properties
     */
    FINAL("@Final", Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE),
    CONTAINER("@Container", Level.FALSE, Level.TRUE, Level.FALSE, Level.TRUE),
    CONSTANT("@Constant"),
    FLUENT("@Fluent"),
    IDENTITY("@Identity"),
    IGNORE_MODIFICATIONS("@IgnoreModifications"),
    SINGLETON("@Singleton"),
    UTILITY_CLASS("@UtilityClass"),
    EXTENSION_CLASS("@ExtensionClass");

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

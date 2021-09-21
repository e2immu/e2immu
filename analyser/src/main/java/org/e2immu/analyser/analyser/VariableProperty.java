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

import java.util.Set;

public enum VariableProperty {

    /*
    "short-lived" properties of the EvaluationResult
     */

    // single simple purpose: on `this` to see if local methods have been called
    METHOD_CALLED("method called"),

    // purpose: goes to true when all methods involved in the computation have been "evaluated"
    CONTEXT_MODIFIED_DELAY("method delay"),
    PROPAGATE_MODIFICATION_DELAY("propagate modification delay"),

    // continuation of METHOD_DELAY from variable properties into field summaries
    SCOPE_DELAY("scope delay"),

    // only lives in change map
    IN_NOT_NULL_CONTEXT("in not-null context"),
    CANDIDATE_FOR_NULL_PTR_WARNING("candidate for null pointer warning"),

    // on final fields with constructor initializer, we need to decide on constructor or instance
    EXTERNAL_IMMUTABLE_BREAK_DELAY("break immutable delay"),

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
            MultiLevel.NULLABLE),
    EXTERNAL_NOT_NULL("external @NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE),
    NOT_NULL_EXPRESSION("@NotNull", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE),
    CONTEXT_NOT_NULL("not null in context", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE),
    CONTEXT_NOT_NULL_DELAY("not null in context delay"),

    CONTEXT_NOT_NULL_FOR_PARENT("not null in context for parent", MultiLevel.NULLABLE, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL,
            MultiLevel.NULLABLE),
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

    IMMUTABLE_BEFORE_CONTRACTED("immutable before contracted"),
    CONTEXT_IMMUTABLE_DELAY("context immutable delay"),
    NEXT_CONTEXT_IMMUTABLE("next context @Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE),

    IMMUTABLE("@Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE),
    CONTEXT_IMMUTABLE("context @Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE),
    EXTERNAL_IMMUTABLE("external @Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE),

    // internal, temporary
    PARTIAL_EXTERNAL_IMMUTABLE("partial external @Immutable", MultiLevel.MUTABLE, MultiLevel.EFFECTIVELY_E2IMMUTABLE, MultiLevel.MUTABLE),
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
    /**
     * The default for parameters is @Modified
     */
    MODIFIED_VARIABLE("@Modified variable"),
    MODIFIED_OUTSIDE_METHOD("modified outside method"),
    CONTEXT_MODIFIED("modified in context"),
    /**
     * The default for methods is @NotModified
     */
    MODIFIED_METHOD("@Modified method"),
    TEMP_MODIFIED_METHOD("@Modified method, temp"),

    /*
    @Dependent, @Independent, @Dependent1, @Dependent2

    INDEPENDENT: overarching value at parameters and methods
        param: not linked to field? independent;  when assigned: dependent, dependent1 when Impl Imm
        constructor: independent if all parameters independent
        method: @Mod+assignment to fields: independent when return value and parameters independent
                @NM: computed on return value only (there can be no assignments, so no linked params to fields)

        static method: independence from parameters to return value

    @Dependent is the default in green mode, @Independent is the default in red mode.
     */
    INDEPENDENT("@Independent", MultiLevel.DEPENDENT, MultiLevel.INDEPENDENT, MultiLevel.DEPENDENT),

    /*
    group of more simple properties
     */

    /**
     * In green mode, @Variable is the default, in red mode, @Final is.
     */
    FINAL("@Final", Level.FALSE, Level.TRUE, Level.FALSE),
    FINALIZER("@Finalizer"),

    /**
     * In green mode, @MutableModifiesArguments is the default, in red mode, @Container is.
     */
    CONTAINER("@Container", Level.FALSE, Level.TRUE, Level.FALSE),
    CONSTANT("@Constant"),
    FLUENT("@Fluent"),
    IDENTITY("@Identity"),
    IGNORE_MODIFICATIONS("@IgnoreModifications"),
    SINGLETON("@Singleton"),
    UTILITY_CLASS("@UtilityClass"),
    EXTENSION_CLASS("@ExtensionClass");

    /*
    copy from field, parameter, this/type to variable, once a value has been determined.
     */
    public static final Set<VariableProperty> FROM_ANALYSER_TO_PROPERTIES
            = Set.of(IDENTITY, FINAL, EXTERNAL_NOT_NULL, EXTERNAL_IMMUTABLE, MODIFIED_OUTSIDE_METHOD, CONTAINER);

    public final String name;
    public final int best;
    public final int falseValue;
    private final int valueWhenAbsent;

    VariableProperty(String name) {
        this(name, Level.FALSE, Level.TRUE, Level.FALSE);
    }

    VariableProperty(String name,
                     int falseValue,
                     int best,
                     int valueWhenAbsent) {
        this.name = name;
        this.best = best;
        this.falseValue = falseValue;
        this.valueWhenAbsent = valueWhenAbsent;
    }

    @Override
    public String toString() {
        return name;
    }

    public int valueWhenAbsent() {
        return valueWhenAbsent;
    }

}

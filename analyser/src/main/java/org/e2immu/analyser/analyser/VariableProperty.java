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

public enum VariableProperty {

    /*
    "short-lived" properties of the EvaluationResult
     */
    // purpose: goes to true when all methods involved in the computation have been "evaluated"

    // only lives in change map
    IN_NOT_NULL_CONTEXT("in not-null context", CauseOfDelay.Cause.IN_NN_CONTEXT),
    CANDIDATE_FOR_NULL_PTR_WARNING("candidate for null pointer warning", CauseOfDelay.Cause.CANDIDATE_NULL_PTR),

    // in ForwardEvaluationInfo
    PROPAGATE_MODIFICATION("propagate modification", CauseOfDelay.Cause.PROP_MOD),

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
    NOT_NULL_PARAMETER("@NotNull parameter", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.NOT_NULL_PARAMETER),
    EXTERNAL_NOT_NULL("external @NotNull", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NOT_INVOLVED_DV, CauseOfDelay.Cause.EXT_NN),
    NOT_NULL_EXPRESSION("@NotNull", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.VALUE_NOT_NULL),
    CONTEXT_NOT_NULL("not null in context", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.CONTEXT_NOT_NULL),

    CONTEXT_NOT_NULL_FOR_PARENT("not null in context for parent", MultiLevel.NULLABLE_DV,
            MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV, MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.CONTEXT_NOT_NULL_FOR_PARENT),

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

    IMMUTABLE_BEFORE_CONTRACTED("immutable before contracted", CauseOfDelay.Cause.IMMUTABLE_BEFORE_CONTRACTED),
    NEXT_CONTEXT_IMMUTABLE("next context @Immutable", MultiLevel.MUTABLE_DV,
            MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.NEXT_C_IMM),

    IMMUTABLE("@Immutable", MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
            MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.VALUE_IMMUTABLE),
    CONTEXT_IMMUTABLE("context @Immutable", MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
            MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.CONTEXT_IMMUTABLE),
    EXTERNAL_IMMUTABLE("external @Immutable", MultiLevel.MUTABLE_DV,
            MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.NOT_INVOLVED_DV, CauseOfDelay.Cause.EXT_IMM),

    // internal, temporary
    PARTIAL_EXTERNAL_IMMUTABLE("partial external @Immutable",
            MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.MUTABLE_DV, null),
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
    MODIFIED_VARIABLE("@Modified variable", CauseOfDelay.Cause.MODIFIED_VARIABLE),
    MODIFIED_OUTSIDE_METHOD("modified outside method", CauseOfDelay.Cause.MODIFIED_OUTSIDE_METHOD),
    CONTEXT_MODIFIED("modified in context", CauseOfDelay.Cause.CONTEXT_MODIFIED),
    /**
     * The default for methods is @NotModified
     */
    MODIFIED_METHOD("@Modified method", CauseOfDelay.Cause.MODIFIED_METHOD),
    TEMP_MODIFIED_METHOD("@Modified method, temp", CauseOfDelay.Cause.TEMP_MM),

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
    INDEPENDENT("@Independent", MultiLevel.DEPENDENT_DV, MultiLevel.INDEPENDENT_DV, MultiLevel.DEPENDENT_DV,
            CauseOfDelay.Cause.VALUE_INDEPENDENT),

    /*
    group of more simple properties
     */

    /**
     * In green mode, @Variable is the default, in red mode, @Final is.
     */
    FINAL("@Final", Level.FALSE_DV, Level.TRUE_DV, Level.FALSE_DV, CauseOfDelay.Cause.FIELD_FINAL),
    FINALIZER("@Finalizer", CauseOfDelay.Cause.FINALIZER),

    /**
     * In green mode, @MutableModifiesArguments is the default, in red mode, @Container is.
     */
    CONTAINER("@Container", Level.FALSE_DV, Level.TRUE_DV, Level.FALSE_DV, CauseOfDelay.Cause.CONTAINER),
    CONSTANT("@Constant", CauseOfDelay.Cause.CONSTANT),
    FLUENT("@Fluent", CauseOfDelay.Cause.FLUENT),
    IDENTITY("@Identity", CauseOfDelay.Cause.IDENTITY),
    IGNORE_MODIFICATIONS("@IgnoreModifications", CauseOfDelay.Cause.IGNORE_MODIFICATIONS),
    SINGLETON("@Singleton", CauseOfDelay.Cause.SINGLETON),
    UTILITY_CLASS("@UtilityClass", CauseOfDelay.Cause.UTILITY_CLASS),
    EXTENSION_CLASS("@ExtensionClass", CauseOfDelay.Cause.EXTENSION_CLASS);

    public final String name;
    private final DV valueWhenAbsentDv;
    public final DV bestDv;
    public final DV falseDv;
    public final CauseOfDelay.Cause cause;

    VariableProperty(String name, CauseOfDelay.Cause cause) {
        this(name, Level.FALSE_DV, Level.TRUE_DV, Level.FALSE_DV, cause);
    }

    VariableProperty(String name,
                     DV falseValue,
                     DV best,
                     DV valueWhenAbsent,
                     CauseOfDelay.Cause cause) {
        this.name = name;
        this.valueWhenAbsentDv = valueWhenAbsent;
        this.bestDv = best;
        this.falseDv = falseValue;
        this.cause = cause;
    }

    @Override
    public String toString() {
        return name;
    }

    public DV valueWhenAbsent() {
        return valueWhenAbsentDv;
    }

    public CauseOfDelay.Cause causeOfDelay() {
        if (cause == null) throw new UnsupportedOperationException("Cannot cause a delay! " + name);
        return cause;
    }
}

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

import org.e2immu.analyser.model.MultiLevel;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Property {

    /*
    "short-lived" properties of the EvaluationResult
     */

    // only lives in change map
    IN_NOT_NULL_CONTEXT("in not-null context", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NOT_INVOLVED_DV, CauseOfDelay.Cause.IN_NN_CONTEXT, PropertyType.OTHER),
    CNN_TRAVELS_TO_PRECONDITION("cnn travels to pc", CauseOfDelay.Cause.CONTEXT_NOT_NULL),
    CANDIDATE_FOR_NULL_PTR_WARNING("candidate for null pointer warning", CauseOfDelay.Cause.CANDIDATE_NULL_PTR),

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
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.NOT_NULL_PARAMETER, PropertyType.OTHER),
    EXTERNAL_NOT_NULL("external @NotNull", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NOT_INVOLVED_DV, CauseOfDelay.Cause.EXTERNAL_NOT_NULL, PropertyType.EXTERNAL),
    NOT_NULL_EXPRESSION("@NotNull", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.VALUE_NOT_NULL, PropertyType.VALUE),
    CONTEXT_NOT_NULL("not null in context", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.CONTEXT_NOT_NULL, PropertyType.CONTEXT),

    NOT_NULL_BREAK("@NotNull break", MultiLevel.NULLABLE_DV, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV,
            MultiLevel.NULLABLE_DV, CauseOfDelay.Cause.VALUE_NOT_NULL, PropertyType.OTHER),
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
            MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.NEXT_C_IMM,
            PropertyType.OTHER),

    IMMUTABLE("@Immutable", MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
            MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.VALUE_IMMUTABLE, PropertyType.VALUE),
    CONTEXT_IMMUTABLE("context @Immutable", MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
            MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.CONTEXT_IMMUTABLE, PropertyType.CONTEXT),
    EXTERNAL_IMMUTABLE("external @Immutable", MultiLevel.MUTABLE_DV,
            MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.NOT_INVOLVED_DV, CauseOfDelay.Cause.EXT_IMM,
            PropertyType.EXTERNAL),

    // internal, used for enclosing-nested or type-subtype interactions (e.g., in the enclosing we have a field whose
    // type is an anonymous (non-static) subtype)
    PARTIAL_IMMUTABLE("partial @Immutable",
            MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.MUTABLE_DV,
            CauseOfDelay.Cause.PARTIAL_IMM, PropertyType.OTHER),
    // internal, temporary
    PARTIAL_EXTERNAL_IMMUTABLE("partial external @Immutable",
            MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, MultiLevel.MUTABLE_DV, null,
            PropertyType.OTHER),

    IMMUTABLE_BREAK("@Immutable break", MultiLevel.MUTABLE_DV, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
            MultiLevel.MUTABLE_DV, CauseOfDelay.Cause.VALUE_IMMUTABLE, PropertyType.OTHER),

    // separate property for fields, in conjunction with a finalizer
    BEFORE_MARK("@BeforeMark", CauseOfDelay.Cause.BEFORE_MARK),
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
    CONTEXT_MODIFIED("modified in context", DV.FALSE_DV, DV.TRUE_DV, DV.FALSE_DV,
            CauseOfDelay.Cause.CONTEXT_MODIFIED, PropertyType.CONTEXT),
    /**
     * The default for methods is @NotModified
     */
    MODIFIED_METHOD("@Modified method", CauseOfDelay.Cause.MODIFIED_METHOD),
    TEMP_MODIFIED_METHOD("@Modified method, temp", CauseOfDelay.Cause.TEMP_MM),
    MODIFIED_METHOD_ALT_TEMP("@Modified method, possibly temp", CauseOfDelay.Cause.MODIFIED_METHOD),
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
            CauseOfDelay.Cause.VALUE_INDEPENDENT, PropertyType.VALUE),

    /*
    group of more simple properties
     */

    /**
     * In green mode, @Variable is the default, in red mode, @Final is.
     */
    FINAL("@Final", DV.FALSE_DV, DV.TRUE_DV, DV.FALSE_DV, CauseOfDelay.Cause.FIELD_FINAL, PropertyType.OTHER),
    FINALIZER("@Finalizer", CauseOfDelay.Cause.FINALIZER),

    /**
     * In green mode, @MutableModifiesArguments is the default, in red mode, @Container is.
     */
    CONTAINER("@Container", MultiLevel.NOT_CONTAINER_DV, MultiLevel.CONTAINER_DV, MultiLevel.NOT_CONTAINER_DV, CauseOfDelay.Cause.CONTAINER, PropertyType.VALUE),
    EXTERNAL_CONTAINER("external @Container", MultiLevel.NOT_CONTAINER_DV, MultiLevel.CONTAINER_DV, MultiLevel.NOT_INVOLVED_DV, CauseOfDelay.Cause.EXT_CONTAINER, PropertyType.EXTERNAL),
    CONTEXT_CONTAINER("context @Container", MultiLevel.NOT_CONTAINER_DV, MultiLevel.CONTAINER_DV, MultiLevel.NOT_CONTAINER_DV, CauseOfDelay.Cause.CONTEXT_CONTAINER, PropertyType.CONTEXT),
    PARTIAL_CONTAINER("partial @Container", MultiLevel.NOT_CONTAINER_DV, MultiLevel.CONTAINER_DV, MultiLevel.NOT_CONTAINER_DV, CauseOfDelay.Cause.CONTAINER, PropertyType.OTHER),

    IGNORE_MODIFICATIONS("@IgnoreModifications", MultiLevel.NOT_IGNORE_MODS_DV, MultiLevel.IGNORE_MODS_DV, MultiLevel.NOT_IGNORE_MODS_DV, CauseOfDelay.Cause.IGNORE_MODIFICATIONS, PropertyType.VALUE),
    EXTERNAL_IGNORE_MODIFICATIONS("external @IgnoreModifications", MultiLevel.NOT_IGNORE_MODS_DV, MultiLevel.IGNORE_MODS_DV, MultiLevel.NOT_IGNORE_MODS_DV, CauseOfDelay.Cause.IGNORE_MODIFICATIONS, PropertyType.EXTERNAL),

    CONSTANT("@Constant", CauseOfDelay.Cause.CONSTANT),
    FLUENT("@Fluent", CauseOfDelay.Cause.FLUENT),
    IDENTITY("@Identity", DV.FALSE_DV, DV.TRUE_DV, DV.FALSE_DV, CauseOfDelay.Cause.IDENTITY, PropertyType.VALUE),
    SINGLETON("@Singleton", CauseOfDelay.Cause.SINGLETON),
    UTILITY_CLASS("@UtilityClass", CauseOfDelay.Cause.UTILITY_CLASS),
    EXTENSION_CLASS("@ExtensionClass", CauseOfDelay.Cause.EXTENSION_CLASS),

    READ("read", CauseOfDelay.Cause.READ),

    // marker in CommaExpression, used in conjunction with PropertyWrapper; cause of delay completely irrelevant
    MARK_CLEAR_INCREMENTAL("mark clear incremental", CauseOfDelay.Cause.CONSTANT);

    public boolean isGroupProperty() {
        return propertyType.isGroupProperty;
    }

    public enum PropertyType {
        EXTERNAL(true),
        VALUE(false),
        CONTEXT(true),
        OTHER(false);

        private final boolean isGroupProperty;

        PropertyType(boolean isGroupProperty) {
            this.isGroupProperty = isGroupProperty;
        }
    }

    public final String name;
    private final DV valueWhenAbsentDv;
    public final DV bestDv;
    public final DV falseDv;
    public final CauseOfDelay.Cause cause;
    public final PropertyType propertyType;

    Property(String name, CauseOfDelay.Cause cause) {
        this(name, DV.FALSE_DV, DV.TRUE_DV, DV.FALSE_DV, cause, PropertyType.OTHER);
    }

    Property(String name,
             DV falseValue,
             DV best,
             DV valueWhenAbsent,
             CauseOfDelay.Cause cause,
             PropertyType propertyType) {
        this.name = name;
        this.valueWhenAbsentDv = valueWhenAbsent;
        this.bestDv = best;
        this.falseDv = falseValue;
        this.cause = cause;
        this.propertyType = propertyType;
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

    public DV max(DV x, DV y) {
        if (bestDv.equals(x) || bestDv.equals(y)) return bestDv;
        return x.max(y);
    }

    public static final Set<Property> EXTERNALS = Arrays.stream(Property.values())
            .filter(p -> p.propertyType == PropertyType.EXTERNAL).collect(Collectors.toUnmodifiableSet());
    public static final Set<Property> CONTEXTS = Arrays.stream(Property.values())
            .filter(p -> p.propertyType == PropertyType.CONTEXT).collect(Collectors.toUnmodifiableSet());
}

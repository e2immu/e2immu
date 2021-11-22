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

import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.model.variable.Variable;

public interface CauseOfDelay {

    enum Cause {
        IMMUTABLE_BEFORE_CONTRACTED("immutable_before_contracted", ""),
        IN_NN_CONTEXT("in_nn_context", ""),
        CANDIDATE_NULL_PTR("candidate_null_ptr", ""),
        FINALIZER("finalizer", ""),
        IGNORE_MODIFICATIONS("ignore_mods", ""),
        VALUE("value", "The value has not yet been determined"),
        VALUE_NOT_NULL("not_null", "The value's NOT_NULL status has not yet been determined"),
        VALUE_IMMUTABLE("immutable", "The value's IMMUTABLE status has not yet been determined"),
        VALUE_INDEPENDENT("independent", "The value's INDEPENDENT status has not yet been determined"),
        CONTEXT_MODIFIED("cm", "Context modified not yet been determined"),
        LINKING("link", "Delay in linking"),
        REMAP_PARAMETER("remap_param", "Remapping a parameter for the companion analyser is not yet possible"),
        FIELD_FINAL("final", "Effectively final has not yet been determined for this field"),
        ASPECT("aspect", "The type's aspect has not yet been determined"),
        MODIFIED_METHOD("mm", "The method's modification status has not yet been determined"),
        ASSIGNED_TO_FIELD("assign_to_field", "The component 'analyseFieldAssignments' has not yet finished"),
        IMMUTABLE("immutable_type", "Type's IMMUTABLE status has not yet been determined"),
        EXTERNAL_NOT_NULL("ext_not_null", "Variable's EXTERNAL_NOT_NULL value not yet determined"),
        TYPE_ANALYSIS("type_analysis", "Type analysis missing"),
        HIDDEN_CONTENT("transparent", "Hidden content of type has not yet been determined"),
        INITIAL_VALUE("initial", "Not yet initialized"),
        APPROVED_PRECONDITIONS("approved_pc", "Approved preconditions for field"),
        TO_IMPLEMENT("to_implement", "Temporary; needs implementation"),
        FIRST_ITERATION("it0", "Certain actions cannot be done in the first iteration"),
        CNN_PARENT("context_not_null_parent", "Context not null for parent"),
        REPLACEMENT("replacement", "Reiterate, because of statement replacement"),
        VARIABLE_DOES_NOT_EXIST("var_missing", "Variable does not (yet) exist"),
        LOCAL_PT_ANALYSERS("local_pt_analysers", "Local primary type analysers not yet present"),
        INITIAL_TIME("initial_time", "Initial time not yet set"),
        LOCAL_VARS_ASSIGNED("local_vars", "Local variables assigned in this loop not yet determined"),
        NOT_INVOLVED("not_involved", "Internal"),
        MIN_INT("min_int", "Minimum integer; should only appear locally need .reduce()"),
        CONTAINER("container", "container property not yet determined"),
        EXTENSION_CLASS("extension_class", ""),
        UTILITY_CLASS("utility_class", ""),
        SINGLETON("singleton", ""),
        IDENTITY("identity", ""),
        FLUENT("fluent", ""),
        CONSTANT("constant", ""),
        TEMP_MM("temp_mm", "Temporary modified method"),
        MODIFIED_OUTSIDE_METHOD("mom", "modified outside method"),
        MODIFIED_VARIABLE("mod_var", "modified variable"),
        EXT_IMM("ext_imm", ""),
        CONTEXT_IMMUTABLE("c_imm", "context immutable"),
        NEXT_C_IMM("next_c_imm", ""),
        CONTEXT_NOT_NULL("cnn", ""),
        CONTEXT_NOT_NULL_FOR_PARENT("cnn_parent", ""),
        EXT_NN("ext_nn", ""),
        NOT_NULL_PARAMETER("nnp", ""),
        PROP_MOD("prop_mod", "");

        public final String msg;
        public final String label;

        Cause(String label, String msg) {
            this.msg = msg;
            this.label = label;
        }
    }

    default Variable variable() {
        return null;
    }

    Cause cause();

    Location location();

    record SimpleCause(Location location, Cause cause) implements CauseOfDelay {
        public SimpleCause(WithInspectionAndAnalysis withInspectionAndAnalysis, Cause cause) {
            this(new Location(withInspectionAndAnalysis), cause);
        }

        @Override
        public String toString() {
            return cause.label + "@" + location.toDelayString();
        }
    }

    record VariableCause(Variable variable, Location location, Cause cause) implements CauseOfDelay {
        @Override
        public String toString() {
            return cause.label + ":" + variable.debug() + "@" + location.toDelayString();
        }
    }
}

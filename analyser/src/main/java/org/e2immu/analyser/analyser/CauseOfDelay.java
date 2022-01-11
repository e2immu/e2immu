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

public interface CauseOfDelay {

    enum Cause {
        APPROVED_PRECONDITIONS("approved_pc", "Approved preconditions for field"),
        ASPECT("aspect", "The type's aspect has not yet been determined"),
        ASSIGNED_TO_FIELD("assign_to_field", "The component 'analyseFieldAssignments' has not yet finished"),
        CANDIDATE_NULL_PTR("candidate_null_ptr", ""),
        CNN_PARENT("context_not_null_parent", "Context not null for parent"),
        CONSTANT("constant", ""),
        CONTAINER("container", "container property not yet determined"),
        CONTEXT_IMMUTABLE("c_imm", "context immutable"),
        CONTEXT_MODIFIED("cm", "Context modified not yet been determined"),
        CONTEXT_NOT_NULL_FOR_PARENT("cnn_parent", ""),
        CONTEXT_NOT_NULL("cnn", ""),
        BREAK_INIT_DELAY("break_init_delay", "cyclic dependency in initalisation"),
        EXT_IMM("ext_imm", "Variable's EXTERNAL_IMMUTABLE value not yet determined"),
        EXTENSION_CLASS("extension_class", ""),
        EXTERNAL_NOT_NULL("ext_not_null", "Variable's EXTERNAL_NOT_NULL value not yet determined"),
        FIELD_FINAL("final", "Effectively final has not yet been determined for this field"),
        FINALIZER("finalizer", ""),
        FIRST_ITERATION("it0", "Certain actions cannot be done in the first iteration"),
        FLUENT("fluent", ""),
        HIDDEN_CONTENT("transparent", "Hidden content of type has not yet been determined"),
        IDENTITY("identity", ""),
        IGNORE_MODIFICATIONS("ignore_mods", ""),
        IMMUTABLE_BEFORE_CONTRACTED("immutable_before_contracted", ""),
        IMMUTABLE("immutable_type", "Type's IMMUTABLE status has not yet been determined"),
        IN_NN_CONTEXT("in_nn_context", ""),
        INITIAL_FLOW_VALUE("initial_flow_value", "Flow not yet initialized"),
        INITIAL_RANGE("initial_range", "Range not yet set"),
        INITIAL_TIME("initial_time", "Initial time not yet set"),
        INITIAL_VALUE("initial", "Not yet initialized"),
        LINKING("link", "Delay in linking"),
        LOCAL_PT_ANALYSERS("local_pt_analysers", "Local primary type analysers not yet present"),
        LOCAL_VARS_ASSIGNED("local_vars", "Local variables assigned in this loop not yet determined"),
        MIN_INT("min_int", "Minimum integer; should only appear locally need .reduce()"),
        MODIFIED_METHOD("mm", "The method's modification status has not yet been determined"),
        MODIFIED_OUTSIDE_METHOD("mom", "modified outside method"),
        MODIFIED_VARIABLE("mod_var", "modified variable"),
        MODIFIED_CYCLE("mod_cycle", "modification of cyclic method calls"),
        NEXT_C_IMM("next_c_imm", ""),
        NOT_INVOLVED("not_involved", "Internal"),
        NOT_NULL_PARAMETER("nnp", ""),
        PROP_MOD("prop_mod", ""),
        REMAP_PARAMETER("remap_param", "Remapping a parameter for the companion analyser is not yet possible"),
        REPLACEMENT("replacement", "Reiterate, because of statement replacement"),
        SINGLE_RETURN_VALUE("svr", "single return value not yet set"),
        SINGLETON("singleton", ""),
        STATE_DELAYED("state", "variable cannot get value because state is delayed"),
        TEMP_MM("temp_mm", "Temporary modified method"),
        TO_IMPLEMENT("to_implement", "Temporary; needs implementation"),
        TYPE_ANALYSIS("type_analysis", "Type analysis missing"),
        UNREACHABLE("unreachable", "No data due to unreachable code - used as a precaution"),
        UTILITY_CLASS("utility_class", ""),
        VALUE_IMMUTABLE("immutable", "The value's IMMUTABLE status has not yet been determined"),
        VALUE_INDEPENDENT("independent", "The value's INDEPENDENT status has not yet been determined"),
        VALUE_NOT_NULL("not_null", "The value's NOT_NULL status has not yet been determined"),
        VALUE("value", "The value has not yet been determined"),
        VARIABLE_DOES_NOT_EXIST("var_missing", "Variable does not (yet) exist");

        public final String msg;
        public final String label;

        Cause(String label, String msg) {
            this.msg = msg;
            this.label = label;
        }
    }

    Cause cause();

    Location location();

}

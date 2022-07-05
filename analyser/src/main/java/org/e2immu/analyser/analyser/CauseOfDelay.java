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

import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;

public interface CauseOfDelay extends Comparable<CauseOfDelay> {
    int LOW = 0;
    int HIGH = 1;

    enum Cause {
        APPROVED_PRECONDITIONS("approved_pc", "Approved preconditions for field"),
        ASPECT("aspect", "The type's aspect has not yet been determined"),
        ASSIGNED_TO_FIELD("assign_to_field", "The component 'analyseFieldAssignments' has not yet finished"),
        BEFORE_MARK("before_mark", "@BeforeMark not yet determined"),
        BREAK_IMMUTABLE_DELAY("break_imm_delay", "", HIGH),
        BREAK_INIT_DELAY("break_init_delay", "cyclic dependency in initialisation", HIGH),
        BREAK_INIT_DELAY_IN_MERGE("bid_merge", "Break init delay in merge", HIGH),
        BREAK_MOM_DELAY("break_mom_delay", "break a content modified - mom delay", HIGH),
        CANDIDATE_NULL_PTR("candidate_null_ptr", ""),
        CNN_PARENT("context_not_null_parent", "Context not null for parent"),
        CNN_TRAVELS_TO_PC("cnn_travels_to_pc", "Context not null travels to precondition, does not result in warning"),
        CONDITION("condition", "condition in inline merge"),
        CONSTANT("constant", ""),
        CONSTRUCTOR_TO_INSTANCE("constructor-to-instance", "", HIGH),
        CONTAINER("container", "container property not yet determined"),
        CONTEXT_CONTAINER("c_cont", "context-container property not yet determined"),
        CONTEXT_IMMUTABLE("c_imm", "context immutable"),
        CONTEXT_MODIFIED("cm", "Context modified not yet been determined", HIGH),
        CONTEXT_NOT_NULL_FOR_PARENT("cnn_parent", ""),
        CONTEXT_NOT_NULL("cnn", ""),
        DELAYED_EXPRESSION("de", "delayed expression to be substituted", HIGH),
        ECI("eci", "Explicit constructor invocation", HIGH),
        ECI_HELPER("eci_helper", "Explicit constructor invocation, helper"),
        EXT_IMM("ext_imm", "Variable's EXTERNAL_IMMUTABLE value not yet determined"),
        EXT_CONTAINER("ext_container", "Variable's EXTERNAL_CONTAINER not yet determined"),
        EXT_IGNORE_MODS("ext_ign_mod", "Variable's EXTERNAL_IGNORE_MODIFICATIONS not yet determined"),
        EXTENSION_CLASS("extension_class", ""),
        EXTERNAL_NOT_NULL("ext_not_null", "Variable's EXTERNAL_NOT_NULL value not yet determined", HIGH),
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
        INITIAL_VALUE("initial", "Not yet initialized", HIGH),
        LINKING("link", "Delay in linking", HIGH),
        LOCAL_PT_ANALYSERS("local_pt_analysers", "Local primary type analysers not yet present"),
        LOCAL_VARS_ASSIGNED("local_vars", "Local variables assigned in this loop not yet determined"),
        MIN_INT("min_int", "Minimum integer; should only appear locally need .reduce()"),
        MODIFIED_METHOD("mm", "The method's modification status has not yet been determined"),
        MODIFIED_OUTSIDE_METHOD("mom", "modified outside method", HIGH),
        MODIFIED_VARIABLE("mod_var", "modified variable"),
        MODIFIED_CYCLE("mod_cycle", "modification of cyclic method calls"),
        NEXT_C_IMM("next_c_imm", ""),
        NO_PRECONDITION_INFO("no precondition info", "no precondition information (yet)", HIGH),
        NOT_INVOLVED("not_involved", "Internal"),
        NOT_NULL_PARAMETER("nnp", ""),
        NOT_YET_EXECUTED("not_yet_executed", "Companion analyser has not been executed yet"),
        PARTIAL_IMM("partial_imm", "partial immutable: immutable without taking parent/enclosing into account"),
        PROP_MOD("prop_mod", ""),
        READ("read", "must be there for property READ, but cannot be delayed"),
        REMAP_PARAMETER("remap_param", "Remapping a parameter for the companion analyser is not yet possible"),
        REPLACEMENT("replacement", "Reiterate, because of statement replacement"),
        SA_APPLY_DUMMY_DELAY("sa_apply_dummy", "Dummy delay in SAApply"),
        SINGLE_RETURN_VALUE("srv", "single return value not yet set", HIGH),
        SINGLETON("singleton", ""),
        STATE_DELAYED("state", "variable cannot get value because state is delayed", HIGH),
        TEMP_MM("temp_mm", "Temporary modified method"),
        TO_IMPLEMENT("to_implement", "Temporary; needs implementation"),
        TYPE_ANALYSIS("type_analysis", "Type analysis missing", HIGH),
        UNREACHABLE("unreachable", "No data due to unreachable code - used as a precaution"),
        UTILITY_CLASS("utility_class", ""),
        VALUE_IMMUTABLE("immutable", "The value's IMMUTABLE status has not yet been determined"),
        VALUE_INDEPENDENT("independent", "The value's INDEPENDENT status has not yet been determined"),
        VALUE_NOT_NULL("not_null", "The value's NOT_NULL status has not yet been determined"),
        VALUE("value", "The value has not yet been determined"),
        VALUES("values", "field values[] not yet determined", HIGH),
        VARIABLE_DOES_NOT_EXIST("var_missing", "Variable does not (yet) exist"),
        WAIT_FOR_ASSIGNMENT("wait_for_assignment", "Wait to see if variable is assigned or not"),
        WAIT_FOR_MODIFICATION("wait_for_modification", "Wait to see if variable is modified or not", HIGH);

        public final String msg;

        public final String label;
        public final int priority;

        Cause(String label, String msg) {
            this(label, msg, LOW);
        }

        Cause(String label, String msg, int priority) {
            this.msg = msg;
            this.label = label;
            this.priority = priority;
        }

    }

    @NotNull
    Cause cause();

    boolean variableIsField(FieldInfo fieldInfo);

    String withoutStatementIdentifier();

    @NotNull
    Location location();

    CauseOfDelay translate(InspectionProvider inspectionProvider, TranslationMap translationMap);
}

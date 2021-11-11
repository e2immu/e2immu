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
        ASSIGNED_TO_FIELD("assign_to_fied", "The component 'analyseFieldAssignments' has not yet finished"),
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
        LOCAL_PT_ANALYSERS("local_pt_analysers", "Local primary type analysers not yet present");

        public final String msg;
        public final String label;

        Cause(String label, String msg) {
            this.msg = msg;
            this.label = label;
        }

        public static Cause from(VariableProperty variableProperty) {
            return switch (variableProperty) {
                case IMMUTABLE -> VALUE_IMMUTABLE;
                case NOT_NULL_EXPRESSION -> VALUE_NOT_NULL;
                case INDEPENDENT -> VALUE_INDEPENDENT;
                default -> throw new UnsupportedOperationException();
            };
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

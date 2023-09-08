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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.Location;

import java.util.Objects;

public record Message(Location location, Label message, String extra) {

    public static int SORT(Message m1, Message m2) {
        int c = m1.location.compareTo(m2.location);
        if (c != 0) return c;
        return m1.message.compareTo(m2.message);
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR
    }

    public enum Label {
        ANNOTATION_ABSENT,
        ANNOTATION_UNEXPECTEDLY_PRESENT,
        ASSERT_EVALUATES_TO_CONSTANT_FALSE,
        ASSERT_EVALUATES_TO_CONSTANT_TRUE(Severity.WARN),
        ASSIGNMENT_TO_CURRENT_VALUE(Severity.WARN),
        ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE,
        ASSIGNMENT_TO_SELF,
        CALL_CYCLE_NOT_NULL(Severity.INFO),
        CALLING_MODIFYING_METHOD_ON_IMMUTABLE_OBJECT,
        CIRCULAR_TYPE_DEPENDENCY(Severity.WARN),
        CONDITION_EVALUATES_TO_CONSTANT_ENN(Severity.WARN),
        CONDITION_EVALUATES_TO_CONSTANT,
        CONTRADICTING_ANNOTATIONS,
        DIVISION_BY_ZERO,
        DUPLICATE_MARK_CONDITION,
        EMPTY_LOOP,
        EVENTUAL_AFTER_REQUIRED,
        EVENTUAL_BEFORE_REQUIRED,
        FACTORY_METHOD_INDEPENDENT_HC,
        FIELD_INITIALIZATION_NOT_NULL_CONFLICT(Severity.WARN), // only when not all field's values linked to parameter
        FINALIZER_METHOD_CALLED_ON_FIELD_NOT_IN_FINALIZER,
        FINALIZER_METHOD_CALLED_ON_PARAMETER,
        IGNORING_RESULT_OF_METHOD_CALL(Severity.WARN),
        INCOMPATIBLE_IMMUTABILITY_CONTRACT_AFTER,
        INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE,
        INCOMPATIBLE_IMMUTABILITY_CONTRACT_BEFORE_NOT_EVENTUALLY_IMMUTABLE,
        INCOMPATIBLE_PRECONDITION,
        INCONSISTENT_INDEPENDENCE_VALUE(Severity.INFO),
        INFINITE_LOOP_CONDITION(Severity.WARN),
        INLINE_CONDITION_EVALUATES_TO_CONSTANT_ENN(Severity.WARN),
        INLINE_CONDITION_EVALUATES_TO_CONSTANT,
        INTERRUPT_IN_LOOP,
        LOOP_ONCE(Severity.WARN),
        METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT,
        METHOD_SHOULD_BE_MARKED_STATIC,
        MODIFICATION_NOT_ALLOWED,
        NON_PRIVATE_FIELD_NOT_FINAL,
        NULL_POINTER_EXCEPTION,
        ONLY_WRONG_MARK_LABEL,
        OVERWRITING_PREVIOUS_ASSIGNMENT,
        PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
        PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT,
        POTENTIAL_NULL_POINTER_EXCEPTION(Severity.WARN),
        POTENTIAL_CONTENT_NOT_NULL(Severity.WARN),
        PRECONDITION_ABSENT,
        PRIVATE_FIELD_NOT_READ_IN_PRIMARY_TYPE,
        PRIVATE_FIELD_NOT_READ_IN_OWNER_TYPE,
        TRIVIAL_CASES_IN_SWITCH,
        TYPE_ANALYSIS_NOT_AVAILABLE(Severity.INFO),
        TYPES_WITH_FINALIZER_ONLY_EFFECTIVELY_FINAL,
        TYPE_HAS_DIFFERENT_VALUE_FOR_INDEPENDENT,
        UNNECESSARY_FIELD_INITIALIZER(Severity.WARN),
        UNNECESSARY_METHOD_CALL(Severity.WARN),
        UNREACHABLE_STATEMENT,
        UNUSED_LOCAL_VARIABLE,
        UNUSED_LOOP_VARIABLE(Severity.WARN),
        UNUSED_PARAMETER(Severity.WARN),
        USELESS_ASSIGNMENT,
        WORSE_THAN_IMPLEMENTED_INTERFACE,
        WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
        WORSE_THAN_OVERRIDDEN_METHOD,
        WRONG_ANNOTATION_PARAMETER,
        WRONG_PRECONDITION;

        public final Severity severity;

        Label() {
            this.severity = Severity.ERROR;
        }

        Label(Severity severity) {
            this.severity = severity;
        }
    }

    public static Message newMessage(Location location, Message.Label message) {
        return new Message(location, message, "");
    }

    public static Message newMessage(Location location, Message.Label message, String extra) {
        return new Message(location, message, extra);
    }

    public Message(Location location, Message.Label message, String extra) {
        this.message = Objects.requireNonNull(message);
        this.location = Objects.requireNonNull(location);
        this.extra = Objects.requireNonNull(extra);
    }

    @Override
    public String toString() {
        return message.severity + " in " + location + ": "
                + Bundle.INSTANCE.get(message.name()) + (extra.isBlank() ? "" : ": " + extra);
    }

    public String detailedMessage() {
        return message.severity + " in " + location.detailedLocation()
                + ": " + Bundle.INSTANCE.get(message.name()) + (extra.isBlank() ? "" : ": " + extra);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message other = (Message) o;
        return message == other.message &&
                extra.equals(other.extra) &&
                location.equals(other.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, extra, location);
    }
}

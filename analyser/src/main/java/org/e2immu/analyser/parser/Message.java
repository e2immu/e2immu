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

package org.e2immu.analyser.parser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.Location;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull1;

import java.util.Map;
import java.util.Objects;

@E2Container
public class Message {


    public enum Severity {
        INFO,
        WARN,
        ERROR
    }

    public static final String DIVISION_BY_ZERO = "Division by zero";
    public static final String NULL_POINTER_EXCEPTION = "Null pointer exception";
    public static final String INLINE_CONDITION_EVALUATES_TO_CONSTANT = "Inline conditional evaluates to constant";
    public static final String CONDITION_EVALUATES_TO_CONSTANT = "Condition in 'if' or 'switch' statement evaluates to constant";

    public static final String INLINE_CONDITION_EVALUATES_TO_CONSTANT_ENN = "Inline conditional evaluates to constant (implied via @NotNull on field)";
    public static final String CONDITION_EVALUATES_TO_CONSTANT_ENN = "Condition in 'if' or 'switch' statement evaluates to constant (implied via @NotNull on field)";

    public static final String ASSERT_EVALUATES_TO_CONSTANT_FALSE = "Condition in 'assert' is always false";
    public static final String ASSERT_EVALUATES_TO_CONSTANT_TRUE = "Condition in 'assert' is always true";

    public static final String EMPTY_LOOP = "Empty loop";
    public static final String UNUSED_LOOP_VARIABLE = "Unused loop variable";

    public static final String UNREACHABLE_STATEMENT = "Unreachable statement";

    public static final String POTENTIAL_NULL_POINTER_EXCEPTION = "Potential null pointer exception";
    public static final String MODIFICATION_NOT_ALLOWED = "Illegal modification suspected";
    public static final String UNNECESSARY_METHOD_CALL = "Unnecessary method call";

    public static final String PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO = "Parameter should not be assigned to";
    public static final String METHOD_SHOULD_BE_MARKED_STATIC = "Method should be marked static";
    public static final String ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE = "Assigning to field outside the type";

    public static final String PRECONDITION_ABSENT = "Precondition missing";
    public static final String ANNOTATION_ABSENT = "Annotation missing";
    public static final String ANNOTATION_UNEXPECTEDLY_PRESENT = "Annotation should be absent";
    public static final String WRONG_ANNOTATION_PARAMETER = "Wrong annotation parameter";
    public static final String PRIVATE_FIELD_NOT_READ = "Private field not read outside constructors";
    public static final String NON_PRIVATE_FIELD_NOT_FINAL = "Non-private field is not effectively final (@Final)";
    public static final String EFFECTIVELY_FINAL_FIELD_NOT_RECORD = "Effectively final field is not allowed to be of a record type";

    public static final String IGNORING_RESULT_OF_METHOD_CALL = "Ignoring result of method call";
    public static final String UNUSED_LOCAL_VARIABLE = "Unused local variable";
    public static final String USELESS_ASSIGNMENT = "Useless assignment";

    public static final String PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT = "Part of short-circuit expression evaluates to constant";

    public static final String WRONG_PRECONDITION = "Wrong precondition";

    public static final String ONLY_BEFORE = "Calling method annotated @Only(before=\"x\") when \"x\" has already been @Mark'ed";
    public static final String ONLY_AFTER = "Calling method annotated @Only(after=\"x\") when \"x\" has not yet been @Mark'ed";
    public static final String ONLY_WRONG_MARK_LABEL = "@Only annotation, wrong mark label";
    public static final String DUPLICATE_MARK_LABEL = "Duplicate mark label";

    public static final String CIRCULAR_TYPE_DEPENDENCY = "Detected circular type dependency: this affects modification computations";

    public static final String CALLING_MODIFYING_METHOD_ON_E2IMMU = "Calling modifying method on level 2 immutable type";

    public static final String INCOMPATIBLE_PRECONDITION = "Incompatible preconditions";
    public static final String WORSE_THAN_OVERRIDDEN_METHOD = "Property value worse than overridden method";
    public static final String WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER = "Property value worse than overridden method's parameter";

    public static final String UNUSED_PARAMETER = "Unused parameter";

    public static final String CONTRADICTING_ANNOTATIONS = "Contradicting annotations";

    public static final String USELESS_LOCAL_CLASS_DECLARATION ="Unused local class declaration (no statements follow)";
    public static final String TRIVIAL_CASES_IN_SWITCH = "Trivial cases in switch";

    @NotNull1
    @E2Container
    public static final Map<String, Severity> SEVERITY_MAP;

    static {
        ImmutableMap.Builder<String, Severity> map = new ImmutableMap.Builder<>();

        // the following is an error because it "covers" for a gap in the definition of modification (See Modified_15)
        map.put(ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE, Severity.ERROR);
        map.put(UNUSED_PARAMETER, Severity.WARN);
        map.put(UNUSED_LOOP_VARIABLE, Severity.WARN);

        map.put(POTENTIAL_NULL_POINTER_EXCEPTION, Severity.WARN);
        map.put(UNNECESSARY_METHOD_CALL, Severity.WARN);
        map.put(IGNORING_RESULT_OF_METHOD_CALL, Severity.WARN);
        map.put(CIRCULAR_TYPE_DEPENDENCY, Severity.WARN);

        map.put(ASSERT_EVALUATES_TO_CONSTANT_TRUE, Severity.WARN);

        map.put(INLINE_CONDITION_EVALUATES_TO_CONSTANT_ENN, Severity.WARN);
        map.put(CONDITION_EVALUATES_TO_CONSTANT_ENN, Severity.WARN);

        SEVERITY_MAP = map.build();
    }

    public final String message;
    public final Severity severity;
    public final Location location;

    public static Message newMessage(Location location, String message) {
        Severity severity = SEVERITY_MAP.getOrDefault(message, Severity.ERROR);
        return new Message(severity, location, message);
    }

    public static Message newMessage(Location location, String message, String extra) {
        Severity severity = SEVERITY_MAP.getOrDefault(message, Severity.ERROR);
        return new Message(severity, location, message + ": " + extra);
    }

    public Message(Severity severity, Location location, String message) {
        this.message = Objects.requireNonNull(message);
        this.severity = Objects.requireNonNull(severity);
        this.location = Objects.requireNonNull(location);
    }

    @Override
    public String toString() {
        return severity + " in " + location + ": " + message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message1 = (Message) o;
        return message.equals(message1.message) &&
                severity == message1.severity &&
                location.equals(message1.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, severity, location);
    }
}

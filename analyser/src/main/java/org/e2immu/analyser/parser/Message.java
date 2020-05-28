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

import java.util.Map;

public class Message {

    public enum Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static final String DIVISION_BY_ZERO = "Division by zero";
    public static final String NULL_POINTER_EXCEPTION = "Null pointer exception";
    public static final String INLINE_CONDITION_EVALUATES_TO_CONSTANT = "Inline conditional evaluates to constant";
    public static final String CONDITION_EVALUATES_TO_CONSTANT = "Condition in 'if' or 'switch' statement evaluates to constant";
    public static final String EMPTY_LOOP = "Empty loop";
    public static final String UNREACHABLE_STATEMENT = "Unreachable statement";

    public static final String POTENTIAL_NULL_POINTER_EXCEPTION = "Potential null pointer exception";
    public static final String POTENTIAL_SIZE_PROBLEM = "Potential size problem";
    public static final String UNNECESSARY_METHOD_CALL = "Unnecessary method call";

    public static final String CANNOT_FIND_METHOD_IN_SUPER_TYPE = "Cannot find method in super type";
    public static final String PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO = "Parameter should not be assigned to";
    public static final String METHOD_SHOULD_BE_MARKED_STATIC = "Method should be marked static";
    public static final String ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE = "Assigning to field outside the primary type";

    public static final String ANNOTATION_UNEXPECTEDLY_PRESENT = "Annotation should be absent";
    public static final String ANNOTATION_ABSENT = "Annotation missing";
    public static final String SIZE_MIN_MISSING = "@Size annotation, min value missing";
    public static final String SIZE_WRONG_MIN_VALUE = "@Size annotation, wrong min value";
    public static final String SIZE_EQUALS_MISSING = "@Size annotation, equals value missing";
    public static final String SIZE_WRONG_EQUALS_VALUE = "@Size annotation, wrong equals value";
    public static final String WRONG_CONSTANT = "@Constant annotation, wrong value";
    public static final String PRIVATE_FIELD_NOT_READ = "Private field not read outside constructors";
    public static final String NON_PRIVATE_FIELD_NOT_FINAL = "Non-private field is not effectively final (@Final)";
    public static final String EFFECTIVELY_FINAL_FIELD_NOT_RECORD = "Effectively final field is not allowed to be of a record type";

    public static final String IGNORING_RESULT_OF_METHOD_CALL = "Ignoring result of method call";
    public static final String UNUSED_LOCAL_VARIABLE = "Unused local variable";
    public static final String USELESS_ASSIGNMENT = "Useless assignment";
    public static final String METHOD_EVALUATES_TO_CONSTANT = "Method evaluates to constant";

    public static final String METHOD_NOT_ALLOWED_TO_CALL_MODIFYING_METHOD = "Method is not allowed to call non-@NotModified method into nested or enclosing type";
    public static final String METHOD_NOT_ALLOWED_TO_ASSIGN_TO_FIELD = "Method is not allowed to assign to field";

    public static final String MULTIPLE_SIZE_ANNOTATIONS = "Multiple @Size annotations in a modifying method";
    public static final String PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT = "Part of short-circuit expression evaluates to constant";

    public static final Map<String, Severity> SEVERITY_MAP;

    static {
        ImmutableMap.Builder<String, Severity> map = new ImmutableMap.Builder<>();
        map.put(DIVISION_BY_ZERO, Severity.ERROR);
        map.put(NULL_POINTER_EXCEPTION, Severity.ERROR);
        map.put(INLINE_CONDITION_EVALUATES_TO_CONSTANT, Severity.ERROR);
        map.put(EMPTY_LOOP, Severity.ERROR);
        map.put(CONDITION_EVALUATES_TO_CONSTANT, Severity.ERROR);
        map.put(METHOD_EVALUATES_TO_CONSTANT, Severity.ERROR);
        map.put(CANNOT_FIND_METHOD_IN_SUPER_TYPE, Severity.ERROR);
        map.put(METHOD_SHOULD_BE_MARKED_STATIC, Severity.ERROR);
        map.put(ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE, Severity.ERROR);
        map.put(PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO, Severity.ERROR);
        map.put(UNREACHABLE_STATEMENT, Severity.ERROR);

        map.put(ANNOTATION_ABSENT, Severity.ERROR);
        map.put(ANNOTATION_UNEXPECTEDLY_PRESENT, Severity.ERROR);
        map.put(SIZE_EQUALS_MISSING, Severity.ERROR);
        map.put(SIZE_MIN_MISSING, Severity.ERROR);
        map.put(SIZE_WRONG_EQUALS_VALUE, Severity.ERROR);
        map.put(SIZE_WRONG_MIN_VALUE, Severity.ERROR);
        map.put(WRONG_CONSTANT, Severity.ERROR);
        map.put(PRIVATE_FIELD_NOT_READ, Severity.ERROR);
        map.put(NON_PRIVATE_FIELD_NOT_FINAL, Severity.ERROR);
        map.put(EFFECTIVELY_FINAL_FIELD_NOT_RECORD, Severity.ERROR);
        map.put(USELESS_ASSIGNMENT, Severity.ERROR);
        map.put(UNUSED_LOCAL_VARIABLE, Severity.ERROR);
        map.put(METHOD_NOT_ALLOWED_TO_CALL_MODIFYING_METHOD, Severity.ERROR);
        map.put(METHOD_NOT_ALLOWED_TO_ASSIGN_TO_FIELD, Severity.ERROR);
        map.put(PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT, Severity.ERROR);

        map.put(POTENTIAL_NULL_POINTER_EXCEPTION, Severity.WARN);
        map.put(UNNECESSARY_METHOD_CALL, Severity.WARN);
        map.put(IGNORING_RESULT_OF_METHOD_CALL, Severity.WARN);
        map.put(POTENTIAL_SIZE_PROBLEM, Severity.WARN);

        SEVERITY_MAP = map.build();
    }

    public final String message;
    public final Severity severity;
    public final Location location;

    public static Message newMessage(Location location, String message) {
        Severity severity = SEVERITY_MAP.get(message);
        if (severity == null) throw new UnsupportedOperationException();
        return new Message(severity, location, message);
    }

    public static Message newMessage(Location location, String message, String extra) {
        Severity severity = SEVERITY_MAP.get(message);
        if (severity == null) throw new UnsupportedOperationException("Need severity for " + message);
        return new Message(severity, location, message + ": " + extra);
    }

    public Message(Severity severity, Location location, String message) {
        this.message = message;
        this.severity = severity;
        this.location = location;
    }

    @Override
    public String toString() {
        return severity + " in " + location + ": " + message;
    }
}

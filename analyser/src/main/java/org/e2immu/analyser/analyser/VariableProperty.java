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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;

public enum VariableProperty {
    CHECK_NOT_NULL("check not null"),
    READ("read"),
    READ_MULTIPLE_TIMES("read+"),
    NOT_YET_READ_AFTER_ASSIGNMENT("not yet read"),

    ASSIGNED("assigned"),
    ASSIGNED_MULTIPLE_TIMES("assigned+"),
    // in a block, are we guaranteed to reach the last assignment?
    // we focus on last assignment because that is what the 'currentValue' holds
    LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED("reached"),

    CREATED("created"), // the state after creation, but before first assignment

    // this variable is changing inside a loop; do not read its value
    ASSIGNED_IN_LOOP("in loop"),

    // the ones corresponding to annotations

    NOT_NULL("@NotNull", true, Level.compose(Level.TRUE, Level.NOT_NULL)), // numeric

    // the following three are on types only
    NOT_NULL_FIELDS("@NotNull(where=FIELDS)", true, Level.compose(Level.TRUE, Level.NOT_NULL)), // numeric
    NOT_NULL_METHODS("@NotNull(where=METHODS)", true, Level.compose(Level.TRUE, Level.NOT_NULL)), // numeric
    NOT_NULL_PARAMETERS("@NotNull(where=PARAMETERS)", true, Level.compose(Level.TRUE, Level.NOT_NULL)), // numeric

    FINAL("@Final"), // boolean
    CONTAINER("@Container"), // boolean
    IMMUTABLE("@Immutable", true, Level.compose(Level.TRUE, Level.E2IMMUTABLE)), // numeric
    NOT_MODIFIED("@NotModified"), // ternary
    CONSTANT("@Constant"), //boolean
    EXTENSION_CLASS("@ExtensionClass"),
    FLUENT("@Fluent"),
    IDENTITY("@Identity"),
    IGNORE_MODIFICATIONS("@IgnoreModifications"),
    INDEPENDENT("@Independent"),
    LINKED("@Linked"),
    MARK("@Mark"),
    ONLY("@Only"),
    OUTPUT("@Output"),
    SINGLETON("@Singleton"),
    UTILITY_CLASS("@UtilityClass");

    public final String name;
    public final boolean canImprove;
    public final int best;

    private VariableProperty(String name) {
        this(name, false, Level.TRUE);
    }

    private VariableProperty(String name, boolean canImprove, int best) {
        this.name = name;
        this.canImprove = canImprove;
        this.best = best;
    }

    @Override
    public String toString() {
        return name;
    }

}

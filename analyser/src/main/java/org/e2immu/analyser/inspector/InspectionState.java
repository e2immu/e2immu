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

package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.Inspector;

import java.util.Objects;

public enum InspectionState {
    TRIGGER_BYTECODE_INSPECTION(1, Inspector.BYTE_CODE_INSPECTION),
    STARTING_BYTECODE(2, Inspector.BYTE_CODE_INSPECTION),
    FINISHED_BYTECODE(3, Inspector.BYTE_CODE_INSPECTION),
    INIT_JAVA_PARSER(4, Inspector.JAVA_PARSER_INSPECTION),
    TRIGGER_JAVA_PARSER(5, Inspector.JAVA_PARSER_INSPECTION),
    STARTING_JAVA_PARSER(6, Inspector.JAVA_PARSER_INSPECTION),
    FINISHED_JAVA_PARSER(7, Inspector.JAVA_PARSER_INSPECTION),
    BY_HAND_WITHOUT_STATEMENTS(8, Inspector.BY_HAND_WITHOUT_STATEMENTS),
    BY_HAND(8, Inspector.BY_HAND),
    BUILT(9, null);

    private final int state;
    private final Inspector inspector;

    InspectionState(int state, Inspector inspector) {
        this.state = state;
        this.inspector = inspector;
    }

    public boolean ge(InspectionState other) {
        return state >= other.state;
    }

    public boolean le(InspectionState other) {
        return state <= other.state;
    }

    public boolean lt(InspectionState other) {
        return state < other.state;
    }

    public Inspector getInspector() {
        return Objects.requireNonNull(inspector, "Need to query before the type is built!");
    }

    public boolean isDone() {
        return this == FINISHED_BYTECODE || this == FINISHED_JAVA_PARSER || this == BUILT;
    }
}

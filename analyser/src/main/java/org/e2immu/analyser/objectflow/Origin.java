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

package org.e2immu.analyser.objectflow;

public enum Origin {
    NO_ORIGIN,

    RESULT_OF_METHOD,
    RESULT_OF_OPERATOR, // very similar to result of method
    FIELD_ACCESS, // access to field or array element without assigning to it

    PARAMETER,
    INTERNAL,
    NEW_OBJECT_CREATION,
    LITERAL,

    // will be replaced by whatever is assigned to the field
    INITIAL_FIELD_FLOW,

    // will be replaced by PARAMETER
    INITIAL_PARAMETER_FLOW,

    // will be replaced by whatever is assigned to the method
    INITIAL_METHOD_FLOW,
}

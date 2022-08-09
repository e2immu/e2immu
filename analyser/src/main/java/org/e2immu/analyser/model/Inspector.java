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

package org.e2immu.analyser.model;

/*
Information about the way inspection was carried out.
For now, the only relevant thing we can deduce is whether the type's statements were inspected, or not.

(So we could have put a simple boolean in TypeInspection, rather than this object.)

Note that "synthetic" is not quite equivalent to "BY_HAND". The byte code inspector can add synthetic
fields and methods; at the moment it cannot add synthetic types.

the label is there for clarity when debugging.
 */
public record Inspector(boolean statements, String label) {

    public static final Inspector BYTE_CODE_INSPECTION = new Inspector(false, "Byte code");
    public static final Inspector JAVA_PARSER_INSPECTION = new Inspector(true, "Java parser");

    public static final Inspector BY_HAND = new Inspector(true, "By hand");
    public static final Inspector BY_HAND_WITHOUT_STATEMENTS = new Inspector(false, "By hand");

}

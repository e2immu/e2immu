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

package org.e2immu.analyser.testexample;

/*
 method-local class; the analyser recognises them, but that's just about it.
 I do not really see their reason for existence.
*/

public class SubTypes_1 {

    protected static String methodWithSubType() {
        String s = "abc";

        class KV {
            final String key;
            final String value;

            KV(String key, String value) {
                this.key = key;
                this.value = value + s;
            }

            @Override
            public String toString() {
                return "KV=(" + key + "," + value + ")";
            }
        }

        KV kv1 = new KV("a", "BC");
        return kv1.toString();
    }
}

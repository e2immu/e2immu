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

package org.e2immu.analyser.parser.own.annotationstore.testexample;

import org.e2immu.annotation.NotNull;

import java.util.Map;

/*
Parts of the Store class in annotation store, for debugging purposes.

This is another example where the link between variable and loop copy plays a role.
 */
public class Store_2 {

    public static int handleMultiSet(@NotNull(content = true) Map<String, Object> body) {
        int countRemoved = 0;
        try { // 1
            for (Map.Entry<String, Object> entry : body.entrySet()) { // 1.0.0
                if (entry.getValue() instanceof String) { // 1.0.0 .0.0
                    if (entry.getKey() != null) { // 1.0.0 .0.0 .0.0
                        countRemoved++; // 1.0.0 .0.0 .0.0 .0.0
                    }
                }
            }
        } catch (RuntimeException re) {
            re.printStackTrace();
        }
        return countRemoved;
    }
}

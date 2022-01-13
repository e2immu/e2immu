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

import java.util.Map;

/*
Minimal version of Store_0, still with the errors:
(1) had to create a DelayedExpression of boolean return type for a delayed instanceof in 0.0.0.
(2) linking delay creates jump from 0 to 1 in propagate modification on entry (links to body = parameter,
  and because we have no annotations in this run, getValue() is an abstract method)
 */
public class Store_1 {

    public static void handleMultiSet(Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getValue() instanceof String) {
                System.out.println("?");
            }
        }
    }

}

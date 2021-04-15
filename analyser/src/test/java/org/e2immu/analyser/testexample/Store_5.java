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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/*
Parts of the Store class in annotation store, for debugging purposes.

 */
public class Store_5 {
    private static final Logger LOGGER = LoggerFactory.getLogger(Store_5.class);

    private static long flexible(Object object, long defaultValue) {
        LOGGER.info("Parsing " + object);
        if (object == null) return defaultValue;
        String s = object.toString();
        try {
            double d = Double.parseDouble(s);
            return (long) d;
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }
}

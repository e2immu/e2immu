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

package org.e2immu.analyser.parser.failing.testexample;

import java.util.function.Consumer;

/*
Parts of the Store class in annotation store, for debugging purposes.

Created dummy interfaces to avoid having to import lots of code.
 */
public class Store_6 {

    public Store_6() {
        ConfigRetriever retriever = ConfigRetriever.create();
        retriever.getConfig(ar -> {
            if (ar.failed()) {
                String config = ar.result();
                // IMPORTANT: remove the following line, and the delay cycle breaks!!!
                int port = (int) flexible(config);
            }
        });
    }

    private static long flexible(Object object) {
        return 9L + object.hashCode();
    }

    static class ConfigRetriever {
        static ConfigRetriever create() {
            throw new UnsupportedOperationException();
        }

        void getConfig(Consumer<AsyncResult> consumer) {
            // nothing needed here
        }
    }

    interface AsyncResult {
        boolean failed();

        String result();
    }
}

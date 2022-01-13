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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/*
Parts of project, to try to fix bugs
 */

public class Project_3 {


    static class Container {
        final String value;
        final Instant updated;
        private volatile Instant read;

        public Container(String value, Instant previousRead) {
            this.value = value;
            this.read = previousRead;
            updated = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        }
    }

    private final Map<String, Container> kvStore = new ConcurrentHashMap<>();

    public void visit(BiConsumer<String, String> visitor) {
        for (Map.Entry<String, Container> entry : kvStore.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().value;
            visitor.accept(key, value);
        }
    }
}


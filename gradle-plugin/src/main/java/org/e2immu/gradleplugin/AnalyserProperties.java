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

package org.e2immu.gradleplugin;

import java.util.Map;

public class AnalyserProperties {

    private Map<String, Object> properties;

    public AnalyserProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Convenience method for setting a single property.
     *
     * @param key the key of the property to be added
     * @param value the value of the property to be added
     */
    public void property(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * Convenience method for setting multiple properties.
     *
     * @param properties the properties to be added
     */
    public void properties(Map<String, ?> properties) {
        this.properties.putAll(properties);
    }

    /**
     * @return The Analyser properties for the current Gradle project that are to be passed to the Analyser gradle.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }
}

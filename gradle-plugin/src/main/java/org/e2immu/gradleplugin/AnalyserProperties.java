/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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

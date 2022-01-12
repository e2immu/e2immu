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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/*
Parts of the Store class in annotation store, for debugging purposes.

 */
public class Store_0 {
    private static final Logger LOGGER = LoggerFactory.getLogger(Store_0.class);

    private final Map<String, Project_0> projects = new HashMap<>();

    private Project_0 getOrCreate(String projectName) {
        Project_0 inMap = projects.get(projectName);
        if (inMap != null) {
            return inMap;
        }
        Project_0 newProject = new Project_0(projectName);
        projects.put(projectName, newProject);
        LOGGER.info("Created new project " + projectName);
        return newProject;
    }

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

    public void handleMultiSet(String projectName, Map<String, Object> body) {
        Project_0 project = getOrCreate(projectName);
        int countUpdated = 0;
        int countIgnored = 0;
        int countRemoved = 0;
        if (body == null ) {
            return;
        }
        try {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                String element = entry.getKey();
                if (entry.getValue() instanceof String) {
                    String current = (String) entry.getValue();
                    if (current.isEmpty()) {
                        String prev = project.remove(element);
                        if (prev != null) countRemoved++;
                    } else {
                        String prev = project.set(element, current);
                        if (prev == null || !prev.equals(current)) countUpdated++;
                    }
                } else {
                    countIgnored++;
                }
            }
        } catch (RuntimeException re) {
            re.printStackTrace();
            LOGGER.error(re.getMessage());
        }
        LOGGER.info("Multi-set updated " + countUpdated + " ignored " + countIgnored + " removed " + countRemoved);
    }
}

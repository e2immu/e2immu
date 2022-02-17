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

import org.e2immu.annotation.NotModified;

import java.util.HashMap;
import java.util.Map;

/*
Parts of the Store class in annotation store, for debugging purposes.
Trying to fix infinite loop.

Removing the assignment (just call getOrCreate()) breaks the loop: then, there is
no variable without linkedVariables not yet computed (because we don't know immutability of Store_3 yet)
 */
public class Store_3 {

    private final Map<String, Project_0> projects = new HashMap<>();

    @NotModified
    private Project_0 getOrCreate() {
        Project_0 inMap = projects.get("x");
        Project_0 newProject = new Project_0("x");
        return newProject;
    }

    public void handleMultiSet() {
        Project_0 project = getOrCreate();
    }
}

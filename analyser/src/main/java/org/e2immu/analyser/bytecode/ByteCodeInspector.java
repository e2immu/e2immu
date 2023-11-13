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

package org.e2immu.analyser.bytecode;


import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.Source;
import org.e2immu.annotation.Modified;

import java.util.List;

public interface ByteCodeInspector {

    /*
     To be called  from TypeMap only.
     The implementation will load as many types as necessary to load this source properly.
     It will only check with the type map for availability of types already loaded; it will NOT recursively start a
     loading process. Only at the end will the TypeMap store the loaded types.

     As a consequence, two parallel calls to inspectFromPath may be doing a bit of duplicate work, e.g. because
     the sources they load share a parent, enclosing type, or interface implemented.
     */
    @Modified
    List<TypeMap.InspectionAndState> inspectFromPath(Source source); // org/junit/Assert

}

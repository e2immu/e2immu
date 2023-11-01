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

package org.e2immu.analyser.resolver;


import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Inspection;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.Primary_0;
import org.e2immu.analyser.resolver.testexample.Primary_1;
import org.e2immu.analyser.resolver.testexample.Record_0;
import org.e2immu.analyser.resolver.testexample.Record_1;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestPrimary extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Primary_0.class);
        TypeInfo x = typeMap.get("org.e2immu.analyser.resolver.testexample.X");
        assertNotNull(x);
        TypeInfo y = typeMap.get("org.e2immu.analyser.resolver.testexample.Y");
        assertNotNull(y);
        TypeInfo z = typeMap.get("org.e2immu.analyser.resolver.testexample.Z");
        assertNull(z); // does not exist!!
        TypeInfo typeInfo = typeMap.get(Primary_0.class);
        assertEquals("org.e2immu.analyser.resolver.testexample.Primary_0", typeInfo.fullyQualifiedName);
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(null, Primary_0.class, Primary_1.class);
        TypeInfo typeInfo = typeMap.get(Primary_1.class);
        TypeInspection ti = typeInfo.typeInspection.get();
        assertSame(Inspection.Access.PUBLIC, ti.getAccess());
        FieldInfo x = typeInfo.getFieldByName("x", true);
        assertNotNull(x.type.typeInfo);
        assertEquals("org.e2immu.analyser.resolver.testexample.X", x.type.typeInfo.fullyQualifiedName);
        assertSame(Inspection.Access.PACKAGE, x.type.typeInfo.typeInspection.get().getAccess());
    }

}

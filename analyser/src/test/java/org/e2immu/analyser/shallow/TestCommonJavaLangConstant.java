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

package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;

import java.lang.constant.Constable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCommonJavaLangConstant extends CommonAnnotatedAPI {

    @Test
    public void testConstable() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Constable.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(MultiLevel.INDEPENDENT_HC_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
    }

    @Test
    public void testConstableDescribeConstable() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Constable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("describeConstable", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }
}

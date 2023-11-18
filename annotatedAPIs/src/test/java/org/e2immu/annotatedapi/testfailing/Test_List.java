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

package org.e2immu.annotatedapi.testfailing;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.annotatedapi.test.CommonTestRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_List extends CommonTestRunner {

    /*
     corresponds to BasicCompanionMethods_0, 2 errors, 0 warnings
     TEST FAILS: https://github.com/e2immu/e2immu/issues/39, see CreateCompanionMethod
     Will probably also require the Parser to run the shallow analysers on those types of AnnotatedXML
     that are not in the AnnotatedAPI
     */
    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = test(List.of("List_0"), 1, 0);
        TypeInfo arrayList = typeMap.get(ArrayList.class);
        TypeAnalysis arrayListAna = arrayList.typeAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, arrayListAna.getProperty(Property.CONTAINER));
        MethodInfo constructor0 = arrayList.findConstructor(0);
        MethodAnalysis constructor0Ana = constructor0.methodAnalysis.get();
        assertEquals(1, constructor0Ana.getCompanionAnalyses().size());
    }
}

/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public class TestPreloadJavaBase {

    private static TypeContext typeContext;

    @BeforeClass
    public static void beforeClass() throws IOException {
        org.e2immu.analyser.util.Logger.activate(Logger.LogTarget.INSPECT,
                Logger.LogTarget.BYTECODE_INSPECTOR);
        Parser parser = new Parser();
        typeContext = parser.getTypeContext();
    }


    @Test
    public void test() {
        TypeInfo list = typeContext.typeMapBuilder.get("java.util.List");
        Assert.assertNotNull(list);

        ParameterizedType listPt = Objects.requireNonNull(list.asParameterizedType(typeContext));
        ParameterizedType typeParam = listPt.parameters.get(0);
        Assert.assertEquals("Type param E", typeParam.toString());
    }
}

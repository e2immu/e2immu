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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.LocalVariableReference;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.parser.Primitives;
import org.junit.Test;

import java.util.List;

public class TestVariableInfo {
    private final Primitives primitives = new Primitives();

    @Test
    public void test1PureOverwrite() {
        VariableInfo viA = makeLocalIntVar("a");
        viA.setProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        VariableInfo viB = makeLocalIntVar("b");

        viA.merge(viB, true, List.of());
    }

    private VariableInfo makeLocalIntVar(String name) {
        return new VariableInfo(new LocalVariableReference(new LocalVariable(List.of(), name, primitives.intParameterizedType, List.of()),
                List.of()), name);
    }

}

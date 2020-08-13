
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.testexample.ImmutabilityAnnotations;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TestImmutabilityAnnotations extends CommonTestRunner {
    public TestImmutabilityAnnotations() {
        super(true);
    }

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo set = typeContext.getFullyQualified(Set.class);
        MethodInfo setOf2 = set.findUniqueMethod("of", 2);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, setOf2.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));

        TypeInfo list = typeContext.getFullyQualified(List.class);
        MethodInfo listOf2 = list.findUniqueMethod("of", 2);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, listOf2.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));

        ParameterizedType setString = new ParameterizedType(set, List.of(Primitives.PRIMITIVES.stringParameterizedType));
        ParameterizedType stringArray = new ParameterizedType(Primitives.PRIMITIVES.stringTypeInfo,1 , ParameterizedType.WildCard.NONE, List.of());
        TypeInfo freezableSet = typeContext.typeStore.get(ImmutabilityAnnotations.class.getCanonicalName()+".FreezableSet");

        // Set<String> contains Set
        Assert.assertTrue(setString.containsComponent(Primitives.PRIMITIVES.stringParameterizedType));

        // FreezableSet contains boolean
        Assert.assertTrue(freezableSet.asParameterizedType().containsComponent(Primitives.PRIMITIVES.booleanParameterizedType));

        // String[] contains String
        Assert.assertTrue(stringArray.containsComponent(Primitives.PRIMITIVES.stringParameterizedType));

        // some negative cases
        Assert.assertFalse(Primitives.PRIMITIVES.booleanParameterizedType.containsComponent(Primitives.PRIMITIVES.stringParameterizedType));

        // interesting... String is a complex type, it contains boolean
        Assert.assertTrue(stringArray.containsComponent(Primitives.PRIMITIVES.booleanParameterizedType));
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = data -> {
        if("generateBefore".equals(data.methodInfo.name) && "0".equals(data.statementId) && "list".equals(data.variableName)) {
            Assert.assertEquals("java.util.List.of(a, b)", data.currentValue.toString());
            int notNull = data.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, notNull);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ImmutabilityAnnotations", 0, 1, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

    }

}

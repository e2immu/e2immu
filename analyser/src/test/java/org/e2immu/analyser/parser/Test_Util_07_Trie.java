
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Util_07_Trie extends CommonTestRunner {

    public Test_Util_07_Trie() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:root>" : "root";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<f:root>" : "";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:get>" : "";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if ("org.e2immu.analyser.util.Trie.TrieNode.map#node".equals(d.variable().fullyQualifiedName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        String expectEval = d.iteration() == 0  ? "<f:map>": "";
                        assertEquals(expectEval, eval.getValue().toString());
                        assertEquals(MultiLevel.NULLABLE, eval.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                        String expected = d.iteration() <= 1 ? "<f:map>" : "";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<f:map>" : "";
                        assertEquals(expected, d.currentValue().toString());
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 1 ? "null!=<f:map>" : "";
                    assertEquals(expected, d.conditionManagerForNextStatement().state().toString());
                }
            }
        };

        testUtilClass(List.of("Trie", "Freezable"), 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}

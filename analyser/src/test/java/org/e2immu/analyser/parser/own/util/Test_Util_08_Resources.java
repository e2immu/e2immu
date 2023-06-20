
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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_08_Resources extends CommonTestRunner {

    public Test_Util_08_Resources() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = "(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("1.0.1.0.0.0.0".equals(d.statementId())) {
                    assertEquals("<no return value>", d.evaluationResult().value().toString());
                }
                if ("1.0.3.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:getPath>" : "dirRelativeToBase.getPath()";

                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = d.iteration() == 0
                            ? "cm@Parameter_f;initial:dirRelativeToBase@Method_recursivelyAddFiles_1.0.2-E"
                            : "";
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.3.0.1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:isEmpty>?new String[0]:<m:split>"
                            : "dirRelativeToBase.getPath().isEmpty()?new String[0]:(dirRelativeToBase.getPath().startsWith(\"/\")?dirRelativeToBase.getPath().substring(1):dirRelativeToBase.getPath()).split(\"/\")";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("1.0.3.0.2.0.1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:endsWith>&&0==<delayed array length>"
                            : "file.getName().endsWith(\".annotated_api\")&&0==packageParts.length";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("1.0.3.0.2.0.1.0.2".equals(d.statementId())) {
                    String value = d.iteration() == 0 ? "<m:add>" : "instance type TrieNode<URL>";
                    assertEquals(value, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                if ("1.0.1.0.0".equals(d.statementId())) {
                    assertEquals("null!=(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)", d.condition().toString());
                    assertEquals("baseDirectory, dirRelativeToBase, subDirs", d.conditionVariablesSorted());
                }
            }
            if ("addJmod".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    Optional<Map.Entry<Variable, Properties>> entry = d.statementAnalysis().propertiesFromSubAnalysers()
                            .filter(v -> v instanceof FieldReference fr && "LOGGER".equals(fr.fieldInfo.name))
                            .findFirst();
                    assertFalse(entry.isPresent());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("fqnToPath".equals(d.methodInfo().name)) {
                if ("parts[i]".equals(d.variableName())) {
                    if ("1.0.4".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<p:extension>" : "extension";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.3.0.2".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.3.0.2.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("name".equals(d.variableName())) {
                    if ("1.0.3.0.2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:getName>" : "file.getName()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.3.0.2.0.1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:getName>" : "file.getName()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("partsFromFile".equals(d.variableName())) {
                    if ("1.0.3.0.2.0.1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:split>" : "file.getName().split(\"\\\\.\")";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("subDirs".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = "(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.3.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:subDirs>"
                                : "(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.1.0.0.0.0".equals(d.statementId())) {
                        // NULLABLE or Content not null... see discussion in EvaluationResult
                        // going from CNN:13 to Nullable has been fixed using a delay on the ForEach condition in SASubBlocks
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "dirRelativeToBase".equals(pi.name)) {
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals("nullable instance type File", d.currentValue().toString());
                    }
                    if ("1.0.1.0.0.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type File", d.currentValue().toString());
                    }
                }
            }
            if ("addJmod".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "LOGGER".equals(fr.fieldInfo.name)) {
                    fail("Variable should not move from anonymous type to enclosing type");
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addDirectoryFromFileSystem".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        testSupportAndUtilClasses(List.of(Resources.class, Trie.class, Freezable.class),
                2, 12, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(), new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

}

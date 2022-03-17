
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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Util_08_Resources extends CommonTestRunner {

    public Test_Util_08_Resources() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        int BIG = 20;
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = "(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("1.0.1.0.0.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<m:recursivelyAddFiles>";
                        default -> "<no return value>";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-C";
                        case 1 -> "wait_for_assignment:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-E";
                        default -> "assign_to_field@Parameter_baseDirectory";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.3.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        default -> "<m:getPath>";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    String delays = switch (d.iteration()) {
                        case 0 -> "initial:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-C";
                        default -> "wait_for_assignment:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-E";
                    };
                    assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
                }
                if ("1.0.3.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "(new File(baseDirectory,dirRelativeToBase.getPath())).isDirectory()&&<null-check>&&<m:isEmpty>?new String[](0):<m:split>";
                        default -> "(new File(baseDirectory,dirRelativeToBase.getPath())).isDirectory()&&<m:isEmpty>&&null!=(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(!f.isDirectory())?new String[](0):<m:split>";
                        //      default -> "file.getName().endsWith(\".annotated_api\")&&0==<delayed array length>"; //FIXME 2 --> pathString
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("1.0.3.0.2.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<m:endsWith>&&0==<delayed array length>";
                        default -> "file.getName().endsWith(\".annotated_api\")&&0==<delayed array length>"; //FIXME 1 --> packageParts
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("1.0.3.0.2.0.1.0.2".equals(d.statementId())) {
                    assertEquals("<m:add>", d.evaluationResult().value().toString());
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial:files@Method_recursivelyAddFiles_1.0.3.0.2-C;initial:org.e2immu.analyser.util.Resources.LOGGER@Method_recursivelyAddFiles_1.0.3.0.2.0.1.0.1-C;initial:packageParts@Method_recursivelyAddFiles_1.0.3.0.2.0.1-C";
                        default -> "container:partsFromFile@Method_recursivelyAddFiles_1.0.3.0.2.0.1.0.1-E";
//FIXME                        default -> "";
                    };
                    assertEquals(expected, d.evaluationResult().causesOfDelay().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("fqnToPath".equals(d.methodInfo().name)) {
                if ("parts[i]".equals(d.variableName())) {
                    if ("1.0.4".equals(d.statementId())) {
                        assertEquals("extension", d.currentValue().toString());
                    }
                }
            }
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, BIG, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertDv(d, BIG, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.3.0.2".equals(d.statementId())) {
                        assertDv(d, BIG, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.3.0.2.0.1.0.2".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("name".equals(d.variableName())) {
                    if ("1.0.3.0.2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<m:getName>" : "file.getName()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.3.0.2.0.1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= BIG ? "<m:getName>" : "file.getName()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("partsFromFile".equals(d.variableName())) {
                    if ("1.0.3.0.2.0.1.0.0".equals(d.statementId())) {
                        assertEquals("<m:split>", d.currentValue().toString());
                    }
                }
                if ("subDirs".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "null==(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)?(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory):<vl:subDirs>"
                            : "(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)";
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.3.0.0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "dirRelativeToBase".equals(pi.name)) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "!<loopIsNotEmptyCondition>||null==(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)?nullable instance type File:<p:dirRelativeToBase>";
                            default -> "subDirs$1.0.1.0.0.length<=0||null==(new File(baseDirectory,dirRelativeToBase.getPath())).listFiles(File::isDirectory)?nullable instance type File:<p:dirRelativeToBase>";
                            // FIXME     default -> "";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String delays = d.iteration() == 0 ? "initial:dirRelativeToBase@Method_recursivelyAddFiles_1.0.1.0.0.0.0-E;initial:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-C"
                                : "wait_for_assignment:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-E";
                        assertEquals(delays, d.currentValue().causesOfDelay().toString());
                    }
                    if ("1.0.1.0.0.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            default -> "<p:dirRelativeToBase>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String delays = d.iteration() == 0 ? "initial:dirRelativeToBase@Method_recursivelyAddFiles_1.0.1.0.0.0.0-E;initial:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-C"
                                : "wait_for_assignment:subDirs@Method_recursivelyAddFiles_1.0.1.0.0-E"; // FIXME why does this not go away?
                        assertEquals(delays, d.currentValue().causesOfDelay().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addDirectoryFromFileSystem".equals(d.methodInfo().name)) {
                assertDv(d, BIG, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("recursivelyAddFiles".equals(d.methodInfo().name)) {
                assertDv(d, BIG, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        testSupportAndUtilClasses(List.of(Resources.class, Trie.class, Freezable.class),
                0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

}

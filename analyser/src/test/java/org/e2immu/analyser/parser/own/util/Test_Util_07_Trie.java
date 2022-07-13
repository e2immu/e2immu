
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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Util_07_Trie extends CommonTestRunner {

    public Test_Util_07_Trie() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name) && 2 == d.methodInfo().methodInspection.get().getParameters().size()) {
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<loopIsNotEmptyCondition>&&(<null-check>||<null-check>)?<vp::container@Class_TrieNode>:<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                        case 1 -> "-1-(instance type int)+upToPosition>=0&&(<simplification>||<null-check>)?null:-1-(instance type int)+upToPosition>=0?null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String):<f:root>";
                        default -> "-1-(instance type int)+upToPosition>=0&&(null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1.get(nullable instance type String)||null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1)?null:-1-(instance type int)+upToPosition>=0?null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String):root";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<f:root>" : "root";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                            case 1 -> "strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):<f:root>";
                            default -> "strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):root";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "node.map:-1,this.root:0";
                            default -> "node.map:2,this.root:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if (d.statementId().equals("2.0.1")) {
                        String value = switch (d.iteration()) {
                            case 0 -> "<vl:node>";
                            default -> "null==node$2.map$0?instance type TrieNode<T>:nullable instance type TrieNode<T>";
                        };
                        assertEquals(value, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "newTrieNode:-1,node.map:-1,s:-1,this.root:0";
                            default -> "newTrieNode:3,node.map:2,this.root:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                            case 1 -> "null==<f:node.data>?strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>:strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):<f:root>";
                            default -> "null==(strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):root).data$3?instance type TrieNode<T>:strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):root";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "strings.length>=1?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<f:root>";
                            default -> "null==(strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):root).data$3?instance type TrieNode<T>:strings.length>=1?null==node$2.map$0?new TrieNode<>():null==node$2.map$0.get(nullable instance type String)?new TrieNode<>():node$2.map$0.get(nullable instance type String):root";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);

                        String lvs = d.iteration() == 0 ? "data:-1,node.data:-1,node.map:-1,this.root:0"
                                : "data:4,node.data:2,node.map:2,this.root:0";
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("newTrieNode".equals(d.variableName())) {
                    if ("2.0.1.1.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:get>" : "node$2.map$0.get(s)";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "node.map:-1,node:-1,s:-1,this.root:-1";
                            default -> "node.map:3,node:3,this.root:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("4".equals(d.statementId())) {
                            assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                            String lvs = d.iteration() == 0 ? "data:-1,node.map:-1,node:-1,this.root:-1"
                                    : "data:4,node.map:2,node:2,this.root:2";
                            assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                        }
                    } else {
                        fail("Have scope " + fr.scope);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("2.0.1.1.0".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<f:map>" : "nullable instance type Map<String,TrieNode<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            String linked = switch (d.iteration()) {
                                case 0 -> "newTrieNode:-1,node:-1,s:-1,this.root:-1";
                                default -> "newTrieNode:3,node:2,this.root:2";
                            };
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    } else {
                        fail("Have scope " + fr.scope);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "data".equals(pi.name)) {
                    if ("4".equals(d.statementId())) {
                        String lvs = d.iteration() == 0 ? "node.data:-1,node.map:-1,node:-1,this.root:-1"
                                : "node.data:4,node.map:4,node:4,this.root:4";
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("goTo".equals(d.methodInfo().name) && 2 == d.methodInfo().methodInspection.get().getParameters().size()) {
                if ("node".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:get>";
                            default -> "null==node$1.map$0?node$1:node$1.map$0.get(strings[i])";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "<loopIsNotEmptyCondition>?<m:get>:<f:root>";
                            default -> "-1-(instance type int)+upToPosition>=0?null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String):root";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                        assertEquals("<return value>", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
            }
            if ("isStrictPrefix".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<m:goTo>" : "-1-(instance type int)+prefix.length>=0&&(null==``node`.map`.get(nullable instance type String)||null==``node`.map`)?null:-1-(instance type int)+prefix.length>=0?``node`.map`.get(nullable instance type String):`root`";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<null-check>&&<null-check>";
                            case 1 -> "<null-check>&&null==<f:node.data>";
                            default -> "null==(-1-(instance type int)+prefix.length>=0?``node`.map`.get(nullable instance type String):`root`).data$1&&(null!=``node`.map`.get(nullable instance type String)||instance type int>=prefix.length)&&(null!=``node`.map`||instance type int>=prefix.length)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name) && 2 == d.methodInfo().methodInspection.get().getParameters().size()) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "!<null-check>";
                        default -> "null!=node$1.map$0";
                    };
                    assertEquals(expected, d.state().toString());
                    String cm = switch (d.iteration()) {
                        case 0 -> "CM{condition=<loopIsNotEmptyCondition>;state=!<null-check>;parent=CM{condition=<loopIsNotEmptyCondition>;parent=CM{parent=CM{}}}}";
                        default -> "CM{condition=-1-i+upToPosition>=0;state=null!=node$1.map$0;parent=CM{condition=-1-i+upToPosition>=0;parent=CM{parent=CM{}}}}";
                    };
                    assertEquals(cm, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "!<null-check>";
                        default -> "true";
                    };
                    assertEquals(expected, d.state().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int params = d.methodInfo().methodInspection.get().getParameters().size();
            if ("goTo".equals(d.methodInfo().name) && params == 2) {
                String expected = d.iteration() <= 1 ? "<m:goTo>"
                        : "/*inline goTo*/-1-(instance type int)+upToPosition>=0&&(null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1.get(nullable instance type String)||null==(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1)?null:-1-(instance type int)+upToPosition>=0?null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String):root";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 2) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("(null==node$1.map$0?node$1:node$1.map$0.get(nullable instance type String)).map$1, node, node$1, node$1.map$0, root, this, upToPosition",
                                inlinedMethod.variablesOfExpressionSorted());
                    } else fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
                assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(1), 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);

                String eventual = switch (d.iteration()) {
                    case 0 -> "[DelayedEventual:initial@Class_Trie]";
                    case 1 -> "[DelayedEventual:final@Field_root]";
                    case 2 -> "[DelayedEventual:immutable@Class_TrieNode]";
                    case 3 -> "[DelayedEventual:cm@Parameter_strings;link:strings@Method_goTo_0:M]";
                    default -> "@Only before: [frozen]";
                };
                assertEquals(eventual, d.methodAnalysis().getEventual().toString());
                if (d.iteration() >= 4) {
                    assertEquals("Precondition[expression=!frozen, causes=[methodCall:ensureNotFrozen]]", d.methodAnalysis().getPrecondition().toString());
                    assertEquals("Precondition[expression=!frozen, causes=[methodCall:ensureNotFrozen]]", d.methodAnalysis().getPreconditionForEventual().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("data".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        testSupportAndUtilClasses(List.of(Trie.class, Freezable.class), 0, 0,
                new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}

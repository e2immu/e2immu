
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
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_07_Trie extends CommonTestRunner {

    public Test_Util_07_Trie() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("2.0.1".equals(d.statementId())) {
                String expected = switch (d.iteration()) {
                    case 0, 2 -> "<null-check>";
                    case 1 -> "null==<f:node.map>";
                    default -> "null==node$2.map$0";
                };
                assertEquals(expected, d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<f:root>" : "root";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                            case 1 -> "strings.length>0?null==<f:node.map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                            case 2 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:nullable instance type TrieNode<T>";
                            default -> "strings.length>0?null==node$2.map$0?new TrieNode<>():strings.length>0&&null!=node$2.map$0&&null==node$2.map$0.get(instance type String)?new TrieNode<>():node$2.map$0.get(instance type String):nullable instance type TrieNode<T>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "node.map:-1,node:0,this.root:-1";
                            case 2 -> "node$2.map:-1,node.map:-1,node:0,this.root:-1";
                            default -> "node$2.map:2,node.map:2,node:0,this.root:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if (d.statementId().equals("2.0.1")) {
                        String value = d.iteration() <= 2 ? "<vl:node>" : "nullable instance type TrieNode<T>";
                        assertEquals(value, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "newTrieNode:-1,node.map:-1,node:0,this.root:-1";
                            case 2 -> "newTrieNode:-1,node$2.map:-1,node.map:-1,node:0,this.root:-1";
                            default -> "newTrieNode:3,node$2.map:2,node.map:2,node:0,this.root:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    String expected = switch (d.iteration()) {
                        case 0 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        case 1 -> "strings.length>0?null==<f:node.map>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:<vl:node>";
                        case 2, 3 -> "strings.length>0?<null-check>?<new:TrieNode<T>>:<null-check>?<new:TrieNode<T>>:<m:get>:nullable instance type TrieNode<T>";
                        default -> "strings.length>0?null==node$2.map$0?new TrieNode<>():strings.length>0&&null!=node$2.map$0&&null==node$2.map$0.get(instance type String)?new TrieNode<>():node$2.map$0.get(instance type String):nullable instance type TrieNode<T>";
                    };
                    if ("3".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("newTrieNode".equals(d.variableName())) {
                    if ("2.0.1.1.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<m:get>" : "node$2.map$0.get(s)";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0, 1 -> "newTrieNode:0,node.map:-1,node:-1,this.root:-1";
                            case 2 -> "newTrieNode:0,node$2.map:-1,node.map:-1,node:-1,this.root:-1";
                            default -> "newTrieNode:0,node$2.map:3,node.map:3,node:3,this.root:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "data".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("4".equals(d.statementId())) {
                            assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        }
                    } else if ("strings.length>0?null==node$2.map$0?new TrieNode<>():strings.length>0&&null!=node$2.map$0&&null==node$2.map$0.get(instance type String)?new TrieNode<>():node$2.map$0.get(instance type String):nullable instance type TrieNode<T>".equals(fr.scope.toString())) {
                        assertTrue(d.iteration() > 0);
                    } else {
                        fail("Have scope " + fr.scope);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    if ("node".equals(fr.scope.toString())) {
                        if ("2.0.1.1.0".equals(d.statementId())) {
                            String expected = d.iteration() <= 2 ? "<f:map>" : "nullable instance type Map<String,TrieNode<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            String linked = switch (d.iteration()) {
                                case 0, 1 -> "newTrieNode:-1,node.map:0,node:-1,this.root:-1";
                                case 2 -> "newTrieNode:-1,node$2.map:2,node.map:0,node:2,this.root:2";
                                default -> "newTrieNode:3,node$2.map:2,node.map:0,node:2,this.root:2";
                            };
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    } else if ("node$2".equals(fr.scope.toString())) {
                        if ("2.0.1.1.0".equals(d.statementId())) {
                            String expected = d.iteration() <= 2 ? "<f:node$2.map>" : "nullable instance type Map<String,TrieNode<T>>";
                            assertEquals(expected, d.currentValue().toString());
                            String linked = switch (d.iteration()) {
                                case 0, 1 -> "node$2.map:0";
                                case 2 -> "newTrieNode:-1,node$2.map:0,node.map:-1,node:2,this.root:2";
                                default -> "newTrieNode:3,node$2.map:0,node.map:2,node:2,this.root:2";
                            };
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    } else {
                        fail("Have scope " + fr.scope);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "root".equals(fr.fieldInfo.name)) {
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 3, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("goTo".equals(d.methodInfo().name) && 2 == d.methodInfo().methodInspection.get().getParameters().size()) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 2 -> "!<null-check>";
                        case 1 -> "null!=<f:node.map>";
                        default -> "null!=node$1.map$0";
                    };
                    assertEquals(expected, d.state().toString());
                    String cm = switch (d.iteration()) {
                        case 0 -> "CM{condition=<loopIsNotEmptyCondition>;state=!<null-check>;parent=CM{condition=<loopIsNotEmptyCondition>;parent=CM{parent=CM{}}}}";
                        case 1 -> "CM{condition=upToPosition>i;state=null!=<f:node.map>;parent=CM{condition=upToPosition>i;parent=CM{parent=CM{}}}}";
                        case 2 -> "CM{condition=upToPosition>i;state=!<null-check>;parent=CM{condition=upToPosition>i;parent=CM{parent=CM{}}}}";
                        default -> "CM{condition=upToPosition>i;state=null!=node$1.map$0;parent=CM{condition=upToPosition>i;parent=CM{parent=CM{}}}}";
                    };
                    assertEquals(cm, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 2 -> "!<null-check>";
                        case 1 -> "<simplification>";
                        default -> "true";
                    };
                    assertEquals(expected, d.state().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int params = d.methodInfo().methodInspection.get().getParameters().size();
            if ("goTo".equals(d.methodInfo().name) && params == 2) {
                String expected = d.iteration() <= 4 ? "<m:goTo>" : "upToPosition>instance type int&&(null==upToPosition>instance type int&&null==node$1.map$0?node$1:node$1.map$0.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i]).map$1.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i])||null==upToPosition>instance type int&&null==node$1.map$0?node$1:node$1.map$0.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i]).map$1)?null:upToPosition>instance type int&&(instance type int>=upToPosition||null!=upToPosition>instance type int&&null==node$1.map$0?node$1:node$1.map$0.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i]).map$1.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i]))&&(instance type int>=upToPosition||null!=upToPosition>instance type int&&null==node$1.map$0?node$1:node$1.map$0.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i]).map$1)?null==node$1.map$0?node$1:node$1.map$0.get(org.e2immu.analyser.util.Trie.goTo(java.lang.String[],int):0:strings[i]):nullable instance type TrieNode<T>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 5) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals("i, node$1, strings, upToPosition",
                                inlinedMethod.getVariablesOfExpression().stream().map(Object::toString).sorted().collect(Collectors.joining(", ")));
                    } else fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
            }
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };

        testSupportAndUtilClasses(List.of(Trie.class, Freezable.class), 0, 7,
                new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}

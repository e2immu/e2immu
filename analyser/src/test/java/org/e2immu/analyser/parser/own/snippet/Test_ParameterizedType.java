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

package org.e2immu.analyser.parser.own.snippet;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_ParameterizedType extends CommonTestRunner {

    public Test_ParameterizedType() {
        super(true);
    }

    // IMPROVE for this test, it is fortunate that List.of().isEmpty() doesn't go to FALSE

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("4.0.1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:isEmpty>" : "List.of().isEmpty()";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("4.0.0".equals(d.statementId())) {
                    String reached = d.iteration() == 0
                            ? "initial:from.typeInfo@Method_targetIsATypeParameter_3-C;initial:from@Method_targetIsATypeParameter_3-E"
                            : "CONDITIONALLY:1";
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                    String absolute = d.iteration() == 0
                            ? "<null-check>&&<null-check>&&!<m:isEmpty>&&!<null-check>"
                            : "!List.of().isEmpty()&&null==from.typeInfo$0&&null!=from.typeParameter$0&&null!=target.typeParameter$0";
                    assertEquals(absolute, d.absoluteState().toString());
                    assertEquals(absolute, d.conditionManagerForNextStatement().absoluteState(d.context()).toString());
                }
                if ("4.0.1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:isEmpty>" : "List.of().isEmpty()";
                    assertEquals(expected, d.condition().toString());
                    String reached = switch (d.iteration()) {
                        case 0 -> "initial:from.typeInfo@Method_targetIsATypeParameter_3-C;initial:from@Method_targetIsATypeParameter_3-E;initial:target.typeParameter@Method_targetIsATypeParameter_0-C";
                        case 1 -> "CONDITIONALLY:1"; // should be a delay
                        default -> "never reaches this point";
                    };
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
                if ("4.0.1".equals(d.statementId())) {
                    String reached = switch (d.iteration()) {
                        case 0 -> "initial:from.typeInfo@Method_targetIsATypeParameter_3-C;initial:from@Method_targetIsATypeParameter_3-E";
                        case 1, 2 -> "CONDITIONALLY:1";
                        default -> throw new UnsupportedOperationException();
                    };
                    assertEquals(reached, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:isEmpty>?<s:int>:<return value>" : "List.of().isEmpty()?5:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<null-check>?<s:int>:<m:isEmpty>?<s:int>:<return value>"
                                : "null==from.typeInfo$0?List.of().isEmpty()?5:<return value>:6";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("4.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:isEmpty>?7:<return value>"
                                : "<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("4.0.4".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<m:isEmpty>?7:8+(<loopIsNotEmptyCondition>?<loopIsNotEmptyCondition>&&-1+<v:min>>=<m:size>?<m:size>:<vl:min>:<f:MAX_VALUE>)"
                                : "8+(List.of().isEmpty()?2147483647:fromTypeBounds$4.0.3.isEmpty()||`otherBound.typeInfo`.length()>=instance type int?instance type int:`otherBound.typeInfo`.length())";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("fromTypeBounds".equals(d.variableName())) {
                    assertNotEquals("4", d.statementId(), "Variable should not exist here!");
                    if ("4.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:List<ParameterizedType>>" : "List.of()";// FIXME companion state data?
                        assertEquals(expected, d.currentValue().toString());
                    }
                    // Note: the variable may exist in "5", as part of the evaluation of the return variable
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable lv) {
                        if ("4.0.4".equals(d.statementId())) {
                            assertEquals("4", lv.parentBlockIndex);
                            String expected = d.iteration() == 0
                                    ? "<loopIsNotEmptyCondition>?<vl:fromTypeBounds>:<s:List<ParameterizedType>>" : "List.of()";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                        if (d.statementId().startsWith("4.0.3.0.0.0.")) {
                            assertEquals("4.0.3.0.0", outside.statementIndex(), "In " + d.statementId());
                        } else if (d.statementId().equals("4.0.3.0.0")) {
                            assertEquals("4.0.3", outside.statementIndex(), "In " + d.statementId());
                        } else {
                            assertEquals("4.0.3", outside.statementIndex(), "In " + d.statementId());
                            assertTrue(outside.previousVariableNature() instanceof VariableNature.NormalLocalVariable);
                            assertTrue(d.statementId().startsWith("4.0.3"));
                        }
                    } else fail();
                }
                if ("myBound".equals(d.variableName())) {
                    if ("4.0.3".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<vl:myBound>" : "instance type ParameterizedType";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ParameterizedType".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        testClass("ParameterizedType_0", 6, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    // quasi identical to test_9, but with "private" on the typeParameter field
    @Test
    public void test_1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<null-check>" : "false";
                    assertEquals(expected, d.state().toString());
                    DV dv = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
                    String delay = d.iteration() == 0 ? "initial_flow_value@Method_targetIsATypeParameter_1-C" : "NEVER:0";
                    assertEquals(delay, dv.toString());
                    assertEquals(d.iteration() == 0, dv.isDelayed());
                }
            }
        };
        // TODO 6 errors is too many
        testClass("ParameterizedType_1", 6, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    // important: there is an internal call cycle from "from" to "normalType" to "iterativelyParseTypes" back to "from"
    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                if ("0.0.07".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<f:CHAR_L>==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<v:firstChar>==<f:MINUS_SUPER>||<v:firstChar>==<f:PLUS_EXTENDS>?signature.charAt(1):<v:firstChar>)"
                            : "'L'==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0))";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("0.0.11".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "(<v:firstChar>==<f:ARRAY_BRACKET>?1+<v:arrays>:<s:int>)>=1?<s:Result>:<f:TYPE_PARAM_T>==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<v:firstChar>==<f:MINUS_SUPER>||<v:firstChar>==<f:PLUS_EXTENDS>?<m:charAt>:<v:firstChar>)?<null-check>?<new:Result>:<new:Result>:<f:CHAR_L>==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<v:firstChar>==<f:MINUS_SUPER>||<v:firstChar>==<f:PLUS_EXTENDS>?<m:charAt>:<v:firstChar>)?<m:normalType>:<f:WILDCARD_STAR>==<m:charAt>?<new:Result>:<new:Result>";
                        case 1 -> "('['==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<v:firstChar>==<f:MINUS_SUPER>||<v:firstChar>==<f:PLUS_EXTENDS>?signature.charAt(1):<v:firstChar>)?1+('['==firstChar$0.0.06?1+arrays$0.0.06:0):0)>=1?<new:Result>:([signature.charAt(0),signature.charAt(1),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,<v:firstChar>,firstChar$0.0.06,<f:ARRAY_BRACKET>,<f:MINUS_SUPER>,<f:PLUS_EXTENDS>,<m:charAt>,<too complex>])?instance type boolean?<new:Result>:<new:Result>:([signature.charAt(0),signature.charAt(1),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,<v:firstChar>,firstChar$0.0.06,<f:ARRAY_BRACKET>,<f:MINUS_SUPER>,<f:PLUS_EXTENDS>,<too complex>,<m:charAt>,<too complex>])?<m:normalType>:'*'==signature.charAt(0)?<new:Result>:<new:Result>";
                        case 2 -> "('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0))?1+('['==firstChar$0.0.06?1+arrays$0.0.06:0):0)>=1?<new:Result>:([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?instance type boolean?<new:Result>:<new:Result>:([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?<m:normalType>:'*'==signature.charAt(0)?<new:Result>:new Result(primitivePt,1,false)";
                        case 3 -> "('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0))?1+('['==firstChar$0.0.06?1+arrays$0.0.06:0):0)>=1?new Result(new ParameterizedType(primitivePt.typeInfo,arrays),arrays+1,false):([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?instance type boolean?<new:Result>:<new:Result>:([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?<m:normalType>:'*'==signature.charAt(0)?new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE,1,false):new Result(primitivePt,1,false)";
                        case 4, 5, 6, 7, 8, 9, 10, 11 -> "('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0))?1+('['==firstChar$0.0.06?1+arrays$0.0.06:0):0)>=1?new Result(new ParameterizedType(primitivePt.typeInfo,arrays),arrays+1,false):([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?instance type boolean?new Result(typeContext.getPrimitives().objectParameterizedType(),signature.indexOf(';')+1,true):new Result(new ParameterizedType((TypeParameter)typeContext.get(signature.substring(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0),signature.indexOf(';')),false),arrays,wildCard),signature.indexOf(';')+1,false):([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?<m:normalType>:'*'==signature.charAt(0)?new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE,1,false):new Result(primitivePt,1,false)";
                        default -> "('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0))?1+('['==firstChar$0.0.06?1+arrays$0.0.06:0):0)>=1?new Result(new ParameterizedType(primitivePt.typeInfo,arrays),arrays+1,false):([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?instance type boolean?new Result(typeContext.getPrimitives().objectParameterizedType(),signature.indexOf(';')+1,true):new Result(new ParameterizedType((TypeParameter)typeContext.get(signature.substring(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0),signature.indexOf(';')),false),arrays,wildCard),signature.indexOf(';')+1,false):([signature.charAt(0),signature.charAt(1),signature.charAt(1+firstCharPos$0.0.06),signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)),arrays$0.0.06,firstChar$0.0.06,instance type boolean])?instance type boolean?null:new Result(`parameterizedType`,`semiColon`+1,`typeNotFoundError`):'*'==signature.charAt(0)?new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE,1,false):new Result(primitivePt,1,false)";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 11, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
            if ("iterativelyParseTypes".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() < 13 ? "<m:from>"
                            : "ParameterizedType_2.from(typeContext,findType,signature.substring(0))";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() < 14, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
            if ("normalType".equals(d.methodInfo().name)) {
                // call to iterativelyParseTypes
                if ("06.0.5.0.3.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.status().isDelayed());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                // in iteration 4, the method call in 0.0.07.0.0 should not cause problems in itself (@NM, parameters @NM)
                // it uses the variables typeContext (P), findType (P), signature (P), arrays (LVR), wildCard (LVR), firstCharPos (LVR)
                if (d.variable() instanceof ParameterInfo pi && "findType".equals(pi.name)) {
                    if ("0.0.06".equals(d.statementId())) {
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07".equals(d.statementId())) {
                        assertDv(d, 13, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07.0.0".equals(d.statementId())) {
                        // is used for the first time in this method in 0.0.07.0.0
                        assertDv(d, 13, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("wildCard".equals(d.variableName())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<vl:wildCard>";
                        case 2, 3 -> "'+'==signature.charAt(0)?<vp:EXTENDS:cm@Parameter_name>:'-'==signature.charAt(0)?<vp:SUPER:cm@Parameter_name>:<vp:NONE:cm@Parameter_name>";
                        default -> "'+'==signature.charAt(0)?WildCard.EXTENDS:'-'==signature.charAt(0)?WildCard.SUPER:WildCard.NONE";
                    };
                    if ("0.0.06".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().toString());
                        if (d.iteration() > 0) {
                            assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        assertDv(d, 13, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07.0.0".equals(d.statementId())) {
                        String expected2 = switch (d.iteration()) {
                            case 0, 1, 2, 3 -> "<vl:wildCard>";
                            case 4, 5, 6, 7, 8, 9, 10, 11, 12 -> "<mod:WildCard>";
                            default -> "'+'==signature.charAt(0)?WildCard.EXTENDS:'-'==signature.charAt(0)?WildCard.SUPER:WildCard.NONE";
                        };
                        assertEquals(expected2, d.currentValue().toString());
                        assertDv(d, 13, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("0.0.07.0.0".equals(d.statementId())) {
                    if (d.variable() instanceof ParameterInfo pi && "typeContext".equals(pi.name)) {
                        assertDv(d, 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if (d.variable() instanceof ParameterInfo pi && "signature".equals(pi.name)) {
                        assertDv(d, 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("arrays".equals(d.variableName())) {
                        assertDv(d, 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("firstCharPos".equals(d.variableName())) {
                        assertDv(d, 6, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("primitivePt".equals(d.variableName())) {
                    if ("0.0.09".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:ParameterizedType>";
                            case 1 -> "<m:primitive>";
                            default -> "switch('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0))?signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:'+'==signature.charAt(0)||'-'==signature.charAt(0)?1:0)):'+'==signature.charAt(0)||'-'==signature.charAt(0)?signature.charAt(1):signature.charAt(0)){'B'->typeContext.getPrimitives().byteParameterizedType();'C'->typeContext.getPrimitives().charParameterizedType();default->throw new RuntimeException(\"Char \"+firstChar+\" does NOT represent a primitive!\");}";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "ARRAY_BRACKET".equals(fr.fieldInfo.name)) {
                    assertNotEquals("0.0.05", d.statementId());
                    if (d.statementId().compareTo("0.0.11") < 0 && d.statementId().compareTo("0.0.") > 0) {
                        String expected = d.iteration() == 0 ? "<f:ARRAY_BRACKET>" : "'['";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0.0.11".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:ARRAY_BRACKET>" : "'['";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("iterativelyParseTypes".equals(d.methodInfo().name)) {
                if ("next".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    String expectedIn5 = d.iteration() == 0 ? "<s:IterativeParsing>" : "instance type IterativeParsing";

                    if ("5".equals(d.statementId())) {
                        String merge = switch (d.iteration()) {
                            case 0 -> "<s:IterativeParsing>";
                            case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 -> "'>'==<m:charAt>?instance type IterativeParsing:instance type IterativeParsing";
                            default -> "'>'==signature.charAt((ParameterizedType_2.from(typeContext,findType,signature.substring(0))).nextPos)?instance type IterativeParsing:instance type IterativeParsing";
                        };
                        assertEquals(merge, d.currentValue().toString());
                        assertEquals(d.iteration() < 14, d.currentValue().isDelayed());
                        assertDv(d, 14, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("5.0.1".equals(d.statementId())) {
                        assertEquals(expectedIn5, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("5.1.1".equals(d.statementId())) {
                        assertEquals(expectedIn5, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("6".equals(d.statementId())) {
                        String expected = d.iteration() < 14 ? "<s:IterativeParsing>" : "instance type IterativeParsing";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 14, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            // iterativelyParseTypes, normalType, from are in this order of processing
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("iterativelyParseTypes".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertEquals("from, iterativelyParseTypes, normalType", methodResolution.callCycleSorted());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d, 13, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 13, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, 14, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d.p(0), 14, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 15, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 15, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), 15, DV.FALSE_DV, Property.MODIFIED_VARIABLE);

                String expected = d.iteration() < 14 ? "<m:iterativelyParseTypes>" : "/*inline iterativelyParseTypes*/next";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("normalType".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertEquals("from, iterativelyParseTypes, normalType", methodResolution.callCycleSorted());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d, 13, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 2, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d.p(0), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 12, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), 0, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(4), 12, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(5), 0, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("from".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertEquals("from, iterativelyParseTypes, normalType", methodResolution.callCycleSorted());
                // ignoreMe... means that the "from" call in iterativelyParseTypes cannot cause delays
                // the order of resolution should therefore be "iterativelyParseTypes", then "normalType", then "from"
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d, 14, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 13, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d.p(1), 14, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() < 13 ? "<m:from>" : "<undetermined return value>"; // too complex for inline
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d, 13, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 13, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("primitive".equals(d.methodInfo().name)) {
                assertFalse(methodResolution.partOfCallCycle());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() <= 1 ? "<m:primitive>"
                        : "/*inline primitive*/switch(firstChar){'B'->primitives.byteParameterizedType();'C'->primitives.charParameterizedType();default->throw new RuntimeException(\"Char \"+firstChar+\" does NOT represent a primitive!\");}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("getPrimitives".equals(d.methodInfo().name)) {
                assertFalse(methodResolution.partOfCallCycle());
            }
            if ("charParameterizedType".equals(d.methodInfo().name)) {
                assertFalse(methodResolution.partOfCallCycle());
            }
            if ("get".equals(d.methodInfo().name)) {
                assertEquals("TypeContext", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ParameterizedType_2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 13, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
            if ("IterativeParsing".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Result".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
                assertEquals("Type org.e2immu.analyser.parser.own.snippet.testexample.ParameterizedType_2.ParameterizedType",
                        d.typeAnalysis().getTransparentTypes().toString());
                assertDv(d, 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("NamedType".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        // TODO one of the null pointer problems is that "from" is nullable, which it is clearly not
        // the other 3 null pointer issues are valid
        testClass("ParameterizedType_2", 4, DONT_CARE,
                new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_2_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ParameterizedType_2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 21, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("IterativeParsing".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Result".equals(d.typeInfo().simpleName)) {
                assertDv(d, 16, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("ParameterizedType_2", 2, DONT_CARE,
                new DebugConfiguration.Builder()
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("ParameterizedType_3", 2, 7,
                new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("normalType".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "findType".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type FindType/*@Identity*/", d.currentValue().toString());
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "arrays".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "wildCard".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("nullable instance type WildCard", d.currentValue().toString());
                    }
                }
                if ("typeInfo".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:TypeInfo:container@Record_TypeInfo>";
                            case 1 -> "<vp:TypeInfo:cm@Parameter_fqn;mom@Parameter_fqn>";
                            default -> "findType.find(path.toString()/*@NotNull 0==this.length()*/.replaceAll(\"[/$]\",\".\"),path.toString()/*@NotNull 0==this.length()*/)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("parameterizedType".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<new:ParameterizedType>"
                                : "new ParameterizedType(typeInfo,arrays,wildCard,typeParameters)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("ParameterizedType".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 0, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("find".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ParameterizedType".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TypeInfo".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        testClass("ParameterizedType_4", 1, 3,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build());
    }

}

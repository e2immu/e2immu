
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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_04_Precondition_7plus extends CommonTestRunner {

    public static final String X_EQUALS = "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)";

    public Test_04_Precondition_7plus() {
        super(true);
    }

    // important: there is an internal call cycle from "from" to "normalType" to "iterativelyParseTypes" back to "from"
    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                if ("0.0.07".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<f:CHAR_L>==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<vl:firstChar>)"
                            : "'L'==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):instance type char)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("0.0.11".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "(<v:firstChar>==<f:ARRAY_BRACKET>?1+<v:arrays>:<vl:arrays>)>=1?<s:Result>:<f:TYPE_PARAM_T>==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<vl:firstChar>)?<null-check>?<new:Result>:<new:Result>:<f:CHAR_L>==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<vl:firstChar>)?<m:normalType>:<f:WILDCARD_STAR>==<m:charAt>?<new:Result>:<new:Result>";
                        case 1 -> "('['==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<vl:firstChar>)?1+('['==firstChar$0.0.06?1+arrays$0.0.06:instance type int):instance type int)>=1?<new:Result>:'T'==('['==(<v:firstChar>==<f:ARRAY_BRACKET>?<m:charAt>:<vl:firstChar>)?signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:instance type int)):instance type char)?<null-check>?<new:Result>:<new:Result>:([arrays$0.0.06,<v:firstChar>,firstChar$0.0.06,firstCharPos$0.0.06,<f:ARRAY_BRACKET>,signature,<too complex>])?<m:normalType>:'*'==signature.charAt(0)?<new:Result>:<new:Result>";
                        case 2 -> "('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):instance type char)?1+('['==firstChar$0.0.06?1+arrays$0.0.06:instance type int):instance type int)>=1?<new:Result>:'T'==('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):instance type char)?signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:instance type int)):instance type char)?([arrays$0.0.06,firstChar$0.0.06,firstCharPos$0.0.06,typeContext,signature,instance type boolean])?<new:Result>:<new:Result>:([arrays$0.0.06,firstChar$0.0.06,firstCharPos$0.0.06,signature,instance type boolean])?<m:normalType>:'*'==signature.charAt(0)?<new:Result>:new Result(primitivePt,1,false)";
                        default -> "('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):instance type char)?1+('['==firstChar$0.0.06?1+arrays$0.0.06:instance type int):instance type int)>=1?new Result(new ParameterizedType(primitivePt.typeInfo,arrays),arrays+1,false):'T'==('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):instance type char)?signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:instance type int)):instance type char)?([arrays$0.0.06,firstChar$0.0.06,firstCharPos$0.0.06,typeContext,signature,instance type boolean])?new Result(typeContext.getPrimitives().objectParameterizedType(),signature.indexOf(';')+1,true):new Result(new ParameterizedType((TypeParameter)typeContext.get(signature.substring(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:instance type int),signature.indexOf(';')),false),arrays,wildCard),signature.indexOf(';')+1,false):([arrays$0.0.06,firstChar$0.0.06,firstCharPos$0.0.06,signature,instance type boolean])?instance type boolean?null:new Result(`parameterizedType`,`semiColon`+1,`typeNotFoundError`):'*'==signature.charAt(0)?new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE,1,false):new Result(primitivePt,1,false)";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 2, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
            if ("iterativelyParseTypes".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() <= 3 ? "<m:from>"
                            : "('['==('['==('['==instance type char?signature.substring(0).charAt(1+instance type int):instance type char)?signature.substring(0).charAt(1+('['==instance type char?1+instance type int:instance type int)):instance type char)?1+('['==('['==instance type char?signature.substring(0).charAt(1+instance type int):instance type char)?1+('['==instance type char?1+instance type int:instance type int):instance type int):instance type int)>=1?new Result(new ParameterizedType(``scope-primitivePt:0`.typeInfo`,'['==instance type char?1+instance type int:instance type int),('['==instance type char?1+instance type int:instance type int)+1,false):([signature,instance type boolean])?instance type boolean?new Result(typeContext.getPrimitives().objectParameterizedType(),signature.substring(0).indexOf(';')+1,true):new Result(new ParameterizedType((TypeParameter)typeContext.get(signature.substring(0).substring(1+('['==('['==instance type char?signature.substring(0).charAt(1+instance type int):instance type char)?1+('['==instance type char?1+instance type int:instance type int):instance type int),signature.substring(0).indexOf(';')),false),'['==instance type char?1+instance type int:instance type int,'+'==signature.substring(0).charAt(0)?`WildCard.EXTENDS`:'-'==signature.substring(0).charAt(0)?`WildCard.SUPER`:`WildCard.NONE`),signature.substring(0).indexOf(';')+1,false):instance type boolean?instance type boolean?null:new Result(`parameterizedType`,`semiColon`+1,`typeNotFoundError`):'*'==signature.substring(0).charAt(0)?new Result(`ParameterizedType.WILDCARD_PARAMETERIZED_TYPE`,1,false):new Result(switch('['==('['==instance type char?signature.substring(0).charAt(1+instance type int):instance type char)?signature.substring(0).charAt(1+('['==instance type char?1+instance type int:instance type int)):instance type char){'B'->typeContext.getPrimitives().byteParameterizedType();'C'->typeContext.getPrimitives().charParameterizedType();default->throw new RuntimeException(\"Char \"+firstChar+\" does NOT represent a primitive!\");},1,false)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() <= 4, d.evaluationResult().causesOfDelay().isDelayed());
                }
            }
            if ("normalType".equals(d.methodInfo().name)) {
                // call to iterativelyParseTypes
                if ("06.0.5.0.3.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1, d.status().isDelayed());
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
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07.0.0".equals(d.statementId())) {
                        // is used for the first time in this method in 0.0.07.0.0
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("wildCard".equals(d.variableName())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<vl:wildCard>";
                        case 1 -> "'+'==signature.charAt(0)?<vp:EXTENDS:container@Enum_WildCard>:'-'==signature.charAt(0)?<vp:SUPER:container@Enum_WildCard>:<vp:NONE:container@Enum_WildCard>";
                        case 2 -> "'+'==signature.charAt(0)?<vp:EXTENDS:cm@Parameter_name>:'-'==signature.charAt(0)?<vp:SUPER:cm@Parameter_name>:<vp:NONE:cm@Parameter_name>";
                        default -> "'+'==signature.charAt(0)?WildCard.EXTENDS:'-'==signature.charAt(0)?WildCard.SUPER:WildCard.NONE";
                    };
                    if ("0.0.06".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().toString());
                        assertEquals(DV.FALSE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));

                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.07.0.0".equals(d.statementId())) {
                        String expected2 = switch (d.iteration()) {
                            case 0, 1, 2 -> "<vl:wildCard>";
                            default -> "'+'==signature.charAt(0)?WildCard.EXTENDS:'-'==signature.charAt(0)?WildCard.SUPER:WildCard.NONE";
                        };
                        assertEquals(expected2, d.currentValue().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("0.0.07.0.0".equals(d.statementId())) {
                    if (d.variable() instanceof ParameterInfo pi && "typeContext".equals(pi.name)) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if (d.variable() instanceof ParameterInfo pi && "signature".equals(pi.name)) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("arrays".equals(d.variableName())) {
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("firstCharPos".equals(d.variableName())) {
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("primitivePt".equals(d.variableName())) {
                    if ("0.0.09".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:ParameterizedType>";
                            case 1 -> "<m:primitive>";
                            default -> "switch('['==('['==firstChar$0.0.06?signature.charAt(1+firstCharPos$0.0.06):instance type char)?signature.charAt(1+('['==firstChar$0.0.06?1+firstCharPos$0.0.06:instance type int)):instance type char){'B'->typeContext.getPrimitives().byteParameterizedType();'C'->typeContext.getPrimitives().charParameterizedType();default->throw new RuntimeException(\"Char \"+firstChar+\" does NOT represent a primitive!\");}";
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
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            // iterativelyParseTypes, normalType, from are in this order of processing
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("iterativelyParseTypes".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertEquals("from, iterativelyParseTypes, normalType", methodResolution.callCycleSorted());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 4, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, 5, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d.p(0), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 6, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);

                String expected = d.iteration() <= 4 ? "<m:iterativelyParseTypes>" : "/*inline iterativelyParseTypes*/new IterativeParsing()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("normalType".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertEquals("from, iterativelyParseTypes, normalType", methodResolution.callCycleSorted());
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 0, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), 0, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(4), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(5), 0, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("from".equals(d.methodInfo().name)) {
                assertTrue(methodResolution.partOfCallCycle());
                assertEquals("from, iterativelyParseTypes, normalType", methodResolution.callCycleSorted());
                // ignoreMe... means that the "from" call in iterativelyParseTypes cannot cause delays
                // the order of resolution should therefore be "iterativelyParseTypes", then "normalType", then "from"
                assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, 3, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d.p(1), 4, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() <= 2 ? "<m:from>"
                        : "/*inline from*/('['==('['==('['==instance type char?signature.charAt(1+instance type int):instance type char)?signature.charAt(1+('['==instance type char?1+instance type int:instance type int)):instance type char)?1+('['==('['==instance type char?signature.charAt(1+instance type int):instance type char)?1+('['==instance type char?1+instance type int:instance type int):instance type int):instance type int)>=1?new Result(new ParameterizedType(scope-primitivePt:0.typeInfo,'['==instance type char?1+instance type int:instance type int),('['==instance type char?1+instance type int:instance type int)+1,false):'T'==('['==('['==('['==instance type char?signature.charAt(1+instance type int):instance type char)?signature.charAt(1+('['==instance type char?1+instance type int:instance type int)):instance type char)?signature.charAt(1+('['==('['==instance type char?signature.charAt(1+instance type int):instance type char)?1+('['==instance type char?1+instance type int:instance type int):instance type int)):instance type char)?(['['==instance type char?1+instance type int:instance type int,'['==instance type char?signature.charAt(1+instance type int):instance type char,'['==instance type char?1+instance type int:instance type int,typeContext,signature,instance type boolean])?new Result(typeContext.getPrimitives().objectParameterizedType(),signature.indexOf(';')+1,true):new Result(new ParameterizedType((TypeParameter)typeContext.get(signature.substring(1+('['==('['==instance type char?signature.charAt(1+instance type int):instance type char)?1+('['==instance type char?1+instance type int:instance type int):instance type int),signature.indexOf(';')),false),'['==instance type char?1+instance type int:instance type int,'+'==signature.charAt(0)?WildCard.EXTENDS:'-'==signature.charAt(0)?WildCard.SUPER:WildCard.NONE),signature.indexOf(';')+1,false):(['['==instance type char?1+instance type int:instance type int,'['==instance type char?signature.charAt(1+instance type int):instance type char,'['==instance type char?1+instance type int:instance type int,signature,instance type boolean])?instance type boolean?null:new Result(`parameterizedType`,`semiColon`+1,`typeNotFoundError`):'*'==signature.charAt(0)?new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE,1,false):new Result(switch('['==('['==instance type char?signature.charAt(1+instance type int):instance type char)?signature.charAt(1+('['==instance type char?1+instance type int:instance type int)):instance type char){'B'->typeContext.getPrimitives().byteParameterizedType();'C'->typeContext.getPrimitives().charParameterizedType();default->throw new RuntimeException(\"Char \"+firstChar+\" does NOT represent a primitive!\");},1,false)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
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
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Precondition_7".equals(d.typeInfo().simpleName)) {
                assertDv(d, 4, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                TypeAnalysisImpl.Builder builder = (TypeAnalysisImpl.Builder) d.typeAnalysis();
                assertEquals(1, builder.nonModifiedCountForMethodCallCycle.size());
                Set<MethodInfo> methodsInCycle = builder.nonModifiedCountForMethodCallCycle.stream().map(Map.Entry::getKey).findFirst().orElseThrow();
                assertEquals(3, methodsInCycle.size());
                TypeAnalysisImpl.CycleInfo cycleInfo = builder.nonModifiedCountForMethodCallCycle.stream().map(Map.Entry::getValue).findFirst().orElseThrow();
                String expected = switch (d.iteration()) {
                    case 0, 1, 2 -> "normalType";
                    case 3 -> "from, normalType";
                    default -> "from, iterativelyParseTypes, normalType";
                };
                assertEquals(expected, cycleInfo.nonModified.stream().map(MethodInfo::name).sorted().collect(Collectors.joining(", ")));
            }
        };

        // TODO one of the null pointer problems is that "from" is nullable, which it is clearly not
        // the other 3 null pointer issues are valid
        testClass("Precondition_7", 6, 13,
                new DebugConfiguration.Builder()
                      //  .addEvaluationResultVisitor(evaluationResultVisitor)
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                      //  .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                      //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_7_1() throws IOException {
        testClass("Precondition_7_1", 2, 7,
                new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_7_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("normalType".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "findType".equals(pi.name)) {
                    assertEquals("nullable instance type FindType/*@Identity*/", d.currentValue().toString());
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
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
                            case 1 -> "<vp:TypeInfo:cm@Parameter_fqn;initial@Field_fqn;mom@Parameter_fqn>";
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
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ParameterizedType".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TypeInfo".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        testClass("Precondition_7_2", 1, 3,
                new DebugConfiguration.Builder()
                    //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                    //    .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                    //    .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("Precondition_8", 0, 1,
                new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "stack".equals(fr.fieldInfo.name)) {
                    assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "Precondition[expression=!<m:isEmpty>, causes=[escape]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
                if (d.iteration() > 0) assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };
        testClass("Precondition_9", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }


    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$5".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof FieldReference fr && "priority".equals(fr.fieldInfo.name)) {
                    if ("Cause.TYPE_ANALYSIS".equals(fr.scope.toString())) {
                        String expected = d.iteration() <= 4 ? "<f:Cause.TYPE_ANALYSIS.priority>"
                                : "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$5".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 1 -> "Precondition[expression=<inline>, causes=[]]";
                    default -> "Precondition[expression=1==`cause`.priority, causes=[methodCall:containsCauseOfDelay]]";
                };
                assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("containsCauseOfDelay".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "Precondition[expression=<m:highPriority>, causes=[escape]]"
                        : "Precondition[expression=1==cause.priority, causes=[escape]]";
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
            }
        };
        testClass("Precondition_10", 0, 6,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    static final String CALL_CYCLE = "apply,apply,defaultImmutable,defaultImmutable,getTypeAnalysisNullWhenAbsent,highPriority,isAtLeastE2Immutable,sumImmutableLevels";

    // without the NOT_INVOLVED, making a call cycle of 3 instead of 2
    @Test
    public void test_10_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int numParams = d.methodInfo().methodInspection.get().getParameters().size();
            MethodResolution methodResolution = d.methodInfo().methodResolution.get();
            if ("defaultImmutable".equals(d.methodInfo().name)) {
                if (numParams == 2) {
                    assertTrue(methodResolution.partOfCallCycle());
                    assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                    assertEquals(CALL_CYCLE, methodResolution
                            .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                } else if (numParams == 3) {
                    assertTrue(methodResolution.partOfCallCycle());
                    assertFalse(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
                    assertEquals(CALL_CYCLE, methodResolution
                            .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                } else fail();
            }
            if ("apply".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(CALL_CYCLE, methodResolution
                        .methodsOfOwnClassReached().stream().map(MethodInfo::name).sorted().collect(Collectors.joining(",")));
                assertTrue(methodResolution.partOfCallCycle());
                assertTrue(methodResolution.ignoreMeBecauseOfPartOfCallCycle());
            }
        };
        testClass("Precondition_10_2", 0, 16,
                new DebugConfiguration.Builder()
                 //       .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .build());
    }

    @Test
    public void test_11() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("expression instanceof Sum", d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<instanceOf:ConstantExpression<?>>&&<null-check>&&expression instanceof Product";
                        case 1 -> "<null-check>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs";
                        case 2 -> "<simplification>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs";
                        default -> "expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression/*(Product)*/.rhs instanceof MethodCall&&expression instanceof Product&&null!=expression/*(Product)*/.lhs&&null!=expression/*(Product)*/.rhs&&null!=expression/*(Product)*/.rhs/*(MethodCall)*/";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2 -> "<null-check>";
                        default -> "expression instanceof MethodCall";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() <= 2 ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
                if ("6".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=!<null-check>&&!<instanceOf:ConstantExpression<?>>&&!<instanceOf:Negation>&&!(expression instanceof Sum)&&(!<instanceOf:ConstantExpression<?>>||!<null-check>||!(expression instanceof Product));parent=CM{}}";
                        case 1 -> "CM{state=!<null-check>&&!<instanceOf:ConstantExpression<?>>&&!<instanceOf:Negation>&&!(expression instanceof Sum)&&(!<null-check>||!(scope-product:2.lhs instanceof ConstantExpression<?>)||!(expression instanceof Product)||null==scope-product:2.lhs);parent=CM{}}";
                        case 2 -> "CM{state=!<null-check>&&!<instanceOf:ConstantExpression<?>>&&!<instanceOf:Negation>&&!(expression instanceof Sum)&&(!<simplification>||!(scope-product:2.lhs instanceof ConstantExpression<?>)||!(expression instanceof Product)||null==scope-product:2.lhs);parent=CM{}}";
                        default -> "CM{state=!(expression instanceof ConstantExpression<?>)&&!(expression instanceof MethodCall)&&!(expression instanceof Negation)&&!(expression instanceof Sum)&&(!(scope-product:2.lhs instanceof ConstantExpression<?>)||!(scope-product:2.rhs instanceof MethodCall)||!(expression instanceof Product)||null==scope-product:2.lhs||null==scope-product:2.rhs||null==scope-product:2.rhs/*(MethodCall)*/);parent=CM{}}";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                    assertEquals(d.iteration() >= 3, d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
                }
            }
        };
        int BIG = 30;
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "expression".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("accept1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "perComponent".equals(fr.fieldInfo.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept6".equals(d.methodInfo().name)) {
                if ("xEquals".equals(d.variableName())) {
                    if ("01".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5, 6 -> "<s:Double>";
                           // case 6 -> "<m:extractEquals>";
                            default -> X_EQUALS;
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("03".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5, 6 -> "<s:Double>";
                            default -> X_EQUALS;
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("inequality".equals(d.variableName())) {
                    if ("04.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:LinearInequalityInOneVariable>";
                            case 1, 2, 3, 4, 5, 6, 7, 8 -> "<new:LinearInequalityInOneVariable>";
                            default -> "new LinearInequalityInOneVariable(b,y,a*xEquals+c,allowEquals)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("intervalX".equals(d.variableName())) {
                    if ("06".equals(d.statementId())) {
                        String expected = d.iteration() <= 8 ? "<s:Interval>"
                                : "2==expressionsInX.size()?null==Interval.extractInterval2(expressionsInX.get(0))||null==Interval.extractInterval2(expressionsInX.get(1))?null:Double.isFinite(`Interval.extractInterval2(expressions.get(0)).left`)&&Infinity==`Interval.extractInterval2(expressions.get(0)).right`&&2==expressionsInX.size()?new Interval(`Interval.extractInterval2(expressions.get(0)).left`,`Interval.extractInterval2(expressions.get(0)).leftIncluded`,`Interval.extractInterval2(expressions.get(1)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):([expressionsInX,expressionsInY,instance type boolean])?new Interval(`Interval.extractInterval2(expressions.get(1)).left`,`Interval.extractInterval2(expressions.get(1)).leftIncluded`,`Interval.extractInterval2(expressions.get(0)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):<return value>:1==expressionsInX.size()?Interval.extractInterval2(expressionsInX.get(0)):null";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 9) {
                            assertEquals("expressionsInX, expressionsInX, expressionsInX, expressionsInX, expressionsInX, expressionsInY, expressionsInX, expressionsInX",
                                    d.currentValue().variables(true)
                                            .stream().map(Variable::simpleName).collect(Collectors.joining(", ")));
                        }
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("extractOneVariable".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:extractOneVariable>"
                        : "/*inline extractOneVariable*/expression instanceof MethodCall&&null!=expression?expression/*(MethodCall)*/:null";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept5".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "<m:accept5>"
                        : "/*inline accept5*/allowEquals?a*px+py*b+c>=0:-1+a*px+py*b+c>=0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 1) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("a, allowEquals, b, c, px, py, this", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("extractInterval1".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
                String srv = d.iteration() <= 3 ? "<m:extractInterval1>"
                        : "/*inline extractInterval1*/2==expressions.size()?null==Interval.extractInterval2(expressions.get(0))||null==Interval.extractInterval2(expressions.get(1))?null:Double.isFinite(`Interval.extractInterval2(expressions.get(0)).left`)&&Infinity==`Interval.extractInterval2(expressions.get(0)).right`&&2==expressions.size()?new Interval(`Interval.extractInterval2(expressions.get(0)).left`,`Interval.extractInterval2(expressions.get(0)).leftIncluded`,`Interval.extractInterval2(expressions.get(1)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):Double.isFinite(`Interval.extractInterval2(expressions.get(0)).right`)&&-Infinity==`left`&&2==expressions.size()&&null!=Interval.extractInterval2(expressions.get(0))&&null!=Interval.extractInterval2(expressions.get(1))&&(!Double.isFinite(`Interval.extractInterval2(expressions.get(0)).left`)||Infinity!=`Interval.extractInterval2(expressions.get(0)).right`)?new Interval(`Interval.extractInterval2(expressions.get(1)).left`,`Interval.extractInterval2(expressions.get(1)).leftIncluded`,`Interval.extractInterval2(expressions.get(0)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):<return value>:1==expressions.size()?Interval.extractInterval2(expressions.get(0)):null";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 4) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressions", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("extractInterval2".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("extractEquals".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept2".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept3".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 7 ? "<m:accept3>" :
                        "/*inline accept3*/([<return value>,expressionsInV,instance type boolean])?null==expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)?null||expressionsInV.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`):`allowEquals`?`a`*expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)+`b`>=0:-1+`a`*expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)+`b`>=0:instance type boolean";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 8) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressionsInV", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("accept4".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("interval".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("LinearInequalityInOneVariable".equals(d.methodInfo().name)) {
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("accept6".equals(d.methodInfo().name)) {
                assertDv(d, 7, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                if (d.iteration() == 9) {
                    assertEquals("", d.getProperty(Property.TEMP_MODIFIED_METHOD).causesOfDelay().toString());
                }
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 8 ? "<m:accept6>" : "/*inline accept6*/[a,b,this,instance type boolean]";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 9) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("a, b, this", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("a".equals(d.fieldInfo().name) && "LinearInequalityInTwoVariables".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("a", d.fieldAnalysis().getValue().toString());
            }
            if ("x".equals(d.fieldInfo().name) && "LinearInequalityInTwoVariables".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("x", d.fieldAnalysis().getValue().toString());
                assertDv(d, BIG, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Interval".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Precondition_11".equals(d.typeInfo().simpleName)) {
                TypeAnalysisImpl.Builder b = (TypeAnalysisImpl.Builder) d.typeAnalysis();
                assertEquals(0L, b.nonModifiedCountForMethodCallCycle.stream().count());
            }
            if ("OneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("Variable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("LinearInequalityInOneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
                assertDv(d, BIG, MultiLevel.CONTAINER_DV, CONTAINER);
            }
            if ("Term".equals(d.typeInfo().simpleName)) {
                assertDv(d, BIG, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
            }
        };


        testClass("Precondition_11", 0, 16,
                new DebugConfiguration.Builder()
                   //     .addEvaluationResultVisitor(evaluationResultVisitor)
                    //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
                     //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                    //    .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }
}

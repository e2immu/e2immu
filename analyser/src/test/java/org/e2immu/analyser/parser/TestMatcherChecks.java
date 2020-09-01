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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.pattern.*;
import org.e2immu.analyser.testexample.MatcherChecks;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

public class TestMatcherChecks extends CommonTestRunner {
    public TestMatcherChecks() {
        super(false);
    }

    private MethodInfo method1;
    private MethodInfo method2;
    private MethodInfo method3;
    private MethodInfo method4;

    @Before
    public void before() throws IOException {
        TypeContext typeContext = testClass("MatcherChecks", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
        TypeInfo matcherChecks = typeContext.getFullyQualified(MatcherChecks.class);
        method1 = matcherChecks.findUniqueMethod("method1", 1);
        method2 = matcherChecks.findUniqueMethod("method2", 1);
        method3 = matcherChecks.findUniqueMethod("method3", 1);
        method4 = matcherChecks.findUniqueMethod("method4", 1);

    }

    // match11 is separate from match12, they make the same change
    @Test
    public void test() {
        match11(method1, method2, method3, method4);
    }

    @Test
    public void test2() {
        match12(method1);
    }

    @Test
    public void test3() {
        match2(method2, method1, method3, method4);
        match3(method3, method1, method2, method4);
        // TODO later match4(typeContext, method4, method1, method2, method3);
    }

    private void match4(TypeContext typeContext, MethodInfo method4, MethodInfo... others) {
        Pattern forLoop = ConditionalAssignment.pattern4(typeContext);
        PatternMatcher patternMatcher = new PatternMatcher(Map.of(forLoop, Replacement.NO_REPLACEMENT));
        log(TRANSFORM, "Start match4 on method4");
        Optional<MatchResult> optMatchResult = patternMatcher.match(method4, method4.methodAnalysis.get().numberedStatements.get().get(0));
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            log(TRANSFORM, "Start match4 on {}", other.name);
            Optional<MatchResult> opt = patternMatcher.match(other, other.methodAnalysis.get().numberedStatements.get().get(0));
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }
    }

    private static final EvaluationContext TEST_EC = new EvaluationContext() {
        @Override
        public Set<String> allUnqualifiedVariableNames() {
            return Set.of("tmp");
        }
    };

    private void match11(MethodInfo method1, MethodInfo... others) {

        // PATTERN

        Pattern conditionalAssignment = ConditionalAssignment.pattern1();

        // REPLACEMENT

        Replacement replacement = ConditionalAssignment.replacement1ToPattern1(conditionalAssignment);
        Statement first = replacement.statements.get(0);
        Assert.assertEquals("T0 lv$0 = expression():0;\n", first.statementString(0));
        Statement second = replacement.statements.get(1);
        Assert.assertEquals("T0 lv0 = (expression(lv0):1) ? (expression():2) : lv$0;\n", second.statementString(0));
        Assert.assertEquals("{expression(lv0):1=TM{{lv0=lv$0}}}", replacement.translationsOnExpressions.toString());

        // MATCH RESULT

        MatchResult matchResult = checkMatchResultPattern1(conditionalAssignment, method1, others);

        // RESULT OF REPLACER

        // tmp0 because tmp already exists (see TEST_EC)
        Replacer.replace(TEST_EC, matchResult, replacement);
        Statement final1 = matchResult.start.replacement.get().statement;
        Assert.assertEquals("String tmp0 = a1;\n", final1.statementString(0));
        Statement final2 = matchResult.start.replacement.get().next.get().orElseThrow().statement;
        Assert.assertEquals("String s1 = tmp0 == null ? \"\" : tmp0;\n", final2.statementString(0));
    }


    private void match12(MethodInfo method1) {

        // PATTERN

        Pattern conditionalAssignment = ConditionalAssignment.pattern1();

        // REPLACEMENT

        Replacement replacement = ConditionalAssignment.replacement2ToPattern1(conditionalAssignment);
        Statement first = replacement.statements.get(0);
        Assert.assertEquals("T0 lv0;\n", first.statementString(0));
        Statement second = replacement.statements.get(1);
        Assert.assertEquals(" {\n" +
                "    T0 lv$0 = expression():0;\n" +
                "    if (expression(lv0):1) {\n" +
                "        lv0 = expression():2;\n" +
                "    } else {\n" +
                "        lv0 = lv$0;\n" +
                "    }\n" +
                "}", second.statementString(0));
        Assert.assertEquals("{expression(lv0):1=TM{{lv0=lv$0}}}", replacement.translationsOnExpressions.toString());

        // MATCH RESULT
        // is identical, as the pattern is the same and we run it on the same method

        MatchResult matchResult = checkMatchResultPattern1(conditionalAssignment, method1);

        // RESULT OF REPLACER

        // tmp0 because tmp already exists (see TEST_EC)
        Replacer.replace(TEST_EC, matchResult, replacement);
        Statement final1 = matchResult.start.replacement.get().statement;
        Assert.assertEquals("String s1;\n", final1.statementString(0));
        NumberedStatement next = matchResult.start.replacement.get().next.get().orElseThrow();
        Statement final2 = next.statement;
        Assert.assertEquals("1", next.index);
        Assert.assertEquals(" {\n" +
                "    String tmp0 = a1;\n" +
                "    if (tmp0 == null) {\n" +
                "        s1 = \"\";\n" +
                "    } else {\n" +
                "        s1 = tmp0;\n" +
                "    }\n" +
                "}", final2.statementString(0));
    }

    private MatchResult checkMatchResultPattern1(Pattern conditionalAssignment, MethodInfo method1, MethodInfo... others) {
        Assert.assertEquals("T0 lv0 = expression():0;\n", conditionalAssignment.statements.get(0).statementString(0));
        Assert.assertEquals("if (expression(lv0):1) {\n" +
                "    lv0 = expression():2;\n" +
                "}\n", conditionalAssignment.statements.get(1).statementString(0));
        Assert.assertEquals(1, conditionalAssignment.types.size());

        PatternMatcher patternMatcher = new PatternMatcher(Map.of(conditionalAssignment, Replacement.NO_REPLACEMENT));

        Optional<MatchResult> optMatchResult = patternMatcher.match(method1, method1.methodAnalysis.get().numberedStatements.get().get(0));
        Assert.assertTrue(optMatchResult.isPresent());
        MatchResult matchResult = optMatchResult.get();
        Expression actual0 = matchResult.translationMap.expressions.get(new Pattern.PlaceHolderExpression(0));
        Assert.assertEquals("a1", actual0.expressionString(0));
        Expression actual1 = matchResult.translationMap.expressions.get(new Pattern.PlaceHolderExpression(1));
        Assert.assertEquals("s1 == null", actual1.expressionString(0));
        Expression actual2 = matchResult.translationMap.expressions.get(new Pattern.PlaceHolderExpression(2));
        Assert.assertEquals("\"\"", actual2.expressionString(0));

        Assert.assertEquals("{lv0=s1}", matchResult.translationMap.variables.toString());
        Assert.assertEquals("{Type param T0=Type java.lang.String}", matchResult.translationMap.types.toString());
        Assert.assertEquals("{LocalVariable lv0 of Type param T0=LocalVariable s1 of Type java.lang.String}",
                matchResult.translationMap.localVariables.toString());


        for (MethodInfo other : others) {
            Optional<MatchResult> opt = patternMatcher.match(other, other.methodAnalysis.get().numberedStatements.get().get(0));
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }

        return matchResult;
    }

    private void match2(MethodInfo method2, MethodInfo... others) {
        Pattern pattern2 = ConditionalAssignment.pattern2();
        PatternMatcher patternMatcher2 = new PatternMatcher(Map.of(pattern2, Replacement.NO_REPLACEMENT));

        Optional<MatchResult> optMatchResult = patternMatcher2.match(method2, method2.methodAnalysis.get().numberedStatements.get().get(0));
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            Optional<MatchResult> opt = patternMatcher2.match(other, other.methodAnalysis.get().numberedStatements.get().get(0));
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }
    }

    private void match3(MethodInfo method2, MethodInfo... others) {
        Pattern pattern3 = ConditionalAssignment.pattern3();
        PatternMatcher patternMatcher3 = new PatternMatcher(Map.of(pattern3, Replacement.NO_REPLACEMENT));

        Optional<MatchResult> optMatchResult = patternMatcher3.match(method2, method2.methodAnalysis.get().numberedStatements.get().get(0));
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            Optional<MatchResult> opt = patternMatcher3.match(other, other.methodAnalysis.get().numberedStatements.get().get(0));
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }
    }

}

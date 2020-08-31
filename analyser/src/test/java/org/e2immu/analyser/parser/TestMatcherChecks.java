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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.pattern.*;
import org.e2immu.analyser.testexample.MatcherChecks;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

public class TestMatcherChecks extends CommonTestRunner {
    public TestMatcherChecks() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        TypeContext typeContext = testClass("MatcherChecks", 0, new DebugConfiguration.Builder()
                .build());
        TypeInfo matcherChecks = typeContext.getFullyQualified(MatcherChecks.class);
        MethodInfo method1 = matcherChecks.findUniqueMethod("method1", 1);
        MethodInfo method2 = matcherChecks.findUniqueMethod("method2", 1);
        MethodInfo method3 = matcherChecks.findUniqueMethod("method3", 1);
        MethodInfo method4 = matcherChecks.findUniqueMethod("method4", 1);
        match1(method1, method2, method3, method4);
        match2(method2, method1, method3, method4);
        match3(method3, method1, method2, method4);
        // TODO later match4(typeContext, method4, method1, method2, method3);
    }

    private void match4(TypeContext typeContext, MethodInfo method4, MethodInfo... others) {
        Pattern forLoop = ConditionalAssignment.pattern4(typeContext);
        PatternMatcher patternMatcher = new PatternMatcher(List.of(forLoop));
        log(TRANSFORM, "Start match4 on method4");
        Optional<MatchResult> optMatchResult = patternMatcher.match(method4.methodAnalysis.get().numberedStatements.get().get(0), false);
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            log(TRANSFORM, "Start match4 on {}", other.name);
            Optional<MatchResult> opt = patternMatcher.match(other.methodAnalysis.get().numberedStatements.get().get(0), false);
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }
    }

    private static final EvaluationContext TEST_EC = new EvaluationContext() {
        @Override
        public Set<String> allUnqualifiedVariableNames() {
            return Set.of("tmp");
        }
    };

    private void match1(MethodInfo method1, MethodInfo... others) {

        // PATTERN

        Pattern conditionalAssignment = ConditionalAssignment.pattern1();
        Assert.assertEquals("T0 lv0 = expression():0;\n", conditionalAssignment.statements.get(0).statementString(0));
        Assert.assertEquals("if (expression(lv0):1) {\n" +
                "    lv0 = expression():2;\n" +
                "}\n", conditionalAssignment.statements.get(1).statementString(0));
        Assert.assertEquals(1, conditionalAssignment.types.size());

        PatternMatcher patternMatcher = new PatternMatcher(List.of(conditionalAssignment));

        Optional<MatchResult> optMatchResult = patternMatcher.match(method1.methodAnalysis.get().numberedStatements.get().get(0), false);
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            Optional<MatchResult> opt = patternMatcher.match(other.methodAnalysis.get().numberedStatements.get().get(0), false);
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }

        // REPLACEMENT

        Replacement replacement = ConditionalAssignment.replacement1ToPattern1(conditionalAssignment);
        Statement first = replacement.statements.get(0);
        Assert.assertEquals("T0 lv$0 = expression():0;\n", first.statementString(0));
        Statement second = replacement.statements.get(1);
        Assert.assertEquals("T0 lv0 = (expression(lv0):1) ? (expression():2) : lv$0;\n", second.statementString(0));

        // MATCH RESULT

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

        // RESULT OF REPLACER

        // tmp0 because tmp already exists (see TEST_EC)
        Replacer.replace(TEST_EC, matchResult, replacement);
        Statement final1 = matchResult.start.replacement.get().statement;
        Assert.assertEquals("String tmp0 = a1;\n", final1.statementString(0));
        Statement final2 = matchResult.start.replacement.get().next.get().orElseThrow().statement;
        Assert.assertEquals("String s1 = tmp0 == null ? \"\" : tmp0;\n", final2.statementString(0));
    }

    private void match2(MethodInfo method2, MethodInfo... others) {
        Pattern pattern2 = ConditionalAssignment.pattern2();
        PatternMatcher patternMatcher2 = new PatternMatcher(List.of(pattern2));

        Optional<MatchResult> optMatchResult = patternMatcher2.match(method2.methodAnalysis.get().numberedStatements.get().get(0), false);
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            Optional<MatchResult> opt = patternMatcher2.match(other.methodAnalysis.get().numberedStatements.get().get(0), false);
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }
    }

    private void match3(MethodInfo method2, MethodInfo... others) {
        Pattern pattern3 = ConditionalAssignment.pattern3();
        PatternMatcher patternMatcher3 = new PatternMatcher(List.of(pattern3));

        Optional<MatchResult> optMatchResult = patternMatcher3.match(method2.methodAnalysis.get().numberedStatements.get().get(0), false);
        Assert.assertTrue(optMatchResult.isPresent());

        for (MethodInfo other : others) {
            Optional<MatchResult> opt = patternMatcher3.match(other.methodAnalysis.get().numberedStatements.get().get(0), false);
            Assert.assertTrue("Failing on " + other.distinguishingName(), opt.isEmpty());
        }
    }

}

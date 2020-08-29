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

package org.e2immu.analyser.pattern;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * For now the patterns are tested one by one, but we will at some point put them in a TRIE structure,
 * based on the statement and expression types.
 * <p>
 * The result, in case of a match, is a MatchResult, which will contain sufficient information
 * to be applied to the
 * <p>
 * Implement a speed-up by making a distinction between failure because of a DELAY, or structural failure.
 * Only combinations of patterns + start statements that cause a DELAY have to be tried again.
 * Maybe have a "recordDelays" for the first iteration, vs "onlyDelays" for subsequent iterations.
 */
public class PatternMatcher {
    private final List<Pattern> patterns;

    public PatternMatcher(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public Optional<MatchResult> match(NumberedStatement startStatement, boolean onlyDelayed) {
        return patterns.stream().flatMap(pattern -> match(pattern, startStatement, onlyDelayed).stream()).findFirst();
    }

    enum SimpleMatchResult {
        YES, NO, DELAY, NOT_YET,
    }

    private Optional<MatchResult> match(Pattern pattern, NumberedStatement startStatement, boolean onlyDelayed) {
        if (onlyDelayed && alreadyDone(pattern, startStatement)) return Optional.empty();
        MatchResult.MatchResultBuilder builder = new MatchResult.MatchResultBuilder(startStatement);
        SimpleMatchResult isMatch = match(pattern, builder, pattern.statements, startStatement);
        if (isMatch == SimpleMatchResult.DELAY) {
            registerDelay(pattern, startStatement);
        }
        return isMatch == SimpleMatchResult.YES ? Optional.of(builder.build()) : Optional.empty();
    }

    private boolean alreadyDone(Pattern pattern, NumberedStatement startStatement) {
        return false; // TODO
    }

    private void registerDelay(Pattern pattern, NumberedStatement startStatement) {
        // TODO
    }

    // -- STATIC AREA; here we don't care about speed-ups, Optionals, etc.

    private static SimpleMatchResult match(Pattern pattern,
                                           MatchResult.MatchResultBuilder builder,
                                           List<Statement> templateStatements,
                                           NumberedStatement startStatement) {
        NumberedStatement currentNumberedStatement = startStatement;
        for (int i = 0; i < pattern.statements.size(); i++) {
            Statement template = pattern.statements.get(i);
            SimpleMatchResult isFastMatch = fastMatch(template, currentNumberedStatement);
            if (isFastMatch == SimpleMatchResult.NO) return SimpleMatchResult.NO;
            if (isFastMatch == SimpleMatchResult.YES) continue;
            assert currentNumberedStatement != null;

            // slower method, first computing code organization
            CodeOrganization codeOrganization = template.codeOrganization();
            SimpleMatchResult isMatch = match(pattern, builder, template, codeOrganization, currentNumberedStatement);
            if (isMatch != SimpleMatchResult.YES) return isMatch;

            if (codeOrganization.statements instanceof Block) {
                SimpleMatchResult blockResult = match(pattern, builder, codeOrganization.statements.getStatements(),
                        currentNumberedStatement.next.get().orElse(null));
            } else if (!codeOrganization.statements.getStatements().isEmpty()) {
                // statements in a switch, between two cases.
            }
            // else block, blocks in switch, try
            for (CodeOrganization subCo : codeOrganization.subStatements) {

            }

            currentNumberedStatement = currentNumberedStatement.next.get().orElse(null);
        }
        return SimpleMatchResult.NO;
    }

    @NotNull
    private static SimpleMatchResult fastMatch(Statement template, NumberedStatement actualNs) {
        if (actualNs == null) {
            return template instanceof Pattern.PlaceHolderStatement ? SimpleMatchResult.YES : SimpleMatchResult.NO;
        }
        Statement actual = actualNs.statement;
        if (template instanceof Pattern.PlaceHolderStatement) {
            return SimpleMatchResult.YES;
        }
        return actual.getClass() == template.getClass() ? SimpleMatchResult.NOT_YET : SimpleMatchResult.NO;
    }

    @NotNull
    private static SimpleMatchResult match(Pattern pattern,
                                           MatchResult.MatchResultBuilder builder,
                                           Statement template,
                                           CodeOrganization templateCo,
                                           NumberedStatement actualNs) {
        Statement actual = actualNs.statement;
        CodeOrganization actualCo = actual.codeOrganization();

        SimpleMatchResult sub = match(pattern, builder, templateCo.expression, actualCo.expression);
        if (sub != SimpleMatchResult.YES) return sub;

        // TODO all the other code organization parts

        return SimpleMatchResult.YES;
    }

    private static SimpleMatchResult match(Pattern pattern, MatchResult.MatchResultBuilder builder,
                                           Expression template, Expression actual) {
        return SimpleMatchResult.NO;
    }
}

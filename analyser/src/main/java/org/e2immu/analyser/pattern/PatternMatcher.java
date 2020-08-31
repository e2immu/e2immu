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
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Optional;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

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
        MatchResult.MatchResultBuilder builder = new MatchResult.MatchResultBuilder(pattern, startStatement);
        SimpleMatchResult isMatch = match(builder, pattern.statements, startStatement);
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

    private static SimpleMatchResult match(MatchResult.MatchResultBuilder builder,
                                           List<Statement> templateStatements,
                                           NumberedStatement startStatement) {
        NumberedStatement currentNumberedStatement = startStatement;
        for (Statement template : templateStatements) {
            SimpleMatchResult isFastMatch = fastMatch(template, currentNumberedStatement);
            if (isFastMatch == SimpleMatchResult.NO) return SimpleMatchResult.NO;
            if (isFastMatch == SimpleMatchResult.YES) continue;
            assert currentNumberedStatement != null;

            // slower method, first computing code organization
            CodeOrganization codeOrganization = template.codeOrganization();
            SimpleMatchResult isMatch = match(builder, codeOrganization, currentNumberedStatement);
            if (isMatch != SimpleMatchResult.YES) return isMatch;

            log(TRANSFORM, "Successfully matched {}", currentNumberedStatement.streamIndices());

            currentNumberedStatement = currentNumberedStatement.next.get().orElse(null);
        }
        return SimpleMatchResult.YES;
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
    private static SimpleMatchResult match(MatchResult.MatchResultBuilder builder,
                                           CodeOrganization templateCo,
                                           NumberedStatement actualNs) {
        Statement actual = actualNs.statement;
        CodeOrganization actualCo = actual.codeOrganization();
        {
            SimpleMatchResult smr = match(builder, templateCo.expression, actualCo.expression);
            if (smr != SimpleMatchResult.YES) return smr;
        }
        if (templateCo.initialisers.size() != actualCo.initialisers.size()) return SimpleMatchResult.NO;
        for (int i = 0; i < templateCo.initialisers.size(); i++) {
            SimpleMatchResult smr = match(builder, templateCo.initialisers.get(i), actualCo.initialisers.get(i));
            if (smr != SimpleMatchResult.YES) return smr;
        }
        if (templateCo.updaters.size() != actualCo.updaters.size()) return SimpleMatchResult.NO;
        for (int i = 0; i < templateCo.updaters.size(); i++) {
            SimpleMatchResult smr = match(builder, templateCo.updaters.get(i), actualCo.updaters.get(i));
            if (smr != SimpleMatchResult.YES) return smr;
        }
        if ((templateCo.localVariableCreation == null) != (actualCo.localVariableCreation == null)) {
            return SimpleMatchResult.NO;
        }
        if (templateCo.localVariableCreation != null) {
            builder.matchLocalVariable(templateCo.localVariableCreation, actualCo.localVariableCreation);
        }
        // primary block
        int numActualBlocks = actualNs.blocks.get().size();
        if (templateCo.statements instanceof Block) {
            if (templateCo.statements == Block.EMPTY_BLOCK) {
                if (numActualBlocks > 0) return SimpleMatchResult.NO;
            } else {
                if (numActualBlocks == 0) return SimpleMatchResult.NO;
                NumberedStatement firstInBlock = actualNs.blocks.get().get(0);
                SimpleMatchResult smr = match(builder, templateCo.statements.getStatements(),
                        firstInBlock);
                if (smr != SimpleMatchResult.YES) return smr;
            }
        } else if (!templateCo.statements.getStatements().isEmpty()) {
            // TODO statements in a switch, between two cases.
        }
        // else block, blocks in switch, try
        int blockCount = 1;
        for (CodeOrganization subCo : templateCo.subStatements) {
            if (numActualBlocks <= blockCount) return SimpleMatchResult.NO;
            NumberedStatement firstInBlock = actualNs.blocks.get().get(blockCount);
            SimpleMatchResult smr = match(builder, subCo.statements.getStatements(), firstInBlock);
            if (smr != SimpleMatchResult.YES) return smr;

            // TODO next to the statements, we also have to test the expressions for switch, catch
        }

        return SimpleMatchResult.YES;
    }

    private static SimpleMatchResult match(MatchResult.MatchResultBuilder builder,
                                           Expression template, Expression actual) {
        if (template == actual) return SimpleMatchResult.YES; // mainly for EmptyExpression


        if (template instanceof Pattern.PlaceHolderExpression) {
            Pattern.PlaceHolderExpression placeHolder = (Pattern.PlaceHolderExpression) template;
            if (!placeHolder.returnType.isAssignableFrom(actual.returnType())) return SimpleMatchResult.NO;
            return builder.containsAllVariables(placeHolder.variablesToMatch, actual.variables()) ?
                    SimpleMatchResult.YES : SimpleMatchResult.NO;
        }
        if (!template.getClass().equals(actual.getClass())) return SimpleMatchResult.NO;
        if (!template.returnType().isAssignableFrom(actual.returnType())) return SimpleMatchResult.NO;

        if (template instanceof VariableExpression) {
            Variable varTemplate = ((VariableExpression) template).variable;
            Variable varActual = ((VariableExpression) actual).variable;
            builder.matchVariable(varTemplate, varActual);
        }
        if (template instanceof Assignment) {
            Assignment aTemplate = (Assignment) template;
            Assignment aActual = (Assignment) actual;
            if (aTemplate.target instanceof VariableExpression && aActual.target instanceof VariableExpression) {
                Variable varTemplate = ((VariableExpression) aTemplate.target).variable;
                Variable varActual = ((VariableExpression) aActual.target).variable;
                builder.matchVariable(varTemplate, varActual);
            } else {
                return SimpleMatchResult.NO; // TODO this is too simplistic
            }
            return match(builder, aTemplate.value, aActual.value);
        }
        if (template instanceof LocalVariableCreation) {
            LocalVariableCreation lvcTemplate = (LocalVariableCreation) template;
            LocalVariableCreation lvcActual = (LocalVariableCreation) actual;
            builder.matchLocalVariable(lvcTemplate.localVariable, lvcActual.localVariable);

            // delegate expression
            return match(builder, lvcTemplate.expression, lvcActual.expression);
        }
        return template.equals(actual) ? SimpleMatchResult.YES : SimpleMatchResult.NO;
    }
}

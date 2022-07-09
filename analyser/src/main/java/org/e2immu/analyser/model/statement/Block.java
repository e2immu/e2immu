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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Block extends StatementWithStructure {
    public final String label;
    public final Structure structure;

    public static Block emptyBlock(Identifier identifier) {
        return new Block(identifier, List.of(), null);
    }

    private Block(Identifier identifier, @NotNull List<Statement> statements, String label) {
        super(identifier);
        structure = new Structure.Builder()
                .setStatementExecution(StatementExecution.ALWAYS)
                .setStatements(statements).build();
        this.label = label;
    }

    @Container(builds = Block.class)
    public static class BlockBuilder {
        private final List<Statement> statements = new ArrayList<>();
        private String label;
        private final Identifier identifier;

        public BlockBuilder(Identifier identifier) {
            this.identifier = identifier;
        }

        @Fluent
        public BlockBuilder setLabel(String label) {
            this.label = label;
            return this;
        }

        @Fluent
        public BlockBuilder addStatement(@NotNull Statement statement) {
            this.statements.add(statement);
            return this;
        }

        @NotModified
        @NotNull
        public Block build() {
            if (statements.isEmpty()) return emptyBlock(identifier);
            // NOTE: we don't do labels on empty blocks. that's pretty useless anyway
            return new Block(identifier, List.copyOf(statements), label);
        }

        public int size() {
            return statements.size();
        }
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (label != null) {
            outputBuilder.add(Space.ONE).add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        outputBuilder.add(Symbol.LEFT_BRACE);
        if (statementAnalysis == null) {
            if (!structure.statements().isEmpty()) {
                outputBuilder.add(structure.statements().stream()
                        .filter(s -> !s.isSynthetic())
                        .map(s -> s.output(qualification, null))
                        .collect(OutputBuilder.joining(Space.NONE, Guide.generatorForBlock())));
            }
        } else {
            Guide.GuideGenerator guideGenerator = Guide.generatorForBlock();
            outputBuilder.add(guideGenerator.start());
            LimitedStatementAnalysis sa;
            if (statementAnalysis.statement() instanceof Block) {
                sa = statementAnalysis.navigationBlock0OrElseNull();
            } else {
                sa = statementAnalysis;
            }
            if (sa != null) {
                statementsString(qualification, outputBuilder, guideGenerator, sa);
            }
            outputBuilder.add(guideGenerator.end());
        }
        outputBuilder.add(Symbol.RIGHT_BRACE);
        return outputBuilder;
    }

    public static void statementsString(Qualification qualification,
                                        OutputBuilder outputBuilder,
                                        Guide.GuideGenerator guideGenerator,
                                        LimitedStatementAnalysis statementAnalysis) {
        statementsString(qualification, outputBuilder, guideGenerator, statementAnalysis, false);
    }

    private static void statementsString(Qualification qualification,
                                         OutputBuilder outputBuilder,
                                         Guide.GuideGenerator guideGenerator,
                                         LimitedStatementAnalysis statementAnalysis,
                                         boolean isNotFirst) {
        LimitedStatementAnalysis sa = statementAnalysis;
        boolean notFirst = isNotFirst;
        while (sa != null) {
            if (!sa.statement().isSynthetic()) {
                if (sa.navigationReplacementIsSet()) {
                    if (!notFirst) notFirst = true;
                    else outputBuilder.add(guideGenerator.mid());
                    outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                            .add(new Text("code will be replaced"))
                            .add(guideGenerator.mid())
                            .add(sa.output(qualification));

                    LimitedStatementAnalysis moreReplaced = sa.navigationNextIsSet() ? sa.navigationNextGetOrElseNull() : null;
                    if (moreReplaced != null) {
                        statementsString(qualification, outputBuilder, guideGenerator, moreReplaced, true); // recursion!
                    }
                    outputBuilder.add(guideGenerator.mid()).add(Symbol.RIGHT_BLOCK_COMMENT);
                    sa = sa.navigationReplacementGet();
                }
                if (!notFirst) notFirst = true;
                else outputBuilder.add(guideGenerator.mid());
                outputBuilder.add(sa.output(qualification));
            }
            sa = sa.navigationNextIsSet() ? sa.navigationNextGetOrElseNull() : null;
        }
    }

    /*
    more complicated version of the above method, meant for old-style switch statements
    explicitly duplicated the code, so that we can study the simple version diving into the more complicated one!
     */
    public static void outputSwitchOldStyle(Qualification qualification,
                                            OutputBuilder outputBuilder,
                                            Guide.GuideGenerator guideGenerator,
                                            LimitedStatementAnalysis statementAnalysis,
                                            Map<String, List<SwitchStatementOldStyle.SwitchLabel>> idToLabels) {
        Guide.GuideGenerator statementGg = null;
        LimitedStatementAnalysis sa = statementAnalysis;
        boolean notFirst = false;
        boolean notFirstInCase = false;
        while (sa != null) {
            if (idToLabels.containsKey(sa.index())) {
                if (statementGg != null) {
                    outputBuilder.add(statementGg.end());
                }
                if (!notFirst) notFirst = true;
                else outputBuilder.add(guideGenerator.mid());
                for (SwitchStatementOldStyle.SwitchLabel switchLabel : idToLabels.get(sa.index())) {
                    outputBuilder.add(switchLabel.output(qualification));
                    guideGenerator.mid();
                }
                statementGg = Guide.generatorForBlock();
                outputBuilder.add(statementGg.start());
                notFirstInCase = false;
            }
            assert statementGg != null;
            if (sa.navigationReplacementIsSet()) {
                if (!notFirstInCase) notFirstInCase = true;
                else outputBuilder.add(statementGg.mid());
                outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                        .add(new Text("code will be replaced"))
                        .add(statementGg.mid())
                        .add(sa.statement().output(qualification, sa));

                LimitedStatementAnalysis moreReplaced = sa.navigationNextIsSet() ? sa.navigationNextGetOrElseNull() : null;
                if (moreReplaced != null) {
                    statementsString(qualification, outputBuilder, statementGg, moreReplaced, true); // recursion!
                }
                outputBuilder.add(statementGg.mid()).add(Symbol.RIGHT_BLOCK_COMMENT);
                sa = sa.navigationReplacementGet();
            }
            if (!notFirstInCase) notFirstInCase = true;
            else outputBuilder.add(statementGg.mid());
            outputBuilder.add(sa.statement().output(qualification, sa));
            sa = sa.navigationNextIsSet() ? sa.navigationNextGetOrElseNull() : null;
        }
        if (statementGg != null) {
            outputBuilder.add(statementGg.end());
        }
    }

    public ParameterizedType mostSpecificReturnType(InspectionProvider inspectionProvider, TypeInfo primaryType) {
        AtomicReference<ParameterizedType> mostSpecific = new AtomicReference<>();
        Primitives primitives = inspectionProvider.getPrimitives();
        visit(statement -> {
            if (statement instanceof ReturnStatement returnStatement) {
                if (returnStatement.expression == EmptyExpression.EMPTY_EXPRESSION) {
                    mostSpecific.set(primitives.voidParameterizedType());
                } else if (returnStatement.expression.isInstanceOf(NullConstant.class)) {
                    if (mostSpecific.get() == null) {
                        mostSpecific.set(primitives.objectParameterizedType());
                    }
                } else {
                    ParameterizedType returnType = returnStatement.expression.returnType();
                    mostSpecific.set(mostSpecific.get() == null ? returnType : mostSpecific.get()
                            .mostSpecific(inspectionProvider, primaryType, returnType));
                }
            }
        });
        return mostSpecific.get() == null ? primitives.voidParameterizedType() : mostSpecific.get();
    }

    @Override
    public List<? extends Element> subElements() {
        return structure.statements();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            structure.statements().forEach(statement -> statement.visit(predicate));
        }
    }

    @Override
    public Statement translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        if (isEmpty()) return this;
        return new Block(identifier, structure.statements().stream()
                .flatMap(st -> Objects.requireNonNull(translationMap.translateStatement(inspectionProvider, st),
                        "Translation of statement of " + st.getClass() + " returns null: " + st).stream())
                .collect(Collectors.toList()), label);
    }

    public boolean isEmpty() {
        return structure.statements().isEmpty();
    }

    @Override
    public Structure getStructure() {
        return structure;
    }
}

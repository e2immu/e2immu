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

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class Block extends StatementWithStructure {
    public final String label;

    public static Block emptyBlock(Identifier identifier) {
        return new Block(identifier, List.of(), null, null);
    }

    private Block(Identifier identifier, @NotNull List<Statement> statements, String label, Comment comment) {
        super(identifier, new Structure.Builder()
                .setStatementExecution(StatementExecution.ALWAYS)
                .setComment(comment)
                .setStatements(statements).build());
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Block other) {
            return Objects.equals(label, other.label) && structure.equals(other.structure);
        }
        return false;
    }

    @Override
    public int getComplexity() {
        return 1 + structure.statements().stream().mapToInt(Statement::getComplexity).sum();
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, structure);
    }

    @Container(builds = Block.class)
    public static class BlockBuilder {
        private final List<Statement> statements = new ArrayList<>();
        private String label;
        private final Identifier identifier;
        private Comment comment;

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

        public void setComment(Comment comment) {
            this.comment = comment;
        }

        @NotModified
        @NotNull
        public Block build() {
            if (statements.isEmpty()) return emptyBlock(identifier);
            // NOTE: we don't do labels on empty blocks. that's pretty useless anyway
            return new Block(identifier, List.copyOf(statements), label, comment);
        }

        public int size() {
            return statements.size();
        }

        @Fluent
        public BlockBuilder addStatements(Collection<Statement> statements) {
            this.statements.addAll(statements);
            return this;
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
                        .map(s -> outputStatement(s, qualification))
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

    private OutputBuilder outputStatement(Statement statement, Qualification qualification) {
        OutputBuilder ob = statement.output(qualification, null);
        Comment comment = statement.getStructure().comment();
        if (comment != null) {
            return comment.output(qualification).add(ob);
        }
        return ob;
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

    /*
    IMPORTANT: blocks must translate into blocks
     */
    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) {
            assert direct.size() == 1 && direct.get(0) instanceof Block;
            return direct;
        }
        boolean change = false;
        List<Statement> tStatements = new ArrayList<>(2 * structure.statements().size());
        for (Statement statement : structure.statements()) {
            List<Statement> tStatement = statement.translate(inspectionProvider, translationMap);
            tStatements.addAll(tStatement);
            change |= tStatement.size() != 1 || tStatement.get(0) != statement;
        }
        if (change) {
            return List.of(new Block(identifier, tStatements, label, structure.comment()));
        }
        return List.of(this);
    }

    public boolean isEmpty() {
        return structure.statements().isEmpty();
    }

    @Override
    public Structure getStructure() {
        return structure;
    }
}

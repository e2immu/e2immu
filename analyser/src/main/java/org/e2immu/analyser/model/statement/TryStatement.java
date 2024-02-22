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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.Precedence;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.*;
import org.e2immu.analyser.util2.PackedIntMap;
import org.e2immu.graph.analyser.PackedInt;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.LimitedStatementAnalysis.startOfBlock;

/*
The execution and condition values are similar to that of an IfElse

if(!catch1 && !catch2) {
   ...
} else if(catch1) {
   ...
} else if(catch2) {
  ...
}
{
  finally
}
 */
public class TryStatement extends StatementWithStructure {
    public final List<Expression> resources;
    public final List<Pair<CatchParameter, Block>> catchClauses;
    public final Block finallyBlock;
    private final List<? extends Element> subElements;

    public TryStatement(Identifier identifier,
                        String label,
                        List<Expression> resources,
                        Block tryBlock,
                        List<Pair<CatchParameter, Block>> catchClauses,
                        Block finallyBlock,
                        Comment comment) {
        super(identifier, label, codeOrganization(resources, tryBlock, catchClauses, finallyBlock, comment));
        this.resources = List.copyOf(resources);
        this.catchClauses = List.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
        subElements = ListUtil.immutableConcat(
                this.resources,
                List.of(tryBlock),
                catchClauses.stream().map(Pair::getV).collect(Collectors.toList()),
                finallyBlock.isEmpty() ? List.of() : List.of(finallyBlock));
    }

    private static Structure codeOrganization(List<Expression> resources,
                                              Block tryBlock,
                                              List<Pair<CatchParameter, Block>> catchClauses,
                                              Block finallyBlock,
                                              Comment comment) {
        Structure.Builder builder = new Structure.Builder()
                .setCreateVariablesInsideBlock(true)
                .addInitialisers(resources)
                // CONDITIONALLY: try is executed when not one of the catch-clauses is executed
                // (there is one Instance-boolean per catch clause)
                .setStatementExecution(StatementExecution.CONDITIONALLY)
                .setBlock(tryBlock); //there's always the main block
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            builder.addSubStatement(new Structure.Builder()
                    .addInitialisers(List.of(pair.k.localVariableCreation))
                    .setStatementExecution(StatementExecution.CONDITIONALLY)
                    .setBlock(pair.v).build());
        }
        if (finallyBlock != null) {
            builder.addSubStatement(new Structure.Builder()
                    .setExpression(EmptyExpression.FINALLY_EXPRESSION)
                    .setBlock(finallyBlock)
                    .setStatementExecution(StatementExecution.ALWAYS)
                    .build());
        }
        return builder
                .setComment(comment)
                .build();
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof TryStatement other) {
            return identifier.equals(other.identifier)
                    && resources.equals(other.resources)
                    && subElements.equals(other.subElements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, resources, subElements);
    }

    @Override
    public int getComplexity() {
        return resources.stream().mapToInt(Expression::getComplexity).sum()
                + structure.block().getComplexity()
                + catchClauses.stream().mapToInt(cc -> cc.v.getComplexity() + 2).sum()
                + finallyBlock.getComplexity();
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        // translations in order of appearance
        List<Expression> resources = this.resources.stream()
                .map(r -> r.translate(inspectionProvider, translationMap))
                .collect(Collectors.toList());
        List<Statement> translatedBlock = structure.block().translate(inspectionProvider, translationMap);
        List<Pair<CatchParameter, Block>> translatedCatchClauses = catchClauses.stream()
                .map(p -> new Pair<>(
                        ConstructorCall.ensureExpressionType(p.k.translate(inspectionProvider, translationMap), CatchParameter.class),
                        ensureBlock(p.v.identifier, p.v.translate(inspectionProvider, translationMap))))
                .collect(Collectors.toList());
        List<Statement> translatedFinally = finallyBlock.translate(inspectionProvider, translationMap);

        return List.of(new TryStatement(identifier, label, resources,
                ensureBlock(structure.block().identifier, translatedBlock),
                translatedCatchClauses,
                ensureBlock(finallyBlock.identifier, translatedFinally),
                structure.comment()));
    }

    public static class CatchParameter extends BaseExpression implements Expression {
        public final LocalVariableCreation localVariableCreation;
        public final List<ParameterizedType> unionOfTypes;

        public CatchParameter(Identifier identifier,
                              LocalVariableCreation localVariableCreation,
                              List<ParameterizedType> unionOfTypes) {
            super(identifier, 2);
            this.localVariableCreation = localVariableCreation;
            this.unionOfTypes = List.copyOf(unionOfTypes);
        }

        @Override
        public UpgradableBooleanMap<TypeInfo> typesReferenced() {
            return UpgradableBooleanMap.of(unionOfTypes.stream().flatMap(pt -> pt.typesReferenced(true).stream())
                    .collect(UpgradableBooleanMap.collector()));
        }

        @Override
        public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
            return PackedIntMap.of(unionOfTypes.stream().flatMap(pt -> pt.typesReferenced2(weight).stream())
                    .collect(PackedIntMap.collector()));
        }

        @Override
        public ParameterizedType returnType() {
            return null;
        }

        @Override
        public OutputBuilder output(Qualification qualification) {
            return new OutputBuilder()
                    .add(unionOfTypes.stream()
                            .map(pt -> new OutputBuilder().add(pt.typeInfo.typeName(
                                    qualification.qualifierRequired(pt.typeInfo))))
                            .collect(OutputBuilder.joining(Symbol.PIPE)))
                    .add(Space.ONE).add(new Text(localVariableCreation.localVariableReference.simpleName()));
        }

        @Override
        public Precedence precedence() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int order() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
            throw new ExpressionComparator.InternalError();
        }

        @Override
        public void visit(Predicate<Element> predicate) {
            if (predicate.test(this)) {
                localVariableCreation.visit(predicate);
            }
        }

        @Override
        public void visit(Visitor visitor) {
            if (visitor.beforeExpression(this)) {
                localVariableCreation.visit(visitor);
            }
            visitor.afterExpression(this);
        }
    }

    // TODO we may want to change output to have the GuideGenerator in the parameter to align catch and finally
    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(Keyword.TRY);
        if (!resources.isEmpty()) {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(resources.stream().map(expression -> expression
                            .output(qualification)).collect(OutputBuilder.joining(Symbol.SEMICOLON)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        outputBuilder
                .addIfNotNull(messageComment(statementAnalysis))
                .add(structure.block().output(qualification, startOfBlock(statementAnalysis, 0)));
        int i = 1;
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            outputBuilder.add(Keyword.CATCH)
                    .add(Symbol.LEFT_PARENTHESIS)
                    .add(pair.k.output(qualification)).add(Symbol.RIGHT_PARENTHESIS)
                    .add(pair.v.output(qualification, startOfBlock(statementAnalysis, i)));
            i++;
        }
        if (!finallyBlock.isEmpty()) {
            outputBuilder
                    .add(Keyword.FINALLY)
                    .add(finallyBlock.output(qualification, startOfBlock(statementAnalysis, i)));
        }
        return outputBuilder;
    }

    @Override
    public List<? extends Element> subElements() {
        return subElements;
    }


    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            subElements.forEach(e -> e.visit(predicate));
        }
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeStatement(this)) {
            resources.forEach(e -> e.visit(visitor));
            visitor.startSubBlock(0);
            structure.block().visit(visitor);
            visitor.endSubBlock(0);
            int i = 1;
            for (Pair<CatchParameter, Block> pair : catchClauses) {
                visitor.startSubBlock(i);
                pair.k.visit(visitor);
                pair.v.visit(visitor);
                visitor.endSubBlock(i);
                i++;
            }
            if (!finallyBlock.isEmpty()) {
                visitor.startSubBlock(i);
                finallyBlock.visit(visitor);
                visitor.endSubBlock(i);
            }
        }
        visitor.afterStatement(this);
    }
}

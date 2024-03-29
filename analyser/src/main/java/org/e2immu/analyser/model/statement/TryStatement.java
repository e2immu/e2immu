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
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.Precedence;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
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
                        List<Expression> resources,
                        Block tryBlock,
                        List<Pair<CatchParameter, Block>> catchClauses,
                        Block finallyBlock) {
        super(identifier, codeOrganization(resources, tryBlock, catchClauses, finallyBlock));
        this.resources = List.copyOf(resources);
        this.catchClauses = List.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
        subElements = ListUtil.immutableConcat(List.of(tryBlock), catchClauses.stream().map(Pair::getV).collect(Collectors.toList()),
                finallyBlock.isEmpty() ? List.of() : List.of(finallyBlock));
    }

    private static Structure codeOrganization(List<Expression> resources,
                                              Block tryBlock,
                                              List<Pair<CatchParameter, Block>> catchClauses,
                                              Block finallyBlock) {
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
        return builder.build();
    }

    @Override
    public Statement translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return new TryStatement(identifier, resources.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(inspectionProvider, structure.block()),
                catchClauses.stream().map(p -> new Pair<>(
                        TranslationMapImpl.ensureExpressionType(p.k.translate(inspectionProvider, translationMap), CatchParameter.class),
                        translationMap.translateBlock(inspectionProvider, p.v))).collect(Collectors.toList()),
                translationMap.translateBlock(inspectionProvider, finallyBlock));
    }

    public static class CatchParameter extends BaseExpression implements Expression {
        public final LocalVariableCreation localVariableCreation;
        public final List<ParameterizedType> unionOfTypes;

        public CatchParameter(Identifier identifier,
                              LocalVariableCreation localVariableCreation,
                              List<ParameterizedType> unionOfTypes) {
            super(identifier);
            this.localVariableCreation = localVariableCreation;
            this.unionOfTypes = List.copyOf(unionOfTypes);
        }

        @Override
        public UpgradableBooleanMap<TypeInfo> typesReferenced() {
            return UpgradableBooleanMap.of(unionOfTypes.stream().flatMap(pt -> pt.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()));
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
                    .add(Space.ONE).add(new Text(localVariableCreation.declarations.get(0).localVariable().name()));
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
        public void visit(Predicate<Element> predicate) {
            throw new UnsupportedOperationException();
        }
    }

    // TODO we may want to change output to have the GuideGenerator in the parameter to align catch and finally
    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("try"));
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
            outputBuilder.add(new Text("catch"))
                    .add(Symbol.LEFT_PARENTHESIS)
                    .add(pair.k.output(qualification)).add(Symbol.RIGHT_PARENTHESIS)
                    .add(pair.v.output(qualification, startOfBlock(statementAnalysis, i)));
            i++;
        }
        if (!finallyBlock.isEmpty()) {
            outputBuilder
                    .add(new Text("finally"))
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
}

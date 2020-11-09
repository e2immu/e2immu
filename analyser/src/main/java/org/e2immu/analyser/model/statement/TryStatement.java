package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.stream.Collectors;

public class TryStatement extends StatementWithStructure {
    public final List<Expression> resources;
    public final List<Pair<CatchParameter, Block>> catchClauses;
    public final Block finallyBlock;
    private final List<? extends Element> subElements;

    public TryStatement(List<Expression> resources,
                        Block tryBlock,
                        List<Pair<CatchParameter, Block>> catchClauses,
                        Block finallyBlock) {
        super(codeOrganization(resources, tryBlock, catchClauses, finallyBlock));
        this.resources = ImmutableList.copyOf(resources);
        this.catchClauses = ImmutableList.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
        subElements = ListUtil.immutableConcat(List.of(tryBlock), catchClauses.stream().map(Pair::getV).collect(Collectors.toList()),
                finallyBlock == Block.EMPTY_BLOCK ? List.of() : List.of(finallyBlock));
    }

    private static Structure codeOrganization(List<Expression> resources,
                                              Block tryBlock,
                                              List<Pair<CatchParameter, Block>> catchClauses,
                                              Block finallyBlock) {
        Structure.Builder builder = new Structure.Builder()
                .setCreateVariablesInsideBlock(true)
                .addInitialisers(resources)
                .setStatementExecution(StatementExecution.ALWAYS)
                .setBlock(tryBlock); //there's always the main block
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            builder.addSubStatement(new Structure.Builder().setLocalVariableCreation(pair.k.localVariable)
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
    public Statement translate(TranslationMap translationMap) {
        return new TryStatement(resources.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(structure.block),
                catchClauses.stream().map(p -> new Pair<>(
                        TranslationMap.ensureExpressionType(p.k.translate(translationMap), CatchParameter.class),
                        translationMap.translateBlock(p.v))).collect(Collectors.toList()),
                translationMap.translateBlock(finallyBlock));
    }

    public static class CatchParameter implements Expression {
        public final LocalVariable localVariable;

        public final List<ParameterizedType> unionOfTypes;

        public CatchParameter(LocalVariable localVariable, List<ParameterizedType> unionOfTypes) {
            this.localVariable = localVariable;
            this.unionOfTypes = ImmutableList.copyOf(unionOfTypes);
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
        public String expressionString(int indent) {
            return unionOfTypes.stream().map(type -> type.typeInfo.simpleName).collect(Collectors.joining(" | "))
                    + " " + localVariable.name;
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("try");
        if (!resources.isEmpty()) {
            sb.append("(");
            sb.append(resources.stream().map(r -> r.expressionString(0)).collect(Collectors.joining("; ")));
            sb.append(")");
        }
        sb.append(structure.block.statementString(indent, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
        int i = 1;
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            sb.append(" catch(");
            sb.append(pair.k.expressionString(0));
            sb.append(")");
            sb.append(pair.v.statementString(indent, StatementAnalysis.startOfBlock(statementAnalysis, i)));
            i++;
        }
        if (finallyBlock != Block.EMPTY_BLOCK) {
            sb.append(" finally");
            sb.append(finallyBlock.statementString(indent, StatementAnalysis.startOfBlock(statementAnalysis, i)));
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return structure.block.sideEffect(evaluationContext);
    }

    @Override
    public List<? extends Element> subElements() {
        return subElements;
    }
}

package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
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

    public static record CatchParameter(LocalVariable localVariable,
                                        List<ParameterizedType> unionOfTypes) implements Expression {
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
        public OutputBuilder output() {
            return new OutputBuilder().add(unionOfTypes.stream().map(pt -> new TypeName(pt.typeInfo)).collect(OutputBuilder.joinElements(Symbol.PIPE)))
                    .add(Spacer.HARD).add(new Text(localVariable.name));
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int order() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NewObject getInstance(EvaluationContext evaluationContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectFlow getObjectFlow() {
            return ObjectFlow.NO_FLOW;
        }
    }

    // TODO we may want to change output to have the GuideGenerator in the parameter to align catch and finally
    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("try"));
        if (!resources.isEmpty()) {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(resources.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.SEMICOLON)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        outputBuilder.add(structure.block.output(StatementAnalysis.startOfBlock(statementAnalysis, 0)));
        int i = 1;
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            outputBuilder.add(new Text("catch"))
                    .add(Symbol.LEFT_PARENTHESIS)
                    .add(pair.k.output()).add(Symbol.RIGHT_PARENTHESIS)
                    .add(pair.v.output(StatementAnalysis.startOfBlock(statementAnalysis, i)));
            i++;
        }
        if (finallyBlock != Block.EMPTY_BLOCK) {
            outputBuilder
                    .add(new Text("finally"))
                    .add(finallyBlock.output(StatementAnalysis.startOfBlock(statementAnalysis, i)));
        }
        return outputBuilder;
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

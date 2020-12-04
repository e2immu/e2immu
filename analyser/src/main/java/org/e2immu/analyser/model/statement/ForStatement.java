package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ForStatement extends LoopStatement {

    /**
     * TODO we can go really far here in analysing the initialiser, condition, and updaters.
     * We should. This will provide a better executedAtLeastOnce predicate.
     *
     * @param label     the label of the block
     * @param condition Cannot be null, but can be EmptyExpression
     * @param block     cannot be null, but can be EmptyBlock
     */
    public ForStatement(String label, List<Expression> initialisers, Expression condition, List<Expression> updaters, Block block) {
        super(new Structure.Builder()
                .setStatementExecution(StatementExecution.CONDITIONALLY)
                .setCreateVariablesInsideBlock(true)
                .addInitialisers(initialisers)
                .setExpression(condition)
                .setExpressionIsCondition(true)
                .setUpdaters(updaters)
                .setBlock(block).build(), label);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForStatement(label,
                structure.initialisers.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateExpression(expression),
                structure.updaters.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(structure.block));
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (label != null) {
            outputBuilder.add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        return outputBuilder.add(new Text("for"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.initialisers.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.SEMICOLON)
                .add(structure.expression.output())
                .add(Symbol.SEMICOLON)
                .add(structure.updaters.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(structure.block.output(StatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(structure.initialisers,
                List.of(expression),
                structure.updaters,
                List.of(structure.block));
    }
}

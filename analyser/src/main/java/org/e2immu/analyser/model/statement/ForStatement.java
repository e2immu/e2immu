package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.StringUtil;

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
                .setStatementsExecutedAtLeastOnce((v, ec) -> false)
                .setCreateVariablesInsideBlock(true)
                .addInitialisers(initialisers)
                .setExpression(condition)
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
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append("for(");
        sb.append(structure.initialisers.stream().map(i -> i.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append("; ");
        sb.append(expression.expressionString(0));
        sb.append("; ");
        sb.append(structure.updaters.stream().map(u -> u.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append(")");
        sb.append(structure.block.statementString(indent, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(structure.initialisers,
                List.of(expression),
                structure.updaters,
                List.of(structure.block));
    }
}

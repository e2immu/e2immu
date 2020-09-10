package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
        super(new CodeOrganization.Builder()
                .setStatementsExecutedAtLeastOnce(v -> false)
                .addInitialisers(initialisers)
                .setExpression(condition)
                .setUpdaters(updaters)
                .setBlock(block).build(), label);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForStatement(label,
                codeOrganization.initialisers.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateExpression(codeOrganization.expression),
                codeOrganization.updaters.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(codeOrganization.block));
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append("for(");
        sb.append(codeOrganization.initialisers.stream().map(i -> i.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append("; ");
        sb.append(codeOrganization.expression.expressionString(0));
        sb.append("; ");
        sb.append(codeOrganization.updaters.stream().map(u -> u.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append(")");
        sb.append(codeOrganization.block.statementString(indent, NumberedStatement.startOfBlock(numberedStatement, 0)));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(codeOrganization.initialisers,
                List.of(codeOrganization.expression),
                codeOrganization.updaters,
                List.of(codeOrganization.block));
    }
}

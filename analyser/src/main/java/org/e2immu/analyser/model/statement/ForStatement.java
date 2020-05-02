package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ForStatement extends LoopStatement {
    public final List<Expression> initialisers;
    public final List<Expression> updaters;

    /**
     * @param label
     * @param condition Cannot be null, but can be EmptyExpression
     * @param block     cannot be null, but can be EmptyBlock
     */
    public ForStatement(String label, List<Expression> initialisers, Expression condition, List<Expression> updaters, Block block) {
        super(label, condition, block, v -> false);
        // TODO we can go really far here in analysing the initialiser, condition, and updaters. We should. This will provide a better executedAtLeastOnce predicate.
        this.initialisers = ImmutableList.copyOf(initialisers);
        this.updaters = ImmutableList.copyOf(updaters);
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append("for(");
        sb.append(initialisers.stream().map(i -> i.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append("; ");
        sb.append(expression.expressionString(0));
        sb.append("; ");
        sb.append(updaters.stream().map(u -> u.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append(")");
        sb.append(block.statementString(indent));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder()
                .addInitialisers(initialisers)
                .setExpression(expression)
                .setUpdaters(updaters)
                .setStatements(block).build();
    }
}

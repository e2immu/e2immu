package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ForStatement extends LoopStatement {
    public final List<Expression> initialisers;
    public final List<Expression> updaters;

    /**
     * @param condition Cannot be null, but can be EmptyExpression
     * @param block     cannot be null, but can be EmptyBlock
     */
    public ForStatement(List<Expression> initialisers, Expression condition, List<Expression> updaters, Block block) {
        super(condition, block);
        this.initialisers = ImmutableList.copyOf(initialisers);
        this.updaters = ImmutableList.copyOf(updaters);
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("for(");
        sb.append(initialisers.stream().map(i -> i.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append(";");
        sb.append(expression.expressionString(0));
        sb.append(";");
        sb.append(updaters.stream().map(u -> u.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append(")");
        return sb.toString();
    }
}

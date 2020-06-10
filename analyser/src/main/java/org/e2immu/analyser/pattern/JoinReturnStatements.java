package org.e2immu.analyser.pattern;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;

import java.util.List;

/*

The goal of this pattern is to standardize a number of constructs involving return statements.



 */
public class JoinReturnStatements {
    public final EvaluationContext evaluationContext;

    public JoinReturnStatements(EvaluationContext evaluationContext) {
        this.evaluationContext = evaluationContext;
    }

    /*
    if(condition) { ... return a; } else { ... return b; }

    with the statements in between not modifying the condition
     */
    public Value joinReturnStatementsInIfThenElse(List<NumberedStatement> statements) {
        if (statements.isEmpty()) return null;
        NumberedStatement last = statements.get(statements.size() - 1);
        if (last.inErrorState()) return null;
        if (!(last.statement instanceof IfElseStatement)) return null;
        String statementId = last.streamIndices();
        IfElseStatement ifElseStatement = (IfElseStatement) last.statement;
        String idOfThenReturn = statementId + ".0." + (ifElseStatement.ifBlock.statements.size() - 1);
        String idOfElseReturn = statementId + ".1." + (ifElseStatement.elseBlock.statements.size() - 1);
        MethodInfo methodInfo = evaluationContext.getCurrentMethod();
        TransferValue thenTv = methodInfo.methodAnalysis.get().returnStatementSummaries.getOtherwiseNull(idOfThenReturn);
        TransferValue elseTv = methodInfo.methodAnalysis.get().returnStatementSummaries.getOtherwiseNull(idOfElseReturn);
        if (thenTv == null || elseTv == null) return null;
        Value condition = last.valueOfExpression.get();
        // TODO check that statements in between do not modify the condition
        return ConditionalValue.conditionalValue(evaluationContext, condition, thenTv.value.get(), elseTv.value.get());
    }

    /**
     * if(condition) return a;
     * ...
     * return b;
     * <p>
     * where the statements in between are not modifying the condition
     *
     * @param statements the statements of a block
     * @return null upon failure to detect something
     */
    public Value joinReturnStatements(List<NumberedStatement> statements) {
        int n = statements.size();
        if (n < 2) return null;

        NumberedStatement last = statements.get(n - 1);
        if (!(last.statement instanceof ReturnStatement)) return null;
        NumberedStatement ifStatement = null;
        IfElseStatement ifElseStatement = null;
        for (int i = statements.size() - 1; i >= 0; i--) {
            NumberedStatement ns = statements.get(i);
            if (ns.statement instanceof IfElseStatement) {
                ifElseStatement = (IfElseStatement) ns.statement;
                if (ifElseStatement.elseBlock == Block.EMPTY_BLOCK) {
                    ifStatement = ns;
                    break;
                }
            }
            // TODO check that the statements that we skip do not modify the condition!
        }
        if (ifStatement == null) return null;

        String statementId = ifStatement.streamIndices();
        String idOfThenReturn = statementId + ".0." + (ifElseStatement.ifBlock.statements.size() - 1);
        MethodInfo methodInfo = evaluationContext.getCurrentMethod();
        TransferValue thenTv = methodInfo.methodAnalysis.get().returnStatementSummaries.getOtherwiseNull(idOfThenReturn);

        Value condition = ifStatement.valueOfExpression.get();
        return ConditionalValue.conditionalValue(evaluationContext, condition, thenTv.value.get(), last.valueOfExpression.get());
    }

}

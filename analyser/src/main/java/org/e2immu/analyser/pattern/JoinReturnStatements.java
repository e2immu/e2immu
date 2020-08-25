package org.e2immu.analyser.pattern;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.List;
import java.util.Set;

/*

The goal of this pattern is to standardize a number of constructs involving return statements.

This method currently works with top-level statements!

 */
public class JoinReturnStatements {
    public final EvaluationContext evaluationContext;

    public JoinReturnStatements(EvaluationContext evaluationContext) {
        this.evaluationContext = evaluationContext;
    }

    public static final JoinResult DELAY = new JoinResult(UnknownValue.NO_VALUE, Set.of());

    public static class JoinResult {
        public final Value value;
        public final Set<String> statementIdsReduced;

        public JoinResult(Value value, Set<String> statementIdsReduced) {
            this.statementIdsReduced = statementIdsReduced;
            this.value = value;
        }
    }

    /*
    if(condition) { ... return a; } else { ... return b; }

    with the statements in between not modifying the condition
     */
    public JoinResult joinReturnStatementsInIfThenElse(List<NumberedStatement> statements) {
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

        if (!last.valueOfExpression.isSet()) return DELAY;
        Value condition = last.valueOfExpression.get();
        // TODO check that statements in between do not modify the condition
        Value res = ConditionalValue.conditionalValueCurrentState(evaluationContext, condition, thenTv.value.get(), elseTv.value.get(), ObjectFlow.NO_FLOW); // TODO ObjectFlow
        return new JoinResult(res, Set.of(idOfThenReturn, idOfElseReturn));
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
    public JoinResult joinReturnStatements(List<NumberedStatement> statements) {
        int n = statements.size();
        if (n < 2) return null;

        NumberedStatement last = statements.get(n - 1);
        if (!(last.statement instanceof ReturnStatement)) return null;
        NumberedStatement ifStatement = null;
        NumberedStatement beforeIfStatement = null;
        IfElseStatement ifElseStatement = null;
        for (int i = statements.size() - 1; i >= 0; i--) {
            NumberedStatement ns = statements.get(i);
            if (ns.statement instanceof IfElseStatement) {
                ifElseStatement = (IfElseStatement) ns.statement;
                if (ifElseStatement.elseBlock == Block.EMPTY_BLOCK) {
                    ifStatement = ns;
                    if (i >= 1) beforeIfStatement = statements.get(i - 1);
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
        if (thenTv == null) return null;

        if (!ifStatement.valueOfExpression.isSet()) return DELAY;
        // once we know there is no delay, we can still exclude if there's a problem
        if (ifStatement.inErrorState()) return null;

        Value condition = ifStatement.valueOfExpression.get();
        //TODO ObjectFlow

        // after the if-statement containing a return, the current state has been changed. We revert to the state BEFORE the if statement.
        Value stateBeforeIf = beforeIfStatement != null ? beforeIfStatement.state.get() :
                methodInfo.methodAnalysis.get().precondition.isSet() ? methodInfo.methodAnalysis.get().precondition.get() : UnknownValue.EMPTY;

        Value res = ConditionalValue.conditionalValueWithState(evaluationContext, condition, stateBeforeIf,
                thenTv.value.get(), last.valueOfExpression.get(), ObjectFlow.NO_FLOW);
        return new JoinResult(res, Set.of(last.streamIndices(), idOfThenReturn));
    }

}

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.ListUtil;

import java.util.Map;
import java.util.stream.Collectors;

public class CheckPrecondition {

    public static void checkPrecondition(Messages messages,
                                         MethodInfo methodInfo,
                                         MethodAnalysis methodAnalysis,
                                         Map<CompanionMethodName, CompanionAnalysis> companionAnalyses) {
        Expression precondition = methodAnalysis.getPrecondition();
        for (Map.Entry<CompanionMethodName, CompanionAnalysis> entry : companionAnalyses.entrySet()) {
            CompanionMethodName cmn = entry.getKey();
            if (cmn.action() == CompanionMethodName.Action.PRECONDITION) {
                if (precondition == null || precondition instanceof BooleanConstant) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.PRECONDITION_ABSENT));
                    return;
                }
                Expression expectedPrecondition = entry.getValue().getValue();

                if (!precondition.equals(expectedPrecondition)) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.WRONG_PRECONDITION, "Expected: '" +
                            expectedPrecondition + "', but got: '" + precondition + "'"));
                }
            }
        }
    }
}

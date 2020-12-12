package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.Map;

public class CheckPrecondition {

    public static void checkPrecondition(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        Expression precondition = methodAnalysis.getPrecondition();
        for (Map.Entry<CompanionMethodName, CompanionAnalysis> entry : methodAnalysis.getCompanionAnalyses().entrySet()) {
            CompanionMethodName cmn = entry.getKey();
            if (cmn.action() == CompanionMethodName.Action.PRECONDITION) {
                if (precondition == null || precondition.isBoolValueTrue()) {
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

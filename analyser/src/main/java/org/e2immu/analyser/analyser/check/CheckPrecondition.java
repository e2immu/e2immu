package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Precondition;

public class CheckPrecondition {

    public static void checkPrecondition(Messages messages, MethodInfo methodInfo) {
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Precondition.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify
        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        Value precondition = methodAnalysis.getPrecondition();
        if (parameters.absent() && precondition != UnknownValue.EMPTY && precondition != null) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "Precondition"));
            return;
        }
        if (precondition == UnknownValue.EMPTY || precondition == null) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.ANNOTATION_ABSENT, "Precondition"));
            return;
        }
        String inAnnotation = annotationExpression.extract("value", "");
        String inMethod = methodAnalysis.getPrecondition().toString();
        if (!inAnnotation.equals(inMethod)) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.WRONG_PRECONDITION, "Expected: '" +
                    inAnnotation + "', but got: '" + inMethod + "'"));
        }
    }
}

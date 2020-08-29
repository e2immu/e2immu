package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Precondition;

public class CheckPrecondition {

    public static void checkPrecondition(Messages messages, MethodInfo methodInfo) {
        AnnotationExpression annotationExpression = methodInfo.hasTestAnnotation(Precondition.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify
        AnnotationType annotationType = annotationExpression.extract("type", null);
        boolean mustBeAbsent = annotationType == AnnotationType.VERIFY_ABSENT;

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        if (mustBeAbsent && methodAnalysis.precondition.isSet()) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "Precondition"));
            return;
        }
        if (!methodAnalysis.precondition.isSet()) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.ANNOTATION_ABSENT, "Precondition"));
            return;
        }
        String inAnnotation = annotationExpression.extract("value", "");
        String inMethod = methodAnalysis.precondition.get().toString();
        if (!inAnnotation.equals(inMethod)) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.WRONG_PRECONDITION, "Expected: '" +
                    inAnnotation + "', but got: '" + inMethod+"'"));
        }
    }
}

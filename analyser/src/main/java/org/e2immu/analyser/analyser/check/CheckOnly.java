package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;

public class CheckOnly {

    public static void checkOnly(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        MethodAnalysis.MarkAndOnly markAndOnly = methodAnalysis.getMarkAndOnly();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Only.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = markAndOnly == null || markAndOnly.mark();
        if (parameters.absent()) {
            if (noData) return; // fine!
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Only"));
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_ABSENT, "@Only"));
            return;
        }
        String before = annotationExpression.extract("before", "");
        if (before.isEmpty()) {
            if (!markAndOnly.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Missing before=\"" + markAndOnly.markLabel() + "\""));
                return;
            }
        } else {
            if (markAndOnly.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but found after=\"" + markAndOnly.markLabel() + "\""));
                return;
            }
            if (!before.equals(markAndOnly.markLabel())) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed before=\"" + markAndOnly.markLabel() + "\""));
            }
            return;
        }
        String after = annotationExpression.extract("after", "");
        if (after.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing after=\"" + markAndOnly.markLabel() + "\""));
        } else {
            if (!after.equals(markAndOnly.markLabel())) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got after=\"" + after + "\" but computed after=\"" + markAndOnly.markLabel() + "\""));
            }
        }
    }

    public static void checkMark(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        MethodAnalysis.MarkAndOnly markAndOnly = methodAnalysis.getMarkAndOnly();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Mark.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = markAndOnly == null || !markAndOnly.mark();
        if (parameters.absent()) {
            if (noData) return; // fine!
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Mark"));
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_ABSENT, "@Mark"));
            return;
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing value \"" + markAndOnly.markLabel() + "\""));
        } else if (!value.equals(markAndOnly.markLabel())) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + markAndOnly.markLabel() + "\""));
        }
    }
}

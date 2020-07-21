package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;

public class CheckOnly {

    public static void checkOnly(Messages messages, MethodInfo methodInfo) {
        MethodAnalysis.OnlyData onlyData = methodInfo.methodAnalysis.get().onlyData.isSet() ?
                methodInfo.methodAnalysis.get().onlyData.get() : null;
        AnnotationExpression annotationExpression = methodInfo.hasTestAnnotation(Only.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationType annotationType = annotationExpression.extract("type", null);
        boolean mustBeAbsent = annotationType == AnnotationType.VERIFY_ABSENT;
        boolean noData = onlyData == null || onlyData.mark;
        if (mustBeAbsent) {
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
            if (!onlyData.after) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Missing before=\"" + onlyData.markLabel + "\""));
                return;
            }
        } else {
            if (onlyData.after) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but found after=\"" + onlyData.markLabel + "\""));
                return;
            }
            if (!before.equals(onlyData.markLabel)) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed before=\"" + onlyData.markLabel + "\""));
            }
            return;
        }
        String after = annotationExpression.extract("after", "");
        if (after.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing after=\"" + onlyData.markLabel + "\""));
        } else {
            if (!after.equals(onlyData.markLabel)) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got after=\"" + after + "\" but computed after=\"" + onlyData.markLabel + "\""));
            }
        }
    }

    public static void checkMark(Messages messages, MethodInfo methodInfo) {
        MethodAnalysis.OnlyData onlyData = methodInfo.methodAnalysis.get().onlyData.isSet() ?
                methodInfo.methodAnalysis.get().onlyData.get() : null;
        AnnotationExpression annotationExpression = methodInfo.hasTestAnnotation(Mark.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationType annotationType = annotationExpression.extract("type", null);
        boolean mustBeAbsent = annotationType == AnnotationType.VERIFY_ABSENT;
        boolean noData = onlyData == null || !onlyData.mark;
        if (mustBeAbsent) {
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
                    Message.ONLY_WRONG_MARK_LABEL, "Missing value \"" + onlyData.markLabel + "\""));
        } else if (!value.equals(onlyData.markLabel)) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + onlyData.markLabel + "\""));
        }
    }
}

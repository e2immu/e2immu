package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.MethodAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;

public class CheckMarkOnly {

    public static void checkOnly(Messages messages, MethodInfo methodInfo, MethodAnalysisImpl.Builder methodAnalysis) {
        MethodAnalysis.MarkAndOnly markAndOnly = methodAnalysis.getMarkAndOnly();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Only.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = markAndOnly == null || markAndOnly == MethodAnalysis.NO_MARK_AND_ONLY || markAndOnly.mark();
        if (parameters.absent()) {
            if (noData) {
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK_ABSENT);
                return; // fine!
            }
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Only"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.ANNOTATION_ABSENT, "@Only"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return;
        }
        String before = annotationExpression.extract("before", "");
        if (before.isEmpty()) {
            if (!markAndOnly.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Missing before=\"" + markAndOnly.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
        } else {
            if (markAndOnly.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but found after=\"" + markAndOnly.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
            if (!before.equals(markAndOnly.markLabel())) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed before=\"" + markAndOnly.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
            }
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            return;
        }
        String after = annotationExpression.extract("after", "");
        if (after.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing after=\"" + markAndOnly.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        } else {
            if (after.equals(markAndOnly.markLabel())) {
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            } else {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got after=\"" + after + "\" but computed after=\"" + markAndOnly.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
            }
        }
    }

    public static void checkMark(Messages messages, MethodInfo methodInfo, MethodAnalysisImpl.Builder methodAnalysis) {
        MethodAnalysis.MarkAndOnly markAndOnly = methodAnalysis.getMarkAndOnly();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Mark.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = markAndOnly == null || markAndOnly == MethodAnalysis.NO_MARK_AND_ONLY || !markAndOnly.mark();
        if (parameters.absent()) {
            if (noData) return; // fine!
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Mark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_ABSENT, "@Mark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return;
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing value \"" + markAndOnly.markLabel() + "\""));
        } else if (value.equals(markAndOnly.markLabel())) {
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
        } else {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + markAndOnly.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        }
    }
}

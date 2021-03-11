package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.MethodAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.TestMark;

public class CheckMarkOnly {

    public static void checkOnly(Messages messages, MethodInfo methodInfo, MethodAnalysisImpl.Builder methodAnalysis) {
        MethodAnalysis.Eventual eventual = methodAnalysis.getEventual();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Only.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = eventual == null || eventual == MethodAnalysis.NOT_EVENTUAL || eventual.mark()
                || eventual.test() != null;
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
            if (!eventual.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Missing before=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
        } else {
            if (eventual.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but found after=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
            if (!before.equals(eventual.markLabel())) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed before=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
            }
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            return;
        }
        String after = annotationExpression.extract("after", "");
        if (after.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing after=\"" + eventual.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        } else {
            if (after.equals(eventual.markLabel())) {
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            } else {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got after=\"" + after + "\" but computed after=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
            }
        }
    }

    public static void checkMark(Messages messages, MethodInfo methodInfo, MethodAnalysisImpl.Builder methodAnalysis) {
        MethodAnalysis.Eventual eventual = methodAnalysis.getEventual();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(Mark.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = eventual == null || eventual == MethodAnalysis.NOT_EVENTUAL || !eventual.mark();
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
                    Message.ONLY_WRONG_MARK_LABEL, "Missing value \"" + eventual.markLabel() + "\""));
        } else if (value.equals(eventual.markLabel())) {
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
        } else {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + eventual.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        }
    }


    public static void checkTestMark(Messages messages, MethodInfo methodInfo, MethodAnalysisImpl.Builder methodAnalysis) {
        MethodAnalysis.Eventual eventual = methodAnalysis.getEventual();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(TestMark.class).orElse(null);
        if (annotationExpression == null) return; // nothing to verify

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = eventual == null || eventual == MethodAnalysis.NOT_EVENTUAL || eventual.test() == null;
        if (parameters.absent()) {
            if (noData) return; // fine!
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "@TestMark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ANNOTATION_ABSENT, "@TestMark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return;
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Missing value \"" + eventual.markLabel() + "\""));
        } else if (value.equals(eventual.markLabel())) {

            boolean before = annotationExpression.extract("before", true);
            if (before == eventual.test()) {
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            } else {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed \"" + eventual.test() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
            }
        } else {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + eventual.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        }
    }
}

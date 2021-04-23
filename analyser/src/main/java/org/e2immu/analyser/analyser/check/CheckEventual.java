/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.MethodAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.TestMark;

public class CheckEventual {

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
                    Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Only"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.Label.ANNOTATION_ABSENT, "@Only"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return;
        }
        String before = annotationExpression.extract("before", "");
        if (before.isEmpty()) {
            if (!eventual.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Missing before=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
        } else {
            if (eventual.after()) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but found after=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
            if (!before.equals(eventual.markLabel())) {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed before=\"" + eventual.markLabel() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return;
            }
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            return;
        }
        String after = annotationExpression.extract("after", "");
        if (after.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Missing after=\"" + eventual.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        } else {
            if (after.equals(eventual.markLabel())) {
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            } else {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Got after=\"" + after + "\" but computed after=\"" + eventual.markLabel() + "\""));
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
                    Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Mark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ANNOTATION_ABSENT, "@Mark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return;
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Missing value \"" + eventual.markLabel() + "\""));
        } else if (value.equals(eventual.markLabel())) {
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
        } else {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + eventual.markLabel() + "\""));
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
                    Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, "@TestMark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return;
        }
        if (noData) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ANNOTATION_ABSENT, "@TestMark"));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return;
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Missing value \"" + eventual.markLabel() + "\""));
        } else if (value.equals(eventual.markLabel())) {

            boolean before = annotationExpression.extract("before", false);
            if (before == !eventual.test()) {
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.OK);
            } else {
                messages.add(Message.newMessage(new Location(methodInfo),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed \"" + eventual.test() + "\""));
                methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
            }
        } else {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + eventual.markLabel() + "\""));
            methodAnalysis.annotationChecks.put(annotationExpression, Analysis.AnnotationCheck.WRONG);
        }
    }
}

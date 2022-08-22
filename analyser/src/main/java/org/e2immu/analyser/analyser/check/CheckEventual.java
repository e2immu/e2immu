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
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.parser.Message;

public class CheckEventual {

    public static Message checkOnly(MethodInfo methodInfo, AnnotationExpression annotationKey, MethodAnalysis methodAnalysis) {
        MethodAnalysis.Eventual eventual = methodAnalysis.getEventual();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(annotationKey).orElse(null);
        if (annotationExpression == null) {
            if (eventual != MethodAnalysis.NOT_EVENTUAL && eventual.isOnly()) {
                // make sure the @Only(...) annotation gets printed
                AnnotationExpression ae = methodAnalysis.annotationGetOrDefaultNull(annotationKey);
                methodAnalysis.putAnnotationCheck(ae, Analysis.AnnotationCheck.COMPUTED);
            }
            return null; // nothing to verify
        }

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = eventual == null || eventual == MethodAnalysis.NOT_EVENTUAL || eventual.mark()
                || eventual.test() != null;
        if (parameters.absent()) {
            if (noData) {
                methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.OK_ABSENT);
                return null; // fine!
            }
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Only");
        }
        if (noData) {
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return Message.newMessage(methodInfo.newLocation(), Message.Label.ANNOTATION_ABSENT, "@Only");
        }
        String before = annotationExpression.extract("before", "");
        if (before.isEmpty()) {
            if (!eventual.after()) {
                methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return Message.newMessage(methodInfo.newLocation(),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Missing before=\"" + eventual.markLabel() + "\"");
            }
        } else {
            if (eventual.after()) {
                methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return Message.newMessage(methodInfo.newLocation(),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but found after=\"" + eventual.markLabel() + "\"");
            }
            if (!before.equals(eventual.markLabel())) {
                methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
                return Message.newMessage(methodInfo.newLocation(),
                        Message.Label.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed before=\"" + eventual.markLabel() + "\"");
            }
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.OK);
            return null;
        }
        String after = annotationExpression.extract("after", "");
        if (after.isEmpty()) {
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Missing after=\"" + eventual.markLabel() + "\"");
        }
        if (after.equals(eventual.markLabel())) {
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.OK);
            return null;
        }
        methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
        return Message.newMessage(methodInfo.newLocation(),
                Message.Label.ONLY_WRONG_MARK_LABEL, "Got after=\"" + after + "\" but computed after=\"" + eventual.markLabel() + "\"");
    }

    public static Message checkMark(MethodInfo methodInfo, AnnotationExpression annotationKey, MethodAnalysis methodAnalysis) {
        MethodAnalysis.Eventual eventual = methodAnalysis.getEventual();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(annotationKey).orElse(null);
        if (annotationExpression == null) {
            if (eventual != MethodAnalysis.NOT_EVENTUAL && eventual.isMark()) {
                // make sure the @Mark(...) annotation gets printed
                AnnotationExpression ae = methodAnalysis.annotationGetOrDefaultNull(annotationKey);
                methodAnalysis.putAnnotationCheck(ae, Analysis.AnnotationCheck.COMPUTED);
            }
            return null; // nothing to verify
        }

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = eventual == null || eventual == MethodAnalysis.NOT_EVENTUAL || !eventual.mark();
        if (parameters.absent()) {
            if (noData) return null; // fine!
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, "@Mark");
        }
        if (noData) {
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ANNOTATION_ABSENT, "@Mark");
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Missing value \"" + eventual.markLabel() + "\"");
        }
        if (value.equals(eventual.markLabel())) {
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.OK);
            return null;
        }
        methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
        return Message.newMessage(methodInfo.newLocation(),
                Message.Label.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + eventual.markLabel() + "\"");
    }


    public static Message checkTestMark(MethodInfo methodInfo, AnnotationExpression annotationKey, MethodAnalysis methodAnalysis) {
        MethodAnalysis.Eventual eventual = methodAnalysis.getEventual();
        AnnotationExpression annotationExpression = methodInfo.hasInspectedAnnotation(annotationKey).orElse(null);
        if (annotationExpression == null) {
            if (eventual != MethodAnalysis.NOT_EVENTUAL && eventual.isTestMark()) {
                // make sure the @TestMark(...) annotation gets printed
                AnnotationExpression ae = methodAnalysis.annotationGetOrDefaultNull(annotationKey);
                methodAnalysis.putAnnotationCheck(ae, Analysis.AnnotationCheck.COMPUTED);
            }
            return null; // nothing to verify
        }

        AnnotationParameters parameters = annotationExpression.e2ImmuAnnotationParameters();
        boolean noData = eventual == null || eventual == MethodAnalysis.NOT_EVENTUAL || eventual.test() == null;
        if (parameters.absent()) {
            if (noData) return null; // fine!
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.PRESENT);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, "@TestMark");
        }
        if (noData) {
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.MISSING);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ANNOTATION_ABSENT, "@TestMark");
        }
        String value = annotationExpression.extract("value", "");
        if (value.isEmpty()) {
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Missing value \"" + eventual.markLabel() + "\"");
        }
        if (value.equals(eventual.markLabel())) {
            boolean before = annotationExpression.extract("before", false);
            if (before == !eventual.test()) {
                methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.OK);
                return null;
            }
            methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
            return Message.newMessage(methodInfo.newLocation(),
                    Message.Label.ONLY_WRONG_MARK_LABEL, "Got before=\"" + before + "\" but computed \"" + eventual.test() + "\"");
        }
        methodAnalysis.putAnnotationCheck(annotationExpression, Analysis.AnnotationCheck.WRONG);
        return Message.newMessage(methodInfo.newLocation(),
                Message.Label.ONLY_WRONG_MARK_LABEL, "Got \"" + value + "\" but computed \"" + eventual.markLabel() + "\"");
    }
}

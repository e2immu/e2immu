package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Size;

import java.util.Optional;

public class CheckSize {
    public static void checkSizeForMethods(TypeContext typeContext, MethodInfo methodInfo) {
        int size = methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        AnnotationExpression optionalAnnotationExpression = methodInfo.hasTestAnnotation(Size.class).orElse(null);
        String where = "Method " + methodInfo.distinguishingName();
        checkSize(typeContext, size, optionalAnnotationExpression, where);
    }

    public static void checkSizeForFields(TypeContext typeContext, FieldInfo fieldInfo) {
        int size = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.SIZE);
        AnnotationExpression optionalAnnotationExpression = fieldInfo.hasTestAnnotation(Size.class).orElse(null);
        String where = "Field " + fieldInfo.fullyQualifiedName();
        checkSize(typeContext, size, optionalAnnotationExpression, where);
    }

    private static void checkSize(TypeContext typeContext, int size, AnnotationExpression annotationExpression, String where) {
        if (annotationExpression == null) return; // nothing to verify
        AnnotationType annotationType = annotationExpression.extract("type", null);
        boolean mustBeAbsent = annotationType == AnnotationType.VERIFY_ABSENT;

        if (mustBeAbsent && size != Level.DELAY) {
            typeContext.addMessage(Message.Severity.ERROR, where +
                    " has a @Size property, but the annotation claims there should not be one");
            return;
        }
        int sizeMin = annotationExpression.extract("min", -1);
        if (sizeMin >= 1) {
            if (!Analysis.haveEquals(size)) {
                int haveMin = Analysis.sizeMin(size);
                if (haveMin != sizeMin) {
                    typeContext.addMessage(Message.Severity.ERROR, where +
                            " claims @Size(min = " + sizeMin + "), but have @Size(min = " + haveMin + ")");
                }
            } else {
                typeContext.addMessage(Message.Severity.ERROR, where +
                        " claims @Size(min = " + sizeMin + "), but have no minimum value");
            }
            return;
        }
        int sizeEquals = annotationExpression.extract("equals", -1);
        if (sizeEquals >= 0) {
            if (Analysis.haveEquals(size)) {
                int haveEquals = Analysis.sizeEquals(size);
                if (haveEquals != sizeEquals) {
                    typeContext.addMessage(Message.Severity.ERROR, where +
                            " claims @Size(equals = " + sizeEquals + "), but have @Size(equals = " + haveEquals + ")");
                }
            } else {
                typeContext.addMessage(Message.Severity.ERROR, where +
                        " claims @Size(equals = " + sizeEquals + "), but have no exact value");
            }
        }
    }
}

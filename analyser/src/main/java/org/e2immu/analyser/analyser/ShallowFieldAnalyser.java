package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.stream.Stream;

public class ShallowFieldAnalyser {

    private final InspectionProvider inspectionProvider;
    private final Messages messages = new Messages();
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    public ShallowFieldAnalyser(InspectionProvider inspectionProvider, E2ImmuAnnotationExpressions e2) {
        this.inspectionProvider = inspectionProvider;
        e2ImmuAnnotationExpressions = e2;
    }

    public void analyser(FieldInfo fieldInfo, boolean typeIsEnum) {
        FieldAnalysisImpl.Builder fieldAnalysisBuilder = new FieldAnalysisImpl.Builder(inspectionProvider.getPrimitives(),
                AnalysisProvider.DEFAULT_PROVIDER,
                fieldInfo, fieldInfo.owner.typeAnalysis.get());

        messages.addAll(fieldAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.FIELD, true,
                fieldInfo.fieldInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));

        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        boolean enumField = typeIsEnum && fieldInspection.isSynthetic();

        // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
        if (fieldInfo.isExplicitlyFinal() || enumField) {
            fieldAnalysisBuilder.setProperty(VariableProperty.FINAL, Level.TRUE);
        }
        // unless annotated with something heavier, ...
        if (enumField && fieldAnalysisBuilder.getProperty(VariableProperty.EXTERNAL_NOT_NULL) == Level.DELAY) {
            fieldAnalysisBuilder.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
        }

        if (fieldAnalysisBuilder.getProperty(VariableProperty.FINAL) == Level.TRUE && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
            Expression initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser();
            Expression value;
            if (initialiser instanceof ConstantExpression<?> constantExpression) {
                value = constantExpression;
            } else {
                value = EmptyExpression.EMPTY_EXPRESSION; // IMPROVE
            }
            fieldAnalysisBuilder.effectivelyFinalValue.set(value);
        }
        fieldInfo.setAnalysis(fieldAnalysisBuilder.build());
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}

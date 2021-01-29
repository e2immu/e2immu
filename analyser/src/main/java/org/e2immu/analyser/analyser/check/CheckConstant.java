package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.NoValue;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Constant;

import java.util.List;
import java.util.function.Function;

public record CheckConstant(Primitives primitives, E2ImmuAnnotationExpressions e2) {

    public void checkConstantForFields(Messages messages, FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Expression singleReturnValue = fieldAnalysis.getEffectivelyFinalValue() != null ?
                fieldAnalysis.getEffectivelyFinalValue() : NoValue.EMPTY;
        checkConstant(messages, (AbstractAnalysisBuilder) fieldAnalysis,
                singleReturnValue,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }

    public void checkConstantForMethods(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        Expression singleReturnValue = methodAnalysis.getSingleReturnValue();
        checkConstant(messages, (AbstractAnalysisBuilder) methodAnalysis,
                singleReturnValue,
                methodInfo.methodInspection.get().getAnnotations(),
                new Location(methodInfo));
    }

    private void checkConstant(Messages messages,
                               AbstractAnalysisBuilder analysis,
                               Expression singleReturnValue,
                               List<AnnotationExpression> annotations,
                               Location where) {
        boolean isConstant = analysis.getPropertyAsIs(VariableProperty.CONSTANT) == Level.TRUE;
        String computedValue = isConstant ? singleReturnValue.minimalOutput() : null;
        Function<AnnotationExpression, String> extractInspected = ae -> {
            String value = ae.extract("value", "");
            return singleReturnValue instanceof StringConstant ? StringUtil.quote(value) : value;
        };

        CheckLinks.checkAnnotationWithValue(messages,
                analysis,
                Constant.class.getName(),
                "@Constant",
                e2.constant.typeInfo(),
                extractInspected,
                computedValue,
                annotations,
                where);
    }


    public AnnotationExpression createConstantAnnotation(E2ImmuAnnotationExpressions e2, Expression value) {
        // we want to avoid double ""
        String constant = value instanceof StringConstant stringConstant ? stringConstant.constant() : value.minimalOutput();
        Expression valueExpression = new MemberValuePair(new StringConstant(primitives(), constant));
        List<Expression> expressions = List.of(valueExpression);
        return new AnnotationExpressionImpl(e2.constant.typeInfo(), expressions);
    }
}

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

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MemberValuePair;
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
                fieldAnalysis.getEffectivelyFinalValue() : EmptyExpression.EMPTY_EXPRESSION;
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

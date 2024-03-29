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

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Constant;

import java.util.List;
import java.util.function.Function;

public record CheckConstant(Primitives primitives, E2ImmuAnnotationExpressions e2) {

    public Message checkConstantForFields(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Expression singleReturnValue = fieldAnalysis.getValue() != null ?
                fieldAnalysis.getValue() : EmptyExpression.EMPTY_EXPRESSION;
        return checkConstant(fieldAnalysis,
                singleReturnValue,
                fieldInfo.fieldInspection.get().getAnnotations(),
                fieldInfo.newLocation());
    }

    public Message checkConstantForMethods(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        Expression singleReturnValue = methodAnalysis.getSingleReturnValue();
        return checkConstant(methodAnalysis,
                singleReturnValue,
                methodInfo.methodInspection.get().getAnnotations(),
                methodInfo.newLocation());
    }

    private Message checkConstant(Analysis analysis,
                                  Expression singleReturnValue,
                                  List<AnnotationExpression> annotations,
                                  Location where) {
        boolean isConstant = analysis.getPropertyFromMapDelayWhenAbsent(Property.CONSTANT).valueIsTrue();
        String computedValue = isConstant ? singleReturnValue.minimalOutput() : null;
        Function<AnnotationExpression, String> extractInspected = ae -> {
            String value = ae.extract("value", "");
            return singleReturnValue instanceof StringConstant ? StringUtil.quote(value) : value;
        };

        return CheckLinks.checkAnnotationWithValue(analysis,
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
        MemberValuePair valueExpression = new MemberValuePair(new StringConstant(primitives(), constant));
        List<MemberValuePair> expressions = List.of(valueExpression);
        return new AnnotationExpressionImpl(e2.constant.typeInfo(), expressions);
    }
}

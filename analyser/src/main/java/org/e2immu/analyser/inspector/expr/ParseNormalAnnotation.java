package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;


public class ParseNormalAnnotation {
    public static Expression parse(ExpressionContext expressionContext, NormalAnnotationExpr n) {
        AnnotationExpressionImpl.Builder b = new AnnotationExpressionImpl.Builder();
        Expression expression = ParseNameExpr.parse(expressionContext, n.getNameAsString(), Identifier.from(n));
        if (expression instanceof TypeExpression typeExpression) {
            b.setTypeInfo(typeExpression.parameterizedType.typeInfo);
            n.getPairs().stream().map(mvp -> {
                String key = mvp.getNameAsString();
                Expression e = expressionContext.parseExpression(mvp.getValue(), new ForwardReturnTypeInfo());
                return new MemberValuePair(key, e);
            }).forEach(b::addExpression);
            return b.build();
        } else throw new UnsupportedOperationException("? got " + expression);
    }
}

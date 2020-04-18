package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.TypeContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckLinks {

    public static AnnotationExpression createLinkAnnotation(TypeContext typeContext, Set<Variable> links) {
        Expression computed = typeContext.constant.get().expressions.get().get(0);
        List<Expression> linkNameList = links.stream().map(variable -> new StringConstant(variable.name())).collect(Collectors.toList());
        Expression linksStringArray = new MemberValuePair("to", new ArrayInitializer(linkNameList));
        List<Expression> expressions = List.of(computed, linksStringArray);
        return AnnotationExpression.fromAnalyserExpressions(typeContext.linked.get().typeInfo, expressions);
    }

}

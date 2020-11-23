package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.AnnotationExpressionImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckLinks {
    private final Primitives primitives;

    public CheckLinks(Primitives primitives) {
        this.primitives = Objects.requireNonNull(primitives);
    }

    public AnnotationExpression createLinkAnnotation(E2ImmuAnnotationExpressions typeContext, Set<Variable> links) {
        Expression computed = typeContext.constant.expressions().get(0);
        List<Expression> linkNameList = links.stream().map(variable -> new StringConstant(primitives,
                variable.simpleName())).collect(Collectors.toList());
        Expression linksStringArray = new MemberValuePair("to", new ArrayInitializer(primitives, linkNameList));
        List<Expression> expressions = List.of(computed, linksStringArray);
        return new AnnotationExpressionImpl(typeContext.linked.typeInfo(), expressions);
    }

}

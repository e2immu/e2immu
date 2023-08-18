package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;
import java.util.stream.Collectors;


/*
 Full sorting of clauses in an "And" or "Or" is not allowed; we want, e.g., in != null before in.isEmpty()
 On the other hand, the sorting is our mechanism to relatively efficiently detect ways to simplify the expression.

 Different method calls, and especially modifying ones,
 should remain in the current order, even if that is not semantically optimal
 */

public class AndOrSorter {
    private AndOrSorter() {}

    public static ArrayList<Expression> sort(EvaluationResult context, List<Expression> expressions) {
        TreeMap<Expression, List<Expression>> expressionsByObject = new TreeMap<>();
        for (Expression expression : expressions) {
            Expression object = objectOfExpression(expression);
            ArrayList<Expression> list = new ArrayList<>();
            list.add(expression);
            expressionsByObject.merge(object, list, (l1, l2) -> {
                l1.addAll(l2);
                return l1;
            });
        }
        List<List<Expression>> sorted =
                expressionsByObject.values().stream().map(l -> {
                    if (l.size() == 1) return l;
                    return sortList(l);
                }).toList();
        return sorted.stream().flatMap(Collection::stream)
                .collect(Collectors.toCollection(() -> new ArrayList<>(expressions.size())));
    }

    private static List<Expression> sortList(List<Expression> list) {
        ArrayList<Expression> l1 = new ArrayList<>();
        ArrayList<Expression> l2 = new ArrayList<>();
        ArrayList<Expression> l3 = new ArrayList<>();
        for (Expression e : list) {
            if (isNullCheck(e)) l1.add(e);
            else if (isMethodCall(e)) {
                l3.add(e);
            } else l2.add(e);
        }
        ArrayList<Expression> res = new ArrayList<>(list.size());
        Collections.sort(l1);
        Collections.sort(l2);
        // do NOT sort l3
        res.addAll(l1);
        res.addAll(l2);
        res.addAll(l3);
        return res;
    }

    private static boolean isMethodCall(Expression e) {
        if (e instanceof Negation n) return isMethodCall(n.expression);
        if (e instanceof EnclosedExpression ee) return isMethodCall(ee.inner());
        if (e instanceof PropertyWrapper pw) return isMethodCall(pw.expression());
        return e instanceof MethodCall;
    }

    public static Expression objectOfExpression(Expression expression) {
        Expression base;
        if (expression instanceof MethodCall methodCall) {
            if (methodCall.object instanceof TypeExpression && !methodCall.parameterExpressions.isEmpty()) {
                // e.g. Character.toUppercase(a) --> a
                base = methodCall.parameterExpressions.get(0);
            } else {
                // e.g. string.toUppercase() --> s
                base = methodCall.object;
            }
        } else {
            base = expression;
        }
        List<Variable> vars = base.variables(Element.DescendMode.NO);
        if (vars.size() == 1) {
            return new VariableExpression(vars.get(0));
        }
        return expression;
    }

    public static boolean isNullCheck(Expression e) {
        Expression ee;
        if (e instanceof Negation n) {
            ee = n.expression;
        } else {
            ee = e;
        }
        return ee instanceof BinaryOperator eq && (eq.lhs.isNullConstant() != eq.rhs.isNullConstant());
    }

}

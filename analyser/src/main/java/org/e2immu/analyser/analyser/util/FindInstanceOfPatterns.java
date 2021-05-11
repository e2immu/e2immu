package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;

/*
if(x instanceof Y y) --> positive (available in 'then')
if(!(x instanceof Y y)) --> negative (available in 'else')
if(x instanceof Y y && y instanceof Z z) --> concatenate, 2x positive
 */
public class FindInstanceOfPatterns {

    public record InstanceOfPositive(InstanceOf instanceOf, boolean positive) {
    }

    public static List<InstanceOfPositive> find(Expression expression) {
        if (expression instanceof PropertyWrapper pw) return find(pw.expression());
        if (expression instanceof EnclosedExpression ee) return find(ee.inner());

        if (expression instanceof Negation negation) {
            return find(negation.expression).stream()
                    .map(iop -> new InstanceOfPositive(iop.instanceOf, !iop.positive)).toList();
        }
        // expression has most likely not been evaluated yet, so ! can be negation or unary !
        if (expression instanceof UnaryOperator unaryOperator && Primitives.isUnaryNot(unaryOperator.operator)) {
            return find(unaryOperator.expression).stream()
                    .map(iop -> new InstanceOfPositive(iop.instanceOf, !iop.positive)).toList();
        }
        if (expression instanceof InstanceOf instanceOf) {
            return List.of(new InstanceOfPositive(instanceOf, true));
        }
        if (expression instanceof And and) {
            return and.expressions().stream().flatMap(e -> find(e).stream()).toList();
        }
        if (expression instanceof BinaryOperator binaryOperator && Primitives.isBinaryAnd(binaryOperator.operator)) {
            return ListUtil.immutableConcat(find(binaryOperator.lhs), find(binaryOperator.rhs));
        }
        return List.of();
    }
}

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/*
lives at method analyser level: each method starts counting from 0, which simplifies debugging.
 */
public class StatementSimplifier {
    private final AtomicInteger counter = new AtomicInteger();

    public Stream<Statement> simplify(Statement statement) {
        ArrayList<Statement> variableCreations = new ArrayList<>();
        Statement changed = simplify(statement, variableCreations);
        if (variableCreations.isEmpty()) return Stream.of(changed);
        return Stream.concat(variableCreations.stream(), Stream.of(changed));
    }

    private Statement simplify(Statement statement, List<Statement> statements) {
        if (statement instanceof ExpressionAsStatement eas) {
            Expression changed = simplify(eas.expression, statements);
            if (changed == eas.expression) return statement; // no change
            return new ExpressionAsStatement(eas.expression.getIdentifier(), changed);
        }
        return statement;
    }

    private Expression simplify(Expression expression, List<Statement> statements) {
        MethodCall methodCall;
        MethodCall mcObject;
        if ((methodCall = expression.asInstanceOf(MethodCall.class)) != null
                && (mcObject = methodCall.object.asInstanceOf(MethodCall.class)) != null) {
            Expression recursiveObject = simplify(mcObject, statements);
            return replaceMethodCallObject(methodCall, recursiveObject, statements);
        }
        return expression; // no change
    }

    private Expression replaceMethodCallObject(MethodCall mc, Expression mcObject, List<Statement> statements) {
        LocalVariable lv = new LocalVariable("intermediate$" + counter.getAndIncrement(), mcObject.returnType());
        LocalVariableReference lvr = new LocalVariableReference(lv, mcObject);
        Identifier identifier = mcObject.getIdentifier();
        LocalVariableCreation lvc = new LocalVariableCreation(identifier, identifier, lvr);
        statements.add(new ExpressionAsStatement(identifier, lvc));

        VariableExpression ve = new VariableExpression(identifier, lvr);
        return mc.withObject(ve);
    }
}

package org.e2immu.analyser.analyser.methodanalysercomponent;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.STATIC_METHOD_CALLS;
import static org.e2immu.analyser.util.Logger.log;

/**
 * This class gathers the code of the method analyser that is concerned with finding out if a `static` keyword or modifier
 * is missing from a method declaration. If it is, the code will raise an error.
 */
public class StaticModifier {

    public static boolean computeStaticMethodCallsOnly(MethodInfo methodInfo, MethodAnalysis methodAnalysis, List<NumberedStatement> numberedStatements) {
        if (!methodAnalysis.staticMethodCallsOnly.isSet()) {
            if (methodInfo.isStatic) {
                methodAnalysis.staticMethodCallsOnly.set(true);
            } else {
                AtomicBoolean atLeastOneCallOnThis = new AtomicBoolean(false);
                statementVisitor(numberedStatements, numberedStatement -> {
                    Stream<MethodCall> methodCalls = numberedStatement.statement.codeOrganization().findExpressionRecursivelyInStatements(MethodCall.class);
                    boolean callOnThis = methodCalls.anyMatch(methodCall ->
                            !methodCall.methodInfo.isStatic &&
                                    methodCall.object == null || ((methodCall.object instanceof This) &&
                                    ((This) methodCall.object).typeInfo == methodInfo.typeInfo));
                    if (callOnThis) atLeastOneCallOnThis.set(true);
                    return !callOnThis; // we have not found a call on This yet, so we keep on searching!
                });
                boolean staticMethodCallsOnly = !atLeastOneCallOnThis.get();
                log(STATIC_METHOD_CALLS, "Method {} is not static, does it have no calls on <this> scope? {}", methodInfo.fullyQualifiedName(), staticMethodCallsOnly);
                methodAnalysis.staticMethodCallsOnly.set(staticMethodCallsOnly);
            }
            return true;
        }
        return false;
    }


    /**
     * Pure convenience method
     *
     * @param statements the statements to visit
     * @param visitor    will accept a statement; must return true for the visiting to continue
     */
    private static void statementVisitor(List<NumberedStatement> statements, Function<NumberedStatement, Boolean> visitor) {
        if (!statements.isEmpty()) {
            statementVisitor(statements.get(0), visitor);
        }
    }

    /**
     * @param start   starting point, according to the way we have organized statement flows
     * @param visitor will accept a statement; must return true for the visiting to continue
     */
    private static boolean statementVisitor(NumberedStatement start, Function<NumberedStatement, Boolean> visitor) {
        NumberedStatement ns = start;
        while (ns != null) {
            if (!visitor.apply(ns)) return false;
            for (NumberedStatement sub : ns.blocks.get()) {
                if (!statementVisitor(sub, visitor)) return false;
            }
            ns = ns.next.get().orElse(null);
        }
        return true; // continue
    }

    public static void detectMissingStaticModifier(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.complainedAboutMissingStaticModifier.isSet()) {
            if (!methodInfo.isStatic && !methodInfo.typeInfo.isInterface()) {
                // we need to check if there's fields being read/assigned/
                if (absentUnlessStatic(methodAnalysis, VariableProperty.READ) &&
                        absentUnlessStatic(methodAnalysis, VariableProperty.ASSIGNED) &&
                        (methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.READ, Level.DELAY) < Level.TRUE) &&
                        !methodInfo.hasOverrides() &&
                        !methodInfo.isDefaultImplementation &&
                        methodAnalysis.staticMethodCallsOnly.isSet() && methodAnalysis.staticMethodCallsOnly.get()) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.METHOD_SHOULD_BE_MARKED_STATIC));
                    methodAnalysis.complainedAboutMissingStaticModifier.set(true);
                    return;
                }
            }
            methodAnalysis.complainedAboutMissingStaticModifier.set(false);
        }
    }

    private static boolean absentUnlessStatic(MethodAnalysis methodAnalysis, VariableProperty variableProperty) {
        return methodAnalysis.fieldSummaries.stream().allMatch(e -> e.getValue().properties.getOtherwise(variableProperty, Level.DELAY) < Level.TRUE || e.getKey().isStatic());
    }
}

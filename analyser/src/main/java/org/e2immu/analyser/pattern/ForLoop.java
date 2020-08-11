package org.e2immu.analyser.pattern;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.BinaryOperator;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.StringUtil;

import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ForLoop {

    private static final String VARIABLE_STUB = "$";
    private static final Object EMPTY = new Object();

    interface Context {

        TypeParameter requestUnboundTypeParameter(int index);

        Variable requestParameter(int index, ParameterizedType type);

        TypeContext getTypeContext();

    }

    enum Style {
        OLD, FUNCTIONAL, OK,

        EXPENSIVE,

        COMPACT, ELABORATE,

        NON_JDK,
    }

    static class CompareTwoLists {

        public static <S, T> boolean compareTwoEqualSizedArray(S[] ss, T[] ts, BiPredicate<S, T> biPredicate) {
            assert ss.length == ts.length;
            style(Style.ELABORATE);
            start();

            int i = 0;
            for (S s : ss) {
                T t = ts[i];
                if (!biPredicate.test(s, t)) return false;
                i += 1;
            }
            return true;
        }

        public static <S, T> boolean compareTwoEqualSizedArray2(S[] ss, T[] ts, BiPredicate<S, T> biPredicate) {
            assert ss.length == ts.length;
            style(Style.OK);
            start();

            for (int i = 0; i < ss.length; i++) {
                if (!biPredicate.test(ss[i], ts[i])) return false;
            }
            return true;
        }

        public static <S, T> boolean compareTwoEqualSized(List<S> ss, List<T> ts, BiPredicate<S, T> biPredicate) {
            assert ss.size() == ts.size();
            style(Style.EXPENSIVE);
            start();

            int i = 0;
            for (S s : ss) {
                T t = ts.get(i);
                if (!biPredicate.test(s, t)) return false;
                i += 1;
            }
            return true;
        }
    }

    static class LimitInForEach {
        public static <T> void limitForEach(Iterable<T> collection, int limit) {
            style(Style.OK);
            start();

            int count = 0;
            for (T t : collection) {
                anyStatementNoBreakout(0, List.of(), List.of(t, count, collection));

                count += 1;
                if (count >= limit) {
                    anyStatementNoBreakout(1, List.of(), List.of(t, count, collection));
                    break;
                }

                startWarn(); // obviously possible, but not a good idea
                anyStatementNoBreakout(2, List.of(), List.of(t, count, collection));
                endWarn();
            }
        }

        // different break-out pattern; but impossible to do #1
        public static <T> void limitLoop(Iterable<T> collection, int limit) {
            style(Style.OK);
            start();

            Iterator<T> iterator = collection.iterator();
            for (int i = 0; i < limit && iterator.hasNext(); i++) {
                T t = iterator.next();
                anyStatementNoBreakout(0, List.of(), List.of(t, i, collection));
            }
        }

        // impossible to do #1 + impossible to modify other local variables (follows from Style.FUNCTIONAL)
        public static <T> void limitStream(Iterable<T> collection, int limit) {
            style(Style.FUNCTIONAL);
            start();

            StreamSupport.stream(collection.spliterator(), false)
                    .limit(limit)
                    .forEach(t -> anyStatementNoBreakout(0, List.of(), List.of(t, EMPTY, collection)));
        }
    }

    static class ForEachWithOccasionalLogging {
        public static <T> void withOccasionalLogging(Iterable<T> collection, Predicate<Integer> whenToLog) {
            style(Style.OK);
            start();

            int count = 0;
            for (T t : collection) {
                anyStatementNoBreakout(0, List.of(), List.of(t, count, collection));

                count += 1;
                if (whenToLog.test(count)) {
                    consume(1, count);
                }

                startWarn(); // obviously possible, but not a good idea
                anyStatementNoBreakout(2, List.of(), List.of(t, count, collection));
                endWarn();
            }
        }

        // zip with index (protonpack)

        public static <T> void withOccasionalLoggingArrayList(ArrayList<T> collection, Predicate<Integer> whenToLog) {
            style(Style.FUNCTIONAL);
            start();

            IntStream.range(0, collection.size())
                    .peek(i -> {
                        if (whenToLog.test(i)) consume(1, i);
                    })
                    .forEach(i -> {
                        T t = collection.get(i);
                        anyStatementNoBreakout(0, List.of(), List.of(t, i, collection));
                    });
        }

        public static <T> void withOccasionalLoggingArray(T[] array, Predicate<Integer> whenToLog) {
            style(Style.FUNCTIONAL);
            start();

            IntStream.range(0, array.length)
                    .peek(i -> {
                        if (whenToLog.test(i)) consume(1, i);
                    })
                    .forEach(i -> {
                        T t = array[i];
                        anyStatementNoBreakout(0, List.of(), List.of(t, i, array));
                    });
        }
    }


    static class FindInCollection {
        public static <T> T findInCollectionUsingReturn(T defaultValue, Iterable<T> collection, Predicate<T> predicate) {
            style(Style.OK);
            start();

            for (T t : collection) {
                consume(0, t);
                if (predicate.test(t)) {
                    consume(1, t);
                    return t;
                }
                consume(2, t);
            }
            consume(3, defaultValue);
            return defaultValue;
        }

        public static <T> T findInCollectionUsingVariable(T defaultValue, Iterable<T> collection, Predicate<T> predicate) {
            style(Style.ELABORATE);
            start();

            T result = defaultValue;
            for (T t : collection) {
                consume(0, t);
                if (predicate.test(t)) {
                    result = t;
                    consume(1, t);
                    break;
                }
                consume(2, t);
            }

            startSubstitute(3);
            if (result == defaultValue) consume(3, result);
            endSubstitute(3);

            consume(4, result);
            return result;
        }

        public static <T> T findInCollection(T defaultValue, Iterable<T> collection, Predicate<T> predicate) {
            style(Style.OLD);
            start();

            Iterator<T> iterator = collection.iterator();
            while (iterator.hasNext()) {
                T t = iterator.next();
                consume(0, t);
                if (predicate.test(t)) {
                    consume(1, t);
                    startSubstitute(4);
                    consume(4, t);
                    endSubstitute(4);
                    return t;
                }
                consume(2, t);
            }
            consume(3, defaultValue);

            startSubstitute(4);
            consume(4, defaultValue);
            endSubstitute(4);

            return defaultValue; // == end(defaultValue);
        }

        public static <T> T findInList(T defaultValue, List<T> list, Predicate<T> predicate) {
            style(Style.OLD, Style.EXPENSIVE);
            start();

            for (int i = 0; i < list.size(); i++) {
                T t = list.get(i);
                consume(0, t);
                if (predicate.test(t)) {
                    consume(1, t);
                    startSubstitute(4);
                    consume(4, defaultValue);
                    endSubstitute(4);
                    return t;
                }
                consume(2, t);
            }
            consume(3, defaultValue);
            startSubstitute(4);
            consume(4, defaultValue);
            endSubstitute(4);
            return defaultValue;
        }


        public static <T> T findInCollectionUsingStream(T defaultValue, Iterable<T> collection, Predicate<T> predicate) {
            style(Style.FUNCTIONAL, Style.ELABORATE);
            start();


            Stream<T> stream = StreamSupport.stream(collection.spliterator(), false);

            startSubstitute(0);
            stream = stream.peek(t -> consume(0, t));
            endSubstitute(0);

            stream = stream.filter(predicate);

            startSubstitute(2);
            stream = stream.peek(t -> consume(2, t));
            endSubstitute(2);

            T result = stream.findFirst().orElse(defaultValue);

            startSubstitute(1, 3);
            if (result == defaultValue) consume(3, result);
            else consume(1, result);
            endSubstitute(1, 3);

            startSubstitute(1);
            if (result != defaultValue) consume(1, result);
            endSubstitute(1);
            startSubstitute(3);
            if (result == defaultValue) consume(1, result);
            endSubstitute(3);

            consume(4, result);
            return result;
        }

        public static <T> T findInCollectionUsingStream(T defaultValue, Collection<T> collection, Predicate<T> predicate) {
            style(Style.FUNCTIONAL, Style.COMPACT);
            start();

            T result = collection.stream()
                    .peek(t -> consume(0, t))
                    .filter(predicate)
                    .peek(t -> consume(2, t))
                    .findFirst()
                    .orElse(defaultValue);
            if (result == defaultValue) consume(3, result);
            else consume(1, result);
            return result;
        }

        public static <T> T findWithGuava(T defaultValue, Iterable<T> iterable, Predicate<T> predicate) {
            style(Style.FUNCTIONAL, Style.NON_JDK);
            start();

            T result = Iterables.tryFind(iterable, predicate::test).or(defaultValue);
            if (result == defaultValue) consume(3, result);
            else consume(1, result);
            return result;
        }
    }

    public static Statement findInCollectionVariable(Context context) {
        TypeParameter typeParameter = context.requestUnboundTypeParameter(0); // T
        ParameterizedType theType = new ParameterizedType(typeParameter, 0, ParameterizedType.WildCard.UNBOUND);
        LocalVariable localVariable = localVariableStub(0, theType); // T t
        LocalVariableReference localVariableReference = new LocalVariableReference(localVariable, List.of());

        Variable defaultValue = context.requestParameter(1, theType);
        VariableExpression defaultValueExpression = new VariableExpression(defaultValue);

        LocalVariableCreation result = new LocalVariableCreation(localVariableStub(0, theType), defaultValueExpression);
        Assignment createVariable = new Assignment(new VariableExpression(result.localVariableReference), defaultValueExpression);

        TypeInfo iterable = context.getTypeContext().getFullyQualified(Iterable.class);
        ParameterizedType iterableT = new ParameterizedType(iterable, List.of(theType));
        Variable collection = context.requestParameter(0, iterableT);
        VariableExpression collectionExpression = new VariableExpression(collection);

        TypeInfo predicateTypeInfo = context.getTypeContext().getFullyQualified(Predicate.class);
        ParameterizedType predicateType = new ParameterizedType(predicateTypeInfo, List.of(theType));
        Variable predicate = context.requestParameter(2, predicateType);

        // predicate.test(t)
        Expression object = new VariableExpression(predicate);
        MethodTypeParameterMap method = predicateType.findSingleAbstractMethodOfInterface();
        MethodCall predicateCall = new MethodCall(object, object, method, List.of(new VariableExpression(result.localVariableReference)));

        Assignment resultEqualsLocal = new Assignment(new VariableExpression(result.localVariableReference),
                new VariableExpression(localVariableReference));
        Block ifBlock = new Block.BlockBuilder()
                .addStatement(new ExpressionAsStatement(resultEqualsLocal))
                .addStatement(new BreakStatement(null))
                .build();
        IfElseStatement ifPredicate = new IfElseStatement(predicateCall, ifBlock, Block.EMPTY_BLOCK);

        Block block = new Block.BlockBuilder()
                .addStatement(new AnyNonModifyingStatement(defaultValue, collection, localVariableReference))
                .addStatement(ifPredicate)
                .build();

        ForEachStatement forEach = new ForEachStatement(null, localVariable, collectionExpression, block);
        return new Block.BlockBuilder()
                .addStatement(new ExpressionAsStatement(createVariable))
                .addStatement(forEach).build();
    }


    /*
    is the statement a classic index loop?

    for(int i=0; i<n; i++) {
       ... use i but do not assign to it
       ... do not break out
    }

    Alternatives provided for: i<n, i<=n, n>i, n>=i; i++, ++i, i+= 1; arbitrary variable name
     */

    public static void start() {
    }

    public static void end(Object... objects) {
    }


    public static void style(Style... style) {

    }

    // static no side effects
    public static void consume(int index, Object... objects) {

    }

    public static void anyStatementNoBreakout(int index, List<Object> modifying, List<Object> nonModifying) {

    }

    public static void endSubstitute(int... index) {

    }

    public static void startSubstitute(int... index) {

    }

    public static void startWarn() {
    }

    public static void endWarn() {
    }

    public static boolean allowMultipleOperations(boolean... b) {
        return b[0];
    }


    static class IndexLoopPattern {

        public static void classicIndexLoopPattern(int n) {
            // we start by declaring a multi-boolean operation
            IntPredicate p = i -> allowMultipleOperations(i < n, i <= n, n > i, n >= i);
            start();
            // now the pattern starts

            for (int i = 0; p.test(i); i += 1) {
                // 2 method calls to indicate that there may be no break, no assignments to i
            }
        }

        public static void indexLoopPatternUsingWhile(int n) {
            // part 1: assertions on parameters as local variables
            assert n >= 0;
            start();

            // part 2: the code
            int i = 0;
            while (i < n) {
                // 2 method calls to indicate that there may be no break, no assignments to i
                i += 1;
            }

            // extra available
            end(i);
            assert i == n;
        }

    }

    static class DecreasingIndex {

        public static void decreasingIndexLoopPattern(int n) {
            assert n >= 0; // translated into restriction
            start();

            for (int i = n; i >= 0; i -= 1) { // i will get picked up as "some variable"
                // 2 method calls to indicate that there may be no break, no assignments to i
            }
        }

        public static void decreasingIndexLoopPattern2(int n) {
            assert n >= 0;
            start();

            for (int i = n; 0 <= i; i -= 1) { // i will get picked up as "some variable"
                // 2 method calls to indicate that there may be no break, no assignments to i
            }
        }

    }

    // using a supplier -> there is need for an additional variable

    static class AlternativeAssignmentInCode {

        public static <T> void pattern1(Supplier<T> initialValue, Predicate<T> predicate, Supplier<T> alternativeValue) {
            T result = initialValue.get();
            consume(0, result);
            if (predicate.test(result)) {
                consume(1, result);
                result = alternativeValue.get();
                consume(2, result);
            }
            consume(3, result);

            end(result);
        }

        public static <T> void pattern4(Supplier<T> initialValue, Predicate<T> predicate, Supplier<T> alternativeValue) {
            T temp = initialValue.get();
            consume(0, temp);
            T result;
            if (predicate.test(temp)) {
                result = alternativeValue.get();
                consume(2, result);
            } else {
                result = temp;
            }

            end(result);
        }
    }

    static class AlternativeAssignmentInReturn {

        // with a redundant variable

        public static <T> T pattern3(Supplier<T> initialValue, Predicate<T> predicate, Supplier<T> alternativeValue) {
            T result = initialValue.get();
            if (predicate.test(result)) {
                return alternativeValue.get();
            }
            return result;
        }

        // shortest

        public static <T> T pattern5(Supplier<T> initialValue, Predicate<T> predicate, Supplier<T> alternativeValue) {
            T temp = initialValue.get();
            return predicate.test(temp) ? alternativeValue.get() : temp;
        }
    }


    public static Statement classicIndexLoop() {
        LocalVariableCreation i = new LocalVariableCreation(localIntVariableStub(0), new IntConstant(0));
        Expression someIntValue = intExpression(0, Set.of(i.localVariableReference));
        Expression conditionWithGreaterThan = new BinaryOperatorTemplate(
                someIntValue,
                Set.of(Primitives.PRIMITIVES.greaterEqualsOperatorInt, Primitives.PRIMITIVES.greaterOperatorInt),
                new VariableExpression(i.localVariableReference),
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression conditionWithLessThan = new BinaryOperatorTemplate(
                new VariableExpression(i.localVariableReference),
                Set.of(Primitives.PRIMITIVES.lessEqualsOperatorInt, Primitives.PRIMITIVES.lessOperatorInt),
                someIntValue,
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression condition = new ExpressionAlternatives(conditionWithGreaterThan, conditionWithLessThan);
        Expression iPlusEquals1 = new Assignment(new VariableExpression(i.localVariableReference),
                new IntConstant(1), Primitives.PRIMITIVES.assignPlusOperatorInt, null);
        Block block = new Block.BlockBuilder()
                .addStatement(new NoAssignmentRestriction(i.localVariableReference))
                .addStatement(new NoBreakoutRestriction())
                .build();
        return new ForStatement(null, List.of(i), condition, List.of(iPlusEquals1), block);
    }

    /*
    decreasing loop

    for(int i=n; i>=0; i--) {
        ... use i but do not assign to it
        ... do not break out
    }

    Alternatives provided: i>=0; 0<=i; i--, --i, i-= 1, arbitrary variable name
     */

    public static Statement decreasingIndexLoop() {
        LocalVariableCreation i = new LocalVariableCreation(localIntVariableStub(0), intExpression(0, Set.of()));
        Expression conditionWithGreaterThan = new BinaryOperatorTemplate(
                new VariableExpression(i.localVariableReference),
                Set.of(Primitives.PRIMITIVES.greaterEqualsOperatorInt),
                new IntConstant(0),
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression conditionWithLessThan = new BinaryOperatorTemplate(
                new IntConstant(0),
                Set.of(Primitives.PRIMITIVES.lessEqualsOperatorInt),
                new VariableExpression(i.localVariableReference),
                true,
                Primitives.PRIMITIVES.booleanParameterizedType);
        Expression condition = new ExpressionAlternatives(conditionWithGreaterThan, conditionWithLessThan);
        Expression iMinusEquals1 = new Assignment(new VariableExpression(i.localVariableReference),
                new IntConstant(1), Primitives.PRIMITIVES.assignMinusOperatorInt, null);
        Block block = new Block.BlockBuilder()
                .addStatement(new NoAssignmentRestriction(i.localVariableReference))
                .addStatement(new NoBreakoutRestriction())
                .build();
        return new ForStatement(null, List.of(i), condition, List.of(iMinusEquals1), block);
    }

    static abstract class GenericRestrictionStatement implements Statement {

        @Override
        public Set<String> imports() {
            return null;
        }

        @Override
        public Set<TypeInfo> typesReferenced() {
            return null;
        }

        @Override
        public SideEffect sideEffect(SideEffectContext sideEffectContext) {
            return null;
        }
    }

    static class NoAssignmentRestriction extends GenericRestrictionStatement {

        public Set<Variable> variables;

        public NoAssignmentRestriction(Variable... variables) {
            this.variables = ImmutableSet.copyOf(variables);
        }

        @Override
        public String statementString(int indent) {
            StringBuilder sb = new StringBuilder();
            StringUtil.indent(sb, indent);
            sb.append("No assignments to ").append(variables).append(";\n");
            return sb.toString();
        }

    }

    static class NoBreakoutRestriction extends GenericRestrictionStatement {
        @Override
        public String statementString(int indent) {
            StringBuilder sb = new StringBuilder();
            StringUtil.indent(sb, indent);
            sb.append("No breakouts allowed;\n");
            return sb.toString();
        }

    }

    static class AnyNonModifyingStatement extends GenericRestrictionStatement {

        public Set<Variable> variables;

        public AnyNonModifyingStatement(Variable... variables) {
            this.variables = ImmutableSet.copyOf(variables);
        }

        @Override
        public String statementString(int indent) {
            return "Any non-modifying statement using only " + variables + ";";
        }
    }


    static class ExpressionAlternatives implements Expression {

        public List<Expression> expressions;

        public ExpressionAlternatives(Expression... expressions) {
            if (expressions == null || expressions.length == 0) throw new UnsupportedOperationException();
            this.expressions = Arrays.asList(expressions);
        }

        @Override
        public ParameterizedType returnType() {
            return expressions.get(0).returnType();
        }

        @Override
        public String expressionString(int indent) {
            return expressions.toString();
        }

        @Override
        public int precedence() {
            return expressions.stream().mapToInt(Expression::precedence).min().orElse(0);
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    static class BinaryOperatorTemplate implements Expression {

        public final ParameterizedType returnType;
        public final Expression e1;
        public final Expression e2;
        public final Set<MethodInfo> operators;
        public final boolean orderImportant; // true-> e1=lhs, e2=rhs; false -> doesn't matter

        public BinaryOperatorTemplate(Expression e1,
                                      Set<MethodInfo> operators,
                                      Expression e2,
                                      boolean orderImportant,
                                      ParameterizedType returnType) {
            this.e1 = e1;
            this.operators = operators;
            this.e2 = e2;
            this.returnType = returnType;
            this.orderImportant = orderImportant;
        }

        @Override
        public String toString() {
            return expressionString(0);
        }

        @Override
        public ParameterizedType returnType() {
            return returnType;
        }

        @Override
        public String expressionString(int indent) {
            return e1 + " " + operators + " " + e2;
        }

        @Override
        public int precedence() {
            return operators.stream().mapToInt(BinaryOperator::precedence).min().orElse(0);
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    public static Expression someIntConstant(int index) {
        return new GenericConstantExpression<Integer>(index, Primitives.PRIMITIVES.intParameterizedType);
    }

    public static Expression intExpression(int index, Set<Variable> variablesNotAllowed) {
        return new GenericExpression(index, Primitives.PRIMITIVES.intParameterizedType, variablesNotAllowed);
    }

    static class GenericConstantExpression<T> implements Expression, Constant<T> {
        public final int index;
        public final ParameterizedType returnType;

        public GenericConstantExpression(int index, ParameterizedType returnType) {
            this.index = index;
            this.returnType = returnType;
        }

        @Override
        public ParameterizedType returnType() {
            return returnType;
        }

        @Override
        public String expressionString(int indent) {
            return "[constant " + index + "]";
        }

        @Override
        public String toString() {
            return expressionString(0);
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T getValue() {
            throw new UnsupportedOperationException();
        }
    }

    static class GenericExpression implements Expression {
        public final int index;
        public final ParameterizedType returnType;
        public final Set<Variable> variablesNotAllowed;

        public GenericExpression(int index, ParameterizedType returnType, Set<Variable> variablesNotAllowed) {
            this.index = index;
            this.returnType = returnType;
            this.variablesNotAllowed = variablesNotAllowed;
        }

        @Override
        public ParameterizedType returnType() {
            return returnType;
        }

        @Override
        public String expressionString(int indent) {
            String notAllowed = variablesNotAllowed.isEmpty() ? "" : " without " + variablesNotAllowed;
            return "[expression " + index + notAllowed + "]";
        }

        @Override
        public String toString() {
            return expressionString(0);
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }
    }

    public static LocalVariable localIntVariableStub(int index) {
        return localVariableStub(index, Primitives.PRIMITIVES.intParameterizedType);
    }

    public static LocalVariable localVariableStub(int index, ParameterizedType type) {
        return new LocalVariable(List.of(), VARIABLE_STUB + index, type, List.of());
    }
}


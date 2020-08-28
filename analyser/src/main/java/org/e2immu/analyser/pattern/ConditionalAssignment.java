/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.pattern;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.Logger;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/*
 Example:
   String s1 = a1;
   if (s1 == null) {
       s1 = "Was null...";
   }
   //here s1 != null


 General structure:
   LV lv = someExpression;
   if(someCondition(lv)) {
     lv = someOtherExpression;
   }

 Replace with:
   LV tmp = someExpression;
   LV lv;
   if(someCondition(tmp)) {
     lv = someOtherExpression;
   } else {
     lv = tmp;
   }
   // it is now easier to compute the value of lv


 Note that the method `conditionalValue` does the same; it should somehow be analysed in a similar way.
 That is possible if we inline, and substitute the predicate in a specific condition (which happens anyway).
 */
public class ConditionalAssignment {

    // first attempt
    public static void tryToDetectTransformation(NumberedStatement statement, EvaluationContext evaluationContext) {
        if (statement.replacement.isSet()) return;
        SideEffectContext sideEffectContext = new SideEffectContext(evaluationContext.getCurrentMethod());

        // assignment or local variable creation
        if (!(statement.statement instanceof ExpressionAsStatement)) return;
        if (!statement.valueOfExpression.isSet()) return;
        Expression expression = ((ExpressionAsStatement) statement.statement).expression;
        Variable variable;
        Expression valueExpression;
        LocalVariable created;
        if (expression instanceof Assignment) {
            Assignment assignment = (Assignment) expression;
            if (assignment.target instanceof VariableExpression) {
                variable = ((VariableExpression) assignment.target).variable;
                valueExpression = assignment.value;
                created = null;
            } else {
                return;
            }
        } else if (expression instanceof LocalVariableCreation) {
            LocalVariableCreation localVariableCreation = (LocalVariableCreation) expression;
            if (localVariableCreation.expression == EmptyExpression.EMPTY_EXPRESSION) return;
            variable = localVariableCreation.localVariableReference;
            valueExpression = localVariableCreation.expression;
            created = localVariableCreation.localVariable;
        } else {
            return;
        }

        // followed by if( ) { } block
        NumberedStatement next = statement.next.get().orElse(null);
        if (next == null) return;
        if (!(next.statement instanceof IfElseStatement)) return;
        if (!next.valueOfExpression.isSet()) return;
        IfElseStatement ifElseStatement = (IfElseStatement) next.statement;
        if (ifElseStatement.elseBlock != Block.EMPTY_BLOCK) return;
        List<Variable> variablesInCondition = ifElseStatement.expression.variables();
        if (!variablesInCondition.contains(variable)) return;

        // in the if-block, there has to be an assignment
        if (ifElseStatement.ifBlock.statements.size() != 1) return;
        Statement stInIf = ifElseStatement.ifBlock.statements.get(0);
        if (!(stInIf instanceof ExpressionAsStatement)) return;
        Expression exprInIf = ((ExpressionAsStatement) stInIf).expression;
        if (!(exprInIf instanceof Assignment)) return;
        Assignment assignmentInIf = (Assignment) exprInIf;
        if (!(assignmentInIf.target instanceof VariableExpression)) return;
        Variable variableInIf = ((VariableExpression) assignmentInIf.target).variable;
        if (!variableInIf.equals(variable)) return;

        Expression newValueExpression = valueExpression.translate(Map.of());
        LocalVariable tmp = new LocalVariable(List.of(), "tmp", variable.parameterizedType(), List.of());
        LocalVariableCreation lvc1 = new LocalVariableCreation(tmp, newValueExpression);
        Statement newS1 = new ExpressionAsStatement(lvc1);
        NumberedStatement newNs1 = new NumberedStatement(sideEffectContext, newS1, statement.parent, statement.indices);
        log(TRANSFORM, "New statement 1: {}", newS1.statementString(0));
        newNs1.blocks.set(List.of());

        if (created != null) {
            LocalVariableCreation lvc2 = new LocalVariableCreation(created, EmptyExpression.EMPTY_EXPRESSION);
            Statement newS2 = new ExpressionAsStatement(lvc2);
            NumberedStatement newNs2 = new NumberedStatement(sideEffectContext, newS2, statement.parent, statement.indices);
            newNs2.blocks.set(List.of());
            newNs1.next.set(Optional.of(newNs2));
            log(TRANSFORM, "New statement 2: {}", newS2.statementString(0));
        }
        statement.replacement.set(newNs1);

        Expression ifExpression = ifElseStatement.expression.translate(Map.of(variable, lvc1.localVariableReference));
        ExpressionAsStatement assignToSomeOther = new ExpressionAsStatement(
                new Assignment(new VariableExpression(variable), assignmentInIf.value.translate(Map.of())));
        ExpressionAsStatement assignToTmp = new ExpressionAsStatement(
                new Assignment(new VariableExpression(variable), new VariableExpression(lvc1.localVariableReference)));
        Block newIfBlock = new Block.BlockBuilder().addStatement(assignToSomeOther).build();
        Block newElseBlock = new Block.BlockBuilder().addStatement(assignToTmp).build();
        Statement newS3 = new IfElseStatement(ifExpression, newIfBlock, newElseBlock);
        log(TRANSFORM, "New statement 3: {}", newS3.statementString(0));
        NumberedStatement newNs3 = new NumberedStatement(sideEffectContext, newS3, next.parent, next.indices);

        NumberedStatement newNs3_00 = new NumberedStatement(sideEffectContext, assignToSomeOther, newNs3, add(next.indices, new int[]{0, 0}));
        newNs3_00.next.set(Optional.empty());
        newNs3_00.neverContinues.set(false);
        newNs3_00.blocks.set(List.of());

        NumberedStatement newNs3_10 = new NumberedStatement(sideEffectContext, assignToTmp, newNs3, add(next.indices, new int[]{1, 0}));
        newNs3_10.next.set(Optional.empty());
        newNs3_10.neverContinues.set(false);
        newNs3_10.blocks.set(List.of());

        newNs3.blocks.set(List.of(newNs3_00, newNs3_10));
        newNs3.neverContinues.set(false);

        if (created == null) {
            newNs1.next.set(Optional.of(newNs3));
        } else {
            newNs1.next.get().orElseThrow().next.set(Optional.of(newNs3));
        }
        newNs3.next.set(next.next.get());
    }

    private static int[] add(int[] a1, int[] a2) {
        int[] a = new int[a1.length + a2.length];
        System.arraycopy(a1, 0, a, 0, a1.length);
        System.arraycopy(a2, 0, a, a1.length, a2.length);
        return a;
    }

    public static <T> T conditionalValue(T initial, Predicate<T> condition, T alternative) {
        return condition.test(initial) ? alternative : initial;
    }
}

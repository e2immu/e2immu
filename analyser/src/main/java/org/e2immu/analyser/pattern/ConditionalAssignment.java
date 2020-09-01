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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.ListUtil;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

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

 Replace with:
   LV lv;
   {
     LV tmp = someExpression;
     if(someCondition(tmp)) {
       lv = someOtherExpression;
     } else {
       lv = tmp;
     }
   }

 Note that the method `conditionalValue` does the same; it should somehow be analysed in a similar way.
 That is possible if we inline, and substitute the predicate in a specific condition (which happens anyway).
 */
public class ConditionalAssignment {


    public static Pattern pattern1() {
        Pattern.PatternBuilder patternBuilder = new Pattern.PatternBuilder("conditionalAssignment");

        /*
         PATTERN

         LV lv = someExpression;
         if(someCondition(lv)) {
           lv = someOtherExpression;
         }

         in numbers:
         T0 lv0 = expression0;
         if(expression1(lv0)) {
           lv0 = expression2;
         }
         */
        ParameterizedType pt0 = patternBuilder.matchType();
        LocalVariable localVariable = patternBuilder.matchLocalVariable(pt0);
        Expression someExpression = patternBuilder.matchSomeExpression(pt0);
        LocalVariableCreation lvc0 = new LocalVariableCreation(localVariable, someExpression);
        Variable lv = lvc0.localVariableReference;
        patternBuilder.registerVariable(lv);
        patternBuilder.addStatement(new ExpressionAsStatement(lvc0));

        Expression someCondition = patternBuilder.matchSomeExpression(Primitives.PRIMITIVES.booleanParameterizedType, lv);
        Expression someOtherExpression = patternBuilder.matchSomeExpression(pt0);
        Assignment assignment1 = new Assignment(new VariableExpression(lv), someOtherExpression);
        Block ifBlock1 = new Block.BlockBuilder().addStatement(new ExpressionAsStatement(assignment1)).build();
        patternBuilder.addStatement(new IfElseStatement(someCondition, ifBlock1, Block.EMPTY_BLOCK));

        return patternBuilder.build();
    }

    public static Replacement replacement1ToPattern1(Pattern pattern1) {
        /*
         REPLACEMENT 1, visually the nicest

         LV tmp = someExpression; // expression0
         LV lv = someCondition(tmp) ?  someOtherExpression: tmp;
         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement11", pattern1);
        LocalVariable lvTmp = replacementBuilder.newLocalVariable("tmp", pattern1.types.get(0));
        Expression rSomeExpression = pattern1.expressions.get(0);
        LocalVariableCreation lvcTmp = new LocalVariableCreation(lvTmp, rSomeExpression);
        Variable tmp = lvcTmp.localVariableReference;
        replacementBuilder.addStatement(new ExpressionAsStatement(lvcTmp));

        Variable lv = pattern1.variables.get(0);
        Expression condition = pattern1.expressions.get(1);
        replacementBuilder.applyTranslation(condition, TranslationMap.fromVariableMap(Map.of(lv, tmp)));
        Expression ifTrue = pattern1.expressions.get(2);
        Expression ifFalse = new VariableExpression(tmp);
        InlineConditionalOperator inlineConditional = new InlineConditionalOperator(condition, ifTrue, ifFalse);

        LocalVariable localVar = new LocalVariable(List.of(), lv.name(), lv.parameterizedType(), List.of());
        LocalVariableCreation lvc = new LocalVariableCreation(localVar, inlineConditional);
        replacementBuilder.addStatement(new ExpressionAsStatement(lvc));

        return replacementBuilder.build();
    }

    public static Replacement replacement2ToPattern1(Pattern pattern1) {
        /*
         REPLACEMENT 2, a much more elaborate version, which does not leave the local variable lying around

         LV lv; // Type0 lv0
         {
           LV tmp = someExpression; // expression0
           if(someCondition(tmp)) {
             lv = someOtherExpression;
           } else {
             lv = tmp;
           }
         }
         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement12", pattern1);
        Variable lv = pattern1.variables.get(0);
        LocalVariable localVar = new LocalVariable(List.of(), lv.name(), lv.parameterizedType(), List.of());
        LocalVariableCreation lvc = new LocalVariableCreation(localVar, EmptyExpression.EMPTY_EXPRESSION);
        replacementBuilder.addStatement(new ExpressionAsStatement(lvc));
        Block.BlockBuilder blockBuilder = new Block.BlockBuilder();

        LocalVariable lvTmp = replacementBuilder.newLocalVariable("tmp", pattern1.types.get(0));
        Expression rSomeExpression = pattern1.expressions.get(0);
        LocalVariableCreation lvcTmp = new LocalVariableCreation(lvTmp, rSomeExpression);
        Variable tmp = lvcTmp.localVariableReference;
        blockBuilder.addStatement(new ExpressionAsStatement(lvcTmp));

        Expression rSomeCondition = pattern1.expressions.get(1);
        replacementBuilder.applyTranslation(rSomeCondition, TranslationMap.fromVariableMap(Map.of(lv, tmp)));
        ExpressionAsStatement assignSomeOtherExpression = new ExpressionAsStatement(
                new Assignment(new VariableExpression(lv), pattern1.expressions.get(2)));
        ExpressionAsStatement assignTmp = new ExpressionAsStatement(
                new Assignment(new VariableExpression(lv), new VariableExpression(tmp)));
        Block newIfBlock = new Block.BlockBuilder().addStatement(assignSomeOtherExpression).build();
        Block newElseBlock = new Block.BlockBuilder().addStatement(assignTmp).build();
        blockBuilder.addStatement(new IfElseStatement(rSomeCondition, newIfBlock, newElseBlock));

        replacementBuilder.addStatement(blockBuilder.build());

        return replacementBuilder.build();
    }

    public static Pattern pattern2() {
        Pattern.PatternBuilder patternBuilder = new Pattern.PatternBuilder("twoReturnsAndIf");

        /* PATTERN

          if(someCondition0) return someExpression1;
          return someOtherExpression2;
         */

        ParameterizedType pt0 = patternBuilder.matchType();
        Expression someCondition0 = patternBuilder.matchSomeExpression(Primitives.PRIMITIVES.booleanParameterizedType);

        Expression someExpression1 = patternBuilder.matchSomeExpression(pt0);
        Block ifBlock = new Block.BlockBuilder().addStatement(new ReturnStatement(someExpression1)).build();

        patternBuilder.addStatement(new IfElseStatement(someCondition0, ifBlock, Block.EMPTY_BLOCK));
        Expression someOtherExpression2 = patternBuilder.matchSomeExpression(pt0);
        patternBuilder.addStatement(new ReturnStatement(someOtherExpression2));

        return patternBuilder.build();
    }


    public static Replacement replacement1ToPattern2(Pattern pattern2) {
        /*
         REPLACEMENT 1 of pattern 2

         return someExpression ? someExpression : someOtherExpression;
         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement21", pattern2);
        Expression condition = pattern2.expressions.get(0);
        Expression ifTrue = pattern2.expressions.get(1);
        Expression ifFalse = pattern2.expressions.get(2);
        InlineConditionalOperator inlineConditional = new InlineConditionalOperator(condition, ifTrue, ifFalse);

        replacementBuilder.addStatement(new ReturnStatement(inlineConditional));

        return replacementBuilder.build();
    }


    public static Pattern pattern3() {
        Pattern.PatternBuilder patternBuilder = new Pattern.PatternBuilder("twoReturnsAndIfElse");

        /* PATTERN

          if(someCondition) {
            return someExpression;
          } else {
            return someOtherExpression;
          }
         */

        ParameterizedType pt0 = patternBuilder.matchType();
        Expression someCondition = patternBuilder.matchSomeExpression(Primitives.PRIMITIVES.booleanParameterizedType);
        Expression someExpression = patternBuilder.matchSomeExpression(pt0);
        Expression someOtherExpression = patternBuilder.matchSomeExpression(pt0);

        Block ifBlock = new Block.BlockBuilder().addStatement(new ReturnStatement(someExpression)).build();
        Block elseBlock = new Block.BlockBuilder().addStatement(new ReturnStatement(someOtherExpression)).build();

        patternBuilder.addStatement(new IfElseStatement(someCondition, ifBlock, elseBlock));

        return patternBuilder.build();
    }


    public static Replacement replacement1ToPattern3(Pattern pattern3) {
        /*
         REPLACEMENT 1 of pattern 2

         return someExpression ? someExpression : someOtherExpression;
         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement31", pattern3);
        Expression condition = pattern3.expressions.get(0);
        Expression ifTrue = pattern3.expressions.get(1);
        Expression ifFalse = pattern3.expressions.get(2);
        InlineConditionalOperator inlineConditional = new InlineConditionalOperator(condition, ifTrue, ifFalse);

        replacementBuilder.addStatement(new ReturnStatement(inlineConditional));

        return replacementBuilder.build();
    }

    /*
     TODO: it would be better if the matching of the condition on list happened at VALUE level (there's normalisation!)
     */

    public static Pattern pattern4(TypeContext typeContext) {
        Pattern.PatternBuilder patternBuilder = new Pattern.PatternBuilder("ClassicForList");

        /* PATTERN

          for(int i=0; i<list.size(); i++) {
            // non-modifying operations on list, and at least one list.get(i), and i not used for other purposes
          }

         */
        LocalVariable indexVar = patternBuilder.matchLocalVariable(Primitives.PRIMITIVES.intParameterizedType);
        LocalVariableCreation indexLvc = new LocalVariableCreation(indexVar, new IntConstant(0));
        Variable i = indexLvc.localVariableReference;
        patternBuilder.registerVariable(i);

        ParameterizedType typeParameter = patternBuilder.matchType();
        TypeInfo listType = typeContext.getFullyQualified(List.class);
        ParameterizedType listPt = new ParameterizedType(listType, List.of(typeParameter));
        Variable list = patternBuilder.matchVariable(listPt);
        VariableExpression listScope = new VariableExpression(list);
        MethodInfo sizeMethod = listType.findUniqueMethod("size", 0);
        MethodTypeParameterMap sizeMethodTypeParameterMap = new MethodTypeParameterMap(sizeMethod, Map.of());
        Expression sizeCall = new MethodCall(listScope, listScope, sizeMethodTypeParameterMap, List.of());
        Expression condition = new BinaryOperator(new VariableExpression(i),
                Primitives.PRIMITIVES.lessOperatorInt, sizeCall, BinaryOperator.COMPARISON_PRECEDENCE);
        Expression updater = new Assignment(new VariableExpression(i), EmptyExpression.EMPTY_EXPRESSION,
                Primitives.PRIMITIVES.assignPlusOperatorInt, false); // i++
        Block block = new Block.BlockBuilder().addStatement(new Pattern.PlaceHolderStatement()).build();
        patternBuilder.addStatement(new ForStatement(null, List.of(indexLvc), condition, List.of(updater), block));

        return patternBuilder.build();
    }


    // first attempt
    public static void tryToDetectTransformation(NumberedStatement statement, EvaluationContext evaluationContext) {
        if (statement.replacement.isSet()) return;

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

        // REPLACEMENT

        Expression newValueExpression = valueExpression.translate(TranslationMap.EMPTY_MAP);
        LocalVariable tmp = new LocalVariable(List.of(), "tmp", variable.parameterizedType(), List.of());
        LocalVariableCreation lvc1 = new LocalVariableCreation(tmp, newValueExpression);
        Statement newS1 = new ExpressionAsStatement(lvc1);
        NumberedStatement newNs1 = new NumberedStatement(newS1, statement.parent, statement.indices);
        log(TRANSFORM, "New statement 1: {}", newS1.statementString(0, null));
        newNs1.blocks.set(List.of());

        if (created != null) {
            LocalVariableCreation lvc2 = new LocalVariableCreation(created, EmptyExpression.EMPTY_EXPRESSION);
            Statement newS2 = new ExpressionAsStatement(lvc2);
            NumberedStatement newNs2 = new NumberedStatement(newS2, statement.parent, statement.indices);
            newNs2.blocks.set(List.of());
            newNs1.next.set(Optional.of(newNs2));
            log(TRANSFORM, "New statement 2: {}", newS2.statementString(0, null));
        }
        statement.replacement.set(newNs1);

        Expression ifExpression = ifElseStatement.expression.translate(
                TranslationMap.fromVariableMap(Map.of(variable, lvc1.localVariableReference)));
        ExpressionAsStatement assignToSomeOther = new ExpressionAsStatement(
                new Assignment(new VariableExpression(variable), assignmentInIf.value.translate(TranslationMap.EMPTY_MAP)));
        ExpressionAsStatement assignToTmp = new ExpressionAsStatement(
                new Assignment(new VariableExpression(variable), new VariableExpression(lvc1.localVariableReference)));
        Block newIfBlock = new Block.BlockBuilder().addStatement(assignToSomeOther).build();
        Block newElseBlock = new Block.BlockBuilder().addStatement(assignToTmp).build();
        Statement newS3 = new IfElseStatement(ifExpression, newIfBlock, newElseBlock);
        log(TRANSFORM, "New statement 3: {}", newS3.statementString(0, null));
        NumberedStatement newNs3 = new NumberedStatement(newS3, next.parent, next.indices);

        NumberedStatement newNs3_00 = new NumberedStatement(assignToSomeOther, newNs3, ListUtil.immutableConcat(next.indices, List.of(0, 0)));
        newNs3_00.next.set(Optional.empty());
        newNs3_00.neverContinues.set(false);
        newNs3_00.blocks.set(List.of());

        NumberedStatement newNs3_10 = new NumberedStatement(assignToTmp, newNs3, ListUtil.immutableConcat(next.indices, List.of(1, 0)));
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

    public static <T> T conditionalValue(T initial, Predicate<T> condition, T alternative) {
        return condition.test(initial) ? alternative : initial;
    }
}

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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

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


    public static Pattern pattern2Extended() {
        Pattern.PatternBuilder patternBuilder = new Pattern.PatternBuilder("twoReturnsAndIfExtended");

        /* PATTERN

          if(someCondition0) {
            someStatements0;
            return someExpression1;
          }
          someOtherStatements1;
          return someOtherExpression2;
         */

        ParameterizedType pt0 = patternBuilder.matchType();
        Expression someCondition0 = patternBuilder.matchSomeExpression(Primitives.PRIMITIVES.booleanParameterizedType);

        Expression someExpression1 = patternBuilder.matchSomeExpression(pt0);
        Block ifBlock = new Block.BlockBuilder()
                .addStatement(patternBuilder.matchSomeStatements()) // 0
                .addStatement(new ReturnStatement(someExpression1)).build();

        patternBuilder.addStatement(new IfElseStatement(someCondition0, ifBlock, Block.EMPTY_BLOCK));
        patternBuilder.addStatement(patternBuilder.matchSomeStatements()); // 1
        Expression someOtherExpression2 = patternBuilder.matchSomeExpression(pt0);
        patternBuilder.addStatement(new ReturnStatement(someOtherExpression2));

        return patternBuilder.build();
    }

    public static Replacement replacement1ToPattern2Extended(Pattern pattern3) {
        /*
         REPLACEMENT 1 of pattern 2 Extended
         Identical to replacement of pattern 3 extended

         lv0 = someCondition0;
         if(lv0) {
           someStatements0;
         } else {
           someOtherStatements1;
         }
         return lv0 ? someExpression1 : someOtherExpression2;
         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement2Extended1", pattern3);
        Expression condition = pattern3.expressions.get(0);
        Expression ifTrue = pattern3.expressions.get(1);
        Expression ifFalse = pattern3.expressions.get(2);
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
         REPLACEMENT 1 of pattern 3

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

    public static Pattern pattern3Extended() {
        Pattern.PatternBuilder patternBuilder = new Pattern.PatternBuilder("twoReturnsAndIfElseExtended");

        /* PATTERN

          if(someCondition0) {
            someStatements0
            return someExpression1;
          } else {
            someOtherStatements1
            return someOtherExpression2;
          }

          there's 2 replacements, depending on some situation
         */

        ParameterizedType pt0 = patternBuilder.matchType();
        Expression someCondition = patternBuilder.matchSomeExpression(Primitives.PRIMITIVES.booleanParameterizedType);
        Expression someExpression = patternBuilder.matchSomeExpression(pt0);
        Expression someOtherExpression = patternBuilder.matchSomeExpression(pt0);
        Statement someStatements0 = patternBuilder.matchSomeStatements();
        Statement someOtherStatements1 = patternBuilder.matchSomeStatements();

        Block ifBlock = new Block.BlockBuilder()
                .addStatement(someStatements0)
                .addStatement(new ReturnStatement(someExpression)).build();
        Block elseBlock = new Block.BlockBuilder()
                .addStatement(someOtherStatements1)
                .addStatement(new ReturnStatement(someOtherExpression)).build();

        patternBuilder.addStatement(new IfElseStatement(someCondition, ifBlock, elseBlock));

        return patternBuilder.build();
    }

    public static Replacement replacement1ToPattern3Extended(Pattern pattern3) {
         /*
         REPLACEMENT 1 of pattern 3 Extended
         If someExpression1 and someOtherExpression2 do not contain references to variables created in the local block,
         and the amount of statements is low; otherwise use replacement 2

         lv0 = someCondition0;
         if(lv0) {
           someStatements0;
         } else {
           someOtherStatements1;
         }
         return lv0 ? someExpression1 : someOtherExpression2;
         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement3Extended1", pattern3);
        Expression condition = pattern3.expressions.get(0);
        Expression ifTrue = pattern3.expressions.get(1);
        Expression ifFalse = pattern3.expressions.get(2);
        InlineConditionalOperator inlineConditional = new InlineConditionalOperator(condition, ifTrue, ifFalse);

        replacementBuilder.addStatement(new ReturnStatement(inlineConditional));

        return replacementBuilder.build();
    }


    public static Replacement replacement2ToPattern3Extended(Pattern pattern3) {
        /*
         REPLACEMENT 1 of pattern 3 Extended

         return someCondition ? method1(localVars) : method2(localVars);

         +
         private T0 method1(localVars) {
           someStatements0;
           return someExpression1;
         }
         private T1 method2(localVars) {
           someStatements1;
           return someOtherExpression2;
         }

         */
        Replacement.ReplacementBuilder replacementBuilder = new Replacement.ReplacementBuilder("Replacement3Extended2", pattern3);
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
        Block block = new Block.BlockBuilder().addStatement(patternBuilder.matchSomeStatements()).build();
        patternBuilder.addStatement(new ForStatement(null, List.of(indexLvc), condition, List.of(updater), block));

        return patternBuilder.build();
    }

    public static <T> T conditionalValue(T initial, Predicate<T> condition, T alternative) {
        return condition.test(initial) ? alternative : initial;
    }

    /* detect: .stream().forEach(), with the aim of replacing with .forEach directly */

    public static Pattern streamForEachPattern(TypeContext typeContext) {
        Pattern.PatternBuilder builder = new Pattern.PatternBuilder("streamForEach");
        TypeInfo stream = typeContext.getFullyQualified(Stream.class);
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        MethodInfo streamMethod = collection.findUniqueMethod("stream", 0);
        MethodInfo forEachMethod = stream.findUniqueMethod("forEach", 0);

        // <T>
        ParameterizedType typeParameter = builder.matchType();
        // Collection<T>
        Expression source = builder.matchSomeExpression(new ParameterizedType(collection, List.of(typeParameter)));
        // .stream()
        MethodCall streamCall = new MethodCall(source, source, new MethodTypeParameterMap(streamMethod, Map.of()), List.of());
        // .forEach()
        MethodCall forEachCall = new MethodCall(streamCall, streamCall,
                new MethodTypeParameterMap(forEachMethod, Map.of()), List.of());
        // as part of some statement
        Statement someStatement = builder.matchStatementWithExpression(forEachCall);
        builder.addStatement(someStatement);
        return builder.build();
    }


}

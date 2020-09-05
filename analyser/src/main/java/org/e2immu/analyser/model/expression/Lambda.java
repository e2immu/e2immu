/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements.recursivelyCreateNumberedStatements;

public class Lambda implements Expression {
    public final MethodInfo methodInfo;
    public final Block block;
    public final List<ParameterInfo> parameters;
    public final ParameterizedType abstractFunctionalType;
    public final ParameterizedType implementation;

    /**
     * @param abstractFunctionalType e.g. java.util.Supplier
     * @param implementation         anonymous type, with single abstract method with implementation block
     */
    public Lambda(@NotNull ParameterizedType abstractFunctionalType,
                  @NotNull ParameterizedType implementation) {
        methodInfo = implementation.findSingleAbstractMethodOfInterface().methodInfo;
        this.block = methodInfo.methodInspection.get().methodBody.get();
        this.parameters = methodInfo.methodInspection.get().parameters;
        if (!abstractFunctionalType.isFunctionalInterface()) throw new UnsupportedOperationException();
        this.abstractFunctionalType = Objects.requireNonNull(abstractFunctionalType);
        if (!implementation.isFunctionalInterface()) throw new UnsupportedOperationException();
        this.implementation = Objects.requireNonNull(implementation);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        throw new UnsupportedOperationException();
        //return new Lambda(translationMap.translateType(abstractFunctionalType), translationMap.translateType(implementation));
    }

    private Expression singleExpression() {
        if (block.statements.size() != 1) return null;
        Statement statement = block.statements.get(0);
        if (!(statement instanceof ReturnStatement)) return null;
        ReturnStatement returnStatement = (ReturnStatement) statement;
        return returnStatement.expression;
    }

    // this is a functional interface
    @Override
    public ParameterizedType returnType() {
        return abstractFunctionalType;
    }

    @Override
    public String expressionString(int indent) {
        String blockString;
        Expression singleExpression = singleExpression();
        if (singleExpression != null) {
            blockString = singleExpression.expressionString(indent);
        } else {
            if (block.statements.isEmpty()) blockString = "{ }";
            else {
                List<NumberedStatement> statements = methodInfo.methodAnalysis.get().numberedStatements.get();
                NumberedStatement numberedStatement = statements.isEmpty() ? null : statements.get(0);
                blockString = block.statementString(indent, numberedStatement);
            }
        }
        if (parameters.size() == 1) {
            return parameters.get(0).stream() + " -> " + blockString;
        }
        return "(" + parameters.stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")) + ")"
                + " -> " + blockString;
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(block.imports());
        parameters.forEach(pe -> imports.addAll(pe.imports()));
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return SetUtil.immutableUnion(block.typesReferenced(), parameters.stream().flatMap(p -> p.typesReferenced().stream()).collect(Collectors.toSet()));
    }

    @Override
    public SideEffect sideEffect(EvaluationContext sideEffectContext) {
        return block.sideEffect(sideEffectContext);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value result = new Instance(methodInfo.typeInfo.asParameterizedType(), null, List.of(), evaluationContext);

        if (block != Block.EMPTY_BLOCK) {
            // we have no guarantee that this block will be executed. maybe there are situations?
            EvaluationContext child = evaluationContext.child(UnknownValue.EMPTY, null, false);

            MethodAnalyser methodAnalyser = new MethodAnalyser(evaluationContext.getE2ImmuAnnotationExpressions());
            boolean changes = methodAnalyser.analyse(methodInfo, child);

            evaluationContext.copyMessages(methodAnalyser.getMessageStream());
            evaluationContext.merge(child);

            if (!methodInfo.methodAnalysis.get().singleReturnValue.isSet()) {
                result = UnknownValue.NO_VALUE; // DELAY, we may have to iterate again
            }
            visitor.visit(this, child, result, changes);
        }

        return result;
    }
}

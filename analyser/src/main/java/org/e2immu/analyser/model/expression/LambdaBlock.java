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
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements.recursivelyCreateNumberedStatements;

public class LambdaBlock implements Expression {
    public final Block block;
    public final List<ParameterInfo> parameters;
    public final ParameterizedType returnType;
    public final ParameterizedType functionalType;
    public final SetOnce<List<NumberedStatement>> numberedStatements = new SetOnce<>();

    public LambdaBlock(@NotNull List<ParameterInfo> parameters,
                       @NotNull Block block,
                       @NotNull ParameterizedType returnType,
                       @NotNull ParameterizedType functionalType) {
        this.block = Objects.requireNonNull(block);
        this.parameters = Objects.requireNonNull(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.functionalType = Objects.requireNonNull(functionalType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new LambdaBlock(parameters.stream().map(v -> (ParameterInfo) translationMap.translateVariable(v)).collect(Collectors.toList()),
                translationMap.translateBlock(block),
                translationMap.translateType(returnType),
                translationMap.translateType(functionalType));
    }

    // this is a functional interface
    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    public String expressionString(int indent) {
        String blockString;
        if (block.statements.isEmpty()) blockString = "{ }";
        else blockString = block.statementString(indent);
        if (parameters.size() == 1) {
            return parameters.get(0).stream();
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
        MethodInfo methodInfo = functionalType.findSingleAbstractMethodOfInterface().methodInfo;
        Value result = new Instance(methodInfo.typeInfo.asParameterizedType(), null, List.of(), evaluationContext);
        // important to note: MethodValue is the result of a method; we return an instance of the type
        //new MethodValue(methodInfo, new TypeValue(methodInfo.typeInfo.asParameterizedType()), List.of());

        if (block != Block.EMPTY_BLOCK) {
            // we have no guarantee that this block will be executed. maybe there are situations?
            EvaluationContext child = evaluationContext.child(UnknownValue.EMPTY, null, false);
            parameters.forEach(child::createLocalVariableOrParameter);

            boolean changes = false;
            if (!numberedStatements.isSet()) {
                List<NumberedStatement> numberedStatements = new LinkedList<>();
                Stack<Integer> indices = new Stack<>();
                recursivelyCreateNumberedStatements(null,
                        block.statements,
                        indices,
                        numberedStatements, true);
                this.numberedStatements.set(numberedStatements);
                changes = true;
            }
            StatementAnalyser statementAnalyser = new StatementAnalyser(child.getCurrentMethod());
            if (!this.numberedStatements.get().isEmpty() && statementAnalyser.computeVariablePropertiesOfBlock(this.numberedStatements.get().get(0), child)) {
                changes = true;
            }
            evaluationContext.copyMessages(statementAnalyser.getMessageStream());
            evaluationContext.merge(child);
            visitor.visit(this, child, result, changes);
        }
        return result;
    }
}

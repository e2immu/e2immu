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

package org.e2immu.analyser.model.statement;

import com.google.common.collect.Sets;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.LocalVariableReference;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Set;

// @ContextClass
// @NullNotAllowed
// @NotNull
public class ForEachStatement extends StatementWithExpression {
    public final LocalVariable localVariable;
    public final Block block;

    public ForEachStatement(LocalVariable localVariable,
                            Expression expression,
                            Block block) {
        super(expression);
        this.block = block;
        this.localVariable = localVariable;
    }

    @Override
    public Set<String> imports() {
        return Sets.union(expression.imports(), localVariable.imports());
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("for (");
        sb.append(localVariable.parameterizedType.stream());
        sb.append(" ");
        sb.append(localVariable.name);
        sb.append(" : ");
        sb.append(expression.expressionString(indent));
        sb.append(")");
        sb.append(block.statementString(indent));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public List<LocalVariableReference> newLocalVariables() {
        return List.of(); // has been added in ExpressionContext.forEachStatement()
    }

    @Override
    public List<Block> blocks() {
        return List.of(block);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect blocksSideEffect = blocks().stream()
                .map(s -> s.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);
        SideEffect conditionSideEffect = expression.sideEffect(sideEffectContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }
}

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

import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;

// @ContextClass
// @NullNotAllowed
// @NotNull
public class IfElseStatement extends StatementWithExpression {
    public final Block elseBlock;
    public final Block ifBlock;

    public IfElseStatement(Expression expression,
                           Block ifBlock,
                           Block elseBlock) {
        super(expression);
        this.ifBlock = ifBlock;
        this.elseBlock = elseBlock;
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("if (");
        sb.append(expression.expressionString(indent));
        sb.append(")");
        sb.append(ifBlock.statementString(indent));
        if (elseBlock != Block.EMPTY_BLOCK) {
            sb.append(" else");
            sb.append(elseBlock.statementString(indent));
        }
        sb.append("\n");
        return sb.toString();
    }

    // note that we add the expression only once


    @Override
    public CodeOrganization codeOrganization() {
        CodeOrganization.Builder builder = new CodeOrganization.Builder().setExpression(expression).setStatements(ifBlock);
        if (elseBlock != Block.EMPTY_BLOCK) {
            builder.addSubStatement(new CodeOrganization.Builder().setNegateParentExpression(true).setStatements(elseBlock).build());
        }
        return builder.build();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect blocksSideEffect = ifBlock.sideEffect(sideEffectContext);
        if (elseBlock != Block.EMPTY_BLOCK) {
            blocksSideEffect = blocksSideEffect.combine(elseBlock.sideEffect(sideEffectContext));
        }
        SideEffect conditionSideEffect = expression.sideEffect(sideEffectContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }
}

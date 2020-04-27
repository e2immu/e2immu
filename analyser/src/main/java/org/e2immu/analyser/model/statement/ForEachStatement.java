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
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.util.StringUtil;

import java.util.Set;

public class ForEachStatement extends LoopStatement {
    public final LocalVariable localVariable;

    public ForEachStatement(String label,
                            LocalVariable localVariable,
                            Expression expression,
                            Block block) {
        super(label, expression, block, v -> v instanceof ArrayValue && !((ArrayValue) v).values.isEmpty());
        this.localVariable = localVariable;
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder().setLocalVariableCreation(localVariable).setExpression(expression).setStatements(block).build();
    }

    @Override
    public Set<String> imports() {
        return Sets.union(expression.imports(), localVariable.imports());
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (label != null) {
            sb.append(label).append(": ");
        }
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
}

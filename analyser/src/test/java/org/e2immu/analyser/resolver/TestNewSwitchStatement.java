/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.resolver;


import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.StatementExecution;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.statement.SwitchStatementNewStyle;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.NewSwitchStatement_0;
import org.e2immu.analyser.resolver.testexample.SwitchExpression_0;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestNewSwitchStatement extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(NewSwitchStatement_0.class);
        TypeInfo typeInfo = typeMap.get(NewSwitchStatement_0.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        SwitchStatementNewStyle stmt = (SwitchStatementNewStyle) block.structure.statements().get(1);
        assertEquals("dataType", stmt.expression.toString());
        assertEquals(3, stmt.switchEntries.size());
        assertSame(StatementExecution.NEVER, stmt.structure.statementExecution());
        SwitchEntry se3 = stmt.switchEntries.get(0);
        assertEquals("BlockEntry.ComputedStatementExecution[[3]]", se3.structure.statementExecution().toString());
        assertEquals("3", se3.labels.get(0).toString());
        assertEquals("dataType==3", se3.structure.expression().toString());

        SwitchEntry se4 = stmt.switchEntries.get(1);
        assertEquals("StatementsEntry.ComputedStatementExecution[[4]]", se4.structure.statementExecution().toString());
        assertEquals("4", se4.labels.get(0).toString());
        assertEquals("dataType", se4.switchVariableAsExpression.toString());
        assertEquals("dataType==4", se4.structure.expression().toString());

        SwitchEntry sed = stmt.switchEntries.get(2);
        assertEquals("StatementsEntry.ComputedStatementExecution[[]]",
                sed.structure.statementExecution().toString());
        assertTrue(sed.labels.isEmpty());
        assertEquals("dataType", sed.switchVariableAsExpression.toString());
        assertEquals("<default>", sed.structure.expression().toString());
    }

}

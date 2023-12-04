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
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.NewSwitchStatement_0;
import org.e2immu.analyser.resolver.testexample.OldSwitchStatement_0;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestOldSwitchStatement extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(OldSwitchStatement_0.class);
        TypeInfo typeInfo = typeMap.get(OldSwitchStatement_0.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        SwitchStatementOldStyle stmt = (SwitchStatementOldStyle) block.structure.statements().get(1);
        assertEquals("dataType", stmt.expression.toString());
        assertEquals(3, stmt.switchLabels.size());
        assertSame(StatementExecution.ALWAYS, stmt.structure.statementExecution());
        assertEquals("a", stmt.label());

        SwitchStatementOldStyle.SwitchLabel se3 = stmt.switchLabels.get(0);
        assertEquals("3", se3.expression().toString());

        SwitchStatementOldStyle.SwitchLabel se4 = stmt.switchLabels.get(1);
        assertEquals("4", se4.expression().toString());
        BreakStatement bs4 = stmt.structure.block().structure.statements().get(2).asInstanceOf(BreakStatement.class);
        assertEquals("a", bs4.goToLabel());
        assertEquals("b", bs4.label);

        SwitchStatementOldStyle.SwitchLabel sed = stmt.switchLabels.get(2);
        assertEquals("<default>", sed.expression().toString());
    }

}

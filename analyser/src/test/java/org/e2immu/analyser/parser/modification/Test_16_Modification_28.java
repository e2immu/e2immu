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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
Statement 2.1.1 copies value, but statement 2.0.0 assigns it; the list becomes part of the mutable
field 'map', and is therefore linked to a modifiable object. Hence, the parameter's @Modified
 */
public class Test_16_Modification_28 extends CommonTestRunner {

    public Test_16_Modification_28() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Statement s3 = d.methodInfo().methodInspection.get().getMethodBody().structure.statements().get(3);
                if (s3 instanceof ExpressionAsStatement eas) {
                    if (eas.expression instanceof MethodCall mc) {
                        assertEquals("Type java.util.List<Integer>", mc.returnType().toString());
                    } else fail();
                } else fail();
            }
        };
        testClass("Modification_28", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}

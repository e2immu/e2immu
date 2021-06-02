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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.testexample.Fluent_1;
import org.e2immu.analyser.testexample.a.IFluent_1;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_57_Lambda extends CommonTestRunner {

    public Test_57_Lambda() {
        super(false);
    }

    // basics
    @Test
    public void test_0() throws IOException {
        testClass("Lambda_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}

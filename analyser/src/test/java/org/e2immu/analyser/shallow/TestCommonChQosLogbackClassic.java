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

package org.e2immu.analyser.shallow;

import ch.qos.logback.classic.Logger;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonChQosLogbackClassic extends CommonAnnotatedAPI {

    @Test
    public void testLoggerLevel() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Logger.class);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("setLevel", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE_DV, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(Level.FALSE_DV, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));

        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
    }

}

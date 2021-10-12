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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestPreloadJavaBase {


    @Test
    public void testNoPreload() throws IOException {
        // there is no preloading by default
        org.e2immu.analyser.util.Logger.activate(Logger.LogTarget.INSPECTOR,
                Logger.LogTarget.BYTECODE_INSPECTOR);
        Parser parser = new Parser();
        TypeContext typeContext = parser.getTypeContext();
        TypeInfo list = typeContext.typeMapBuilder.get("java.util.List");
        assertNull(list);
    }

    @Test
    public void testPreload() throws IOException {
        org.e2immu.analyser.util.Logger.activate(Logger.LogTarget.INSPECTOR,
                Logger.LogTarget.BYTECODE_INSPECTOR);
        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .build();
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfiguration)
                .build();
        Parser parser = new Parser(configuration);
        TypeContext typeContext = parser.getTypeContext();
        TypeInfo list = typeContext.typeMapBuilder.get("java.util.List");
        assertNotNull(list);
    }
}

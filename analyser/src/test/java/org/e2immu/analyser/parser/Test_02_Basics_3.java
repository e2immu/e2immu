
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.Test;

import java.io.IOException;

/*
Aims to catch problems in assigning properties from the field to the variable in statement 0, setS1.
The values there are a summary of what happened deeper down, which is different from what is in the field.
(The field cannot be @NotNull but locally we know s will not be null at that point.)
 */
public class Test_02_Basics_3 extends CommonTestRunner {

    // not loading in the AnnotatedAPIs, so System.out will have @Modified=1 after println()

    @Test
    public void test() throws IOException {
        testClass("Basics_3", 0, 1, new DebugConfiguration.Builder()

                .build());
    }

}

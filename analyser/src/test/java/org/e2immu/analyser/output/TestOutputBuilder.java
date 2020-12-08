/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.output;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestOutputBuilder {

    @Test
    public void test() {
        OutputBuilder[] outputBuilders = { new OutputBuilder().add(new Text("a")),
                new OutputBuilder().add(new Text("b")),
                new OutputBuilder().add(new Text("c")), };
        OutputBuilder all = Arrays.stream(outputBuilders).collect(OutputBuilder.joining(Symbol.COMMA));
        System.out.println(all.list);
        Assert.assertEquals(9, all.list.size());
        Assert.assertTrue(all.list.get(0) instanceof Guide guide && guide.position() == Guide.Position.START);
        Assert.assertTrue(all.list.get(3) instanceof Guide guide && guide.position() == Guide.Position.MID);
        Assert.assertTrue(all.list.get(6) instanceof Guide guide && guide.position() == Guide.Position.MID);
        Assert.assertTrue(all.list.get(8) instanceof Guide guide && guide.position() == Guide.Position.END);
    }
}

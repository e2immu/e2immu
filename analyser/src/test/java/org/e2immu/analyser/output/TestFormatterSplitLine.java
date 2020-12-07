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

public class TestFormatterSplitLine {

    // public int method  (17 chars)
    private OutputBuilder createExample0() {
        Guide.GuideGenerator gg = new Guide.GuideGenerator();
        Guide.GuideGenerator gg2 = new Guide.GuideGenerator();
        return new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE)
                .add(new Text("method"));
    }

    // public int method(int p1, int p2) { return p1+p2; }
    //        10|     18|            32|
    private OutputBuilder createExample1() {
        Guide.GuideGenerator gg = new Guide.GuideGenerator();
        Guide.GuideGenerator gg2 = new Guide.GuideGenerator();
        return new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE)
                .add(new Text("method"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(gg.start())
                .add(gg.mid()).add(new Text("int")).add(Space.ONE).add(new Text("p1")).add(Symbol.COMMA)
                .add(gg.mid()).add(new Text("int")).add(Space.ONE).add(new Text("p2"))
                .add(gg.end())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg.start())
                .add(gg.mid())
                .add(new Text("return")).add(Space.ONE)
                .add(new Text("p1")).add(Symbol.binaryOperator("+")).add(new Text("p2")).add(Symbol.SEMICOLON)
                .add(gg.end())
                .add(Symbol.RIGHT_BRACE);
    }

    @Test
    public void testLookAhead() {
        Formatter formatter = new Formatter(FormattingOptions.DEFAULT); // options
        Assert.assertEquals(17, formatter.lookAhead(createExample0().list, 20));
        Assert.assertEquals(17, formatter.lookAhead(createExample0().list, 15));

        // up to the ( now
        Assert.assertEquals(18, formatter.lookAhead(createExample1().list, 20));

        // up to the { now, we've included the whole (...) guide
        Assert.assertEquals(33, formatter.lookAhead(createExample1().list, 35));
    }

    @Test
    public void testLineSplit1() {
        String PACKAGE = "org.e2immu.analyser.output";
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("package")).add(Space.ONE).add(new Text(PACKAGE));
        Assert.assertEquals("package " + PACKAGE, outputBuilder.toString());
        Assert.assertEquals("package " + PACKAGE + "\n", new Formatter(FormattingOptions.DEFAULT).write(outputBuilder));

        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("package\n    " + PACKAGE + "\n", new Formatter(options).write(outputBuilder));
    }

    @Test
    public void testLineSplit2() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(8)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("static")).add(Space.ONE)
                .add(new Text("abstract")).add(Space.ONE)
                .add(new Text("method")).add(Symbol.SEMICOLON);
        Assert.assertEquals("public\n  static\n  abstract\n  method;\n",
                new Formatter(options).write(outputBuilder));
    }
}

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

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class TestFormatterSplitLine {

    // public int method  (17 chars)
    private OutputBuilder createExample0() {
        return new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE)
                .add(new Text("method"));
    }

    // public int method(int p1, int p2) { return p1+p2; }
    //        10|     18|            33|
    private OutputBuilder createExample1() {
        Guide.GuideGenerator gg = new Guide.GuideGenerator();
        Guide.GuideGenerator gg2 = Guide.generatorForBlock();

        return new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE)
                .add(new Text("method"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(gg.start()).add(new Text("int")).add(Space.ONE).add(new Text("p1")).add(Symbol.COMMA)
                .add(gg.mid()).add(new Text("int")).add(Space.ONE).add(new Text("p2"))
                .add(gg.end())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg2.start()).add(new Text("return")).add(Space.ONE)
                .add(new Text("p1")).add(Symbol.binaryOperator("+")).add(new Text("p2")).add(Symbol.SEMICOLON)
                .add(gg2.end())
                .add(Symbol.RIGHT_BRACE);
    }

    @Test
    public void testLookAhead() {
        Formatter formatter = new Formatter(FormattingOptions.DEFAULT); // options
        Assert.assertEquals(17, formatter.lookAhead(createExample0().list, 20));
        Assert.assertEquals(17, formatter.lookAhead(createExample0().list, 15));

        // up to the ( now
        Assert.assertEquals(18, formatter.lookAhead(createExample1().list, 20));

        List<OutputElement> list = createExample1().list;
        // up to the { now, we've included the whole (...) guide
        Assert.assertEquals(35, formatter.lookAhead(createExample1().list, 35));

        // int p1, (7 = 6+the start guide)
        List<OutputElement> subList = list.subList(7, list.size());
        Assert.assertEquals(7, formatter.lookAhead(subList, 120));
    }

    @Test
    public void testLineSplit1() {
        String PACKAGE = "org.e2immu.analyser.output";
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("package")).add(Space.ONE).add(new Text(PACKAGE));
        Assert.assertEquals("package " + PACKAGE, outputBuilder.toString());
        Assert.assertEquals("package " + PACKAGE + "\n", new Formatter(FormattingOptions.DEFAULT).write(outputBuilder));

        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();

        Assert.assertEquals(34, new Formatter(options).lookAhead(outputBuilder.list, 20));

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

    @Test
    public void testGuide1() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("""
                        public int method(
                          int p1,
                          int p2) {
                          return p1 + p2;
                        }  
                        """,
                new Formatter(options).write(createExample1()));
    }

    @Test
    public void testGuide1LongLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();

        // the space is recognized by the forward method
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(createExample1().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        Assert.assertEquals(",", info.get(7).string());
        Assert.assertNull(info.get(8).string());
        Assert.assertEquals(" int", info.get(9).string());

        Assert.assertEquals(53, new Formatter(options).lookAhead(createExample1().list, 120));

        Assert.assertEquals("public int method(int p1, int p2) { return p1 + p2; }\n",
                new Formatter(options).write(createExample1()));
    }

    @Test
    public void testGuide1Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120)
                .setSpacesInTab(2).setTabsForLineSplit(2).setCompact(true).build();
        Assert.assertEquals("public int method(int p1,int p2){return p1+p2;}\n",
                new Formatter(options).write(createExample1()));
    }

    private OutputBuilder createExample2() {
        Guide.GuideGenerator gg = new Guide.GuideGenerator();
        Guide.GuideGenerator gg1 = Guide.generatorForBlock();
        Guide.GuideGenerator gg2 = new Guide.GuideGenerator();

        return new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE)
                .add(new Text("method"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(gg.start()).add(new Text("int")).add(Space.ONE).add(new Text("p1")).add(Symbol.COMMA)
                .add(gg.mid()).add(new Text("int")).add(Space.ONE).add(new Text("p2")).add(Symbol.COMMA)
                .add(gg.mid()).add(new Text("double")).add(Space.ONE).add(new Text("somewhatLonger")).add(Symbol.COMMA)
                .add(gg.mid()).add(new Text("double")).add(Space.ONE).add(new Text("d"))
                .add(gg.end())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg1.start()).add(new Text("log")).add(Symbol.LEFT_PARENTHESIS)
                .add(gg2.start()).add(new Text("p1")).add(Symbol.COMMA)
                .add(gg2.mid()).add(new Text("p2")).add(gg2.end()).add(Symbol.RIGHT_PARENTHESIS).add(Symbol.SEMICOLON)
                .add(gg1.mid()).add(new Text("return")).add(Space.ONE)
                .add(new Text("p1")).add(Symbol.binaryOperator("+")).add(new Text("p2")).add(Symbol.SEMICOLON)
                .add(gg1.end())
                .add(Symbol.RIGHT_BRACE);
    }

    @Test
    public void testGuide2() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("""
                        public int method(
                          int p1,
                          int p2,
                          double
                              somewhatLonger,
                          double d) {
                          log(p1, p2);
                          return p1 + p2;
                        }  
                        """,
                //      01234567890123456789
                new Formatter(options).write(createExample2()));
    }

    @Test
    public void testGuide2MidLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(80).setCompact(false).build();
        // around 90 characters long
        Assert.assertEquals("""
                        public int method(int p1, int p2, double somewhatLonger, double d) {
                            log(p1, p2);
                            return p1 + p2;
                        }
                        """,
                new Formatter(options).write(createExample2()));
    }

    @Test
    public void testGuide2LongLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(false).build();
        // around 90 characters long
        Assert.assertEquals("public int method(int p1, int p2, double somewhatLonger, double d) { log(p1, p2); return p1 + p2; }\n",
                new Formatter(options).write(createExample2()));
    }

    @Test
    public void testGuide2Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(true).build();
        // around 90 characters long

        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(createExample2().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        Assert.assertEquals(41, info.size());
        Assert.assertEquals(" somewhatLonger", info.get(14).string());
        Assert.assertNull(info.get(16).string()); // ensure that the MID is there

        Assert.assertEquals(89, new Formatter(options).lookAhead(createExample2().list, 120));

        Assert.assertEquals("public int method(int p1,int p2,double somewhatLonger,double d){log(p1,p2);return p1+p2;}\n",
                new Formatter(options).write(createExample2()));
    }

    private OutputBuilder createExample3() {
        Guide.GuideGenerator gg = Guide.generatorForBlock();
        Guide.GuideGenerator gg1 = Guide.generatorForBlock();
        Guide.GuideGenerator gg2 = Guide.generatorForBlock();

        return new OutputBuilder()
                .add(new Text("try")).add(Symbol.LEFT_BRACE)
                .add(gg.start())
                .add(new Text("if")).add(Symbol.LEFT_PARENTHESIS).add(new Text("a")).add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg1.start()).add(new Text("assert")).add(Space.ONE).add(new Text("b")).add(Symbol.SEMICOLON).add(gg1.end())
                .add(Symbol.RIGHT_BRACE)
                .add(new Text("else")).add(Symbol.LEFT_BRACE)
                .add(gg2.start()).add(new Text("assert")).add(Space.ONE).add(new Text("c")).add(Symbol.SEMICOLON)
                .add(gg2.mid()).add(new Text("exit")).add(Symbol.LEFT_PARENTHESIS).add(new Text("1")).add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.SEMICOLON).add(gg2.end()).add(Symbol.RIGHT_BRACE)
                .add(gg.end())
                .add(Symbol.RIGHT_BRACE);
    }

    // check that the lookahead does not go into line split mode with a { ... } guide
    @Test
    public void testGuide3() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("""
                        try {
                          if(a) {
                            assert b;
                          } else {
                            assert c;
                            exit(1);
                          }
                        }  
                        """,
                //      01234567890123456789
                //        if(a) { assert b; } -> the end of the guide is within 20...
                new Formatter(options).write(createExample3()));
    }

    // identical at 30... if the whole statement doesn't fit in 30, then it gets split on {
    @Test
    public void testGuide3ShortLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(30)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("""
                        try {
                          if(a) { 
                            assert b; 
                          } else {
                            assert c;
                            exit(1);
                          }
                        }
                        """,
                new Formatter(options).write(createExample3()));
    }


    @Test
    public void testGuide3Midline() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(80)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("try { if(a) { assert b; } else { assert c; exit(1); } }\n",
                new Formatter(options).write(createExample3()));
    }

    @Test
    public void testGuide3Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(true).build();
        Assert.assertEquals("try{if(a){assert b;}else{assert c;exit(1);}}\n",
                new Formatter(options).write(createExample3()));
    }

    private OutputBuilder createExample4() {
        Guide.GuideGenerator ggA = Guide.generatorForAnnotationList();
        Guide.GuideGenerator gg = new Guide.GuideGenerator();
        Guide.GuideGenerator gg2 = Guide.generatorForBlock();

        return new OutputBuilder()
                .add(ggA.start()).add(Symbol.AT).add(new Text("NotModified")).add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA.mid()).add(Symbol.AT).add(new Text("Independent")).add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA.mid()).add(Symbol.AT).add(new Text("NotNull")).add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA.mid()).add(new Text("public")).add(Space.ONE).add(new Text("int")).add(Space.ONE)
                .add(new Text("method"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(gg.start()).add(new Text("int")).add(Space.ONE).add(new Text("p1")).add(Symbol.COMMA)
                .add(gg.mid()).add(new Text("int")).add(Space.ONE).add(new Text("p2"))
                .add(gg.end())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg2.start()).add(new Text("return")).add(Space.ONE)
                .add(new Text("p1")).add(Symbol.binaryOperator("+")).add(new Text("p2")).add(Symbol.SEMICOLON)
                .add(gg2.end())
                .add(Symbol.RIGHT_BRACE)
                .add(ggA.end());
    }

    @Test
    public void testGuide4() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Assert.assertEquals("""
                        @NotModified
                        @Independent
                        @NotNull 
                        public int method(
                          int p1,
                          int p2) {
                          return p1 + p2;
                        }
                        """,
                //      01234567890123456789
                //        if(a) { assert b; } -> the end of the guide is within 20...
                new Formatter(options).write(createExample4()));
    }

    @Test
    public void testGuide4Compact() throws IOException {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(true).build();
        Formatter formatter = new Formatter(options);
        List<OutputElement> list = createExample4().list;
        Assert.assertEquals(41, list.size());

        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        Assert.assertNull(info.get(30).string()); // end of } guide
        Assert.assertEquals("}", info.get(31).string());
        Assert.assertNull(info.get(32).string()); // end of annotation guide

        Assert.assertEquals(82, formatter.lookAhead(list, 120));
        Assert.assertEquals(40, formatter.writeLine(list, new StringWriter(), 0, 82, new Stack<>()));

        Assert.assertEquals("@NotModified @Independent @NotNull public int method(int p1,int p2){return p1+p2;}\n",
                new Formatter(options).write(createExample4()));
    }

    private OutputBuilder createExample5() {
        Guide.GuideGenerator ggA = Guide.generatorForAnnotationList();
        Guide.GuideGenerator gg = new Guide.GuideGenerator();
        Guide.GuideGenerator gg2 = Guide.generatorForBlock();
        Guide.GuideGenerator ggA2 = Guide.generatorForAnnotationList();

        return new OutputBuilder()
                .add(ggA.start())
                .add(Symbol.AT)
                .add(new TypeName("E2Container", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("class"))
                .add(Space.ONE)
                .add(new Text("Basics_0"))
                .add(Symbol.LEFT_BRACE)
                .add(gg2.start())
                .add(ggA2.start())
                .add(Symbol.AT)
                .add(new TypeName("Constant", "", ""))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("\"abc\""))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(Symbol.AT)
                .add(new TypeName("E2Container", "", ""))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("absent"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("true"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(Symbol.AT)
                .add(new TypeName("Final", "", ""))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("absent"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("true"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotNull", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(gg.start())
                .add(new Text("private"))
                .add(Space.ONE)
                .add(gg.mid())
                .add(new Text("final"))
                .add(gg.end())
                .add(Space.ONE)
                .add(new Text("String"))
                .add(Space.ONE)
                .add(new Text("explicitlyFinal"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("\"abc\""))
                .add(Symbol.SEMICOLON)
                .add(ggA2.end())
                .add(gg2.end())
                .add(Symbol.RIGHT_BRACE)
                .add(ggA.end());
    }

    @Test
    public void testGuide5() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(false).build();
        Assert.assertEquals("""
                        @E2Container 
                        public class Basics_0 { 
                            @Constant("abc") 
                            @E2Container(absent = true) 
                            @Final(absent = true) 
                            @NotNull 
                            private final String explicitlyFinal = "abc"; 
                        }
                        """,
                new Formatter(options).write(createExample5()));
    }

    @Test
    public void testGuide5LongLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(400).setCompact(false).build();
        Assert.assertEquals("""
                        @E2Container public class Basics_0 { @Constant("abc") @E2Container(absent = true) @Final(absent = true) @NotNull private final String explicitlyFinal = "abc"; }
                        """,
                new Formatter(options).write(createExample5()));
    }

    @Test
    public void testGuide5CompactShortLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(80).setCompact(true).build();
        Assert.assertEquals("""
                        @E2Container 
                        public class Basics_0{
                        @Constant("abc")
                        @E2Container(absent=true)
                        @Final(absent=true)
                        @NotNull
                        private final String explicitlyFinal="abc";
                        }
                        """,
                new Formatter(options).write(createExample5()));
    }

    // public method(int p1, int p2);
    @Test
    public void testForward1() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(8)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(new Text("public")) // 0
                .add(Space.ONE) //1
                .add(Space.ONE) //2
                .add(new Text("method")) // 3
                .add(Symbol.LEFT_PARENTHESIS) // 4
                .add(new Text("int")) //5
                .add(Space.ONE) //6
                .add(new Text("p1")) // 7
                .add(Symbol.COMMA) // 8
                .add(new Text("int"))
                .add(Space.ONE)
                .add(new Text("p2")) // 11
                .add(Symbol.RIGHT_PARENTHESIS) // 12
                .add(Symbol.SEMICOLON); // 13
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 100);
        Assert.assertEquals("public", info.get(0).string());
        Assert.assertEquals(3, info.get(1).pos()); // pos 1, 2 have been skipped
        Assert.assertEquals(6, info.get(1).chars()); // 6 chars have been written before this space
    }

    // !a && b == c
    @Test
    public void testForward2() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(8)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(Symbol.UNARY_BOOLEAN_NOT) // 0
                .add(new Text("a")) //1
                .add(Symbol.binaryOperator("&&")) //2
                .add(new Text("b")) // 3
                .add(Symbol.binaryOperator("==")) // 4
                .add(new Text("c")) //5
                .add(Symbol.SEMICOLON); // 6
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 100);
        Assert.assertEquals("!", info.get(0).string());
    }

    // a = { { "b", "c" }, "d" };
    @Test
    public void testForward3() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(8)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(new Text("a")) // 0
                .add(Symbol.binaryOperator("=")) //1
                .add(Symbol.LEFT_BRACE) // 2
                .add(Symbol.LEFT_BRACE)//3
                .add(new Text("\"b\"")) // 4
                .add(Symbol.COMMA)
                .add(new Text("\"c\"")) // 6
                .add(Symbol.RIGHT_BRACE)
                .add(Symbol.COMMA)
                .add(new Text("\"d\"")) // 9
                .add(Symbol.RIGHT_BRACE)
                .add(Symbol.SEMICOLON); // 11
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 100);
        Assert.assertEquals("a", info.get(0).string());
    }

    @Test
    public void testForward4() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(15)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(createExample0().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 25);
        Assert.assertEquals(3, info.size());
    }

    @Test
    public void testForward5() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(15)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        new Formatter(options).forward(createExample0().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 15);
        Assert.assertEquals(2, info.size());
    }

    // public method(int p1, int p2); with guides
    @Test
    public void testForward6() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(8)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        Guide.GuideGenerator guideGenerator = new Guide.GuideGenerator();
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(new Text("public")) // 0
                .add(Space.ONE) //1
                .add(new Text("method")) // 2
                .add(Symbol.LEFT_PARENTHESIS)
                .add(guideGenerator.start())
                .add(new Text("int")); // 5
        List<Formatter.ForwardInfo> info = new ArrayList<>();
        boolean interrupted = new Formatter(options).forward(outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 14);
        Assert.assertFalse(interrupted);
        Assert.assertEquals(3, info.size()); // excluding the start guide
    }
}

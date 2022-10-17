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

package org.e2immu.analyser.output;

import org.e2immu.analyser.output.formatter.Forward;
import org.e2immu.analyser.output.formatter.ForwardInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestFormatter1 {

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
        Guide.GuideGenerator gg = Guide.generatorForParameterDeclaration();
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
    public void testLineSplit1() {
        String PACKAGE = "org.e2immu.analyser.output";
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("package")).add(Space.ONE).add(new Text(PACKAGE));
        assertEquals("package " + PACKAGE, outputBuilder.toString());
        assertEquals("package " + PACKAGE + "\n", new Formatter(FormattingOptions.DEFAULT).write(outputBuilder));

        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Formatter formatter = new Formatter(options);

        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, options.lengthOfLine() + 20);
        assertEquals(2, info.size());
        assertEquals(" " + PACKAGE, info.get(1).string());

        assertEquals("package\n    " + PACKAGE + "\n", formatter.write(outputBuilder));
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

        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        assertEquals(5, info.size());

        assertEquals("public\n  static\n  abstract\n  method;\n",
                new Formatter(options).write(outputBuilder));
    }

    @Test
    public void testGuide1() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(20)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        assertEquals("""
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
        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, createExample1().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        assertEquals(",", info.get(7).string());
        assertNull(info.get(8).string());
        assertEquals(" int", info.get(9).string());

        //assertEquals(53, new Formatter(options).lookAhead(createExample1().list, 120));

        assertEquals("public int method(int p1, int p2) { return p1 + p2; }\n",
                new Formatter(options).write(createExample1()));
    }

    @Test
    public void testGuide1Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120)
                .setSpacesInTab(2).setTabsForLineSplit(2).setCompact(true).build();
        assertEquals("public int method(int p1,int p2){return p1+p2;}\n",
                new Formatter(options).write(createExample1()));
    }

    private OutputBuilder createExample2() {
        Guide.GuideGenerator gg = Guide.generatorForParameterDeclaration();
        Guide.GuideGenerator gg1 = Guide.generatorForBlock();
        Guide.GuideGenerator gg2 = Guide.defaultGuideGenerator();

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
        assertEquals("""
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
        assertEquals("""
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
        assertEquals("public int method(int p1, int p2, double somewhatLonger, double d) { log(p1, p2); return p1 + p2; }\n",
                new Formatter(options).write(createExample2()));
    }

    @Test
    public void testGuide2Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(true).build();
        // around 90 characters long

        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, createExample2().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        assertEquals(41, info.size());
        assertEquals(" somewhatLonger", info.get(14).string());
        assertNull(info.get(16).string()); // ensure that the MID is there

        //assertEquals(89, new Formatter(options).lookAhead(createExample2().list, 120));

        assertEquals("public int method(int p1,int p2,double somewhatLonger,double d){log(p1,p2);return p1+p2;}\n",
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
        assertEquals("""
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
        assertEquals("""
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
        assertEquals("try { if(a) { assert b; } else { assert c; exit(1); } }\n",
                new Formatter(options).write(createExample3()));
    }

    @Test
    public void testGuide3Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(true).build();
        assertEquals("try{if(a){assert b;}else{assert c;exit(1);}}\n",
                new Formatter(options).write(createExample3()));
    }

    private OutputBuilder createExample4() {
        Guide.GuideGenerator ggA = Guide.generatorForAnnotationList();
        Guide.GuideGenerator gg = Guide.generatorForParameterDeclaration();
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
        assertEquals("""
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
    public void testGuide4Compact() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(true).build();
        Formatter formatter = new Formatter(options);
        List<OutputElement> list = createExample4().list;
        assertEquals(41, list.size());

        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        assertNull(info.get(30).string()); // end of } guide
        assertEquals("}", info.get(31).string());
        assertNull(info.get(32).string()); // end of annotation guide

        assertEquals("@NotModified @Independent @NotNull public int method(int p1,int p2){return p1+p2;}\n",
                formatter.write(createExample4()));
    }

    private OutputBuilder createExample5(boolean extended) {
        Guide.GuideGenerator ggA = Guide.generatorForAnnotationList();
        Guide.GuideGenerator gg = Guide.defaultGuideGenerator();
        Guide.GuideGenerator gg2 = Guide.generatorForBlock();
        Guide.GuideGenerator ggA2 = Guide.generatorForAnnotationList();

        OutputBuilder outputBuilder = new OutputBuilder()
                .add(ggA.start())
                .add(Symbol.AT)
                .add(new TypeName("E2Container"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("class"))
                .add(Space.ONE)
                .add(new Text("Basics_0"))
                .add(Symbol.LEFT_BRACE)
                .add(gg2.start())
                .add(ggA2.start())
                .add(Symbol.AT)
                .add(new TypeName("Constant"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("\"abc\""))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(Symbol.AT)
                .add(new TypeName("E2Container"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("absent"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("true"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(Symbol.AT)
                .add(new TypeName("Final"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("absent"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("true"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(ggA2.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotNull"))
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
                .add(ggA2.end());
        if (extended) {
            outputBuilder
                    .add(gg2.mid())
                    .add(new Text("private"))
                    .add(Space.ONE)
                    .add(new Text("String"))
                    .add(Space.ONE)
                    .add(new Text("nonFinal"))
                    .add(Symbol.binaryOperator("="))
                    .add(new Text("\"xyz\""))
                    .add(Symbol.SEMICOLON);
        }
        return outputBuilder.add(gg2.end())
                .add(Symbol.RIGHT_BRACE)
                .add(ggA.end());
    }

    @Test
    public void testGuide5() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(false).build();
        Formatter formatter = new Formatter(options);
        List<OutputElement> list = createExample5(false).list;

        // the two guides one after the other should not result in a blank line
        assertTrue(list.get(55) instanceof Guide);
        assertTrue(list.get(56) instanceof Guide);
        assertEquals(Symbol.RIGHT_BRACE, list.get(57));
        assertTrue(list.get(58) instanceof Guide);

        assertEquals("""
                @E2Container
                public class Basics_0 {
                    @Constant("abc")
                    @E2Container(absent = true)
                    @Final(absent = true)
                    @NotNull
                    private final String explicitlyFinal = "abc";
                }
                """, formatter.write(createExample5(false)));
    }

    @Test
    public void testGuide5LongLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(400).setCompact(false).build();
        Formatter formatter = new Formatter(options);

        assertEquals("""
                @E2Container public class Basics_0 { @Constant("abc") @E2Container(absent = true) @Final(absent = true) @NotNull private final String explicitlyFinal = "abc"; }
                """, formatter.write(createExample5(false)));
    }

    @Test
    public void testGuide5CompactShortLine() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(80).setCompact(true).build();
        assertEquals("""
                @E2Container
                public class Basics_0{
                @Constant("abc")
                @E2Container(absent=true)
                @Final(absent=true)
                @NotNull
                private final String explicitlyFinal="abc";
                }
                """, new Formatter(options).write(createExample5(false)));
    }

    @Test
    public void testGuide6() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(120).setCompact(false).build();
        Formatter formatter = new Formatter(options);

        assertEquals("""
                @E2Container
                public class Basics_0 {
                    @Constant("abc")
                    @E2Container(absent = true)
                    @Final(absent = true)
                    @NotNull
                    private final String explicitlyFinal = "abc";
                    private String nonFinal = "xyz";
                }
                """, formatter.write(createExample5(true)));
    }


    // variant on example 1
    private OutputBuilder createExample7() {
        Guide.GuideGenerator gg = Guide.generatorForParameterDeclaration();
        Guide.GuideGenerator gg2 = Guide.generatorForBlock();

        return new OutputBuilder()
                .add(new Text("public")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE)
                .add(new Text("method"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(gg.start())
                .add(Symbol.AT).add(new Text("E2Immutable")).add(Space.ONE)
                .add(new Text("int")).add(Space.ONE).add(new Text("p1")).add(Symbol.COMMA)
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
    public void testGuide7Long() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(80)
                .setSpacesInTab(2).setTabsForLineSplit(2).build();
        Formatter formatter = new Formatter(options);
        List<OutputElement> list = createExample7().list;
        assertEquals(30, list.size());

        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 120);
        assertEquals("@", info.get(5).string());

        assertEquals("public int method(@E2Immutable int p1, int p2) { return p1 + p2; }\n",
                formatter.write(createExample7()));
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
        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 100);
        assertEquals("public", info.get(0).string());
        assertEquals(3, info.get(1).pos()); // pos 1, 2 have been skipped
        assertEquals(6, info.get(1).chars()); // 6 chars have been written before this space
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
        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 100);
        assertEquals("!", info.get(0).string());
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
        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 100);
        assertEquals("a", info.get(0).string());
    }

    @Test
    public void testForward4() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(15)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, createExample0().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 25);
        assertEquals(3, info.size());
    }

    @Test
    public void testForward5() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(15)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        List<ForwardInfo> info = new ArrayList<>();
        Forward.forward(options, createExample0().list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 15);
        assertEquals(3, info.size());
    }

    // public method(int p1, int p2); with guides
    @Test
    public void testForward6() {
        FormattingOptions options = new FormattingOptions.Builder().setLengthOfLine(8)
                .setSpacesInTab(2).setTabsForLineSplit(1).build();
        Guide.GuideGenerator guideGenerator = Guide.defaultGuideGenerator();
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(new Text("public")) // 0
                .add(Space.ONE) //1
                .add(new Text("method")) // 2
                .add(Symbol.LEFT_PARENTHESIS)
                .add(guideGenerator.start())
                .add(new Text("int")); // 5
        List<ForwardInfo> info = new ArrayList<>();
        boolean interrupted = Forward.forward(options, outputBuilder.list, fi -> {
            info.add(fi);
            System.out.println(fi);
            return false;
        }, 0, 14);
        assertFalse(interrupted);
        assertEquals(3, info.size()); // excluding the start guide
    }
}

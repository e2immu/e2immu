package org.e2immu.analyser.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFormatter5 {

    private static OutputBuilder createExample0() {

        Guide.GuideGenerator ggBlock = Guide.generatorForBlock();
        Guide.GuideGenerator ggComment = Guide.generatorForMultilineComment();

        return new OutputBuilder()
                .add(ggBlock.start())
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(ggComment.start())
                .add(new Text("line 1"))
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggComment.mid())
                .add(new Text("line 2"))
                .add(ggComment.end())
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(Symbol.AT)
                .add(new TypeName("ImmutableContainer"))
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("IMPLIED"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotNull"))
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("OK"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(ggBlock.end());
    }


    @Test
    public void testExample0() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        Formatter formatter = new Formatter(options);
        OutputBuilder example = createExample0();

        assertEquals("/*line 1 line 2*/ @ImmutableContainer /*IMPLIED*/ @NotNull /*OK*/\n", formatter.write(example));
    }

    private static OutputBuilder createExample1() {

        Guide.GuideGenerator ggBlock = Guide.generatorForBlock();
        Guide.GuideGenerator ggComment = Guide.generatorForMultilineComment();

        return new OutputBuilder()
                .add(ggBlock.start())
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(ggComment.start())
                .add(new Text("line 1 is much longer than in the previous example, we want to force everything"))
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggComment.mid())
                .add(new Text("on multiple lines. So therefore, line 2 is also rather long"))
                .add(ggComment.end())
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(Symbol.AT)
                .add(new TypeName("ImmutableContainer"))
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("IMPLIED"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotNull"))
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("OK"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(ggBlock.end());
    }


    @Test
    public void testExample1() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        Formatter formatter = new Formatter(options);
        OutputBuilder example = createExample1();

        assertEquals("""
                    
                    /*
                    line 1 is much longer than in the previous example, we want to force everything
                    on multiple lines. So therefore, line 2 is also rather long
                    */
                    @ImmutableContainer /*IMPLIED*/
                    @NotNull /*OK*/
                """, formatter.write(example));
    }


    private static OutputBuilder createExample2() {

        Guide.GuideGenerator ggBlock = Guide.generatorForAnnotationList();
        Guide.GuideGenerator ggComment = Guide.generatorForMultilineComment();
        Guide.GuideGenerator ggMethodModifiers = Guide.defaultGuideGenerator();
        Guide.GuideGenerator ggParams = Guide.generatorForParameterDeclaration();
        Guide.GuideGenerator ggMethodBlock = Guide.generatorForBlock();
        Guide.GuideGenerator ggSwitchBlock = Guide.generatorForBlock();

        Guide.GuideGenerator ggA1 = Guide.defaultGuideGenerator();
        Guide.GuideGenerator ggA2 = Guide.generatorForBlock();
        OutputBuilder aBlock = new OutputBuilder()
                .add(ggA1.start())
                .add(ggA2.start())
                .add(new Text("\"a\""))
                .add(Symbol.SEMICOLON)
                .add(ggA2.end())
                .add(ggA1.end())
                .add(Space.ONE_IS_NICE_EASY_SPLIT);

        Guide.GuideGenerator ggB1 = Guide.defaultGuideGenerator();
        Guide.GuideGenerator ggB2 = Guide.generatorForBlock();
        OutputBuilder bBlock = new OutputBuilder()
                .add(ggB1.start())
                .add(ggB2.start())
                .add(new Text("\"b\""))
                .add(Symbol.SEMICOLON)
                .add(ggB2.end())
                .add(ggB1.end())
                .add(Space.ONE_IS_NICE_EASY_SPLIT);

        Guide.GuideGenerator ggD1 = Guide.defaultGuideGenerator();
        Guide.GuideGenerator ggD2 = Guide.generatorForBlock();
        OutputBuilder defaultBlock = new OutputBuilder()
                .add(ggD1.start())
                .add(ggD2.start())
                .add(new QualifiedName("c"))
                .add(Symbol.binaryOperator("=="))
                .add(new Text("'a'"))
                .add(Symbol.LOGICAL_OR)
                .add(new QualifiedName("c"))
                .add(Symbol.binaryOperator("=="))
                .add(new Text("'b'"))
                .add(Symbol.QUESTION_MARK)
                .add(new QualifiedName("b"))
                .add(Symbol.COLON)
                .add(new Text("\"c\""))
                .add(Symbol.SEMICOLON)
                .add(ggD2.end())
                .add(ggD1.end());

        return new OutputBuilder()
                .add(ggBlock.start())
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(ggComment.start())
                .add(new Text("should raise a warning that the condition is always false, plus that b is never used"))
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggComment.mid())
                .add(new Text("as a consequence, default always returns \"c\" so we have @NotNull"))
                .add(ggComment.end())
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(Symbol.AT)
                .add(new TypeName("ImmutableContainer"))
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("IMPLIED")) // 14
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotNull")) // 19
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("OK"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Space.ONE_IS_NICE_EASY_SPLIT)
                .add(ggBlock.mid())
                .add(ggMethodModifiers.start()) // 25
                .add(new Text("public"))
                .add(Space.ONE)
                .add(ggMethodModifiers.mid())
                .add(new Text("static"))
                .add(ggMethodModifiers.end()) // 30
                .add(Space.ONE)
                .add(new TypeName("String"))
                .add(Space.ONE)
                .add(new Text("method")) // 34
                .add(Symbol.LEFT_PARENTHESIS)
                .add(ggParams.start())
                .add(new TypeName("char")) // 37
                .add(Space.ONE)
                .add(new Text("c"))
                .add(Symbol.COMMA)
                .add(ggParams.mid())
                .add(new TypeName("String"))
                .add(Space.ONE)
                .add(new Text("b"))
                .add(ggParams.end())
                .add(Symbol.RIGHT_PARENTHESIS) // 46
                .add(Symbol.LEFT_BRACE)
                .add(ggMethodBlock.start())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new QualifiedName("c"))
                .add(Symbol.RIGHT_PARENTHESIS) // 54
                .add(Symbol.LEFT_BRACE)
                .add(ggSwitchBlock.start())
                .add(ggSwitchBlock.mid())
                .add(new Text("a")) // 58
                .add(Symbol.LAMBDA)
                .add(aBlock) // 60-66
                .add(ggSwitchBlock.mid())
                .add(ggSwitchBlock.mid())
                .add(new Text("b")) // 69
                .add(Symbol.LAMBDA)
                .add(bBlock) // 71-77
                .add(ggSwitchBlock.mid())
                .add(ggSwitchBlock.mid())
                .add(new Text("default")) // 80
                .add(Symbol.LAMBDA)
                .add(defaultBlock) // 82-97
                .add(ggSwitchBlock.end())
                .add(Symbol.RIGHT_BRACE) // 99
                .add(Symbol.SEMICOLON)
                .add(Symbol.LEFT_BLOCK_COMMENT) // 101
                .add(new Text("inline conditional evaluates to constant")) // 102
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(ggMethodBlock.end())
                .add(Symbol.RIGHT_BRACE)
                .add(ggBlock.end());
    }


    @Test
    public void testExample2() {
        Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
        OutputBuilder example = createExample2();
        assertEquals(107, example.list.size());

        assertEquals("""
                    
                    /*
                    should raise a warning that the condition is always false, plus that b is never used
                    as a consequence, default always returns "c" so we have @NotNull
                    */
                    @ImmutableContainer /*IMPLIED*/
                    @NotNull /*OK*/
                    public static String method(char c, String b) {
                        return switch(c) { a -> "a"; b -> "b"; default -> c == 'a' || c == 'b' ? b : "c"; }; /*
                                inline conditional evaluates to constant*/}
                """, formatter.write(example));
    }
}

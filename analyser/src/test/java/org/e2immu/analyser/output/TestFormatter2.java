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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFormatter2 {

    private OutputBuilder createExample0() {
        Guide.GuideGenerator type = Guide.generatorForAnnotationList();
        Guide.GuideGenerator field = Guide.generatorForAnnotationList();
        Guide.GuideGenerator paramsConstructor = Guide.generatorForAnnotationList();
        Guide.GuideGenerator constructor = Guide.generatorForAnnotationList();
        Guide.GuideGenerator method = Guide.generatorForAnnotationList();

        Guide.GuideGenerator blockType = Guide.generatorForBlock();
        Guide.GuideGenerator blockConstructor = Guide.generatorForBlock();
        Guide.GuideGenerator blockMethod = Guide.generatorForBlock();

        Guide.GuideGenerator modifiersField = Guide.defaultGuideGenerator();

        return new OutputBuilder()
                .add(type.start())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("class"))
                .add(Space.ONE)
                .add(new Text("Basics_1"))
                .add(Symbol.LEFT_BRACE)
                .add(blockType.start())
                .add(field.start())
                .add(Symbol.AT)
                .add(new TypeName("Linked"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("to"))
                .add(Symbol.binaryOperator("="))
                .add(Symbol.LEFT_BRACE)
                .add(new Text("\"p0\""))
                .add(Symbol.RIGHT_BRACE)
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(modifiersField.start())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(modifiersField.mid())
                .add(new Text("final"))
                .add(modifiersField.end())
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("f1"))
                .add(Symbol.SEMICOLON)
                .add(field.end())
                .add(blockType.mid())
                .add(constructor.start())
                .add(Symbol.AT)
                .add(new TypeName("Dependent"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(constructor.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("Basics_1"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(paramsConstructor.start())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p0"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p1"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE)
                .add(new Text("String"))
                .add(Space.ONE)
                .add(new Text("p2"))
                .add(paramsConstructor.end())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(blockConstructor.start())
                .add(Space.NONE)
                .add(Space.NONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("s1"))
                .add(Symbol.binaryOperator("="))
                .add(new QualifiedName("p0"))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.mid())
                .add(new QualifiedName("this"))
                .add(Symbol.DOT)
                .add(new QualifiedName("f1"))
                .add(Symbol.binaryOperator("="))
                .add(new QualifiedName("s1"))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.end())
                .add(Symbol.RIGHT_BRACE)
                .add(constructor.end())
                .add(blockType.mid())
                .add(method.start())
                .add(Symbol.AT)
                .add(new TypeName("Independent"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("getF1"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(blockMethod.start())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new QualifiedName("f1"))
                .add(Symbol.SEMICOLON)
                .add(blockMethod.end())
                .add(Symbol.RIGHT_BRACE)
                .add(method.end())
                .add(blockType.end())
                .add(Symbol.RIGHT_BRACE)
                .add(type.end());
    }

    @Test
    public void testExample0() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        Formatter formatter = new Formatter(options);
        OutputBuilder example = createExample0();

        assertEquals("""
                public class Basics_1 {
                    @Linked(to = { "p0" }) @NotModified @Nullable public final Set<String> f1;
                    
                    @Dependent
                    public Basics_1(@NotModified @Nullable Set<String> p0, @NotModified @Nullable Set<String> p1, @Nullable String p2) {
                        Set<String> s1 = p0;
                        this.f1 = s1;
                    }
                    
                    @Independent @NotModified @Nullable public Set<String> getF1() { return f1; }
                }
                """, formatter.write(example));
    }

    private OutputBuilder createExample1() {
        Guide.GuideGenerator type = Guide.generatorForAnnotationList();
        Guide.GuideGenerator field = Guide.generatorForAnnotationList();
        Guide.GuideGenerator paramsConstructor = Guide.generatorForAnnotationList();
        Guide.GuideGenerator constructor = Guide.generatorForAnnotationList();
        Guide.GuideGenerator method = Guide.generatorForAnnotationList();

        Guide.GuideGenerator blockType = Guide.generatorForBlock();
        Guide.GuideGenerator blockConstructor = Guide.generatorForBlock();
        Guide.GuideGenerator blockMethod = Guide.generatorForBlock();

        Guide.GuideGenerator modifiersField = Guide.defaultGuideGenerator();

        return new OutputBuilder()
                .add(new Text("package"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.analyser.parser.failing.testexample"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("java.util.Set"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(type.start())
                .add(Symbol.AT)
                .add(new TypeName("Dependent"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(type.mid())
                .add(Symbol.AT)
                .add(new TypeName("E1Container"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(type.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("class"))
                .add(Space.ONE)
                .add(new Text("Basics_1"))
                .add(Symbol.LEFT_BRACE)
                .add(blockType.start())
                .add(field.start())
                .add(Symbol.AT)
                .add(new TypeName("Linked"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("to"))
                .add(Symbol.binaryOperator("="))
                .add(Symbol.LEFT_BRACE)
                .add(new Text("\"p0\""))
                .add(Symbol.RIGHT_BRACE)
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(modifiersField.start())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(modifiersField.mid())
                .add(new Text("final"))
                .add(modifiersField.end())
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("f1"))
                .add(Symbol.SEMICOLON)
                .add(field.end())
                .add(blockType.mid())
                .add(constructor.start())
                .add(Symbol.AT)
                .add(new TypeName("Dependent"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(constructor.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("Basics_1"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(paramsConstructor.start())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p0"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p1"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE)
                .add(new Text("String"))
                .add(Space.ONE)
                .add(new Text("p2"))
                .add(paramsConstructor.end())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(blockConstructor.start())
                .add(Space.NONE)
                .add(Space.NONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("s1"))
                .add(Symbol.binaryOperator("="))
                .add(new QualifiedName("p0"))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.mid())
                .add(new QualifiedName("this"))
                .add(Symbol.DOT)
                .add(new QualifiedName("f1"))
                .add(Symbol.binaryOperator("="))
                .add(new QualifiedName("s1"))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.end())
                .add(Symbol.RIGHT_BRACE)
                .add(constructor.end())
                .add(blockType.mid())
                .add(method.start())
                .add(Symbol.AT)
                .add(new TypeName("Independent"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("getF1"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(blockMethod.start())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new QualifiedName("f1"))
                .add(Symbol.SEMICOLON)
                .add(blockMethod.end())
                .add(Symbol.RIGHT_BRACE)
                .add(method.end())
                .add(blockType.end())
                .add(Symbol.RIGHT_BRACE)
                .add(type.end());
    }

    @Test
    public void testExample1() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        assertEquals("""
                        package org.e2immu.analyser.parser.failing.testexample;
                        import java.util.Set;
                        @Dependent
                        @E1Container
                        public class Basics_1 {
                            @Linked(to = { "p0" }) @NotModified @Nullable public final Set<String> f1;
                            
                            @Dependent
                            public Basics_1(@NotModified @Nullable Set<String> p0, @NotModified @Nullable Set<String> p1, @Nullable String p2) {
                                Set<String> s1 = p0;
                                this.f1 = s1;
                            }
                            
                            @Independent @NotModified @Nullable public Set<String> getF1() { return f1; }
                        }
                        """,
                new Formatter(options).write(createExample1()));
    }

    private OutputBuilder createExample2() {
        Guide.GuideGenerator gg59 = Guide.generatorForBlock();
        Guide.GuideGenerator gg62 = Guide.generatorForBlock();

        return new OutputBuilder()
                .add(new Text("static"))
                .add(Space.ONE)
                .add(new Text("int"))
                .add(Space.ONE)
                .add(new Text("test"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg59.start())
                .add(Space.NONE)
                .add(Space.NONE)
                .add(new Text("List<String>"))
                .add(Space.ONE)
                .add(new Text("list"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("new"))
                .add(Space.ONE)
                .add(new Text("ArrayList"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.SEMICOLON)
                .add(gg59.mid())
                .add(new Text("if"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new QualifiedName("list"))
                .add(Symbol.DOT)
                .add(new Text("size"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.binaryOperator(">"))
                .add(new Text("0"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("Condition in 'if' or 'switch' statement evaluates to constant"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("Unreachable statement"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Symbol.LEFT_BRACE)
                .add(gg62.start())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new Text("3"))
                .add(Symbol.SEMICOLON)
                .add(gg62.end())
                .add(Symbol.RIGHT_BRACE)
                .add(gg59.mid())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new QualifiedName("list"))
                .add(Symbol.DOT)
                .add(new Text("size"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.binaryOperator("+"))
                .add(new Text("4"))
                .add(Symbol.SEMICOLON)
                .add(gg59.end())
                .add(Symbol.RIGHT_BRACE);
    }

    @Test
    public void testExample2() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        assertEquals("""
                        static int test() {
                            List<String> list = new ArrayList();
                            
                            if(list.size() > 0) /*Condition in 'if' or 'switch' statement evaluates to constant*/ /*Unreachable statement*/ {
                                return 3;
                            }
                            
                            return list.size() + 4;
                        }
                        """,
                new Formatter(options).write(createExample2()));
    }

    // slightly larger version of 2
    private OutputBuilder createExample3() {
        Guide.GuideGenerator gg57 = Guide.generatorForBlock();
        Guide.GuideGenerator gg59 = Guide.generatorForBlock();
        Guide.GuideGenerator gg62 = Guide.generatorForBlock();
        Guide.GuideGenerator gg64 = Guide.generatorForAnnotationList();
        Guide.GuideGenerator gg65 = Guide.generatorForAnnotationList();

        return new OutputBuilder().add(new Text("package"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.analyser.parser.failing.testexample"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("java.util.ArrayList"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("java.util.List"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.annotation.Constant"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(gg65.start())
                .add(Symbol.AT)
                .add(new TypeName("E2Container"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg65.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("class"))
                .add(Space.ONE)
                .add(new Text("BasicCompanionMethods_0"))
                .add(Symbol.LEFT_BRACE)
                .add(gg57.start())
                .add(gg64.start())
                .add(Symbol.AT)
                .add(new TypeName("Constant"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new Text("\"4\""))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg64.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg64.mid())
                .add(new Text("static"))
                .add(Space.ONE)
                .add(new Text("int"))
                .add(Space.ONE)
                .add(new Text("test"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg59.start())
                .add(Space.NONE)
                .add(Space.NONE)
                .add(new Text("List<String>"))
                .add(Space.ONE)
                .add(new Text("list"))
                .add(Symbol.binaryOperator("="))
                .add(new Text("new"))
                .add(Space.ONE)
                .add(new Text("ArrayList"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.SEMICOLON)
                .add(gg59.mid())
                .add(new Text("if"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new QualifiedName("list"))
                .add(Symbol.DOT)
                .add(new Text("size"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.binaryOperator(">"))
                .add(new Text("0"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("Condition in 'if' or 'switch' statement evaluates to constant"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Symbol.LEFT_BLOCK_COMMENT)
                .add(new Text("Unreachable statement"))
                .add(Symbol.RIGHT_BLOCK_COMMENT)
                .add(Symbol.LEFT_BRACE)
                .add(gg62.start())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new Text("3"))
                .add(Symbol.SEMICOLON)
                .add(gg62.end())
                .add(Symbol.RIGHT_BRACE)
                .add(gg59.mid())
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new QualifiedName("list"))
                .add(Symbol.DOT)
                .add(new Text("size"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(Symbol.binaryOperator("+"))
                .add(new Text("4"))
                .add(Symbol.SEMICOLON)
                .add(gg59.end())
                .add(Symbol.RIGHT_BRACE)
                .add(gg64.end())
                .add(gg57.end())
                .add(Symbol.RIGHT_BRACE)
                .add(gg65.end());
    }


    @Test
    public void testExample3() {
        assertEquals("""
                        package org.e2immu.analyser.parser.failing.testexample;
                        import java.util.ArrayList;
                        import java.util.List;
                        import org.e2immu.annotation.Constant;
                        @E2Container
                        public class BasicCompanionMethods_0 {
                            @Constant("4")
                            @NotModified
                            static int test() {
                                List<String> list = new ArrayList();
                                
                                if(list.size() > 0) /*Condition in 'if' or 'switch' statement evaluates to constant*/ /*Unreachable statement*/ {
                                    return 3;
                                }
                                
                                return list.size() + 4;
                            }
                        }
                        """,
                new Formatter(FormattingOptions.DEFAULT).write(createExample3()));
    }
}

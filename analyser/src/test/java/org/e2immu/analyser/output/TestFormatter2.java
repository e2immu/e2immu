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

        Guide.GuideGenerator modifiersField = new Guide.GuideGenerator();

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
                .add(new TypeName("Linked", "", ""))
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
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
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
                .add(new TypeName("Dependent", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(constructor.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("Basics_1"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(paramsConstructor.start())
                .add(Symbol.AT)
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p0"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p1"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
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
                .add(new VariableName("p0", null, VariableName.Nature.LOCAL))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.mid())
                .add(new VariableName("this", null, VariableName.Nature.STATIC))
                .add(Symbol.DOT)
                .add(new VariableName("f1", null, VariableName.Nature.INSTANCE))
                .add(Symbol.binaryOperator("="))
                .add(new VariableName("s1", null, VariableName.Nature.LOCAL))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.end())
                .add(Symbol.RIGHT_BRACE)
                .add(constructor.end())
                .add(blockType.mid())
                .add(method.start())
                .add(Symbol.AT)
                .add(new TypeName("Independent", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
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
                .add(new VariableName("f1", null, VariableName.Nature.INSTANCE))
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
        int len = example.list.size();
        Assert.assertEquals(23, formatter.lookAhead(example.list.subList(1, len), options.lengthOfLine()));

        Assert.assertEquals("""
                public class Basics_1 {
                    @Linked(to = { "p0" }) @NotModified @Nullable public final Set<String> f1;
                    @Dependent
                    public Basics_1(
                        @NotModified @Nullable Set<String> p0,
                        @NotModified @Nullable Set<String> p1,
                        @Nullable String p2) { 
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

        Guide.GuideGenerator modifiersField = new Guide.GuideGenerator();

        return new OutputBuilder()
                .add(new Text("package"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.analyser.testexample"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("java.util.Set"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(type.start())
                .add(Symbol.AT)
                .add(new TypeName("Dependent", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(type.mid())
                .add(Symbol.AT)
                .add(new TypeName("E1Container", "", ""))
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
                .add(new TypeName("Linked", "", ""))
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
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(field.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
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
                .add(new TypeName("Dependent", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(constructor.mid())
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("Basics_1"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(paramsConstructor.start())
                .add(Symbol.AT)
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p0"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE)
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
                .add(Space.ONE)
                .add(new Text("Set<String>"))
                .add(Space.ONE)
                .add(new Text("p1"))
                .add(Symbol.COMMA)
                .add(paramsConstructor.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
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
                .add(new VariableName("p0", null, VariableName.Nature.LOCAL))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.mid())
                .add(new VariableName("this", null, VariableName.Nature.STATIC))
                .add(Symbol.DOT)
                .add(new VariableName("f1", null, VariableName.Nature.INSTANCE))
                .add(Symbol.binaryOperator("="))
                .add(new VariableName("s1", null, VariableName.Nature.LOCAL))
                .add(Symbol.SEMICOLON)
                .add(blockConstructor.end())
                .add(Symbol.RIGHT_BRACE)
                .add(constructor.end())
                .add(blockType.mid())
                .add(method.start())
                .add(Symbol.AT)
                .add(new TypeName("Independent", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("NotModified", "", ""))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(method.mid())
                .add(Symbol.AT)
                .add(new TypeName("Nullable", "", ""))
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
                .add(new VariableName("f1", null, VariableName.Nature.INSTANCE))
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
        Assert.assertEquals("""
                        package org.e2immu.analyser.testexample;
                        import java.util.Set;
                        @Dependent
                        @E1Container
                        public class Basics_1 {
                            @Linked(to = { "p0" }) @NotModified @Nullable public final Set<String> f1;
                            @Dependent
                            public Basics_1(
                                @NotModified @Nullable Set<String> p0,
                                @NotModified @Nullable Set<String> p1,
                                @Nullable String p2) { 
                                Set<String> s1 = p0; 
                                this.f1 = s1; 
                            }
                            @Independent @NotModified @Nullable public Set<String> getF1() { return f1; } 
                        }
                        """,
                new Formatter(options).write(createExample1()));
    }
}

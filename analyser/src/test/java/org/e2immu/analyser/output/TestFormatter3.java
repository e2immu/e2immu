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

public class TestFormatter3 {

    private static OutputBuilder createExample0() {
        Guide.GuideGenerator gg31 = Guide.generatorForAnnotationList();
        Guide.GuideGenerator gg21 = Guide.generatorForBlock();
        Guide.GuideGenerator gg30 = Guide.generatorForAnnotationList();

        Guide.GuideGenerator gg22 = Guide.defaultGuideGenerator();
        Guide.GuideGenerator gg23 = Guide.generatorForParameterDeclaration();
        Guide.GuideGenerator gg24 = Guide.generatorForBlock();

        Guide.GuideGenerator gg25 = Guide.defaultGuideGenerator(); // fluent method call sequence
        Guide.GuideGenerator gg28 = Guide.generatorForBlock(); // opening of lambda
        Guide.GuideGenerator gg29 = Guide.generatorForBlock();

        return new OutputBuilder().add(new Text("package"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.analyser.testexample"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("java.util.stream.Stream"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.annotation.NotModified"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(new Text("import"))
                .add(Space.ONE)
                .add(new Text("org.e2immu.annotation.NotNull"))
                .add(Symbol.SEMICOLON)
                .add(Space.NEWLINE)
                .add(gg31.start()) // priority=false, startNL=false, endNL=false
                .add(Symbol.AT)
                .add(new TypeName("E2Container"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg31.mid()) // priority=false, startNL=false, endNL=false
                .add(Symbol.AT)
                .add(new TypeName("ExtensionClass"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg31.mid()) // priority=false, startNL=false, endNL=false
                .add(new Text("public"))
                .add(Space.ONE)
                .add(new Text("class"))
                .add(Space.ONE)
                .add(new Text("Basics_5"))
                .add(Symbol.LEFT_BRACE)
                .add(gg21.start()) // priority=true, startNL=true, endNL=true
                .add(gg30.start()) // priority=false, startNL=false, endNL=false
                .add(Symbol.AT)
                .add(new TypeName("NotModified"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg30.mid()) // priority=false, startNL=false, endNL=false
                .add(Symbol.AT)
                .add(new TypeName("NotNull"))
                .add(Space.ONE_REQUIRED_EASY_SPLIT)
                .add(gg30.mid()) // priority=false, startNL=false, endNL=false
                .add(gg22.start()) // priority=false, startNL=false, endNL=false
                .add(new Text("public"))
                .add(Space.ONE)
                .add(gg22.mid()) // priority=false, startNL=false, endNL=false
                .add(new Text("static"))
                .add(gg22.end()) // priority=false, startNL=false, endNL=false
                .add(Space.ONE)
                .add(new Text("String"))
                .add(Space.ONE)
                .add(new Text("add"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(gg23.start()) // priority=false, startNL=true, endNL=false
                .add(Symbol.AT)
                .add(new TypeName("NotNull"))
                .add(Space.ONE)
                .add(new Text("String"))
                .add(Space.ONE)
                .add(new Text("input"))
                .add(gg23.end()) // priority=false, startNL=true, endNL=false
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg24.start()) // priority=true, startNL=true, endNL=true
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new Text("Stream"))
                .add(gg25.start()) // priority=false, startNL=false, endNL=false
                .add(Symbol.DOT)
                .add(new Text("of"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new QualifiedName("input"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(gg25.mid()) // priority=false, startNL=false, endNL=false
                .add(Symbol.DOT)
                .add(new Text("map"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new QualifiedName("s"))
                .add(Symbol.binaryOperator("->"))
                .add(Symbol.LEFT_BRACE)
                .add(gg28.start()) // priority=false, startNL=false, endNL=false
                .add(new Text("if"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(new QualifiedName("s"))
                .add(Symbol.binaryOperator("=="))
                .add(new Text("null"))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(gg29.start()) // priority=true, startNL=true, endNL=true
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new Text("\"null\""))
                .add(Symbol.SEMICOLON)
                .add(gg29.end()) // priority=true, startNL=true, endNL=true
                .add(Symbol.RIGHT_BRACE)
                .add(gg28.mid()) // priority=false, startNL=false, endNL=false
                .add(new Text("return"))
                .add(Space.ONE)
                .add(new QualifiedName("s"))
                .add(Symbol.binaryOperator("+"))
                .add(new Text("\"something\""))
                .add(Symbol.SEMICOLON)
                .add(gg28.end())
                .add(Symbol.RIGHT_BRACE)
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(gg25.mid()) // priority=false, startNL=false, endNL=false
                .add(Symbol.DOT)
                .add(new Text("findAny"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(gg25.mid()) // priority=false, startNL=false, endNL=false
                .add(Symbol.DOT)
                .add(new Text("get"))
                .add(Symbol.OPEN_CLOSE_PARENTHESIS)
                .add(gg25.end()) // priority=false, startNL=false, endNL=false
                .add(Symbol.SEMICOLON)
                .add(gg24.end()) // priority=true, startNL=true, endNL=true
                .add(Symbol.RIGHT_BRACE)
                .add(gg30.end()) // priority=false, startNL=false, endNL=false
                .add(gg21.end()) // priority=true, startNL=true, endNL=true
                .add(Symbol.RIGHT_BRACE)
                .add(gg31.end()); // priority=false, startNL=false, endNL=false
    }


    @Test
    public void testExample0() {
        FormattingOptions options = FormattingOptions.DEFAULT;
        Formatter formatter = new Formatter(options);
        OutputBuilder example = createExample0();

        assertEquals("""
                  package org.e2immu.analyser.testexample;
                  import java.util.stream.Stream;
                  import org.e2immu.annotation.NotModified;
                  import org.e2immu.annotation.NotNull;
                  @E2Container
                  @ExtensionClass
                  public class Basics_5 {
                      @NotModified
                      @NotNull
                      public static String add(@NotNull String input) {
                          return Stream.of(input).map(s -> { if(s == null) { return "null"; } return s + "something"; }).findAny().get();
                      }
                  }
                  """, formatter.write(example));
    }
}

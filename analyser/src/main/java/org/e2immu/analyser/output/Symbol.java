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

import static org.e2immu.analyser.output.Space.*;

public record Symbol(String symbol, Space left, Space right) implements OutputElement {

    public static final Symbol INSTANCE_OF = new Symbol("instanceof", ONE, ONE);
    public static final Symbol UNARY_BOOLEAN_NOT = new Symbol("!", NOT_FOR_LEFT_PARENTHESIS, NONE);
    public static final Symbol UNARY_MINUS = new Symbol("-", NOT_FOR_LEFT_PARENTHESIS, NONE);
    public static final Symbol AT = new Symbol("@", NOT_FOR_LEFT_PARENTHESIS, NONE);

    public static final Symbol PIPE = new Symbol("|", EASY_LR, EASY_LR);

    public static final Symbol COMMA = new Symbol(",", NONE, EASY);
    public static final Symbol SEMICOLON = new Symbol(";", NONE, EASY);

    // a ? b : c;
    public static final Symbol QUESTION_MARK = new Symbol("?", EASY_LR, EASY_LR);
    public static final Symbol COLON = new Symbol(":", EASY_LR, EASY_LR);
    public static final Symbol COLON_LABEL = new Symbol(":", NONE, EASY);
    public static final Symbol DOUBLE_COLON = new Symbol(":", NONE, NONE);

    public static final Symbol DOT = new Symbol(".", EASY, NONE);

    public static final Symbol LEFT_PARENTHESIS = new Symbol("(", NONE, EASY);
    public static final Symbol RIGHT_PARENTHESIS = new Symbol(")", NONE, EASY);
    public static final Symbol OPEN_CLOSE_PARENTHESIS = new Symbol("()", NONE, EASY);

    public static final Symbol LEFT_BRACE = new Symbol("{", NONE, EASY);
    public static final Symbol RIGHT_BRACE = new Symbol("}", EASY, EASY);

    public static final Symbol LEFT_BRACKET = new Symbol("[", NONE, EASY);
    public static final Symbol RIGHT_BRACKET = new Symbol("]", NONE, EASY);
    public static final Symbol OPEN_CLOSE_BRACKET = new Symbol("[]", NONE, EASY);

    public static final Symbol LEFT_ANGLE_BRACKET = new Symbol("<", NONE, EASY);
    public static final Symbol RIGHT_ANGLE_BRACKET = new Symbol(">", EASY, EASY);
    public static final Symbol DIAMOND = new Symbol("<>", NONE, EASY);


    public static final Symbol LOGICAL_AND = new Symbol("&&", EASY_LR, EASY_LR);
    public static final Symbol LOGICAL_OR = new Symbol("||", EASY_LR, EASY_LR);
    public static final Symbol LAMBDA = new Symbol("->", EASY_LR, EASY_LR);

    public static final Symbol LEFT_BLOCK_COMMENT = new Symbol("/*", NONE, NONE);
    public static final Symbol RIGHT_BLOCK_COMMENT = new Symbol("*/", NONE, NONE);

    public static Symbol plusPlusPrefix(String s) {
        return new Symbol(s, EASY, NONE);
    }

    public static Symbol plusPlusSuffix(String s) {
        return new Symbol(s, NONE, EASY);
    }

    public static Symbol assignment(String s) {
        return new Symbol(s, EASY_LR, EASY_LR);
    }

    public static Symbol binaryOperator(String s) {
        return new Symbol(s, EASY_LR, EASY_LR);
    }

    @Override
    public String minimal() {
        return left.minimal() + symbol + right.minimal();
    }

    @Override
    public String debug() {
        return left.debug() + symbol + right.debug();
    }
}

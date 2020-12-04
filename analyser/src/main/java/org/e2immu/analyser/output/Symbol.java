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

    public static final Symbol INSTANCE_OF = new Symbol("instanceof", MUST_HAVE_ONE, MUST_HAVE_ONE);
    public static final Symbol UNARY_BOOLEAN_NOT = new Symbol("!", NOT_FOR_LEFT_PARENTHESIS, NEVER);
    public static final Symbol UNARY_MINUS = new Symbol("-", NOT_FOR_LEFT_PARENTHESIS, NEVER);
    public static final Symbol AT = new Symbol("@", NOT_FOR_LEFT_PARENTHESIS, NEVER);

    public static final Symbol COMMA = new Symbol(",", NEVER, EASY);
    public static final Symbol SEMICOLON = new Symbol(";", NEVER, EASY);

    // a ? b : c;
    public static final Symbol QUESTION_MARK = new Symbol("?", ONE_LR, ONE_LR);
    public static final Symbol COLON = new Symbol(":", ONE_LR, ONE_LR);

    public static final Symbol DOT = new Symbol(".", EASY, NEVER);

    public static final Symbol LEFT_PARENTHESIS = new Symbol("(", NEVER, EASY);
    public static final Symbol RIGHT_PARENTHESIS = new Symbol(")", NEVER, EASY);
    public static final Symbol OPEN_CLOSE_PARENTHESIS = new Symbol("()", NEVER, EASY);

    public static final Symbol LEFT_BRACE = new Symbol("{", NEVER, EASY);
    public static final Symbol RIGHT_BRACE = new Symbol("}", EASY, EASY);

    public static final Symbol LEFT_BRACKET = new Symbol("[", NEVER, EASY);
    public static final Symbol RIGHT_BRACKET = new Symbol("]", NEVER, EASY);
    public static final Symbol OPEN_CLOSE_BRACKET = new Symbol("[]", NEVER, EASY);

    public static final Symbol LOGICAL_AND = new Symbol("&&", ONE_LR, ONE_LR);
    public static final Symbol LOGICAL_OR = new Symbol("||", ONE_LR, ONE_LR);
    public static final Symbol LAMBDA = new Symbol("->", ONE_LR, ONE_LR);

    public static Symbol plusPlusPrefix(String s) {
        return new Symbol(s, EASY, NEVER);
    }

    public static Symbol plusPlusSuffix(String s) {
        return new Symbol(s, NEVER, EASY);
    }

    public static Symbol assignment(String s) {
        return new Symbol(s, ONE_LR, ONE_LR);
    }

    public static Symbol binaryOperator(String s) {
        return new Symbol(s, ONE_LR, ONE_LR);
    }

    @Override
    public String minimal() {
        return symbol;
    }

    @Override
    public String debug() {
        return symbol;
    }
}

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

import org.e2immu.analyser.util.StringUtil;

import static org.e2immu.analyser.output.Space.*;

public record Symbol(String symbol, Space left, Space right, String constant) implements OutputElement {

    public static final Symbol INSTANCE_OF = new Symbol("instanceof", ONE, ONE, "INSTANCE_OF");
    public static final Symbol UNARY_BOOLEAN_NOT = new Symbol("!", RELAXED_NO_SPACE_SPLIT_ALLOWED, NONE, "UNARY_BOOLEAN_NOT");
    public static final Symbol UNARY_MINUS = new Symbol("-", RELAXED_NO_SPACE_SPLIT_ALLOWED, NONE, "UNARY_MINUS");
    public static final Symbol AT = new Symbol("@", ONE_IS_NICE_EASY_SPLIT, NONE, "AT");

    public static final Symbol PIPE = binaryOperator("|");

    public static final Symbol COMMA = new Symbol(",", NONE, ONE_IS_NICE_EASY_SPLIT, "COMMA");
    public static final Symbol SEMICOLON = new Symbol(";", NONE, ONE_IS_NICE_EASY_SPLIT, "SEMICOLON");

    // a ? b : c;
    public static final Symbol QUESTION_MARK = binaryOperator("?");
    public static final Symbol COLON = binaryOperator(":");
    public static final Symbol COLON_LABEL = new Symbol(":", NONE, ONE_IS_NICE_EASY_SPLIT, "COLON_LABEL");
    public static final Symbol DOUBLE_COLON = new Symbol("::", NONE, NONE, "DOUBLE_COLON");

    public static final Symbol DOT = new Symbol(".", NO_SPACE_SPLIT_ALLOWED, NONE, "DOT");

    public static final Symbol LEFT_PARENTHESIS = new Symbol("(", NONE, NO_SPACE_SPLIT_ALLOWED, "LEFT_PARENTHESIS");
    public static final Symbol RIGHT_PARENTHESIS = new Symbol(")", NONE, RELAXED_NO_SPACE_SPLIT_ALLOWED, "RIGHT_PARENTHESIS");
    public static final Symbol OPEN_CLOSE_PARENTHESIS = new Symbol("()", NONE, RELAXED_NO_SPACE_SPLIT_ALLOWED, "OPEN_CLOSE_PARENTHESIS");

    public static final Symbol LEFT_BRACE = new Symbol("{", ONE_IS_NICE_EASY_SPLIT, ONE_IS_NICE_SPLIT_BEGIN_END, "LEFT_BRACE");
    public static final Symbol RIGHT_BRACE = new Symbol("}", ONE_IS_NICE_SPLIT_BEGIN_END, ONE_IS_NICE_EASY_SPLIT, "RIGHT_BRACE");

    public static final Symbol LEFT_BRACKET = new Symbol("[", NONE, NO_SPACE_SPLIT_ALLOWED, "LEFT_BRACKET");
    public static final Symbol RIGHT_BRACKET = new Symbol("]", NONE, RELAXED_NO_SPACE_SPLIT_ALLOWED, "RIGHT_BRACKET");

    public static final Symbol LEFT_ANGLE_BRACKET = new Symbol("<", NONE, NONE, "LEFT_ANGLE_BRACKET");
    public static final Symbol RIGHT_ANGLE_BRACKET = new Symbol(">", NONE, ONE_IS_NICE_EASY_SPLIT, "RIGHT_ANGLE_BRACKET");
    public static final Symbol AND_TYPES = binaryOperator("&");

    public static final Symbol LOGICAL_AND = binaryOperator("&&");
    public static final Symbol LOGICAL_OR = binaryOperator("||");
    public static final Symbol LAMBDA = binaryOperator("->");
    public static final Symbol NOT_EQUALS = binaryOperator("!=");

    public static final Symbol LEFT_BLOCK_COMMENT = new Symbol("/*", ONE_IS_NICE_EASY_SPLIT, NONE, "LEFT_BLOCK_COMMENT");
    public static final Symbol RIGHT_BLOCK_COMMENT = new Symbol("*/", NONE, ONE_IS_NICE_EASY_SPLIT, "RIGHT_BLOCK_COMMENT");

    public static Symbol plusPlusPrefix(String s) {
        return new Symbol(s, ONE_IS_NICE_EASY_SPLIT, NONE, null);
    }

    public static Symbol plusPlusSuffix(String s) {
        return new Symbol(s, NONE, ONE_IS_NICE_EASY_SPLIT, null);
    }

    public static Symbol assignment(String s) {
        return new Symbol(s, ONE_IS_NICE_EASY_L, ONE_IS_NICE_EASY_R, null);
    }

    public static Symbol binaryOperator(String s) {
        return new Symbol(s, ONE_IS_NICE_EASY_L, ONE_IS_NICE_EASY_R, null);
    }

    @Override
    public String minimal() {
        return left.minimal() + symbol + right.minimal();
    }

    @Override
    public String debug() {
        return left.debug() + symbol + right.debug();
    }

    @Override
    public int length(FormattingOptions options) {
        return left.length(options) + symbol.length() + right().length(options);
    }

    @Override
    public String write(FormattingOptions options) {
        return left.write(options) + symbol + right.write(options);
    }

    @Override
    public String generateJavaForDebugging() {
        return ".add(Symbol" + (constant != null ? "." + constant : ".binaryOperator(" + StringUtil.quote(symbol) + ")") + ")";
    }
}

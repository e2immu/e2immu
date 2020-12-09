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

import com.google.common.math.DoubleMath;
import org.e2immu.analyser.model.expression.FloatConstant;
import org.e2immu.analyser.model.expression.Numeric;
import org.e2immu.analyser.util.StringUtil;

public record Text(String text, String debug) implements OutputElement {

    public Text(String text) {
        this(text, text);
    }


    public static String formatNumber(double d, Class<? extends Numeric> clazz) {
        if (DoubleMath.isMathematicalInteger(d)) {
            return Long.toString((long) d);
        }
        if (clazz.equals(FloatConstant.class)) {
            return d + "f";
        }
        return Double.toString(d);
    }

    @Override
    public String minimal() {
        return text;
    }

    @Override
    public String debug() {
        return debug;
    }

    @Override
    public int length(FormattingOptions options) {
        return options.debug() ? debug.length() : text.length();
    }

    @Override
    public String write(FormattingOptions options) {
        return options.debug() ? debug : text;
    }

    @Override
    public String generateJavaForDebugging() {
        return ".add(new Text(" + StringUtil.quote(text) + "))";
    }
}

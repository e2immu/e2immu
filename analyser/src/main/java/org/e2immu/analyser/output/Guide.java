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

import java.util.concurrent.atomic.AtomicInteger;

public record Guide(int index, Position position) implements OutputElement {
    private static final AtomicInteger generator = new AtomicInteger();

    public enum Position {
        START("S"), MID(""), END("E");
        private final String msg;

        Position(String msg) {
            this.msg = msg;
        }
    }

    public static class GuideGenerator {
        public final int index;

        public GuideGenerator() {
            index = generator.incrementAndGet();
        }

        public Guide start() {
            return new Guide(index, Position.START);
        }

        public Guide mid() {
            return new Guide(index, Position.MID);
        }

        public Guide end() {
            return new Guide(index, Position.END);
        }
    }


    @Override
    public String minimal() {
        return "";
    }

    @Override
    public String debug() {
        return "";
    }

    @Override
    public String trace() {
        return "/*" + position.msg + index + "*/";
    }

    @Override
    public int length(FormattingOptions options) {
        return 0;
    }

    @Override
    public String write(FormattingOptions options) {
        return "";
    }
}

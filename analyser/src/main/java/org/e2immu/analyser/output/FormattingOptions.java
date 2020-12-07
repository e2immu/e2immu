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

public record FormattingOptions(int lengthOfLine,
                                int spacesInTab,
                                int tabsForLineSplit,
                                boolean binaryOperatorsAtEndOfLine,
                                boolean debug,
                                boolean compact) {

    public static final FormattingOptions DEFAULT = new FormattingOptions(120,
            4, 2, true, false, false);

    public static class Builder {

        int lengthOfLine;
        int spacesInTab;
        int tabsForLineSplit;
        boolean binaryOperatorsAtEndOfLine;
        boolean debug;
        boolean compact;

        public Builder() {
            this(DEFAULT);
        }

        public Builder(FormattingOptions options) {
            this.lengthOfLine = options.lengthOfLine;
            this.spacesInTab = options.spacesInTab;
            this.tabsForLineSplit = options.tabsForLineSplit;
            this.binaryOperatorsAtEndOfLine = options.binaryOperatorsAtEndOfLine;
            this.debug = options.debug;
        }

        public Builder setLengthOfLine(int lengthOfLine) {
            this.lengthOfLine = lengthOfLine;
            return this;
        }

        public Builder setSpacesInTab(int spacesInTab) {
            this.spacesInTab = spacesInTab;
            return this;
        }

        public Builder setTabsForLineSplit(int tabsForLineSplit) {
            this.tabsForLineSplit = tabsForLineSplit;
            return this;
        }

        public Builder setBinaryOperatorsAtEndOfLine(boolean binaryOperatorsAtEndOfLine) {
            this.binaryOperatorsAtEndOfLine = binaryOperatorsAtEndOfLine;
            return this;
        }

        public Builder setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder setCompact(boolean compact) {
            this.compact = compact;
            return this;
        }

        public FormattingOptions build() {
            return new FormattingOptions(lengthOfLine, spacesInTab, tabsForLineSplit, binaryOperatorsAtEndOfLine, debug, compact);
        }
    }
}

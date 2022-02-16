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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.NotModified;

@ERContainer
public record FormattingOptions(int lengthOfLine,
                                int spacesInTab,
                                int tabsForLineSplit,
                                boolean binaryOperatorsAtEndOfLine,
                                boolean debug,
                                boolean compact,
                                boolean allFieldsRequireThis,
                                boolean allStaticFieldsRequireType) {

    public static final FormattingOptions DEFAULT = new Builder().build();

    @Container
    public static class Builder {

        private int lengthOfLine;
        private int spacesInTab;
        private int tabsForLineSplit;
        private boolean binaryOperatorsAtEndOfLine;
        private boolean debug;
        private boolean compact;
        private boolean allFieldsRequireThis;
        private boolean allStaticFieldsRequireType;

        public Builder() {
            this.lengthOfLine = 120;
            this.spacesInTab = 4;
            this.tabsForLineSplit = 2;
            this.binaryOperatorsAtEndOfLine = true;
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
            if (compact) {
                this.tabsForLineSplit = 0;
                this.spacesInTab = 0;
            }
            return this;
        }

        public Builder setAllFieldsRequireThis(boolean allFieldsRequireThis) {
            this.allFieldsRequireThis = allFieldsRequireThis;
            return this;
        }

        public Builder setAllStaticFieldsRequireType(boolean allStaticFieldsRequireType) {
            this.allStaticFieldsRequireType = allStaticFieldsRequireType;
            return this;
        }

        @NotModified
        public FormattingOptions build() {
            return new FormattingOptions(lengthOfLine, spacesInTab, tabsForLineSplit, binaryOperatorsAtEndOfLine, debug, compact,
                    allFieldsRequireThis, allStaticFieldsRequireType);
        }
    }
}

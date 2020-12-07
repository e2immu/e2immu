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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public record Formatter(FormattingOptions options) {

    public String write(OutputBuilder outputBuilder) {
        try (StringWriter stringWriter = new StringWriter()) {
            write(outputBuilder, stringWriter);
            return stringWriter.toString();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static final int NO_GUIDE = -1;
    private static final int LINE_SPLIT = -2;

    // -1 is used for no tabs at all
    // guide == -2 is for line split without guides
    private record Tab(int tabs, int guideId) {
    }

    public void write(OutputBuilder outputBuilder, Writer writer) throws IOException {
        List<OutputElement> list = new ArrayList<>(outputBuilder.list);
        int pos = 0;
        int end = list.size();
        Stack<Tab> tabs = new Stack<>();
        while (pos < end) {
            int spaces = (tabs.isEmpty() ? 0 : tabs.peek().tabs) * options().spacesInTab();
            int guide = (tabs.isEmpty() ? NO_GUIDE : tabs.peek().guideId);
            boolean lineSplit = guide == LINE_SPLIT;

            indent(spaces, writer);
            int lineLength = options.lengthOfLine() - spaces;

            // if lookahead <= line length, either everything fits (write until lookahead reached)
            // or there is a guide starting at lookahead
            // if lookahead > line length, write until best break. If there is no line split, start one
            int lookAhead = lookAhead(list.subList(pos, end), lineLength);

            if (lookAhead <= lineLength) {
                pos = writeUntilNewLineOrEnd(list, writer, pos, end, spaces, lookAhead);
                if (lineSplit) tabs.pop();
            } else {
                pos = writeUntilBestBreak(list, writer, pos, end, spaces, lineLength);
                if (!lineSplit) {
                    int prevTabs = tabs.isEmpty() ? 0 : tabs.peek().tabs;
                    tabs.add(new Tab(prevTabs + options.tabsForLineSplit(), LINE_SPLIT));
                }
            }
            writer.write("\n");
        }
    }

    private int writeUntilBestBreak(List<OutputElement> list, Writer writer, int start, int end,
                                    int cStart, int cEnd) throws IOException {
        OutputElement outputElement;
        int pos = start;
        int cPos = cStart;
        boolean lastOneWasSpace = true; // used to avoid writing double spaces
        boolean wroteOnce = false; // don't write a space at the beginning of the line
        boolean allowBreak = false;
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE)) {
            String string = outputElement.write(options);
            cPos += string.length();
            if (cPos > cEnd && allowBreak && wroteOnce) {// don't write anymore...
                return pos;
            }

            // check for double spaces
            if (outputElement instanceof Space) {
                lastOneWasSpace |= !string.isEmpty();
                allowBreak |= outputElement != Space.NONE;
            } else if (string.length() > 0) {
                if (lastOneWasSpace && wroteOnce) {
                    writer.write(" ");
                }
                lastOneWasSpace = false;
                writer.write(string);
                wroteOnce = true;
            }
            ++pos;
        }
        return pos;
    }


    private int writeUntilNewLineOrEnd(List<OutputElement> list, Writer writer,
                                       int start, int end,
                                       int cStart, int cEnd) throws IOException {
        OutputElement outputElement;
        int pos = start;
        int cPos = cStart;
        boolean lastOneWasSpace = true; // used to avoid writing double spaces
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE) && cPos <= cEnd) {
            boolean write = true;
            String string = outputElement.write(options);
            cPos += string.length();

            // check for double spaces
            if (outputElement instanceof Space) {
                if (lastOneWasSpace) write = false;
                lastOneWasSpace = !string.isEmpty();
            } else if (string.length() > 0) {
                lastOneWasSpace = false;
            }
            if (write) {
                writer.write(string);
            }
            ++pos;
        }
        return pos;
    }

    private void indent(int spaces, Writer writer) throws IOException {
        for (int i = 0; i < spaces; i++) writer.write(" ");
    }

    /*
    returns the number of characters that minimally need to be output until we reach a newline or,
    once beyond the line length (max len minus tabs), the beginning of a Guide
     */
    int lookAhead(List<OutputElement> list, int lineLength) {
        int sum = 0;
        Stack<Integer> startOfGuides = new Stack<>();
        for (OutputElement outputElement : list) {
            if (outputElement == Space.NEWLINE) return sum;
            if (outputElement instanceof Guide guide) {
                if (guide.position() == Guide.Position.START) {
                    startOfGuides.push(sum);
                } else if (guide.position() == Guide.Position.END) {
                    startOfGuides.pop();
                }
            }
            sum += outputElement.length(options);
            if (sum > lineLength) {
                // exceeding the allowed length of the line... go to first open guide if present
                if (startOfGuides.isEmpty()) {
                    return sum;
                }
                return startOfGuides.get(0);
            }
        }
        return sum;
    }
}

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
import java.util.function.Function;

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

    // guides typically organised as  ( S int i, M int j, M int k E )

    public void write(OutputBuilder outputBuilder, Writer writer) throws IOException {
        List<OutputElement> list = new ArrayList<>(outputBuilder.list);
        int pos = 0;
        int end = list.size();
        Stack<Tab> tabs = new Stack<>();
        while (pos < end) {
            int spaces = (tabs.isEmpty() ? 0 : tabs.peek().tabs) * options().spacesInTab();
            int currentGuide = (tabs.isEmpty() ? NO_GUIDE : tabs.peek().guideId);
            int prevTabs = tabs.isEmpty() ? 0 : tabs.peek().tabs;
            boolean lineSplit = currentGuide == LINE_SPLIT;

            indent(spaces, writer);
            int lineLength = options.lengthOfLine() - spaces;

            // if lookahead <= line length, either everything fits (write until lookahead reached)
            // or there is a guide starting at lookahead
            // if lookahead > line length, write until best break. If there is no line split, start one
            int lookAhead = lookAhead(list.subList(pos, end), lineLength);

            if (lookAhead <= lineLength) {
                pos = writeUntilNewLineOrEnd(list, writer, pos, end, lookAhead);
                if (lineSplit) tabs.pop();
            } else {
                pos = writeUntilBestBreak(list, writer, pos, end, lineLength);
                if (!lineSplit) {
                    tabs.add(new Tab(prevTabs + options.tabsForLineSplit(), LINE_SPLIT));
                }
            }
            boolean newLine = true;
            if (pos < end) {
                OutputElement outputElement = list.get(pos);
                if (outputElement instanceof Guide guide) {
                    if (guide.position() == Guide.Position.START) {
                        tabs.add(new Tab(prevTabs + 1, guide.index()));
                    } else if (guide.position() == Guide.Position.MID) {
                        assert currentGuide == guide.index();
                    } else {
                        assert currentGuide == guide.index();
                        tabs.pop();
                        newLine = false;
                    }
                    pos++;
                }
            }
            if (newLine) writer.write("\n");
        }
    }

    private int writeUntilBestBreak(List<OutputElement> list, Writer writer, int start, int end, int cEnd) throws IOException {
        OutputElement outputElement;
        int pos = start;
        int cPos = 0;
        boolean lastOneWasSpace = true; // used to avoid writing double spaces
        boolean wroteOnce = false; // don't write a space at the beginning of the line
        boolean allowBreak = false; // write until the first allowed space
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
                                       int start, int end, int cEnd) throws IOException {
        OutputElement outputElement;
        int pos = start;
        int cPos = 0;
        boolean lastOneWasSpace = true; // used to avoid writing double spaces
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE) && cPos <= cEnd) {
            if (cPos == cEnd && outputElement instanceof Guide) {
                return pos;
            }

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

    private record PosAndGuide(int pos, int guide) {
    }

    int lookAhead(List<OutputElement> list, int lineLength) {
        int sum = 0;
        Stack<PosAndGuide> startOfGuides = new Stack<>();
        for (OutputElement outputElement : list) {
            if (outputElement == Space.NEWLINE) return sum;
            if (outputElement instanceof Guide guide) {
                switch (guide.position()) {
                    case START -> startOfGuides.push(new PosAndGuide(sum, guide.index()));
                    case MID -> {
                        if (startOfGuides.isEmpty() || startOfGuides.get(0).guide != guide.index()) {
                            return sum;
                        }
                    }
                    case END -> {
                        if (!startOfGuides.isEmpty()) {
                            assert startOfGuides.peek().guide == guide.index();
                            startOfGuides.pop();
                        }
                    }
                }
            }
            sum += outputElement.length(options);
            if (sum > lineLength) {
                // exceeding the allowed length of the line... go to first open guide if present
                if (startOfGuides.isEmpty()) {
                    return sum;
                }
                return startOfGuides.get(0).pos;
            }
        }
        return sum;
    }

    record ForwardInfo(int pos, int chars, String string, Split before) {
    }

    public int forward(List<OutputElement> list, Function<ForwardInfo, Boolean> writer, int start, int maxChars) {
        OutputElement outputElement;
        int pos = start;
        int chars = 0;
        int end = list.size();
        boolean lastOneWasSpace = true; // used to avoid writing double spaces
        Split split = Split.NEVER;
        boolean wroteOnce = false; // don't write a space at the beginning of the line
        boolean allowBreak = false; // write until the first allowed space
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE)) {
            String string;

            Split splitAfterWriting = Split.NEVER;
            boolean spaceAfterWriting = false;
            if (outputElement instanceof Symbol symbol) {
                split = symbol.left().split;
                int leftLength = symbol.left().length(options);
                lastOneWasSpace |= leftLength > 0;
                string = symbol.symbol();
                int rightLength = symbol.right().length(options);
                spaceAfterWriting |= rightLength > 0;
                splitAfterWriting = symbol.right().split;
            } else {
                string = outputElement.write(options);
            }
            // check for double spaces
            if (outputElement instanceof Space space) {
                lastOneWasSpace |= !string.isEmpty();
                split = split.easiest(space.split);
                allowBreak |= outputElement != Space.NONE;
            } else if (string.length() > 0) {
                boolean writeSpace = lastOneWasSpace && wroteOnce;
                int stringLen = string.length();
                int goingToWrite = stringLen + (writeSpace ? 1 : 0);
                if (chars + goingToWrite > maxChars && allowBreak && wroteOnce) {// don't write anymore...
                    return pos;
                }
                if (writeSpace) {
                    if (writer.apply(new ForwardInfo(pos - 1, chars, " ", split))) return pos;
                    chars++;
                }
                if (writer.apply(new ForwardInfo(pos, chars, string, split))) return pos;
                lastOneWasSpace = false;
                split = splitAfterWriting;
                wroteOnce = true;
                chars += stringLen;
            } else {
                // empty string indicates that there is a Guide on this position
                // split means nothing here
                if (writer.apply(new ForwardInfo(pos, chars, "", Split.NEVER))) return pos;
            }
            lastOneWasSpace |= spaceAfterWriting;
            ++pos;
        }
        return pos;
    }

}

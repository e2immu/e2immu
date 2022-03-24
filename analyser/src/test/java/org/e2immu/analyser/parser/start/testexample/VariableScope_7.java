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

package org.e2immu.analyser.parser.start.testexample;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class VariableScope_7 {

    interface Message {
    }

    public static final VariableScope_7 EMPTY = new VariableScope_7(Set.of());

    private final Set<Message> messages;

    public VariableScope_7() {
        this.messages = new HashSet<>();
    }

    private VariableScope_7(Set<Message> set) {
        this.messages = set;
    }

    public void addAll(VariableScope_7 messages) {
        this.messages.addAll(messages.messages);
    }

    public void addAll(Stream<Message> messageStream) {
        messageStream.forEach(messages::add);
    }

    public void add(Message message) {
        this.messages.add(message);
    }

    public Stream<Message> getMessageStream() {
        return messages.stream();
    }

    public int size() {
        return messages.size();
    }

    public VariableScope_7 combine(VariableScope_7 other) {
        if (this.messages.isEmpty()) return other;
        if (other.messages.isEmpty()) return this;
        VariableScope_7 m = new VariableScope_7();
        m.messages.addAll(other.messages);
        return new VariableScope_7(Set.copyOf(messages));
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }
}

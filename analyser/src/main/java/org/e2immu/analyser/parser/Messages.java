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

package org.e2immu.analyser.parser;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Container
public class Messages {

    public static final Messages EMPTY = new Messages(Set.of());

    @NotNull(content = true)
    private final Set<Message> messages;

    public Messages() {
        this.messages = new HashSet<>();
    }

    private Messages(Set<Message> set) {
        this.messages = set;
    }

    @Override
    public String toString() {
        return "messages=" + messages.stream().map(Object::toString).sorted().collect(Collectors.joining(";"));
    }

    @Modified
    public void addAll(@NotNull(content = true) @NotModified Messages messages) {
        this.messages.addAll(messages.messages);
    }

    @Modified
    public void addAll(@NotNull(content = true) @NotModified Stream<Message> messageStream) {
        messageStream.forEach(messages::add);
    }

    @Modified
    public void add(@NotNull Message message) {
        this.messages.add(message);
    }

    @NotNull(content = true)
    public Stream<Message> getMessageStream() {
        return messages.stream();
    }

    public int size() {
        return messages.size();
    }

    public Messages combine(Messages other) {
        if (this.messages.isEmpty()) return other;
        if (other.messages.isEmpty()) return this;
        Messages m = new Messages();
        m.messages.addAll(other.messages);
        return new Messages(Set.copyOf(messages));
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }
}

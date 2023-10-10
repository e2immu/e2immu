package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;

import java.util.ArrayList;
import java.util.List;

public class Container_9 {

    @Container
    interface Item {
        int weight();

        void setMessage(String message);
    }

    static class ItemImpl implements Item {
        private String message;
        private final int weight;

        public ItemImpl(int weight) {
            this.weight = weight;
        }

        @Override
        public int weight() {
            return weight;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    @Container
    interface Items {
        @Modified
        void add(Item item);

        @Modified
        void modifying();

    }

    @Container (absent = true)
    static class ItemsImpl implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item) {
            this.items.add(item);
        }

        @Modified
        @Override
        public void modifying() {
            this.items.get(0).setMessage("Clear!"); //
        }
    }

    // should raise an error: violating @Container in Items
    @Container(absent = true)
    static class ItemsImpl2 implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item) {
            this.items.add(item);
        }

        @Modified
        @Override
        public void modifying() {
            Item item = new ItemImpl(10);
            item.setMessage("msg");
            this.items.add(item);
        }

        public List<Item> getItems() {
            return items;
        }
    }

    @Container
    static class ItemsImpl3 implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item) {
            this.items.add(item);
        }

        @Override
        public void modifying() {
            items.clear(); // no link with an item
        }

        public List<Item> getItems() {
            return items;
        }
    }
}

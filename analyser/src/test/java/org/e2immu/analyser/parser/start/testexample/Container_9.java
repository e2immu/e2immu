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

    @Container(absent = true)
    static class Items1 implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item1) {
            this.items.add(item1);
        }

        // must cause an error!
        @Modified
        @Override
        public void modifying() {
            // see also Modification_30
            this.items.get(0).setMessage("Clear!");
        }
    }

    static class Items2 implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item2) {
            this.items.add(item2);
        }

        // must cause an error: modifying content of items
        @Modified
        @Override
        public void modifying() {
            Item item = this.items.get(0);
            item.setMessage("Clear!"); // see also Modification_30
        }
    }

    @Container(absent = true)
    static class Items3 implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item3) {
            this.items.add(item3);
        }

        // must cause an error, even if 'local' cannot come from the outside
        @Modified
        @Override
        public void modifying() {
            Item local = new ItemImpl(10);
            local.setMessage("msg");
            this.items.add(local);
        }

        public List<Item> getItems() {
            return items;
        }
    }

    // correct implementation
    @Container
    static class Items4 implements Items {
        private final List<Item> items = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item4) {
            this.items.add(item4);
        }

        @Override
        public void modifying() {
            items.clear(); // no link with an item
        }

        public List<Item> getItems() {
            return items;
        }
    }

    // correct implementation, but not a container
    @Container(absent = true)
    static class Items5 implements Items {
        private final List<Item> items = new ArrayList<>();
        private final List<Item> second = new ArrayList<>();

        @Modified
        @Override
        public void add(Item item5) {
            this.items.add(item5);
        }

        @Override
        public void modifying() {
            items.clear(); // no link with an item
        }

        public List<Item> getItems() {
            return items;
        }

        // not container!
        public void countMessage(@Modified Item item) {
            item.setMessage(second.size() + " items");
        }

        public void addToSecond(Item item) {
            second.add(item);
        }
    }

    // not inheriting
    @Container
    static class Items6 {
        private final List<Item> items = new ArrayList<>();

        @Modified
        public void add(Item item6) {
            this.items.add(item6);
        }

        @Modified
        public void modifying() {
            items.clear(); // no link with an item
        }

        public List<Item> getItems() {
            return items;
        }
    }

    @Container(absent = true)
    static class Items7 {
        private final List<Item> items = new ArrayList<>();

        @Modified
        public void add(Item item7) {
            this.items.add(item7);
        }

        // fails container restriction, but does not raise error
        @Modified
        public void modifying() {
            Item item = items.get(0);
            item.setMessage(items.size() + " items");
        }

        public List<Item> getItems() {
            return items;
        }
    }
}

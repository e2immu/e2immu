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

        @Modified
        @Override
        public void modifying() {
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

        // must cause an error!
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
}

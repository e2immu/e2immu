package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Modified;

import java.util.ArrayList;
import java.util.List;

public class Modification_30 {

    interface Item {
        int weight();

        @Modified
        void setMessage(String message);
    }


    static class ItemsImpl  {
        private final List<Item> items = new ArrayList<>();

        @Modified
        public void modifying() {
            this.items.get(0).setMessage("Clear!");
        }

        @Modified
        public void modifying2() {
            Item item = this.items.get(0);
            item.setMessage("Clear!");
        }

        public List<Item> getItems() {
            return items;
        }
    }

}

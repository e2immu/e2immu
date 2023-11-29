package org.e2immu.analyser.parser.start.testexample;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

public class Container_10C {

    private final ArrayList<V> values = new ArrayList<>();

    public Container_10C getBag(String key) {
        if(key.indexOf('.')!=-1){
            T tok = new T(key, '.');
            Container_10C currentBag = this;
            while(tok.hasMoreElements()){
                String subKey = tok.nextElement();
                if(tok.hasMoreElements()){
                    currentBag = currentBag.getBag(subKey);
                    if(currentBag==null) {
                        return null;
                    }
                } else {
                    return currentBag.getBag(subKey);
                }
            }
            return null;
        } else {
            Iterator<V> it = this.values.iterator();
            while(it.hasNext()){
                V p = it.next();
                if(p.isbag() && p.getKey().equals(key)) {
                    return (Container_10C) p.getValue();
                }
            }
            return null;
        }
    }

    public static class V implements Serializable {
        private final String key ;
        private final Object value ;
        private boolean b;

        public V(String key, Object value, boolean b) {
            this.key = key;
            this.value = value;
            this.b = b;
        }

        public String getKey() {
            return this.key;
        }

        public Object getValue() {
            return this.value;
        }

        public boolean isbag() {
            return this.b;
        }
    };

    private class T {

        private char s;
        private boolean hasMoreElements = false;
        private String current = null;

        public T(String value, char s) {

            this.current = value;
            this.s = s;
            this.hasMoreElements = (current!=null && !current.isEmpty());
        }

        public boolean hasMoreElements() {

            return this.hasMoreElements;
        }

        public String nextElement() {

            // Stop when we have no more
            if(!this.hasMoreElements) {
                return null;
            }

            int idx = this.current.indexOf(this.s);
            if(idx==-1){
                this.hasMoreElements=false;
                return this.current;
            }

            // Get next part
            String res = this.current.substring(0, idx);
            this.current = current.substring(idx + 1);
            this.hasMoreElements = (current!=null && current.length()>0);

            return res;
        }
    }

}
package org.e2immu.analyser.resolver.testexample;


public class ArrayInitializer_1 {

    interface R {
        String get(int i);
    }

    String[] method1(R rst) {
        return new String[]{rst.get(1), rst.get(2), rst.get(3)};
    }

    Object[] method2(long supplierId, String invoiceNumber, long invoiceType) {
        Object[] params = {Long.valueOf(supplierId), invoiceNumber, Long.valueOf(invoiceType)};
        return params;
    }
}

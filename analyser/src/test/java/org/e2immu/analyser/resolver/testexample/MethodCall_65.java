package org.e2immu.analyser.resolver.testexample;

import java.util.List;

public class MethodCall_65 {

    interface VWFRCDPDealData {

    }

    static class VWFRCDPBorrowerIndividual {
        List<VWFRCDPDealData> getDealData() {
            return null;
        }
    }

    static class Borrower {
        DealDataList dealDataList;

        public void setDealDataList(DealDataList value) {
            this.dealDataList = value;
        }

        static class DealDataList {

        }
    }

    private org.e2immu.analyser.resolver.testexample.MethodCall_65.Borrower.DealDataList createCDPBorrowerDealDataList(List<VWFRCDPDealData> dealDataList) {
        return null;
    }

    Borrower method(VWFRCDPBorrowerIndividual individual) {
        Borrower borrower = new Borrower();
        borrower.setDealDataList(createCDPBorrowerDealDataList(individual.getDealData()));
        return borrower;
    }
}

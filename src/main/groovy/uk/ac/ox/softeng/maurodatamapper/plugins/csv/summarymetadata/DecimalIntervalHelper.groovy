package uk.ac.ox.softeng.maurodatamapper.plugins.csv.summarymetadata

import grails.util.Pair

class DecimalIntervalHelper {


    static void main(String[] args) {

        DecimalIntervalHelper decimalIntervalHelper = new DecimalIntervalHelper(0.65, 3)
        decimalIntervalHelper.intervals.each { interval ->
            System.err.println(interval.key)
        }
    }

    BigDecimal minNum, maxNum
    BigDecimal difference

    String labelSeparator = " - "

    // These determine the interval length

    BigDecimal intervalLength

    BigDecimal firstIntervalStart
    List<BigDecimal> intervalStarts

    LinkedHashMap<String, Pair<BigDecimal, BigDecimal>> intervals


    DecimalIntervalHelper(BigDecimal minNum, BigDecimal maxNum) {
        this.minNum = minNum
        this.maxNum = maxNum
        difference = maxNum - minNum

        //System.err.println(differencePeriod)
        calculateInterval()
        // calculateIntervalStart()
        calculateIntervalStarts()
        calculateIntervals()
    }

    void calculateInterval() {

        if (0 < difference && difference <= 0.1 ) {
            intervalLength = 0.02
        } else if (0.1 < difference && difference <= 1 ) {
            intervalLength = 0.2
        } else if (1 < difference && difference <= 5 ) {
            intervalLength = 1
        } else if (5 < difference && difference <= 10 ) {
            intervalLength = 2
        } else if (10 < difference && difference <= 20 ) {
            intervalLength = 5
        } else if (20 < difference && difference <= 100 ) {
            intervalLength = 10
        } else if (100 < difference && difference <= 200 ) {
            intervalLength = 20
        } else if (200 < difference && difference <= 1000 ) {
            intervalLength = 100
        } else if (1000 < difference && difference <= 2000 ) {
            intervalLength = 200
        } else if (2000 < difference && difference <= 10000 ) {
            intervalLength = 1000
        } else if (10000 < difference && difference <= 20000 ) {
            intervalLength = 2000
        } else if (20000 < difference && difference <= 100000 ) {
            intervalLength = 10000
        } else if (100000 < difference && difference <= 200000 ) {
            intervalLength = 20000
        } else if (200000 < difference && difference <= 1000000 ) {
            intervalLength = 100000
        } else if (1000000 < difference && difference <= 2000000 ) {
            intervalLength = 200000
        } else if (2000000 < difference && difference <= 10000000 ) {
            intervalLength = 1000000
        } else if (10000000 < difference && difference <= 20000000 ) {
            intervalLength = 2000000
        } else if (20000000 < difference && difference <= 100000000 ) {
            intervalLength = 10000000
        } else intervalLength = maxNum - minNum / 10

        firstIntervalStart = minNum.divideAndRemainder(intervalLength)[0] * intervalLength
    }

    void calculateIntervalStarts() {
        intervalStarts = []
        BigDecimal currNum = firstIntervalStart
        while(currNum < maxNum) {
            intervalStarts.add(currNum)
            currNum += intervalLength
        }
    }

    void calculateIntervals() {
        intervals = new LinkedHashMap()
        intervalStarts.each { start ->

            BigDecimal finish = start + intervalLength
            String label = "" + start + labelSeparator + finish
            intervals[label] = (new Pair(start, finish))
        }

    }
}

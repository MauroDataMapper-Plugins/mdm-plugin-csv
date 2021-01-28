/*
 * Copyright 2020 University of Oxford and Health and Social Care Information Centre, also known as NHS Digital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package uk.ac.ox.softeng.maurodatamapper.plugins.csv

import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.DataType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.EnumerationType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.enumeration.EnumerationValue
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.summarymetadata.DateIntervalHelper
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.summarymetadata.DecimalIntervalHelper
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.summarymetadata.IntegerIntervalHelper
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.summarymetadata.SummaryMetadataHelper

import grails.util.Pair
import org.apache.commons.lang3.time.DateUtils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ColumnSpec {

    static String[] dateFormats = ["yyyyMMdd", "yyy-MM-dd", "M/y", "dd/MM/yyyy", "M/d/y", "d-M-y", "M-d-y",
                                   "dd/MM/yyyy hh:mm:ss", "yyy-MM-dd hh:mm:ss" ]

    static String[] dataTypes = ["String", "Integer", "Decimal", "Date", "DateTime", "Time"]

    String headerName
    CsvDataModelImporterProviderServiceParameters csvImportOptions
    Set<String> possibleDataTypes = new HashSet<String>()
    Map<Object, Integer> distinctValues = [:]
    Object maxValue
    Object minValue
    Boolean optional = false

    // for Summary Metadata
    List<Object> typedValues = []
    Map<String, Integer> valueDistribution = [:]

    ColumnSpec(String header, CsvDataModelImporterProviderServiceParameters options) {
        this.headerName = header
        this.csvImportOptions = options
        if(!csvImportOptions.detectTypes) {
            possibleDataTypes.add("String")
        }
    }

    void addValue(String value) {
        Object typedValue = value
        if(csvImportOptions.detectTypes) {
            typedValue = getTypedValue(value)
            if(typedValue != null) {
                possibleDataTypes.add(typedValue.getClass().simpleName)
            } else {
                optional = true
            }
        }
        if(distinctValues.size() < (csvImportOptions.maxEnumerations + 1) &&
                (csvImportOptions.detectEnumerations ||
                        csvImportOptions.detectTerminologies ||
                        csvImportOptions.generateSummaryMetadata)) {
            if(distinctValues[typedValue]) {
                distinctValues[typedValue] = distinctValues[typedValue] + 1
            } else {
                distinctValues[typedValue] = 1
            }
        }
        if(csvImportOptions.generateSummaryMetadata) {
            if(typedValue && maxValue && typedValue.class != maxValue.class) {
                typedValue = typedValue.toString()
            } else {
                if(typedValue && typedValue > maxValue || !maxValue) {
                    maxValue = typedValue
                }
                if(typedValue && typedValue < minValue || !minValue) {
                    minValue = typedValue
                }

            }
        }
        if(csvImportOptions.generateSummaryMetadata) {
            typedValues.add(typedValue)
        }
    }

    Object[] tableDataRow() {
        //return [headerName, possibleDataTypes, "C", "D", "E", "F"]
        String distinctVals = ">"+csvImportOptions.maxEnumerations
        if(distinctValues.size() <= csvImportOptions.maxEnumerations) {
            distinctVals = distinctValues.size()
        }
        return [
                headerName,
                possibleDataTypes,
                "" + optional,
                distinctVals,
                "" + minValue,
                "" + maxValue
        ]
    }

    static String[] tableHeaderRow() {
        return [
                "Column name",
                "Possible types",
                "Optional",
                "Distinct values",
                "Min value",
                "Max value"
        ]
    }


    static Object getTypedValue(String input) {
        if(!input || input == "") {
            return null
        }
        if (input.isInteger()) {
            return input.toInteger()
        }
        if (input.isBigDecimal()) {
            return input.toBigDecimal()
        }
        try {
            Date date = DateUtils.parseDate(input, dateFormats)
            return date
        } catch (Exception e) { /* Do nothing */ }

        return input
    }

    int getMinMultiplicity() {
        if(optional) {
            return 0
        }
        return 1
    }

    int getMaxMultiplicity() {
        return 1
    }

    DataType getDataType(Map<String, DataType> dataTypes) {
        System.err.println(this.headerName)
        System.err.println("TooUniqueValue : ${csvImportOptions.tooUniqueValue}")
        System.err.println("TooUniqueValue : ${averageDistribution()}")
        if(csvImportOptions.detectEnumerations &&
                distinctValues.size() <= csvImportOptions.maxEnumerations &&
                possibleDataTypes.size() == 1 &&
                (possibleDataTypes.getAt(0) == "String" || possibleDataTypes.getAt(0) == "Integer") &&
                averageDistinctValues() >= csvImportOptions.tooUniqueValue)  {
            return getEnumeratedDataType()
        } else return getPrimitiveDataType(dataTypes)

    }

    DataType getEnumeratedDataType() {
        EnumerationType enumerationType = new EnumerationType(label: headerName)
        distinctValues.keySet().sort().each { value ->
            if(value) {
                enumerationType.addToEnumerationValues(new EnumerationValue(key: value, value: value))
            }
        }
        return enumerationType
    }

    DataType getPrimitiveDataType(Map<String, DataType> dataTypes) {
        String dataTypeName = decideDataType()
        if(dataTypeName) {
            return dataTypes[dataTypeName]
        }
        return null
    }

    String decideDataType() {
        if(possibleDataTypes.size() == 0) {
            return "String"
        }
        if(possibleDataTypes.size() == 1) {
            return possibleDataTypes[0]
        }
        if(possibleDataTypes.size() == 2) {
            if(possibleDataTypes.containsAll("Integer","Decimal")) {
                return "Decimal"
            }
            if(possibleDataTypes.containsAll("Date","DateTime")) {
                return "DateTime"
            }
        }
        return "String"

    }

    SummaryMetadata calculateSummaryMetadata() {

        if(decideDataType() == "Date") {
            calculateDateSummaryMetadata()
        } else if(decideDataType() == "Decimal") {
            calculateDecimalSummaryMetadataByRange()
        } else if(decideDataType() == "Integer" && distinctValues.size() <= csvImportOptions.maxEnumerations) {
            calculateIntegerSummaryMetadataByDistinctValues()
        } else if(decideDataType() == "Integer") {
            calculateIntegerSummaryMetadataByRange()
        } else if(decideDataType() == "String" && distinctValues.size() <= csvImportOptions.maxEnumerations) {
            calculateStringSummaryMetadata()
        } else {
            return null
        }
        if(csvImportOptions.testOnly) {
            return null
        }
        if(csvImportOptions.smallestSummaryValue && csvImportOptions.smallestSummaryValue > 1) {
            if(averageDistribution() < csvImportOptions.tooUniqueValue) {
                return null
            }
            hideSmallValues(csvImportOptions.smallestSummaryValue)
        }
        return SummaryMetadataHelper.createSummaryMetadataFromMap(headerName, "Value Distribution", valueDistribution)

    }

    void hideSmallValues(Integer smallestValue) {
        valueDistribution.each { key, value ->
            if (value != 0 && value < smallestValue) {
                valueDistribution[key] = smallestValue
            }
        }
    }

    BigDecimal averageDistinctValues() {
        Map<Object, Integer> nonTrivialValues = distinctValues
                .findAll { key, value ->
                    value > 0 && key
                }
        System.err.println(nonTrivialValues)
        System.err.println(nonTrivialValues.size())
        if(nonTrivialValues.size() > 0) {
            BigDecimal total  = (BigDecimal) nonTrivialValues
                    .collect { key, value -> value}.sum()
            System.err.println(total)
            BigDecimal average = total / nonTrivialValues.size()
            System.err.println(average)
            return average
        } else {
            return Integer.MAX_VALUE
        }
    }


    BigDecimal averageDistribution() {
        System.err.println(this.headerName)
        System.err.println(valueDistribution)
        Map<String, Integer> nonTrivialValues = valueDistribution
                .findAll { key, value ->
                    value > 0 && !key.equalsIgnoreCase("null")
                }
        System.err.println(nonTrivialValues)
        System.err.println(nonTrivialValues.size())
        if(nonTrivialValues.size() > 0) {
            BigDecimal total  = (BigDecimal) nonTrivialValues
                    .collect { key, value -> value}.sum()
            System.err.println(total)
            BigDecimal average = total / nonTrivialValues.size()
            System.err.println(average)
            return average
        } else {
            return Integer.MAX_VALUE
        }
    }

    static LocalDateTime convertToLocalDateTimeViaMillisecond(Date dateToConvert) {
        return Instant.ofEpochMilli(dateToConvert.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
    }

    void calculateDateSummaryMetadata() {
        LocalDateTime ldtMinValue = convertToLocalDateTimeViaMillisecond((Date) minValue)
        LocalDateTime ldtMaxValue = convertToLocalDateTimeViaMillisecond((Date) maxValue)

        DateIntervalHelper dateIntervalHelper = new DateIntervalHelper(ldtMinValue, ldtMaxValue)
        Map<String, Pair<LocalDateTime, LocalDateTime>> intervals = dateIntervalHelper.getIntervals()

        intervals.keySet().sort().each { intervalName ->
            valueDistribution[intervalName] = 0
        }
        typedValues.each { typedValue ->
            if(!typedValue) {
                if(valueDistribution["Null"]) {
                    valueDistribution["Null"] = valueDistribution["Null"] + 1
                } else {
                    valueDistribution["Null"] = 1
                }
            } else {
                LocalDateTime ldtTypedValue = convertToLocalDateTimeViaMillisecond((Date) typedValue)
                intervals.each { interval ->
                    if(ldtTypedValue >= interval.value.aValue && ldtTypedValue < interval.value.bValue) {
                        valueDistribution[interval.key] = valueDistribution[interval.key] + 1
                    }
                }
            }
        }
    }

    void calculateIntegerSummaryMetadataByRange() {
        Integer intMinValue = (Integer) minValue
        Integer intMaxValue = (Integer) maxValue

        IntegerIntervalHelper integerIntervalHelper = new IntegerIntervalHelper(intMinValue, intMaxValue)
        Map<String, Pair<Integer, Integer>> intervals = integerIntervalHelper.getIntervals()

        intervals.keySet().sort().each { intervalName ->
            valueDistribution[intervalName] = 0
        }
        typedValues.each { typedValue ->
            if(!typedValue) {
                if(valueDistribution["Null"]) {
                    valueDistribution["Null"] = valueDistribution["Null"] + 1
                } else {
                    valueDistribution["Null"] = 1
                }
            } else {
                intervals.each { interval ->
                    if((Integer)typedValue >= interval.value.aValue && (Integer)typedValue < interval.value.bValue) {
                        valueDistribution[interval.key] = valueDistribution[interval.key] + 1
                    }
                }
            }
        }
    }

    void calculateDecimalSummaryMetadataByRange() {
        BigDecimal intMinValue = (BigDecimal) minValue
        BigDecimal intMaxValue = (BigDecimal) maxValue

        DecimalIntervalHelper decimalIntervalHelper = new DecimalIntervalHelper(intMinValue, intMaxValue)
        Map<String, Pair<BigDecimal, BigDecimal>> intervals = decimalIntervalHelper.getIntervals()

        intervals.keySet().sort().each { intervalName ->
            valueDistribution[intervalName] = 0
        }
        typedValues.each { typedValue ->
            if(!typedValue) {
                if(valueDistribution["Null"]) {
                    valueDistribution["Null"] = valueDistribution["Null"] + 1
                } else {
                    valueDistribution["Null"] = 1
                }
            } else {
                intervals.each { interval ->
                    if((BigDecimal)typedValue >= interval.value.aValue && (BigDecimal)typedValue < interval.value.bValue) {
                        valueDistribution[interval.key] = valueDistribution[interval.key] + 1
                    }
                }
            }
        }
    }

    void calculateStringSummaryMetadata() {
        distinctValues.keySet().sort().each { valueName ->
            if(!valueName) {
                valueDistribution ["Null"] = distinctValues[valueName]
            } else {
                valueDistribution [(String) valueName] = distinctValues[valueName]
            }
        }
    }

    void calculateIntegerSummaryMetadataByDistinctValues() {
        distinctValues.keySet().sort().each { valueName ->
            if(!valueName) {
                valueDistribution ["Null"] = distinctValues[valueName]
            } else {
                valueDistribution ["" + (Integer) valueName] = distinctValues[valueName]
            }
        }
    }


}


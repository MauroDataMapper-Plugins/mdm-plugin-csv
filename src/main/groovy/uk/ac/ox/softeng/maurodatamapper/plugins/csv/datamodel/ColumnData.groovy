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
package uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel

import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.DataType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.EnumerationType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.enumeration.EnumerationValue
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.provider.importer.parameter.CsvDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.summarymetadata.DateIntervalHelper
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.summarymetadata.DecimalIntervalHelper
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.summarymetadata.IntegerIntervalHelper
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.summarymetadata.SummaryMetadataHelper

import grails.util.Pair
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.time.DateUtils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Slf4j
class ColumnData {

    static String[] dateFormats = ["yyyyMMdd", "yyy-MM-dd", "M/y", "dd/MM/yyyy", "M/d/y", "d-M-y", "M-d-y",
                                   "dd/MM/yyyy hh:mm:ss", "yyy-MM-dd hh:mm:ss"]

    private String headerName
    private CsvDataModelImporterProviderServiceParameters csvImportOptions
    private Set<String> possibleDataTypes = new HashSet<String>()
    private Map<Object, Integer> distinctValues = [:]
    private Boolean optional = false

    // for Summary Metadata
    private List<Object> typedValues = []
    private Map<String, Integer> valueDistribution = [:]

    ColumnData(String header, CsvDataModelImporterProviderServiceParameters options) {
        this.headerName = header
        this.csvImportOptions = options
        if (!csvImportOptions.detectTypes) {
            possibleDataTypes.add("String")
        }
    }

    void addValue(String value) {
        Object typedValue = value
        if (csvImportOptions.detectTypes) {
            typedValue = getTypedValue(value)
            if (typedValue != null) {
                possibleDataTypes.add(typedValue.getClass().simpleName)
            } else {
                optional = true
            }
        }
        if (distinctValues.size() < (csvImportOptions.maxEnumerations + 1) &&
            (csvImportOptions.detectEnumerations ||
             csvImportOptions.detectTerminologies ||
             csvImportOptions.generateSummaryMetadata)) {
            if (distinctValues[typedValue]) {
                distinctValues[typedValue] = distinctValues[typedValue] + 1
            } else {
                distinctValues[typedValue] = 1
            }
        }

        if (csvImportOptions.generateSummaryMetadata) {
            typedValues.add(typedValue)
        }
    }

    int getMinMultiplicity() {
        optional ? 0 : 1
    }

    int getMaxMultiplicity() {
        1
    }

    DataType getDataType(Map<String, DataType> dataTypes) {
        if (csvImportOptions.detectEnumerations &&
            distinctValues.size() <= csvImportOptions.maxEnumerations &&
            possibleDataTypes.size() == 1 &&
            (possibleDataTypes.first() == 'String' || possibleDataTypes.first() == 'Integer') &&
            averageDistinctValues() >= csvImportOptions.tooUniqueValue) {
            return getEnumeratedDataType()
        }
        getPrimitiveDataType(dataTypes)
    }

    DataType getEnumeratedDataType() {
        EnumerationType enumerationType = new EnumerationType(label: headerName)
        distinctValues.keySet().sort().each { value ->
            if (value) {
                enumerationType.addToEnumerationValues(new EnumerationValue(key: value, value: value))
            }
        }
        return enumerationType
    }

    DataType getPrimitiveDataType(Map<String, DataType> dataTypes) {
        dataTypes.size() > 1 ? dataTypes[decideDataType()] : dataTypes.String
    }

    String decideDataType() {
        if (!possibleDataTypes) {
            return 'String'
        }

        if (possibleDataTypes.size() == 1) {
            return possibleDataTypes.first()
        }

        // Ensure that numeric accuracy doesn't get reduced
        if (possibleDataTypes.every { it in ['Integer', 'Decimal', 'BigDecimal'] }) {
            return 'Decimal'
        }

        // Ensure that datetime accuracy doesn't get reduced
        if (possibleDataTypes.every { it in ['Date', 'DateTime'] }) {
            return 'DateTime'
        }

        log.debug('{} possible datatypes defaulting to [String]', possibleDataTypes)
        'String'
    }

    SummaryMetadata calculateSummaryMetadata() {

        if (decideDataType() == 'Date') {
            calculateDateSummaryMetadata()
        } else if (decideDataType() == 'Decimal') {
            calculateDecimalSummaryMetadataByRange()
        } else if (decideDataType() == 'Integer' && distinctValues.size() <= csvImportOptions.maxEnumerations) {
            calculateIntegerSummaryMetadataByDistinctValues()
        } else if (decideDataType() == 'Integer') {
            calculateIntegerSummaryMetadataByRange()
        } else if (decideDataType() == 'String' && distinctValues.size() <= csvImportOptions.maxEnumerations) {
            calculateStringSummaryMetadata()
        } else {
            // Not possible to calculate SM for other types
            return null
        }

        if (csvImportOptions.testOnly) {
            return null
        }
        if (csvImportOptions.smallestSummaryValue && csvImportOptions.smallestSummaryValue > 1) {
            if (averageDistribution() < csvImportOptions.tooUniqueValue) {
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
        log.trace('{}', nonTrivialValues)
        log.trace('{}', nonTrivialValues.size())
        if (nonTrivialValues.size() > 0) {
            BigDecimal total = (BigDecimal) nonTrivialValues
                .collect { key, value -> value }.sum()
            log.trace('{}', total)
            BigDecimal average = total / nonTrivialValues.size()
            log.trace('{}', average)
            return average
        } else {
            return Integer.MAX_VALUE
        }
    }

    BigDecimal averageDistribution() {
        log.trace(this.headerName)
        log.trace('{}', valueDistribution)
        Map<String, Integer> nonTrivialValues = valueDistribution
            .findAll { key, value ->
                value > 0 && !key.equalsIgnoreCase("null")
            }
        log.trace('{}', nonTrivialValues)
        log.trace('{}', nonTrivialValues.size())
        if (nonTrivialValues.size() > 0) {
            BigDecimal total = (BigDecimal) nonTrivialValues
                .collect { key, value -> value }.sum()
            log.trace('{}', total)
            BigDecimal average = total / nonTrivialValues.size()
            log.trace('{}', average)
            return average
        } else {
            return Integer.MAX_VALUE
        }
    }

    Object getMinValue() {
        // This assumes all typed values are of the same equivalent type
        typedValues.findAll().min()
    }

    Object getMaxValue() {
        // This assumes all typed values are of the same equivalent type
        typedValues.findAll().max()
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
            if (!typedValue) {
                if (valueDistribution["Null"]) {
                    valueDistribution["Null"] = valueDistribution["Null"] + 1
                } else {
                    valueDistribution["Null"] = 1
                }
            } else {
                LocalDateTime ldtTypedValue = convertToLocalDateTimeViaMillisecond((Date) typedValue)
                intervals.each { interval ->
                    if (ldtTypedValue >= interval.value.aValue && ldtTypedValue < interval.value.bValue) {
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
            if (!typedValue) {
                if (valueDistribution["Null"]) {
                    valueDistribution["Null"] = valueDistribution["Null"] + 1
                } else {
                    valueDistribution["Null"] = 1
                }
            } else {
                intervals.each { interval ->
                    if ((Integer) typedValue >= interval.value.aValue && (Integer) typedValue < interval.value.bValue) {
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
            if (!typedValue) {
                if (valueDistribution["Null"]) {
                    valueDistribution["Null"] = valueDistribution["Null"] + 1
                } else {
                    valueDistribution["Null"] = 1
                }
            } else {
                intervals.each { interval ->
                    if ((BigDecimal) typedValue >= interval.value.aValue && (BigDecimal) typedValue < interval.value.bValue) {
                        valueDistribution[interval.key] = valueDistribution[interval.key] + 1
                    }
                }
            }
        }
    }

    void calculateStringSummaryMetadata() {
        distinctValues.keySet().sort().each { valueName ->
            if (!valueName) {
                valueDistribution["Null"] = distinctValues[valueName]
            } else {
                valueDistribution[(String) valueName] = distinctValues[valueName]
            }
        }
    }

    void calculateIntegerSummaryMetadataByDistinctValues() {
        distinctValues.keySet().sort().each { valueName ->
            if (!valueName) {
                valueDistribution["Null"] = distinctValues[valueName]
            } else {
                valueDistribution["" + (Integer) valueName] = distinctValues[valueName]
            }
        }
    }

    static Object getTypedValue(String input) {
        if (!input) {
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
        } catch (Exception ignored) { /* Do nothing */ }

        input
    }

    static LocalDateTime convertToLocalDateTimeViaMillisecond(Date dateToConvert) {
        return Instant.ofEpochMilli(dateToConvert.getTime())
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }

    Object[] tableDataRow() {
        //return [headerName, possibleDataTypes, "C", "D", "E", "F"]
        String distinctVals = ">" + csvImportOptions.maxEnumerations
        if (distinctValues.size() <= csvImportOptions.maxEnumerations) {
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

}


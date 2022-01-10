/*
 * Copyright 2020-2021 University of Oxford and Health and Social Care Information Centre, also known as NHS Digital
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

    static String[] dateFormats = ['yyyyMMdd', 'yyy-MM-dd', 'M/y', 'dd/MM/yyyy', 'M/d/y', 'd-M-y', 'M-d-y',
                                   'dd/MM/yyyy hh:mm:ss', 'yyy-MM-dd hh:mm:ss']

    static String NULL_VALUE_KEY = 'NULL'

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
            possibleDataTypes.add('String')
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
        } else {
            if (!value) {
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

        if (csvImportOptions.generateSummaryMetadata || csvImportOptions.detectTypes) {
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
            isPotentialEnumeration() &&
            possibleDataTypes.size() == 1 &&
            (possibleDataTypes.first() == 'String' || possibleDataTypes.first() == 'Integer') &&
            averageDistinctValues() >= csvImportOptions.tooUniqueValue) {
            return getEnumeratedDataType()
        }
        getPrimitiveDataType(dataTypes)
    }

    DataType getEnumeratedDataType() {
        EnumerationType enumerationType = new EnumerationType(label: headerName)
        distinctValues.keySet().sort().each {value ->
            if (value) {
                enumerationType.addToEnumerationValues(new EnumerationValue(key: value, value: value))
            }
        }
        return enumerationType
    }

    DataType getPrimitiveDataType(Map<String, DataType> dataTypes) {
        dataTypes.size() > 1 ? dataTypes[decideDataType()] : dataTypes.String
    }

    boolean isPotentialEnumeration() {
        distinctValues.size() <= csvImportOptions.maxEnumerations
    }

    String decideDataType() {
        if (!possibleDataTypes) {
            return 'String'
        }

        if (possibleDataTypes.size() == 1) {
            return possibleDataTypes.first()
        }

        // Ensure that numeric accuracy doesn't get reduced
        if (possibleDataTypes.every {it in ['Integer', 'Decimal', 'BigDecimal']}) {
            return 'Decimal'
        }

        // Ensure that datetime accuracy doesn't get reduced
        if (possibleDataTypes.every {it in ['Date', 'DateTime']}) {
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
        } else if (decideDataType() == 'Integer' && isPotentialEnumeration()) {
            calculateSummaryMetadataByDistinctValues()
        } else if (decideDataType() == 'Integer') {
            calculateIntegerSummaryMetadataByRange()
        } else if (decideDataType() == 'String' && isPotentialEnumeration()) {
            calculateSummaryMetadataByDistinctValues()
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
        return SummaryMetadataHelper.createSummaryMetadataFromMap(headerName, 'Value Distribution', valueDistribution)

    }

    void hideSmallValues(Integer smallestValue) {
        valueDistribution.each {key, value ->
            if (value != 0 && value < smallestValue) {
                valueDistribution[key] = smallestValue
            }
        }
    }

    BigDecimal averageDistinctValues() {
        Map<Object, Integer> nonTrivialValues = distinctValues.findAll {key, value ->
            key != null && value > 0
        }
        log.trace('{}', nonTrivialValues)
        log.trace('{}', nonTrivialValues.size())
        if (nonTrivialValues.size() > 0) {
            BigDecimal total = (BigDecimal) nonTrivialValues.collect {key, value -> value}.sum()
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
        Map<String, Integer> nonTrivialValues = valueDistribution.findAll {key, value ->
            value > 0 && !key.equalsIgnoreCase(NULL_VALUE_KEY)
        }
        log.trace('{}', nonTrivialValues)
        log.trace('{}', nonTrivialValues.size())
        if (nonTrivialValues.size() > 0) {
            BigDecimal total = (BigDecimal) nonTrivialValues
                .collect {key, value -> value}.sum()
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

        initialiseValueDistribution(intervals)

        typedValues.each {typedValue ->
            if (typedValue == null) {
                valueDistribution[NULL_VALUE_KEY] = valueDistribution[NULL_VALUE_KEY] + 1
            } else {
                LocalDateTime ldtTypedValue = convertToLocalDateTimeViaMillisecond((Date) typedValue)
                intervals.each {interval ->
                    if (ldtTypedValue >= interval.value.aValue && ldtTypedValue < interval.value.bValue) {
                        valueDistribution[interval.key] = valueDistribution[interval.key] + 1
                    }
                }
            }
        }
    }

    void calculateIntegerSummaryMetadataByRange() {
        IntegerIntervalHelper integerIntervalHelper = new IntegerIntervalHelper((Integer) minValue, (Integer) maxValue)
        buildNumericValueDistribution(integerIntervalHelper.getIntervals())
    }

    void calculateDecimalSummaryMetadataByRange() {
        DecimalIntervalHelper decimalIntervalHelper = new DecimalIntervalHelper((BigDecimal) minValue, (BigDecimal) maxValue)
        buildNumericValueDistribution(decimalIntervalHelper.getIntervals())
    }

    void initialiseValueDistribution(Map<String, Pair> intervals) {
        valueDistribution = intervals.keySet().collectEntries {intervalName ->
            [intervalName, 0]
        }
        if (optional) valueDistribution[NULL_VALUE_KEY] = 0
    }

    void buildNumericValueDistribution(Map<String, Pair<String, Number>> intervals) {
        initialiseValueDistribution(intervals)
        typedValues.each {typedValue ->
            if (typedValue == null) {
                valueDistribution[NULL_VALUE_KEY] = valueDistribution[NULL_VALUE_KEY] + 1
            } else {
                intervals.each {interval ->
                    if (typedValue >= interval.value.aValue && typedValue < interval.value.bValue) {
                        valueDistribution[interval.key] = valueDistribution[interval.key] + 1
                    }
                }
            }
        }
    }

    void calculateSummaryMetadataByDistinctValues() {
        if (!distinctValues) return
        valueDistribution = distinctValues.sort {it.key}.collectEntries {valueName, value ->
            valueName == null ? [NULL_VALUE_KEY, value] : ["$valueName".toString(), value]
        } as Map<String, Integer>
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
        } catch (Exception ignored) {/* Do nothing */}

        input
    }

    static LocalDateTime convertToLocalDateTimeViaMillisecond(Date dateToConvert) {
        return Instant.ofEpochMilli(dateToConvert.getTime())
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
}


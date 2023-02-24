/*
 * Copyright 2020-2023 University of Oxford and NHS England
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
package uk.ac.ox.softeng.maurodatamapper.plugins.csv.reader

import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiBadRequestException
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter

import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.ICSVParser
import com.opencsv.exceptions.CsvValidationException

/**
 * Wrapper for the basic OpenCsv Reader to handle headers or no headers.
 * The com.opencsv.CSVReaderHeaderAware class can auto map out headers but it cannot be initialised with a preset list of headers
 * which means we can't use it for files with no headers.
 *
 */
class CsvReader {

    static char[] separators = [',', '|', '\t', ';'] as char[]

    private CSVReader reader

    private final Map<String, Integer> headerIndex = [:]
    byte[] fileContents
    String filename

    CsvReader(FileParameter fileParameter, boolean firstRowIsHeader, String[] headerFields) {
        this.fileContents = fileParameter.fileContents
        this.filename = fileParameter.fileName
        Character bestSeparator = findBestSeparator()
        CSVParser parser = new CSVParserBuilder().withSeparator(bestSeparator.charValue()).build()
        reader = new CSVReaderBuilder(getNewFileReader()).withCSVParser(parser).build()
        initialiseHeaders(firstRowIsHeader, headerFields)
    }

    Set<String> getHeaders() {
        headerIndex.keySet()
    }

    Map<String, String> readRow() throws IOException, CsvValidationException {
        String[] strings = reader.readNext()
        if (!strings) return null

        if (strings.length != headerIndex.size()) {
            throw new IOException(String.format(
                ResourceBundle.getBundle(ICSVParser.DEFAULT_BUNDLE_NAME, Locale.getDefault())
                    .getString("header.data.mismatch.with.line.number"),
                reader.getRecordsRead(), headerIndex.size(), strings.length))
        }
        headerIndex.findAll { key, index -> index < strings.length }.collectEntries { key, index ->
            [key, strings[index]]
        } as Map<String, String>
    }

    private getNewFileReader() {
        new InputStreamReader(new ByteArrayInputStream(fileContents))
    }

    private Character findBestSeparator() {
        CSVReader basicCSVReader
        int maxColumns = 0
        Character bestSeparator = null
        separators.each { sep ->
            CSVParser parser = new CSVParserBuilder().withSeparator(sep).build()
            basicCSVReader = new CSVReaderBuilder(getNewFileReader())
                .withCSVParser(parser)
                .build()
            try {
                int thisSize = basicCSVReader.readNext().length
                if (thisSize > maxColumns) {
                    bestSeparator = sep
                    maxColumns = thisSize
                }
            } catch (Exception ignored) {
                return
            }
        }
        if (!maxColumns) {
            throw new ApiBadRequestException('CSV01', "Cannot parse file [{${filename}] using any separators")
        }
        bestSeparator
    }

    private initialiseHeaders(boolean firstRowIsHeader, String[] headerFields) {
        String[] headers = firstRowIsHeader ? reader.readNextSilently() : headerFields
        // Some "definitions" use the same values for the headers so we need to make sure they are all unique
        for (int i = 0; i < headers.length; i++) {
            String key = headers[i].trim()
            if (headerIndex.containsKey(key)) {
                key = "${key}(${i})"
            }
            headerIndex.put(key, i)
        }
    }
}

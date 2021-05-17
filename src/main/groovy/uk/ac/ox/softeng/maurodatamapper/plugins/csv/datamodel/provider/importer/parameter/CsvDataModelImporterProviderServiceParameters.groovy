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
package uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.provider.importer.parameter


import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.config.ImportGroupConfig
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.config.ImportParameterConfig
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.parameter.DataModelFileImporterProviderServiceParameters

import groovy.util.logging.Slf4j

@Slf4j
class CsvDataModelImporterProviderServiceParameters extends DataModelFileImporterProviderServiceParameters {

    @ImportParameterConfig(
        displayName = 'Maximum Enumerations',
        description = 'The maximum number of unique values to be interpreted as a defined enumeration',
        order = 4,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Configuration',
            order = 2
        )
    )
    Integer maxEnumerations = 20

    @ImportParameterConfig(
            displayName = 'Generate Summary Metadata',
            description = 'Whether to produce data distribution charts for column values',
            order = 3,
            group = @ImportGroupConfig(
                    name = 'Configuration',
                    order = 2
            )
    )
    Boolean generateSummaryMetadata = true

    @ImportParameterConfig(
            displayName = 'Detect Enumerations',
            description = 'Whether to treat columns with small numbers of unique values as enumerations',
            order = 2,
            group = @ImportGroupConfig(
                    name = 'Configuration',
                    order = 2
            )
    )
    Boolean detectEnumerations = true

    @ImportParameterConfig(
        displayName = 'Detect Types',
        description = 'Whether to detect appropriate types from the values in each column',
        order = 1,
        group = @ImportGroupConfig(
            name = 'Configuration',
            order = 2
        )
    )
    Boolean detectTypes = true

    @ImportParameterConfig(
        displayName = 'First row is Header',
        description = ['Is the first row the header row?',
            'If the first row is not a header row then the list of headers must be supplied in the Headers field.'],
        order = 5,
        group = @ImportGroupConfig(
            name = 'Configuration',
            order = 2
        )
    )
    Boolean firstRowIsHeader = true

    @ImportParameterConfig(
        displayName = 'Headers',
        description = ['Comma-separated list of values to use as the column headers.',
            'If supplied will be used as the headers instead of whatever\'s found in the CSV file.'],
        order = 6,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Configuration',
            order = 2
        )
    )
    String headers


    // These parameters are not really used yet
    BigDecimal tooUniqueValue = 1.5
    Integer smallestSummaryValue = 10
    Boolean detectTerminologies = false
    Boolean testOnly = false

    void setDefaults() {
        if (!maxEnumerations) {
            maxEnumerations = 20
        }
        if(!tooUniqueValue) {
            tooUniqueValue = 1.5
        }
        if(!smallestSummaryValue) {
            smallestSummaryValue = 10
        }
    }

}

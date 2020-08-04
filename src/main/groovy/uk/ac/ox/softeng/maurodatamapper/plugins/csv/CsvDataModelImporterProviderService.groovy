/*
 * Copyright 2020 University of Oxford
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

import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.DataModelImporterProviderService
import uk.ac.ox.softeng.maurodatamapper.security.User

import java.sql.Connection
import java.sql.PreparedStatement

class CsvDataModelImporterProviderService
    extends DataModelImporterProviderService<CsvDataModelImporterProviderServiceParameters> {

    @Override
    String getDisplayName() {
        'CSV Importer'
    }

    @Override
    String getVersion() {
        '2.0.0-SNAPSHOT'
    }

    @Override
    DataModel importDataModel(User user, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        return null
    }

    @Override
    List<DataModel> importDataModels(User user, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        return null
    }

    @Override
    Set<String> getKnownMetadataKeys() {
        [''] as Set<String>
    }

    @Override
    Boolean allowsExtraMetadataKeys() {
        true
    }

    @Override
    Boolean canImportMultipleDomains() {
        return null
    }
}

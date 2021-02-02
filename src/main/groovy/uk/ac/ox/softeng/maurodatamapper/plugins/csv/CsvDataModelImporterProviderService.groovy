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

import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiNotYetImplementedException
import uk.ac.ox.softeng.maurodatamapper.core.container.Folder
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelType
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.DataModelImporterProviderService
import uk.ac.ox.softeng.maurodatamapper.security.User

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
    DataModel importModel(User user, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        String modelName = csvDataModelImporterProviderServiceParameters.importFile.fileName
        if (csvDataModelImporterProviderServiceParameters.modelName) {
            modelName = csvDataModelImporterProviderServiceParameters.modelName
        }

        final DataModel dataModel = dataModelService.createAndSaveDataModel(
            user,
            Folder.get(csvDataModelImporterProviderServiceParameters.folderId),
            DataModelType.DATA_ASSET,
            modelName,
                null, null, null)
        CSVImporter csvImporter = new CSVImporter()
        csvImporter.importSingleFile(dataModel, csvDataModelImporterProviderServiceParameters.importFile.fileContents, csvDataModelImporterProviderServiceParameters)
        dataModelService.checkImportedDataModelAssociations(user, dataModel)
        return dataModel
    }

    @Override
    List<DataModel> importModels(User user, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        throw new ApiNotYetImplementedException('CDMIPS', 'importModels')
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

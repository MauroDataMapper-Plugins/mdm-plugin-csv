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
package uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.provider.importer

import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiBadRequestException
import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiNotYetImplementedException
import uk.ac.ox.softeng.maurodatamapper.core.authority.AuthorityService
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelType
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataClass
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataElement
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.DataType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.DataTypeService
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.EnumerationType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.PrimitiveType
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.DataModelImporterProviderService
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.ColumnData
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.provider.importer.parameter.CsvDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.reader.CsvReader
import uk.ac.ox.softeng.maurodatamapper.security.User

class CsvDataModelImporterProviderService
    extends DataModelImporterProviderService<CsvDataModelImporterProviderServiceParameters> {

    AuthorityService authorityService
    DataTypeService dataTypeService



    @Override
    String getDisplayName() {
        'CSV DataModel Importer'
    }

    @Override
    String getVersion() {
        getClass().getPackage().getSpecificationVersion() ?: 'SNAPSHOT'
    }

    @Override
    Boolean canImportMultipleDomains() {
        false
    }

    @Override
    List<DataModel> importModels(User currentUser, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        throw new ApiNotYetImplementedException('CSV03', 'importModels')
    }

    @Override
    DataModel importModel(User currentUser, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        csvDataModelImporterProviderServiceParameters.setDefaults()
        DataModel dataModel = importSingleFile(csvDataModelImporterProviderServiceParameters)
        dataModelService.checkImportedDataModelAssociations(currentUser, dataModel)
        dataModel
    }

    DataModel importSingleFile(CsvDataModelImporterProviderServiceParameters parameters) {
        Map<String, ColumnData> columns = getColumnDefinitions(parameters)

        if (!columns) {
            throw new ApiBadRequestException('CSV02', "Nothing to load into DataModel from file [${parameters.importFile.fileName}]")
        }

        String modelName = parameters.importFile.fileName.take(parameters.importFile.fileName.lastIndexOf('.'))
        DataModel dataModel = new DataModel(label: modelName, modelType: DataModelType.DATA_ASSET, authority: authorityService.defaultAuthority)

        Map<String, DataType> dataTypes = getPrimitiveDataTypes(parameters)
        dataTypes.each { k, dt ->
            dataModel.addToDataTypes(dt)
        }

        DataClass topLevelClass = new DataClass(label: 'CSV fields')
        dataModel.addToDataClasses(topLevelClass)

        columns.each { header, column ->
            DataType dataType = column.getDataType(dataTypes)
            if (dataType instanceof EnumerationType) {
                dataModel.addToDataTypes(dataType)
            }
            if (!dataType) log.warn('Unknown datatype {}', column.decideDataType())
            DataElement dataElement = new DataElement(
                label: header,
                dataType: dataType,
                minMultiplicity: column.getMinMultiplicity(),
                maxMultiplicity: column.getMaxMultiplicity())
            topLevelClass.addToDataElements(dataElement)
            SummaryMetadata summaryMetadata = column.calculateSummaryMetadata()
            if (summaryMetadata) {
                if (!summaryMetadata.label) {
                    summaryMetadata.label = dataElement.label
                }
                dataElement.addToSummaryMetadata(summaryMetadata)
                topLevelClass.addToSummaryMetadata(summaryMetadata)
            }
        }
        dataModel
    }

    Map<String, ColumnData> getColumnDefinitions(CsvDataModelImporterProviderServiceParameters parameters) {
        CsvReader csvReader = new CsvReader(parameters.importFile, parameters.firstRowIsHeader, parameters.headers?.split(','))
        Map<String, ColumnData> columns = csvReader.getHeaders().collectEntries { header ->
            [header, new ColumnData(header, parameters)]
        }
        Map<String, String> row = csvReader.readRow()

        int skippedRowCount = 0
        boolean skippedLast = false
        while (row) {
            if (!skippedLast) {
                columns.each { header, column ->
                    column.addValue(row[header])
                }
            }
            try {
                row = csvReader.readRow()
                skippedLast = false
            } catch (Exception e) {
                log.debug('Skipping row because of exception: {}', e.getMessage())
                skippedLast = true
                skippedRowCount++
            }
        }
        if (skippedRowCount > 0) {
            log.warn('Skipped {} rows', skippedRowCount)
        }
        columns
    }

    Map<String, DataType> getPrimitiveDataTypes(CsvDataModelImporterProviderServiceParameters parameters) {
        Map<String, DataType> types = dataTypeService.defaultListOfDataTypes.collectEntries {
            [it.label, new PrimitiveType(label: it.label, description: it.description)]
        }
        types.Integer = types.Number
        types.BigDecimal = types.Decimal
        types.String = types.Text
        parameters.detectTypes ? types : [String: types.String]
    }
}

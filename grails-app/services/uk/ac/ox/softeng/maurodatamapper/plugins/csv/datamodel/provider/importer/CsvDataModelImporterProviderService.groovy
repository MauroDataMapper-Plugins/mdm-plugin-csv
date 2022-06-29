/*
 * Copyright 2020-2022 University of Oxford and Health and Social Care Information Centre, also known as NHS Digital
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
import uk.ac.ox.softeng.maurodatamapper.core.model.CatalogueItem
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
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

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class CsvDataModelImporterProviderService
    extends DataModelImporterProviderService<CsvDataModelImporterProviderServiceParameters> {

    static AuthorityService authorityService
    static DataTypeService dataTypeService

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
    Boolean handlesContentType(String contentType) {
        contentType.equalsIgnoreCase('text/csv')
    }

    @Override
    List<DataModel> importModels(User currentUser, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        throw new ApiNotYetImplementedException('CSV01', 'importModels')
    }

    @Override
    DataModel importModel(User currentUser, CsvDataModelImporterProviderServiceParameters csvDataModelImporterProviderServiceParameters) {
        csvDataModelImporterProviderServiceParameters.setDefaults()
        DataModel dataModel = importSingleFile(currentUser, csvDataModelImporterProviderServiceParameters)
        dataModelService.checkImportedDataModelAssociations(currentUser, dataModel)
        dataModel
    }

    DataModel importMultipleFiles(User currentUser, List<CsvDataModelImporterProviderServiceParameters> parametersList, String label) {
        DataModel dataModel = new DataModel(label: label, modelType: DataModelType.DATA_ASSET.label, authority: authorityService.defaultAuthority)

        Map<String, DataType> dataTypes = getPrimitiveDataTypes(parametersList)
        dataTypes.each {k, dt ->
            dataModel.addToDataTypes(dt)
        }

        def (Map<String, ColumnData> allColumns, Map<String, Map<String, ColumnData>> fileColumns) = getColumnDefinitions(parametersList, true)
        Map<String, DataType> allDataTypes = allColumns.collectEntries {[it.key, it.value.getDataType(dataTypes)]}

        parametersList.eachWithIndex {csvParameters, parametersIndex ->

            List<String> pathNames = Path.of(removeFileNameSuffix(csvParameters.importFile.fileName, 'csv')).findAll {it}.collect {it.toString()}
            DataClass fileClass = new DataClass(label: pathNames[-1])
            CatalogueItem classParent = dataModel
            log.debug('CSV path names = {}', pathNames.toString())
            pathNames.eachWithIndex {name, i ->
                if (i == pathNames.size() - 1) {
                    if (classParent.dataClasses.find {it.label == fileClass.label}) log.error('Found duplicate class [{}], with path [{}]', fileClass.label, pathNames.toString())
                    classParent.addToDataClasses(fileClass)
                } else {
                    DataClass parentClass = (classParent instanceof DataModel ? classParent.childDataClasses : classParent.dataClasses).find {it.label == name}
                    if (parentClass) {
                        classParent = parentClass
                    } else {
                        DataClass intermediateClass = new DataClass(label: name, index: parametersIndex)
                        if (classParent.dataClasses.find {it.label == intermediateClass.label})
                            log.error('Found duplicate intermediate class [{}], with path [{}]', fileClass.label, pathNames.toString())
                        classParent.addToDataClasses(intermediateClass)
                        classParent = intermediateClass
                    }
                }
            }

            Map<String, ColumnData> columns = fileColumns[csvParameters.importFile.fileName]

            columns.eachWithIndex {header, column, columnIndex ->
                DataType dataType = allDataTypes[header]
                if (dataType instanceof EnumerationType && !dataModel.findDataTypeByLabel(dataType.label)) {
                    log.debug('Adding datatype with label {}', dataType.label)
                    dataModel.addToDataTypes(dataType)
                }
                if (!dataType) log.warn('Unknown datatype {}', column.decideDataType())
                DataElement dataElement = new DataElement(
                    label: header,
                    dataType: dataType,
                    minMultiplicity: column.getMinMultiplicity(),
                    maxMultiplicity: column.getMaxMultiplicity(),
                    idx: columnIndex
                )
                fileClass.addToDataElements(dataElement)
                if (csvParameters.generateSummaryMetadata) {
                    SummaryMetadata summaryMetadata = column.calculateSummaryMetadata(currentUser, OffsetDateTime.now())
                    if (summaryMetadata) {
                        SummaryMetadata dcSummaryMetadata = column.calculateSummaryMetadata(currentUser, OffsetDateTime.now())
                        dataElement.addToSummaryMetadata(summaryMetadata)
                        fileClass.addToSummaryMetadata(dcSummaryMetadata)
                    }
                }
            }
        }

        dataModel
    }

    DataModel importSingleFile(User currentUser, CsvDataModelImporterProviderServiceParameters parameters) {
        log.info('Loading CSV model from {}', parameters.getImportFile().getFileName())
        String fileType = parameters.getImportFile().getFileType()
        log.info('Loading CSV model filetype {}', fileType)

        String modelName = removeFileNameSuffix(removeFileNameSuffix(parameters.importFile.fileName, 'zip'), 'csv')

        List<CsvDataModelImporterProviderServiceParameters> parametersList = []

        if (fileType == "application/zip" || fileType == "application/x-zip-compressed") {
            log.info('Loading Zip File')

            if (!parameters.firstRowIsHeader) {
                parameters.firstRowIsHeader = true
                log.warn('Using first row of CSV files as headers when importing from archive')
            }

            File tempDir = Files.createTempDirectory("temp").toFile()
            log.info('Temp Folder Location {}', tempDir.getAbsolutePath())

            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(parameters.getImportFile().getFileContents()))
            ZipEntry zipEntry = zis.getNextEntry()
            while (zipEntry != null) {
                File newFile = newFile(tempDir, zipEntry)

                if (zipEntry.isDirectory() || newFile.isHidden() || !isCsvFile(zipEntry.getName())) {
                    if (newFile.isHidden()) {
                        log.warn('Skipping [{}], is a hidden file', zipEntry.getName())
                    } else {
                        log.warn('Skipping [{}], is not a CSV file', zipEntry.getName())
                    }
                    zipEntry = zis.getNextEntry()
                    continue
                }

                // If CSV file is in directory, create it
                File parent = newFile.getParentFile()
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                FileOutputStream fos = new FileOutputStream(newFile)
                int len
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()

                parametersList << parameters.clone().tap {it.importFile = new FileParameter(zipEntry.getName(), 'text/csv', newFile.bytes)}

                zipEntry = zis.getNextEntry()
            }
            zis.closeEntry()
            zis.close()
        } else {
            if (!parameters.firstRowIsHeader && !parameters.headers) {
                throw new ApiBadRequestException('CSVDMI01', 'Headers field must be present or first row must be used as header')
            }
            parameters.importFile.fileName = 'CSV fields'
            parametersList << parameters
        }

        importMultipleFiles(currentUser, parametersList, modelName)
    }

    Tuple2<Map<String, ColumnData>, Map<String, Map<String, ColumnData>>> getColumnDefinitions(List<CsvDataModelImporterProviderServiceParameters> csvParametersList,
                                                                                               Boolean multipleFiles) {
        Map<String, ColumnData> allColumns = multipleFiles ? [:] : null
        Map<String, Map<String, ColumnData>> fileColumns = [:]
        csvParametersList.each {parameters ->
            CsvReader csvReader = new CsvReader(parameters.importFile, parameters.firstRowIsHeader, parameters.headers?.split(','))
            fileColumns[parameters.importFile.fileName] = csvReader.getHeaders().collectEntries {header ->
                [header, new ColumnData(header, parameters)]
            }
            if (multipleFiles) {
                csvReader.getHeaders().each {header ->
                    allColumns.putIfAbsent(header, new ColumnData(header, parameters))
                }
            }

            Map<String, String> row = csvReader.readRow()
            int skippedRowCount = 0
            boolean skippedLast = false
            while (row) {
                if (!skippedLast) {
                    fileColumns[parameters.importFile.fileName].each {header, column ->
                        column.addValue(row[header])
                        if (multipleFiles) allColumns[header].addValue(row[header])
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
        }

        new Tuple2(allColumns, fileColumns)
    }

    Map<String, DataType> getPrimitiveDataTypes(List<CsvDataModelImporterProviderServiceParameters> parametersList) {
        Map<String, DataType> types = dataTypeService.defaultListOfDataTypes.collectEntries {
            [it.label, new PrimitiveType(label: it.label, description: it.description)]
        }
        types.Integer = types.Number
        types.BigDecimal = types.Decimal
        types.String = types.Text
        parametersList.any {it.detectTypes} ? types : [String: types.String]
    }

    Map<String, DataType> getPrimitiveDataTypes(CsvDataModelImporterProviderServiceParameters parameters) {
        getPrimitiveDataTypes([parameters])
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName())

        String destDirPath = destinationDir.getCanonicalPath()
        String destFilePath = destFile.getCanonicalPath()

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName())
        }

        return destFile
    }

    private boolean isCsvFile(String filename) {
        filename.endsWithIgnoreCase('.csv')
    }

    private String removeFileNameSuffix(String fileName, String suffix) {
        if (fileName.endsWithIgnoreCase('.' + suffix)) {
            fileName.take(fileName.lastIndexOf('.' + suffix))
        } else {
            fileName
        }
    }
}

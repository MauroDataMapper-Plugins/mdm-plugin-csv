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

import uk.ac.ox.softeng.maurodatamapper.core.container.Folder
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadataService
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataClass
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataElement
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.EnumerationType
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.provider.importer.parameter.CsvDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.test.integration.BaseIntegrationSpec
import uk.ac.ox.softeng.maurodatamapper.util.GormUtils

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.util.BuildSettings
import grails.validation.ValidationException
import groovy.util.logging.Slf4j
import org.junit.Assert
import spock.lang.Shared

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.junit.Assert.assertTrue

@Slf4j
@Integration
@Rollback
class CsvDataModelImporterProviderServiceSpec extends BaseIntegrationSpec {

    CsvDataModelImporterProviderService csvDataModelImporterProviderService
    DataModelService dataModelService
    SummaryMetadataService summaryMetadataService

    @Shared
    Path resourcesPath

    def setupSpec() {
        resourcesPath =
            Paths.get(BuildSettings.BASE_DIR.absolutePath, 'src', 'integration-test', 'resources').toAbsolutePath()
    }

    @Override
    void setupDomainData() {
        folder = new Folder(label: 'catalogue', createdBy: admin.emailAddress)
        checkAndSave(folder)
    }

    def 'Test importing simple.csv'() {
        given:
        setupDomainData()

        def parameters = new CsvDataModelImporterProviderServiceParameters(
            importFile: new FileParameter('simple.csv', 'csv', loadBytes('simple.csv')),
            folderId: folder.id
        )
        when:
        DataModel dataModel = importAndValidateModel('simple', parameters)

        then:
        dataModel
        dataModel.label == 'simple'
        dataModel.dataTypes.size() == 8
        dataModel.dataClasses.size() == 1

        when:
        DataClass dataClass = dataModel.dataClasses.first()

        then:
        dataClass.label == 'CSV fields'
        dataClass.dataElements.size() == 2
        dataClass.dataElements.any { it.label == 'Name' && it.dataType.label == 'Text' }
        dataClass.dataElements.any { it.label == 'Age' && it.dataType.label == 'Number' }
    }

    def 'Test importing Lauth.csv'() {
        given:
        setupDomainData()

        List<String> headers = ['Organisation Code', 'Name', 'National Grouping', 'High Level Health Geography', 'Address Line 1', 'Address Line 2',
                                'Address Line 3', 'Address Line 4', 'Address Line 5', 'Postcode', 'Open Date', 'Close Date', 'Null',
                                'Organisation Sub-Type Code', 'Null',
                                'Null', 'Null', 'Null', 'Null', 'Null', 'Null', 'Amended Record Indicator', 'Null', 'Null', 'Null', 'Null', 'Null']

        def parameters = new CsvDataModelImporterProviderServiceParameters(
            importFile: new FileParameter('Lauth.csv', 'csv', loadBytes('Lauth.csv')),
            folderId: folder.id,
            firstRowIsHeader: false,
            headers: headers.join(',')
        )
        when:
        DataModel dataModel = importAndValidateModel('Lauth', parameters)

        then:
        dataModel
        dataModel.label == 'Lauth'
        dataModel.dataTypes.size() == 13
        dataModel.primitiveTypes.size() == 8
        dataModel.primitiveTypes.size() == 8
        dataModel.enumerationTypes.size() == 5
        dataModel.dataClasses.size() == 1

        when:
        DataClass dataClass = dataModel.dataClasses.first()

        then:
        dataClass.label == 'CSV fields'
        dataClass.dataElements.size() == 27
        headers.eachWithIndex {h, i ->
            String l = h == 'Null' && i > 12 ? "Null($i)" : h
            Assert.assertTrue "$l exists", dataClass.dataElements.any {it.label == l}
        }

        when:
        dataModelService.saveModelWithContent(dataModel)
        DataModel saved = dataModelService.get(dataModel.id)

        then:
        saved.label == 'Lauth'
        saved.dataTypes.size() == 13
        saved.primitiveTypes.size() == 8
        saved.primitiveTypes.size() == 8
        saved.enumerationTypes.size() == 5
        saved.dataClasses.size() == 1
        saved.dataClasses.first().dataElements.size() == 27

        when:
        dataClass = saved.dataClasses.first()
        List<SummaryMetadata> summaryMetadata = summaryMetadataService.findAllByMultiFacetAwareItemId(dataClass.id)

        then:
        dataClass.summaryMetadata.size() == 20
        summaryMetadata.size() == 20
        dataClass.dataElements.each {de ->
            summaryMetadata = summaryMetadataService.findAllByMultiFacetAwareItemId(de.id)
            summaryMetadata.size() == 1
        }

    }

    def 'Test importing Postcode-districts.csv'() {
        given:
        setupDomainData()

        def parameters = new CsvDataModelImporterProviderServiceParameters(
            importFile: new FileParameter('Postcode-districts.csv', 'csv', loadBytes('Postcode-districts.csv')),
            folderId: folder.id
        )
        when:
        DataModel dataModel = importAndValidateModel('Postcode-districts', parameters)

        then:
        dataModel
        dataModel.label == 'Postcode-districts'
        dataModel.dataTypes.size() == 8
        dataModel.dataClasses.size() == 1

        when:
        DataClass dataClass = dataModel.dataClasses.first()

        then:
        dataClass.label == 'CSV fields'
        dataClass.dataElements.size() == 13
        dataClass.dataElements.any { it.label == 'Postcode' && it.dataType.label == 'Text' }
        dataClass.dataElements.any { it.label == 'Latitude' && it.dataType.label == 'Decimal' }
        dataClass.dataElements.any { it.label == 'Longitude' && it.dataType.label == 'Decimal' }
        dataClass.dataElements.any { it.label == 'Easting' && it.dataType.label == 'Number' }
        dataClass.dataElements.any { it.label == 'Northing' && it.dataType.label == 'Number' }
        dataClass.dataElements.any { it.label == 'Grid Reference' && it.dataType.label == 'Text' }
        dataClass.dataElements.any { it.label == 'Town/Area' && it.dataType.label == 'Text' }
        dataClass.dataElements.any { it.label == 'Region' && it.dataType.label == 'Text' }
        dataClass.dataElements.any { it.label == 'Postcodes' && it.dataType.label == 'Number' }
        dataClass.dataElements.any { it.label == 'Active postcodes' && it.dataType.label == 'Number' }
        dataClass.dataElements.any { it.label == 'Population' && it.dataType.label == 'Number' }
        dataClass.dataElements.any { it.label == 'Households' && it.dataType.label == 'Number' }
        dataClass.dataElements.any { it.label == 'Nearby districts' && it.dataType.label == 'Text' }
    }

    def 'Test importing alphabets.zip with type detection and summary metadata'() {
        given:
        setupDomainData()

        def parameters = new CsvDataModelImporterProviderServiceParameters(
            importFile: new FileParameter('alphabets.zip', 'application/zip', loadBytes('alphabets.zip')),
            folderId: folder.id
        )
        when:
        DataModel dataModel = importAndValidateModel('alphabets', parameters)
        dataModel = dataModelService.saveModelWithContent(dataModel)

        then:
        dataModel.dataTypes.size() == 9
        dataModel.dataClasses.size() == 2

        and:
        verifyAlphabetsDataClassesWithTypesAndSummaryMetadata(dataModel)
    }

    def 'Test importing alphabets.zip without type detection or summary metadata'() {
        given:
        setupDomainData()

        def parameters = new CsvDataModelImporterProviderServiceParameters(
            importFile: new FileParameter('alphabets.zip', 'application/zip', loadBytes('alphabets.zip')),
            folderId: folder.id,
            generateSummaryMetadata: false,
            detectEnumerations: false,
            detectTypes: false
        )
        when:
        DataModel dataModel = importAndValidateModel('alphabets', parameters)
        dataModel = dataModelService.saveModelWithContent(dataModel)

        then:
        dataModel.dataTypes.size() == 1
        dataModel.dataClasses.size() == 2

        when:
        DataClass dataClass = dataModel.dataClasses.find {it.label == 'english'}
        List<DataElement> dataElements = dataClass.getDataElements().sort()

        then:
        dataElements.size() == 3
        dataElements[0].label == 'id'
        dataElements[0].dataType.label == 'Text'
        dataElements[0].minMultiplicity == 1
        dataElements[0].maxMultiplicity == 1
        dataElements[1].label == 'english_letter'
        dataElements[1].dataType.label == 'Text'
        dataElements[1].minMultiplicity == 1
        dataElements[1].maxMultiplicity == 1
        dataElements[2].label == 'is_vowel'
        dataElements[2].dataType.label == 'Text'
        dataElements[2].minMultiplicity == 1
        dataElements[2].maxMultiplicity == 1

        dataClass.summaryMetadata.size() == 0

        when:
        dataClass = dataModel.dataClasses.find{it.label == 'greek'}
        dataElements = dataClass.getDataElements().sort()

        then:
        dataElements.size() == 4
        dataElements[0].label == 'id'
        dataElements[0].dataType.label == 'Text'
        dataElements[0].minMultiplicity == 1
        dataElements[0].maxMultiplicity == 1
        dataElements[1].label == 'greek_letter'
        dataElements[1].dataType.label == 'Text'
        dataElements[1].minMultiplicity == 1
        dataElements[1].maxMultiplicity == 1
        dataElements[2].label == 'english_letter'
        dataElements[2].dataType.label == 'Text'
        dataElements[2].minMultiplicity == 0
        dataElements[2].maxMultiplicity == 1
        dataElements[3].label == 'is_vowel'
        dataElements[3].dataType.label == 'Text'
        dataElements[3].minMultiplicity == 1
        dataElements[3].maxMultiplicity == 1

        dataClass.summaryMetadata.size() == 0
    }

    void 'Test importing csv-tree.zip with type detection and summary metadata'() {
        given:
        setupDomainData()

        CsvDataModelImporterProviderServiceParameters parameters = new CsvDataModelImporterProviderServiceParameters(
            importFile: new FileParameter('csv-tree.zip', 'application/zip', loadBytes('csv-tree.zip')),
            folderId: folder.id
        )
        when:
        DataModel dataModel = importAndValidateModel('csv-tree', parameters)
        dataModel = dataModelService.saveModelWithContent(dataModel)

        then:
        dataModel.dataTypes.size() == 9
        dataModel.dataClasses.size() == 5
        dataModel.childDataClasses.size() == 1
        DataClass mainClass = dataModel.childDataClasses.first()
        mainClass.label == 'csv-tree'
        mainClass.dataClasses.first().label == 'simple'
        mainClass.dataClasses.last().label == 'alphabets'

        and:
        DataClass simpleClass = mainClass.dataClasses.first()
        simpleClass.dataElements.size() == 2
        simpleClass.dataElements.any {it.label == 'Name' && it.dataType.label == 'Text'}
        simpleClass.dataElements.any {it.label == 'Age' && it.dataType.label == 'Number'}

        and:
        verifyAlphabetsDataClassesWithTypesAndSummaryMetadata(dataModel)
    }

    byte[] loadBytes(String filename) {
        Path testFilePath = resourcesPath.resolve("${filename}").toAbsolutePath()
        assert Files.exists(testFilePath)
        Files.readAllBytes(testFilePath)
    }

    private DataModel importAndValidateModel(String entryId, CsvDataModelImporterProviderServiceParameters parameters) {
        DataModel dataModel = csvDataModelImporterProviderService.importModel(admin, parameters)
        assert dataModel
        assert dataModel.label == entryId
        dataModel.folder = folder
        dataModelService.validate(dataModel)
        if (dataModel.errors.hasErrors()) {
            GormUtils.outputDomainErrors(messageSource, dataModel)
            throw new ValidationException("Domain object is not valid. Has ${dataModel.errors.errorCount} errors", dataModel.errors)
        }
        dataModel
    }

    private void verifyAlphabetsDataClassesWithTypesAndSummaryMetadata(DataModel dataModel) {
        DataClass dataClass = dataModel.dataClasses.find {it.label == 'english'}
        List<DataElement> dataElements = dataClass.getDataElements().sort()

        assertTrue dataElements.size() == 3
        assertTrue dataElements[0].label == 'id'
        assertTrue dataElements[0].dataType.label == 'Number'
        assertTrue dataElements[0].minMultiplicity == 1
        assertTrue dataElements[0].maxMultiplicity == 1
        assertTrue dataElements[1].label == 'english_letter'
        assertTrue dataElements[1].dataType.label == 'Text'
        assertTrue dataElements[1].minMultiplicity == 1
        assertTrue dataElements[1].maxMultiplicity == 1
        assertTrue dataElements[2].label == 'is_vowel'
        assertTrue dataElements[2].dataType.label == 'is_vowel'
        assertTrue dataElements[2].minMultiplicity == 1
        assertTrue dataElements[2].maxMultiplicity == 1

        assertTrue dataClass.summaryMetadata.size() == 2
        assertTrue dataClass.summaryMetadata.any {
            it.label == 'id' &&
            it.summaryMetadataType.name() == 'MAP' &&
            it.summaryMetadataReports.first().reportValue ==
            '{"26 - 28":10,"10 - 12":10,"20 - 22":10,"12 - 14":10,"14 - 16":10,"18 - 20":10,"6 - 8":10,"22 - 24":10,"16 - 18":10,"0 - 2":10,"2 - 4":10,"4 - 6":10,"8 - ' +
            '10":10,"24 - 26":10}'
        }
        assertTrue dataElements[0].summaryMetadata.size() == 1
        assertTrue dataElements[0].summaryMetadata.any {
            it.label == 'id' &&
            it.summaryMetadataType.name() == 'MAP' &&
            it.summaryMetadataReports.first().reportValue ==
            '{"26 - 28":10,"10 - 12":10,"20 - 22":10,"12 - 14":10,"14 - 16":10,"18 - 20":10,"6 - 8":10,"22 - 24":10,"16 - 18":10,"0 - 2":10,"2 - 4":10,"4 - 6":10,"8 - 10":10,"24 - 26":10}'
        }
        assertTrue dataClass.summaryMetadata.any {
            it.label == 'is_vowel' && it.summaryMetadataType.name() == 'MAP' && it.summaryMetadataReports.first().reportValue == '{"False":21,"True":10}'
        }
        assertTrue dataElements[2].summaryMetadata.size() == 1
        assertTrue dataElements[2].summaryMetadata.any {
            it.label == 'is_vowel' && it.summaryMetadataType.name() == 'MAP' && it.summaryMetadataReports.first().reportValue == '{"False":21,"True":10}'
        }

        dataClass = dataModel.dataClasses.find{it.label == 'greek'}
        dataElements = dataClass.getDataElements().sort()

        assertTrue dataElements.size() == 4
        assertTrue dataElements[0].label == 'id'
        assertTrue dataElements[0].dataType.label == 'Number'
        assertTrue dataElements[0].minMultiplicity == 1
        assertTrue dataElements[0].maxMultiplicity == 1
        assertTrue dataElements[1].label == 'greek_letter'
        assertTrue dataElements[1].dataType.label == 'Text'
        assertTrue dataElements[1].minMultiplicity == 1
        assertTrue dataElements[1].maxMultiplicity == 1
        assertTrue dataElements[2].label == 'english_letter'
        assertTrue dataElements[2].dataType.label == 'Text'
        assertTrue dataElements[2].minMultiplicity == 0
        assertTrue dataElements[2].maxMultiplicity == 1
        assertTrue dataElements[3].label == 'is_vowel'
        assertTrue dataElements[3].dataType.label == 'is_vowel'
        assertTrue dataElements[3].dataType instanceof EnumerationType
        assertTrue dataElements[3].dataType.enumerationValues.size() == 2
        assertTrue dataElements[3].dataType.findEnumerationValueByKey('True') as boolean
        assertTrue dataElements[3].dataType.findEnumerationValueByKey('False') as boolean
        assertTrue dataElements[3].minMultiplicity == 1
        assertTrue dataElements[3].maxMultiplicity == 1

        assertTrue dataClass.summaryMetadata.size() == 2
        assertTrue dataClass.summaryMetadata.any {
            it.label == 'id' &&
            it.summaryMetadataType.name() == 'MAP' &&
            it.summaryMetadataReports.first().reportValue ==
            '{"10 - 12":10,"20 - 22":10,"12 - 14":10,"14 - 16":10,"18 - 20":10,"6 - 8":10,"22 - 24":10,"16 - 18":10,"0 - 2":10,"2 - 4":10,"4 - 6":10,"8 - 10":10,"24 - 26":10}'
        }
        assertTrue dataElements[0].summaryMetadata.size() == 1
        assertTrue dataElements[0].summaryMetadata.any {
            it.label == 'id' &&
            it.summaryMetadataType.name() == 'MAP' &&
            it.summaryMetadataReports.first().reportValue ==
            '{"10 - 12":10,"20 - 22":10,"12 - 14":10,"14 - 16":10,"18 - 20":10,"6 - 8":10,"22 - 24":10,"16 - 18":10,"0 - 2":10,"2 - 4":10,"4 - 6":10,"8 - 10":10,"24 - 26":10}'
        }
        assertTrue dataClass.summaryMetadata.any {
            it.label == 'is_vowel' && it.summaryMetadataType.name() == 'MAP' && it.summaryMetadataReports.first().reportValue == '{"False":17,"True":10}'
        }
        assertTrue dataElements[3].summaryMetadata.size() == 1
        assertTrue dataElements[3].summaryMetadata.any {
            it.label == 'is_vowel' && it.summaryMetadataType.name() == 'MAP' && it.summaryMetadataReports.first().reportValue == '{"False":17,"True":10}'
        }
    }
}
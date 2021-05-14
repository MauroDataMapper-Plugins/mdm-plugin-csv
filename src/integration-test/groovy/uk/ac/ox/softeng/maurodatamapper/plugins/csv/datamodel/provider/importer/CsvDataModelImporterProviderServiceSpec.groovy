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

import uk.ac.ox.softeng.maurodatamapper.core.container.Folder
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadataService
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataClass
import uk.ac.ox.softeng.maurodatamapper.plugins.csv.datamodel.provider.importer.parameter.CsvDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.test.integration.BaseIntegrationSpec
import uk.ac.ox.softeng.maurodatamapper.util.GormUtils

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import grails.util.BuildSettings
import grails.validation.ValidationException
import groovy.util.logging.Slf4j
import org.junit.Assert
import spock.lang.Shared

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
@Integration
@Rollback
class CsvDataModelImporterProviderServiceSpec extends BaseIntegrationSpec {

    CsvDataModelImporterProviderService csvDataModelImporterProviderService
    DataModelService dataModelService
    SummaryMetadataService summaryMetadataService

    @Shared
    Path resourcesPath

    @OnceBefore
    void setupServerClient() {
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
}
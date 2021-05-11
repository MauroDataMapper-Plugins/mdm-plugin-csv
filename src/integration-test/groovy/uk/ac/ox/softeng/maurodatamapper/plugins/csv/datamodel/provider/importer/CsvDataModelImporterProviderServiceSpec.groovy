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
package uk.ac.ox.softeng.maurodatamapper.plugins.csv

import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVReaderHeaderAware
import com.opencsv.CSVReaderHeaderAwareBuilder
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataClass
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataElement
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.DataType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.EnumerationType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.PrimitiveType

class CSVImporter {

    static char[] separators = [',', '|', '\t', ';'] as char[]


    static void importSingleFile(DataModel dataModel, byte[] fileContents, CsvDataModelImporterProviderServiceParameters parameters) {

        parameters.setDefaults()
        Map<String, ColumnSpec> columns = getColumnDefinitions(fileContents, parameters)
        if(columns) {
            /* if (parameters.testOnly) {
                printColumnDetails(columns)
            } else { */
                createAndUploadDataModel(dataModel, columns, parameters)
            //}
        }

    }

    static Map<String, ColumnSpec> getColumnDefinitions(byte[] fileContents, CsvDataModelImporterProviderServiceParameters parameters) {
        CSVReader csvReader = getCSVReader(fileContents, parameters.importFile.fileName)
        Map<String, String> valuesMap = csvReader.readMap()
        Map<String, ColumnSpec> columns = [:]
        valuesMap.keySet().each { header ->
            columns[header] = new ColumnSpec(header, parameters)
        }
        int noSkipped = 0
        boolean skippedLast = false
        while (valuesMap) {
            if(!skippedLast) {
                columns.keySet().each { header ->
                    columns[header].addValue(valuesMap[header])
                }
            }
            try {
                valuesMap = csvReader.readMap()
                skippedLast = false
            } catch(Exception e) {
                System.err.println(e.getMessage())
                System.err.println(valuesMap)
                skippedLast = true
                noSkipped++
            }
        }
        if(noSkipped > 0) {
            System.err.println("Skipped rows: ${noSkipped}")
        }

        return columns
    }

    static CSVReaderHeaderAware getCSVReader(byte[] fileContents, String filename) {
        CSVReader basicCSVReader
        int maxColumns = 0
        Character bestSeparator = null
        separators.each {sep ->
            CSVParser parser =
                    new CSVParserBuilder().withSeparator(sep).build()
            basicCSVReader = new CSVReaderBuilder(new InputStreamReader(new ByteArrayInputStream(fileContents)))
                    .withCSVParser(parser)
                    .build()
            try {
                int thisSize = basicCSVReader.readNext().length
                if(thisSize > maxColumns) {
                    bestSeparator = sep
                    maxColumns = thisSize
                }
            } catch(Exception e) {
                return
            }
        }
        if(!bestSeparator) {
            bestSeparator = separators[0]
        }
        if(maxColumns == 0) {
            System.err.println("Cannot parse file: ${filename}")
            return null
        }
        CSVParser parser =
                new CSVParserBuilder().withSeparator(bestSeparator.charValue()).build()
        CSVReaderHeaderAware csvReader = ((CSVReaderHeaderAwareBuilder) new CSVReaderHeaderAwareBuilder(new InputStreamReader(new ByteArrayInputStream(fileContents)))
                .withCSVParser(parser))
                .build()
        return csvReader
    }

    static void createAndUploadDataModel(DataModel dataModel,
                                         Map<String, ColumnSpec> columns,
                                         //String folderPathAddition,
                                         CsvDataModelImporterProviderServiceParameters parameters) {
        /*String folderPath = parameters.folderPath
        if(folderPathAddition && folderPathAddition != "" ) {
            folderPath += "." + folderPathAddition
        }
        System.err.println(folderPath)
        UUID parentFolder = catalogueClient.findOrCreateFolderByPath(folderPath)
        String filename = FilenameUtils.getBaseName(file.toString())
        String dataModelName = filename
        if(options.modelName) {
            dataModelName = options.modelName
        }
        DataModel dataModel = new DataModel(type: DataModelType.DATA_ASSET, label: dataModelName)

         */
        Map<String, DataType> dataTypes = getPrimitiveDataTypes(parameters)
        dataModel.addToDataTypes(dataTypes.values())
        DataClass topLevelClass = new DataClass(label: 'CSV fields')
        dataModel.addToDataClasses(topLevelClass)
        columns.keySet().each {header ->
            DataType dataType = columns[header].getDataType(dataTypes)
            if(dataType instanceof EnumerationType) {
                dataModel.addToDataTypes(dataType)
            }
            DataElement dataElement = new DataElement(
                    label: header,
                    dataType: dataType,
                    minMultiplicity: columns[header].getMinMultiplicity(),
                    maxMultiplicity: columns[header].getMaxMultiplicity())
            topLevelClass.addToDataElements(dataElement)
            SummaryMetadata summaryMetadata = columns[dataElement.label].calculateSummaryMetadata()
            if (summaryMetadata) {
                if(!summaryMetadata.label) {
                    summaryMetadata.label = dataElement.label
                }
                System.err.println("Summary metadata label: " + summaryMetadata.label)
                dataElement.addToSummaryMetadata(summaryMetadata)
                topLevelClass.addToSummaryMetadata(summaryMetadata)
            }
        }
    }

    static Map<String, DataType> getPrimitiveDataTypes(CsvDataModelImporterProviderServiceParameters parameters) {
        if(parameters.detectTypes) {
            Map<String, DataType> types = [:]
            ColumnSpec.dataTypes.each { typeName ->
                types[typeName] = new PrimitiveType(label: typeName)
            }
            types["BigDecimal"] = types["Decimal"]
            return types
        } else {
            return ["String": new PrimitiveType(label: 'String')]
        }
    }

}

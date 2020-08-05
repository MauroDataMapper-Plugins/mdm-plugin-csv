package uk.ac.ox.softeng.maurodatamapper.plugins.csv.summarymetadata

import groovy.json.JsonBuilder
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadata
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.SummaryMetadataType
import uk.ac.ox.softeng.maurodatamapper.datamodel.facet.summarymetadata.SummaryMetadataReport

import java.time.OffsetDateTime

class SummaryMetadataHelper {


    static SummaryMetadata createSummaryMetadataFromMap(String headerName, String description, Map<String, Integer> valueDistribution ) {
        SummaryMetadata summaryMetadata = new SummaryMetadata(
            name: headerName,
            description: description,
            summaryMetadataType: SummaryMetadataType.MAP
        )
        SummaryMetadataReport smr = new SummaryMetadataReport(
            reportDate: OffsetDateTime.now(),
            reportValue: new JsonBuilder(valueDistribution).toString()
        )
        summaryMetadata.addToSummaryMetadataReports(smr)
        return summaryMetadata
    }
}

package org.recap.service.accession.resolver;

import org.marc4j.marc.Record;
import org.recap.RecapCommonConstants;
import org.recap.model.accession.AccessionRequest;
import org.recap.model.accession.AccessionResponse;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.jpa.ReportDataEntity;
import org.recap.service.accession.BulkAccessionService;
import org.recap.service.partnerservice.PrincetonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sheiks on 26/05/17.
 */
@Service
public class PULBibDataResolver extends BibDataResolver {

    @Autowired
    private PrincetonService princetonService;

    public PULBibDataResolver(BulkAccessionService bulkAccessionService) {
        super(bulkAccessionService);
    }

    @Override
    public boolean isInterested(String institution) {
        return RecapCommonConstants.PRINCETON.equals(institution);
    }

    @Override
    public String getBibData(String itemBarcode, String customerCode) {
        return princetonService.getBibData(itemBarcode);
    }

    @Override
    public Object unmarshal(String bibDataResponse) {
        return marcRecordConvert(bibDataResponse);
    }

    @Override
    public ItemEntity getItemEntityFromRecord(Object object, Integer owningInstitutionId) {
        return getItemEntityFormMarcRecord((List<Record>) object,owningInstitutionId);
    }

    @Override
    public String processXml(Set<AccessionResponse> accessionResponses, Object object, List<Map<String, String>> responseMapList, String owningInstitution, List<ReportDataEntity> reportDataEntityList, AccessionRequest accessionRequest) throws Exception {
        return bulkAccessionService.processAccessionForMarcXml(accessionResponses, object,
                responseMapList, owningInstitution, reportDataEntityList, accessionRequest);
    }
}

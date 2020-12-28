package org.recap.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.recap.RecapCommonConstants;
import org.recap.RecapConstants;
import org.recap.model.ILSConfigProperties;
import org.recap.model.accession.AccessionRequest;
import org.recap.model.accession.AccessionResponse;
import org.recap.model.jpa.*;
import org.recap.model.request.ItemCheckInRequest;
import org.recap.model.request.ItemCheckinResponse;
import org.recap.repository.jpa.*;
import org.recap.service.accession.AccessionInterface;
import org.recap.service.accession.AccessionResolverFactory;
import org.recap.spring.SwaggerAPIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Created by sheiks on 26/05/17.
 */
@Service
@EnableAsync
public class AccessionProcessService {

    private static final Logger logger = LoggerFactory.getLogger(AccessionProcessService.class);

    @Autowired
    private ItemDetailsRepository itemDetailsRepository;

    @Autowired
    private ReportDetailRepository reportDetailRepository;

    @Autowired
    private ItemBarcodeHistoryDetailsRepository itemBarcodeHistoryDetailsRepository;

    @Autowired
    private InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    AccessionUtil accessionUtil;

    @Autowired
    PropertyUtil propertyUtil;

    @Autowired
    AccessionResolverFactory accessionResolverFactory;

    private Map<String, Integer> institutionEntityMap;

    @Value("${scsb.gateway.url}")
    private String scsbUrl;

    public Object processRecords(Set<AccessionResponse> accessionResponses, List<Map<String, String>> responseMaps,
                                 AccessionRequest accessionRequest, List<ReportDataEntity> reportDataEntitys,
                                 String owningInstitution, boolean writeToReport,ImsLocationEntity imsLocationEntity) {
        String customerCode = accessionRequest.getCustomerCode();
        String itemBarcode = accessionRequest.getItemBarcode();
        // Check item availability
        List<ItemEntity> itemEntityList = getItemEntityList(itemBarcode, customerCode);
        boolean itemExists = checkItemBarcodeAlreadyExist(itemEntityList);
        if (itemExists) { // If available check deaccessioned item or not

            boolean isDeaccessionedItem = isItemDeaccessioned(itemEntityList);
            if (isDeaccessionedItem) { // If deacccessioned item make it available
                String response = accessionUtil.reAccessionItem(itemEntityList);
                if (response.equals(RecapCommonConstants.SUCCESS)) {
                    response = accessionUtil.indexReaccessionedItem(itemEntityList);
                    accessionUtil.saveItemChangeLogEntity(RecapConstants.REACCESSION, RecapConstants.ITEM_ISDELETED_TRUE_TO_FALSE, itemEntityList);
                    reAccessionedCheckin(itemEntityList);
                }
                accessionUtil.setAccessionResponse(accessionResponses, itemBarcode, response);
                reportDataEntitys.addAll(accessionUtil.createReportDataEntityList(accessionRequest, response));
            } else { // else, error response
                String itemAreadyAccessionedMessage;
                if (CollectionUtils.isNotEmpty(itemEntityList.get(0).getBibliographicEntities())) {
                    String itemAreadyAccessionedOwnInstBibId = itemEntityList.get(0).getBibliographicEntities() != null ? itemEntityList.get(0).getBibliographicEntities().get(0).getOwningInstitutionBibId() : " ";
                    String itemAreadyAccessionedOwnInstHoldingId = itemEntityList.get(0).getHoldingsEntities() != null ? itemEntityList.get(0).getHoldingsEntities().get(0).getOwningInstitutionHoldingsId() : " ";
                    itemAreadyAccessionedMessage = RecapConstants.ITEM_ALREADY_ACCESSIONED + RecapConstants.OWN_INST_BIB_ID + itemAreadyAccessionedOwnInstBibId + RecapConstants.OWN_INST_HOLDING_ID + itemAreadyAccessionedOwnInstHoldingId + RecapConstants.OWN_INST_ITEM_ID + itemEntityList.get(0).getOwningInstitutionItemId();
                } else {
                    itemAreadyAccessionedMessage = RecapConstants.ITEM_ALREADY_ACCESSIONED;
                }
                accessionUtil.setAccessionResponse(accessionResponses, itemBarcode, itemAreadyAccessionedMessage);
                reportDataEntitys.addAll(accessionUtil.createReportDataEntityList(accessionRequest, itemAreadyAccessionedMessage));
            }

        } else { // If not available

            ILSConfigProperties ilsConfigProperties = propertyUtil.getILSConfigProperties(owningInstitution);
            AccessionInterface formatResolver = accessionResolverFactory.getFormatResolver(ilsConfigProperties.getBibDataFormat());

            // Call ILS - Bib Data API
            String bibData = getBibData(accessionResponses, accessionRequest, reportDataEntitys, owningInstitution, customerCode, itemBarcode, formatResolver,imsLocationEntity);
            if (bibData != null) {
                try { // Check whether owningInsitutionItemId attached with another barcode.
                    Object unmarshalObject = formatResolver.unmarshal(bibData);
                    Integer owningInstitutionId = getInstitutionIdCodeMap().get(owningInstitution);
                    ItemEntity itemEntity = formatResolver.getItemEntityFromRecord(unmarshalObject, owningInstitutionId);
                    boolean accessionProcess = formatResolver.isAccessionProcess(itemEntity, owningInstitution);
                    // Process XML Record
                    if (accessionProcess) { // Accession process
                        formatResolver.processXml(accessionResponses, unmarshalObject, responseMaps, owningInstitution, reportDataEntitys, accessionRequest,imsLocationEntity);
                        callCheckin(accessionRequest.getItemBarcode(), owningInstitution);
                    } else {  // If attached
                        String oldBarcode = itemEntity.getBarcode();
                        // update item record with new barcode. Accession Process
                        formatResolver.processXml(accessionResponses, unmarshalObject, responseMaps, owningInstitution, reportDataEntitys, accessionRequest,imsLocationEntity);
                        callCheckin(accessionRequest.getItemBarcode(), owningInstitution);
                        // Move item record information to history table
                        ItemBarcodeHistoryEntity itemBarcodeHistoryEntity = prepareBarcodeHistoryEntity(itemEntity, itemBarcode, oldBarcode);
                        itemBarcodeHistoryDetailsRepository.save(itemBarcodeHistoryEntity);
                    }
                } catch (Exception e) {
                    if (writeToReport) {
                        processException(accessionResponses, accessionRequest, reportDataEntitys, owningInstitution,imsLocationEntity ,e);
                    } else {
                        return accessionRequest;
                    }
                }
            }
            generateAccessionSummaryReport(responseMaps, owningInstitution);
        }

        // Save report
        accessionUtil.saveReportEntity(owningInstitution, reportDataEntitys);

        return accessionResponses;
    }

    public String getBibData(Set<AccessionResponse> accessionResponses, AccessionRequest accessionRequest, List<ReportDataEntity> reportDataEntitys, String owningInstitution, String customerCode, String itemBarcode, AccessionInterface formatResolver,ImsLocationEntity imsLocationEntity) {
        String bibData = null;
        StopWatch individualStopWatch = new StopWatch();
        individualStopWatch.start();
        try {
            // Calling ILS - Bib Data API
            bibData = formatResolver.getBibData(itemBarcode, customerCode, owningInstitution);
        } catch (Exception e) { // Process dummy record if record not found in ILS
            processException(accessionResponses, accessionRequest, reportDataEntitys, owningInstitution,imsLocationEntity, e);
        } finally {
            individualStopWatch.stop();
            logger.info("Time taken to get bib data from {} ILS : {}", owningInstitution, individualStopWatch.getTotalTimeSeconds());
        }
        return bibData;
    }


    /**
     * Get item entity list for the given item barcode and customer code.
     *
     * @param itemBarcode  the item barcode
     * @param customerCode the customer code
     * @return the list
     */
    public List<ItemEntity> getItemEntityList(String itemBarcode, String customerCode) {
        return itemDetailsRepository.findByBarcodeAndCustomerCode(itemBarcode, customerCode);
    }

    /**
     * This method checks item barcode already exist for the given item list.
     *
     * @param itemEntityList the item entity list
     * @return the boolean
     */
    public boolean checkItemBarcodeAlreadyExist(List<ItemEntity> itemEntityList) {
        boolean itemExists = false;
        if (itemEntityList != null && !itemEntityList.isEmpty()) {
            itemExists = true;
        }
        return itemExists;
    }

    /**
     * This method checks is item deaccessioned for the given item list.
     *
     * @param itemEntityList the item entity list
     * @return the boolean
     */
    public boolean isItemDeaccessioned(List<ItemEntity> itemEntityList) {
        boolean itemDeleted = false;
        if (itemEntityList != null && !itemEntityList.isEmpty()) {
            for (ItemEntity itemEntity : itemEntityList) {
                if(itemEntity.isDeleted()) {
                    return itemEntity.isDeleted();
                }
            }
        }
        return itemDeleted;
    }

    /**
     * This method is used to generate AccessionSummary Report
     * <p>
     * It saves the data in report_t and report_data_t
     *
     * @param responseMapList   the response map list
     * @param owningInstitution the owning institution
     */
    public void generateAccessionSummaryReport(List<Map<String, String>> responseMapList, String owningInstitution) {
        int successBibCount = 0;
        int successItemCount = 0;
        int failedBibCount = 0;
        int failedItemCount = 0;
        int exitsBibCount = 0;
        String reasonForFailureBib = "";
        String reasonForFailureItem = "";

        for (Map<String, String> responseMap : responseMapList) {
            successBibCount = successBibCount + (responseMap.get(RecapCommonConstants.SUCCESS_BIB_COUNT) != null ? Integer.parseInt(responseMap.get(RecapCommonConstants.SUCCESS_BIB_COUNT)) : 0);
            failedBibCount = failedBibCount + (responseMap.get(RecapCommonConstants.FAILED_BIB_COUNT) != null ? Integer.parseInt(responseMap.get(RecapCommonConstants.FAILED_BIB_COUNT)) : 0);
            if (failedBibCount == 0) {
                if (StringUtils.isEmpty(responseMap.get(RecapCommonConstants.REASON_FOR_ITEM_FAILURE))) {
                    successItemCount = 1;
                } else {
                    failedItemCount = 1;
                }
            }
            exitsBibCount = exitsBibCount + (responseMap.get(RecapCommonConstants.EXIST_BIB_COUNT) != null ? Integer.parseInt(responseMap.get(RecapCommonConstants.EXIST_BIB_COUNT)) : 0);

            if (!StringUtils.isEmpty(responseMap.get(RecapCommonConstants.REASON_FOR_BIB_FAILURE)) && !reasonForFailureBib.contains(responseMap.get(RecapCommonConstants.REASON_FOR_BIB_FAILURE))) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(responseMap.get(RecapCommonConstants.REASON_FOR_BIB_FAILURE));
                stringBuilder.append(",");
                stringBuilder.append(reasonForFailureBib);
                reasonForFailureBib = stringBuilder.toString();
            }
            if ((!StringUtils.isEmpty(responseMap.get(RecapCommonConstants.REASON_FOR_ITEM_FAILURE))) && StringUtils.isEmpty(reasonForFailureBib) &&
                    !reasonForFailureItem.contains(responseMap.get(RecapCommonConstants.REASON_FOR_ITEM_FAILURE))) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(responseMap.get(RecapCommonConstants.REASON_FOR_ITEM_FAILURE));
                stringBuilder.append(",");
                stringBuilder.append(reasonForFailureItem);
                reasonForFailureItem = stringBuilder.toString();
            }
        }

        List<ReportEntity> reportEntityList = new ArrayList<>();
        List<ReportDataEntity> reportDataEntities = new ArrayList<>();
        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(RecapCommonConstants.ACCESSION_REPORT);
        reportEntity.setType(RecapCommonConstants.ACCESSION_SUMMARY_REPORT);
        reportEntity.setCreatedDate(new Date());
        reportEntity.setInstitutionName(owningInstitution);

        ReportDataEntity successBibCountReportDataEntity = new ReportDataEntity();
        successBibCountReportDataEntity.setHeaderName(RecapCommonConstants.BIB_SUCCESS_COUNT);
        successBibCountReportDataEntity.setHeaderValue(String.valueOf(successBibCount));
        reportDataEntities.add(successBibCountReportDataEntity);

        ReportDataEntity successItemCountReportDataEntity = new ReportDataEntity();
        successItemCountReportDataEntity.setHeaderName(RecapCommonConstants.ITEM_SUCCESS_COUNT);
        successItemCountReportDataEntity.setHeaderValue(String.valueOf(successItemCount));
        reportDataEntities.add(successItemCountReportDataEntity);

        ReportDataEntity existsBibCountReportDataEntity = new ReportDataEntity();
        existsBibCountReportDataEntity.setHeaderName(RecapCommonConstants.NUMBER_OF_BIB_MATCHES);
        existsBibCountReportDataEntity.setHeaderValue(String.valueOf(exitsBibCount));
        reportDataEntities.add(existsBibCountReportDataEntity);

        ReportDataEntity failedBibCountReportDataEntity = new ReportDataEntity();
        failedBibCountReportDataEntity.setHeaderName(RecapCommonConstants.BIB_FAILURE_COUNT);
        failedBibCountReportDataEntity.setHeaderValue(String.valueOf(failedBibCount));
        reportDataEntities.add(failedBibCountReportDataEntity);

        ReportDataEntity failedItemCountReportDataEntity = new ReportDataEntity();
        failedItemCountReportDataEntity.setHeaderName(RecapCommonConstants.ITEM_FAILURE_COUNT);
        failedItemCountReportDataEntity.setHeaderValue(String.valueOf(failedItemCount));
        reportDataEntities.add(failedItemCountReportDataEntity);

        ReportDataEntity reasonForBibFailureReportDataEntity = new ReportDataEntity();
        reasonForBibFailureReportDataEntity.setHeaderName(RecapConstants.FAILURE_BIB_REASON);
        if (reasonForFailureBib.startsWith("\n")) {
            reasonForFailureBib = reasonForFailureBib.substring(1, reasonForFailureBib.length() - 1);
        }
        reasonForFailureBib = reasonForFailureBib.replaceAll("\n", ",");
        reasonForFailureBib = reasonForFailureBib.replaceAll(",$", "");
        reasonForBibFailureReportDataEntity.setHeaderValue(reasonForFailureBib);
        reportDataEntities.add(reasonForBibFailureReportDataEntity);

        ReportDataEntity reasonForItemFailureReportDataEntity = new ReportDataEntity();
        reasonForItemFailureReportDataEntity.setHeaderName(RecapConstants.FAILURE_ITEM_REASON);
        if (reasonForFailureItem.startsWith("\n")) {
            reasonForFailureItem = reasonForFailureItem.substring(1, reasonForFailureItem.length() - 1);
        }
        reasonForFailureItem = reasonForFailureItem.replaceAll("\n", ",");
        reasonForFailureItem = reasonForFailureItem.replaceAll(",$", "");
        reasonForItemFailureReportDataEntity.setHeaderValue(reasonForFailureItem);
        reportDataEntities.add(reasonForItemFailureReportDataEntity);

        reportEntity.setReportDataEntities(reportDataEntities);
        reportEntityList.add(reportEntity);
        reportDetailRepository.saveAll(reportEntityList);
    }

    public void processException(Set<AccessionResponse> accessionResponsesList, AccessionRequest accessionRequest,
                                 List<ReportDataEntity> reportDataEntityList, String owningInstitution,ImsLocationEntity imsLocationEntity ,Exception ex) {
        String response = ex.getMessage();
        if (StringUtils.contains(response, RecapConstants.ITEM_BARCODE_NOT_FOUND)) {
            logger.error(RecapCommonConstants.LOG_ERROR, response);
        } else if (StringUtils.contains(response, RecapConstants.MARC_FORMAT_PARSER_ERROR)) {
            logger.error(RecapCommonConstants.LOG_ERROR, response);
            response = RecapConstants.INVALID_MARC_XML_ERROR_MSG;
            logger.error(RecapConstants.EXCEPTION, ex);
        } else {
            response = RecapConstants.EXCEPTION + response;
            logger.error(RecapConstants.EXCEPTION, ex);
        }
        //Create dummy record
        response = accessionUtil.createDummyRecordIfAny(response, owningInstitution, reportDataEntityList, accessionRequest,imsLocationEntity);
        accessionUtil.setAccessionResponse(accessionResponsesList, accessionRequest.getItemBarcode(), response);
        reportDataEntityList.addAll(accessionUtil.createReportDataEntityList(accessionRequest, response));
    }


    public List<AccessionRequest> removeDuplicateRecord(List<AccessionRequest> trimmedAccessionRequests) {
        Set<AccessionRequest> accessionRequests = new HashSet<>(trimmedAccessionRequests);
        return new ArrayList<>(accessionRequests);
    }

    @Async
    public void callCheckin(String itemBarcode, String owningInstitutionId) {
        ItemCheckInRequest itemRequestInfo = new ItemCheckInRequest();
        RestTemplate restTemplate = new RestTemplate();
        try {
            itemRequestInfo.setItemBarcodes(Collections.singletonList(itemBarcode));
            itemRequestInfo.setItemOwningInstitution(owningInstitutionId);
            ILSConfigProperties ilsConfigProperties = propertyUtil.getILSConfigProperties(owningInstitutionId);
            if ("REST".equalsIgnoreCase(ilsConfigProperties.getIlsRefileEndpointProtocol())) {
                HttpEntity request = new HttpEntity<>(getHttpHeadersAuth());
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(scsbUrl + RecapConstants.SERVICEPATH.REFILE_ITEM_IN_ILS);
                builder.queryParam(RecapCommonConstants.ITEMBARCODE, itemBarcode);
                builder.queryParam(RecapConstants.OWNING_INST, owningInstitutionId);
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                ResponseEntity<ItemRefileResponse> responseEntity = restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.POST, request, ItemRefileResponse.class);
                stopWatch.stop();
                logger.info("Time taken to refile item barcode {} in NYPL : {}", itemBarcode, stopWatch.getTotalTimeSeconds());
                logger.info("Refile response for item barcode {} : {}", itemBarcode, null != responseEntity.getBody() ? responseEntity.getBody().getScreenMessage() : null);
            } else {
                HttpEntity request = new HttpEntity<>(itemRequestInfo, getHttpHeadersAuth());
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                ResponseEntity<ItemCheckinResponse> responseEntity = restTemplate.exchange(scsbUrl + RecapConstants.SERVICEPATH.CHECKIN_ITEM, HttpMethod.POST, request, ItemCheckinResponse.class);
                stopWatch.stop();
                logger.info("Time taken to checkin item barcode {} in {} : {}", itemBarcode, owningInstitutionId, stopWatch.getTotalTimeSeconds());
                logger.info("Checkin response for item barcode {} : {}", itemBarcode, null != responseEntity.getBody() ? responseEntity.getBody().getScreenMessage() : null);
            }
        } catch (RestClientException ex) {
            logger.error(RecapConstants.EXCEPTION, ex);
        } catch (Exception ex) {
            logger.error(RecapConstants.EXCEPTION, ex);
        }
    }

    @Async
    void reAccessionedCheckin(List<ItemEntity> itemEntityList) {
        if (itemEntityList != null && !itemEntityList.isEmpty()) {
            for (ItemEntity itemEntity : itemEntityList) {
                callCheckin(itemEntity.getBarcode(), itemEntity.getInstitutionEntity().getInstitutionCode());
            }
        }
    }

    private HttpHeaders getHttpHeadersAuth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(RecapCommonConstants.API_KEY, SwaggerAPIProvider.getInstance().getSwaggerApiKey());
        return headers;
    }

    public ItemBarcodeHistoryEntity prepareBarcodeHistoryEntity(ItemEntity itemEntity, String newBarcode, String oldBarcode) {
        ItemBarcodeHistoryEntity itemBarcodeHistoryEntity = new ItemBarcodeHistoryEntity();
        itemBarcodeHistoryEntity.setOwningingInstitution(itemEntity.getInstitutionEntity().getInstitutionCode());
        itemBarcodeHistoryEntity.setOwningingInstitutionItemId(itemEntity.getOwningInstitutionItemId());
        itemBarcodeHistoryEntity.setOldBarcode(oldBarcode);
        itemBarcodeHistoryEntity.setNewBarcode(newBarcode);
        itemBarcodeHistoryEntity.setCreatedDate(new Date());
        return itemBarcodeHistoryEntity;
    }

    /**
     * Gets institution id and institution code from db and puts it into a map where status id as key and status code as value.
     *
     * @return the institution entity map
     */
    public synchronized Map<String,Integer> getInstitutionIdCodeMap() {
        if (null == institutionEntityMap) {
            institutionEntityMap = new HashMap<>();
            try {
                Iterable<InstitutionEntity> institutionEntities = institutionDetailsRepository.findAll();
                for (InstitutionEntity institutionEntity : institutionEntities) {
                    institutionEntityMap.put(institutionEntity.getInstitutionCode(), institutionEntity.getId());
                }
            } catch (Exception e) {
                logger.error(RecapConstants.EXCEPTION,e);
            }
        }
        return institutionEntityMap;
    }

}

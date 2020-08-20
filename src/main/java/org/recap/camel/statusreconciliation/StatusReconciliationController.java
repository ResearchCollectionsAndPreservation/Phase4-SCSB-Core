package org.recap.camel.statusreconciliation;

import com.google.common.collect.Lists;
import org.apache.camel.ProducerTemplate;
import org.recap.RecapConstants;
import org.recap.RecapCommonConstants;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.jpa.ItemStatusEntity;
import org.recap.model.jpa.RequestStatusEntity;
import org.recap.repository.jpa.ItemDetailsRepository;
import org.recap.repository.jpa.ItemStatusDetailsRepository;
import org.recap.repository.jpa.RequestItemStatusDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by hemalathas on 1/6/17.
 */
@RestController
@RequestMapping("/statusReconciliation")
public class StatusReconciliationController {

    private static final Logger logger = LoggerFactory.getLogger(StatusReconciliationController.class);

    @Value("${scsb.circ.url}")
    private String scsbCircUrl;

    @Value("${status.reconciliation.batch.size}")
    private Integer batchSize;

    @Value("${status.reconciliation.day.limit}")
    private Integer statusReconciliationDayLimit;

    @Autowired
    private ItemStatusDetailsRepository itemStatusDetailsRepository;

    @Autowired
    private ItemDetailsRepository itemDetailsRepository;

    @Value("${status.reconciliation.las.barcode.limit}")
    private Integer statusReconciliationLasBarcodeLimit;

    @Autowired
    private ProducerTemplate producer;

    @Autowired
    private RequestItemStatusDetailsRepository requestItemStatusDetailsRepository;

    /**
     * Gets logger.
     *
     * @return the logger
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * Gets batch size.
     *
     * @return the batch size
     */
    public Integer getBatchSize() {
        return batchSize;
    }

    /**
     * Gets status reconciliation day limit.
     *
     * @return the status reconciliation day limit
     */
    public Integer getStatusReconciliationDayLimit() {
        return statusReconciliationDayLimit;
    }

    /**
     * Gets item status details repository.
     *
     * @return the item status details repository
     */
    public ItemStatusDetailsRepository getItemStatusDetailsRepository() {
        return itemStatusDetailsRepository;
    }

    /**
     * Gets item details repository.
     *
     * @return the item details repository
     */
    public ItemDetailsRepository getItemDetailsRepository() {
        return itemDetailsRepository;
    }


    /**
     * Get request item status details repository request item status details repository.
     *
     * @return the request item status details repository
     */
    public RequestItemStatusDetailsRepository getRequestItemStatusDetailsRepository(){
        return requestItemStatusDetailsRepository ;
    }

    /**
     * Gets status reconciliation las barcode limit.
     *
     * @return the status reconciliation las barcode limit
     */
    public Integer getStatusReconciliationLasBarcodeLimit() {
        return statusReconciliationLasBarcodeLimit;
    }

    /**
     * Gets producer.
     *
     * @return the producer
     */
    public ProducerTemplate getProducer() {
        return producer;
    }

    /**
     * Get from date long.
     *
     * @param pageNum the page num
     * @return the long
     */
    public Long getFromDate(int pageNum){
        return pageNum * Long.valueOf(getBatchSize());
    }

    /**
     * Prepare the item entites for the status reconciliation and
     * placing the status reconciliation csv records in the scsb active-mq.
     *
     * @return the response entity
     */
    @GetMapping(value = "/itemStatusReconciliation")
    public ResponseEntity itemStatusReconciliation(){
        ItemStatusEntity itemStatusEntity = getItemStatusDetailsRepository().findByStatusCode(RecapConstants.ITEM_STATUS_NOT_AVAILABLE);
        List<String> requestStatusCodes = Arrays.asList(RecapCommonConstants.REQUEST_STATUS_RETRIEVAL_ORDER_PLACED, RecapCommonConstants.REQUEST_STATUS_EDD, RecapCommonConstants.REQUEST_STATUS_CANCELED, RecapCommonConstants.REQUEST_STATUS_INITIAL_LOAD);
        List<RequestStatusEntity> requestStatusEntityList = getRequestItemStatusDetailsRepository().findByRequestStatusCodeIn(requestStatusCodes);
        List<Integer> requestStatusIds = requestStatusEntityList.stream().map(RequestStatusEntity::getId).collect(Collectors.toList());
        logger.info("status reconciliation request ids : {} ",requestStatusIds);
        Map<String,Integer> itemCountAndStatusIdMap = getTotalPageCount(requestStatusIds,itemStatusEntity.getId());
        if (itemCountAndStatusIdMap.size() > 0){
            int totalPagesCount = itemCountAndStatusIdMap.get("totalPagesCount");
            getLogger().info("status reconciliation total page count :{}",totalPagesCount);
            List<StatusReconciliationCSVRecord> statusReconciliationCSVRecordList = new ArrayList<>();
            List<StatusReconciliationCSVRecord> statusReconciliationCSVRecordList1 = new ArrayList<>();
            List<StatusReconciliationErrorCSVRecord> statusReconciliationErrorCSVRecords = new ArrayList<>();
            for (int pageNum = 0; pageNum < totalPagesCount + 1; pageNum++) {
                long from = getFromDate(pageNum);
                List<ItemEntity> itemEntityList = getItemDetailsRepository().getNotAvailableItems(getStatusReconciliationDayLimit(),requestStatusIds,from, getBatchSize(),itemStatusEntity.getId());
                logger.info("items fetched from data base ----->{}",itemEntityList.size());
                List<List<ItemEntity>> itemEntityChunkList = Lists.partition(itemEntityList, getStatusReconciliationLasBarcodeLimit());
                HttpEntity httpEntity = new HttpEntity(statusReconciliationCSVRecordList);
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<List<StatusReconciliationCSVRecord>> responseEntity = restTemplate.exchange(scsbCircUrl+ RecapConstants.GFA_ITEM_STATUS_COMPARISON_URL, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<List<StatusReconciliationCSVRecord>>() {});
                statusReconciliationCSVRecordList = responseEntity.getBody();
                //statusReconciliationCSVRecordList = getGfaService().itemStatusComparison(itemEntityChunkList,statusReconciliationErrorCSVRecords);
                statusReconciliationCSVRecordList1.addAll(statusReconciliationCSVRecordList);
                getLogger().info("status reconciliation page num:{} and records {} processed",pageNum,from+getBatchSize());
            }
            getProducer().sendBodyAndHeader(RecapConstants.STATUS_RECONCILIATION_REPORT, statusReconciliationCSVRecordList1, RecapConstants.FOR, RecapConstants.STATUS_RECONCILIATION);
            getProducer().sendBodyAndHeader(RecapConstants.STATUS_RECONCILIATION_REPORT,statusReconciliationErrorCSVRecords, RecapConstants.FOR, RecapConstants.STATUS_RECONCILIATION_FAILURE);
        }
        return new ResponseEntity("Success", HttpStatus.OK);
    }

    /**
     * Get total page count for the status reconciliation.
     *
     * @return the map
     * @param requestStatusIds
     * @param itemStatusId
     */
    public Map<String,Integer> getTotalPageCount(List<Integer> requestStatusIds, Integer itemStatusId){
        Map<String,Integer> itemCountAndStatusIdMap = new HashMap<>();
        long itemCount = getItemDetailsRepository().getNotAvailableItemsCount(getStatusReconciliationDayLimit(),requestStatusIds,itemStatusId);
        getLogger().info("status reconciliation total item records count :{}" ,itemCount);
        if (itemCount > 0){
            int totalPagesCount = (int) (itemCount / getBatchSize());
            itemCountAndStatusIdMap.put("totalPagesCount",totalPagesCount);
        }
        return itemCountAndStatusIdMap;
    }
}

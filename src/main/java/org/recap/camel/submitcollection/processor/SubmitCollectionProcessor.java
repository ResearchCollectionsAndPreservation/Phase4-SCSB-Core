package org.recap.camel.submitcollection.processor;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.recap.RecapConstants;
import org.recap.RecapCommonConstants;
import org.recap.camel.EmailPayLoad;
import org.recap.model.reports.ReportDataRequest;
import org.recap.service.common.SetupDataService;
import org.recap.service.submitcollection.SubmitCollectionBatchService;
import org.recap.service.submitcollection.SubmitCollectionReportGenerator;
import org.recap.service.submitcollection.SubmitCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by premkb on 19/3/17.
 */
@Service
@Scope("prototype")
public class SubmitCollectionProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitCollectionProcessor.class);

    @Autowired
    private SubmitCollectionService submitCollectionService;

    @Autowired
    private SubmitCollectionBatchService submitCollectionBatchService;

    @Autowired
    private SubmitCollectionReportGenerator submitCollectionReportGenerator;

    @Autowired
    private ProducerTemplate producer;

    @Value("${email.submit.collection.subject}")
    private String submitCollectionEmailSubject;

    @Value("${pul.ftp.submit.collection.report.dir}")
    private String submitCollectionPULReportLocation;

    @Value("${cul.ftp.submit.collection.report.dir}")
    private String submitCollectionCULReportLocation;

    @Value("${nypl.ftp.submit.collection.report.dir}")
    private String submitCollectionNYPLReportLocation;

    @Value("${pul.email.submit.collection.to}")
    private String emailToPUL;

    @Value("${cul.email.submit.collection.to}")
    private String emailToCUL;

    @Value("${nypl.email.submit.collection.to}")
    private String emailToNYPL;

    private String institutionCode;

    private boolean isCGDProtection;
    @Value("${pul.email.submit.collection.cc}")
    private String emailCCForPul;
    @Value("${cul.email.submit.collection.cc}")
    private String emailCCForCul;
    @Value("${nypl.email.submit.collection.cc}")
    private String emailCCForNypl;

    @Autowired
    private SetupDataService setupDataService;

    public SubmitCollectionProcessor(){};

    public SubmitCollectionProcessor(String inputInstitutionCode,boolean isCGDProtection) {
        this.institutionCode = inputInstitutionCode;
        this.isCGDProtection = isCGDProtection;
    }

    /**
     * Process input.
     *
     * @param exchange the exchange
     * @throws Exception the exception
     */
    public void processInput(Exchange exchange) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.info("Submit Collection : Route started and started processing the records from ftp for submitcollection");
        String inputXml = exchange.getIn().getBody(String.class);
        String xmlFileName = exchange.getIn().toString();
        logger.info("Processing xmlFileName----->{}",xmlFileName);
        Set<Integer> processedBibIds = new HashSet<>();
        Set<String> updatedBoundWithDummyRecordOwnInstBibIdSet = new HashSet<>();
        List<Map<String,String>> idMapToRemoveIndexList = new ArrayList<>();
        List<Map<String,String>> bibIdMapToRemoveIndexList = new ArrayList<>();
        List<Integer> reportRecordNumList = new ArrayList<>();
        Integer institutionId = (Integer) setupDataService.getInstitutionCodeIdMap().get(institutionCode);
        try {
            submitCollectionBatchService.process(institutionCode,inputXml,processedBibIds,idMapToRemoveIndexList,bibIdMapToRemoveIndexList,xmlFileName,reportRecordNumList, false, isCGDProtection,updatedBoundWithDummyRecordOwnInstBibIdSet);
            logger.info("Submit Collection : Solr indexing started for {} records", processedBibIds.size());
            logger.info("idMapToRemoveIndex---> {}", idMapToRemoveIndexList.size());
            if (!processedBibIds.isEmpty()) {
                submitCollectionBatchService.indexData(processedBibIds);
                logger.info("Submit Collection : Solr indexing completed and remove the incomplete record from solr index for {} records", idMapToRemoveIndexList.size());
            }
            if(!updatedBoundWithDummyRecordOwnInstBibIdSet.isEmpty()){
                logger.info("Updated boudwith dummy record own inst bib id size-->{}",updatedBoundWithDummyRecordOwnInstBibIdSet.size());
                submitCollectionService.indexDataUsingOwningInstBibId(new ArrayList<>(updatedBoundWithDummyRecordOwnInstBibIdSet),institutionId);
            }
            if (!idMapToRemoveIndexList.isEmpty() || !bibIdMapToRemoveIndexList.isEmpty()) {//remove the incomplete record from solr index
                StopWatch stopWatchRemovingDummy = new StopWatch();
                stopWatchRemovingDummy.start();
                logger.info("Calling indexing to remove dummy records");
                new Thread(() -> {
                    try {
                        submitCollectionBatchService.removeBibFromSolrIndex(bibIdMapToRemoveIndexList);
                        submitCollectionBatchService.removeSolrIndex(idMapToRemoveIndexList);
                        logger.info("Removed dummy records from solr");
                    } catch (Exception e) {
                        logger.error(RecapCommonConstants.LOG_ERROR,e);
                    }
                }).start();
                stopWatchRemovingDummy.stop();
                logger.info("Time take to call and execute solr call to remove dummy-->{} sec",stopWatchRemovingDummy.getTotalTimeSeconds());
            }
            ReportDataRequest reportRequest = getReportDataRequest(xmlFileName);
            String generatedReportFileName = submitCollectionReportGenerator.generateReport(reportRequest);
            producer.sendBodyAndHeader(RecapConstants.EMAIL_Q, getEmailPayLoad(xmlFileName,generatedReportFileName), RecapConstants.EMAIL_BODY_FOR, RecapConstants.SUBMIT_COLLECTION);
            stopWatch.stop();
            logger.info("Submit Collection : Total time taken for processing through ftp---> {} sec",stopWatch.getTotalTimeSeconds());
        } catch (Exception e) {
            logger.error(RecapCommonConstants.LOG_ERROR,e);
            exchange.setException(e);
        }
    }

    public void caughtException(Exchange exchange){
        logger.info("inside caught exception..........");
        Exception exception = (Exception) exchange.getProperty(Exchange.EXCEPTION_CAUGHT);
        if(exception!=null){
            String fileName = (String)exchange.getIn().getHeader(Exchange.FILE_NAME);
            String filePath = (String)exchange.getIn().getHeader(Exchange.FILE_PARENT);
            String institutionCode1=(String)exchange.getIn().getHeader(RecapCommonConstants.INSTITUTION);
            producer.sendBodyAndHeader(RecapConstants.EMAIL_Q, getEmailPayLoadForExcepion(institutionCode1,fileName,filePath,exception,exception.getMessage()), RecapConstants.EMAIL_BODY_FOR, RecapConstants.SUBMIT_COLLECTION_EXCEPTION);
        }
    }

    private EmailPayLoad getEmailPayLoadForExcepion(String institutionCode,String name,String filePath,Exception exception,String exceptionMessage) {
        EmailPayLoad emailPayLoad = new EmailPayLoad();
        emailPayLoad.setSubject(RecapConstants.SUBJECT_FOR_SUBMIT_COL_EXCEPTION);
        emailPayLoad.setXmlFileName(name);
        if(RecapCommonConstants.PRINCETON.equalsIgnoreCase(institutionCode)){
            emailPayLoad.setTo(emailToPUL);
            emailPayLoad.setLocation(getFtpLocation(submitCollectionPULReportLocation));
            emailPayLoad.setInstitution(RecapCommonConstants.PRINCETON);
            emailPayLoad.setCc(emailCCForPul);
            emailPayLoad.setLocation(filePath);
            emailPayLoad.setException(exception);
            emailPayLoad.setExceptionMessage(exceptionMessage);
        } else if(RecapCommonConstants.COLUMBIA.equalsIgnoreCase(institutionCode)){
            emailPayLoad.setTo(emailToCUL);
            emailPayLoad.setLocation(getFtpLocation(submitCollectionCULReportLocation));
            emailPayLoad.setInstitution(RecapCommonConstants.COLUMBIA);
            emailPayLoad.setCc(emailCCForCul);
            emailPayLoad.setLocation(filePath);
            emailPayLoad.setException(exception);
            emailPayLoad.setExceptionMessage(exceptionMessage);
        } else if(RecapCommonConstants.NYPL.equalsIgnoreCase(institutionCode)){
            emailPayLoad.setTo(emailToNYPL);
            emailPayLoad.setLocation(getFtpLocation(submitCollectionNYPLReportLocation));
            emailPayLoad.setInstitution(RecapCommonConstants.NYPL);
            emailPayLoad.setCc(emailCCForNypl);
            emailPayLoad.setLocation(filePath);
            emailPayLoad.setException(exception);
            emailPayLoad.setExceptionMessage(exceptionMessage);
        }
        return  emailPayLoad;

    }

    private ReportDataRequest getReportDataRequest(String xmlFileName) {
        ReportDataRequest reportRequest = new ReportDataRequest();
        logger.info("filename--->{}-{}", RecapCommonConstants.SUBMIT_COLLECTION_REPORT,xmlFileName);
        reportRequest.setFileName(RecapCommonConstants.SUBMIT_COLLECTION_REPORT+"-"+xmlFileName);
        reportRequest.setInstitutionCode(institutionCode.toUpperCase());
        reportRequest.setReportType(RecapCommonConstants.SUBMIT_COLLECTION_SUMMARY);
        reportRequest.setTransmissionType(RecapCommonConstants.FTP);
        return reportRequest;
    }

    private EmailPayLoad getEmailPayLoad(String xmlFileName,String reportFileName) {
        EmailPayLoad emailPayLoad = new EmailPayLoad();
        emailPayLoad.setSubject(submitCollectionEmailSubject);
        emailPayLoad.setReportFileName(reportFileName);
        emailPayLoad.setXmlFileName(xmlFileName);
        if(RecapCommonConstants.PRINCETON.equalsIgnoreCase(institutionCode)){
            emailPayLoad.setTo(emailToPUL);
            emailPayLoad.setLocation(getFtpLocation(submitCollectionPULReportLocation));
            emailPayLoad.setInstitution(RecapCommonConstants.PRINCETON);
            emailPayLoad.setCc(emailCCForPul);
        } else if(RecapCommonConstants.COLUMBIA.equalsIgnoreCase(institutionCode)){
            emailPayLoad.setTo(emailToCUL);
            emailPayLoad.setLocation(getFtpLocation(submitCollectionCULReportLocation));
            emailPayLoad.setInstitution(RecapCommonConstants.COLUMBIA);
            emailPayLoad.setCc(emailCCForCul);
        } else if(RecapCommonConstants.NYPL.equalsIgnoreCase(institutionCode)){
            emailPayLoad.setTo(emailToNYPL);
            emailPayLoad.setLocation(getFtpLocation(submitCollectionNYPLReportLocation));
            emailPayLoad.setInstitution(RecapCommonConstants.NYPL);
            emailPayLoad.setCc(emailCCForNypl);
        }
        return  emailPayLoad;
    }

    private String getFtpLocation(String ftpLocation) {
        if (ftpLocation.contains(File.separator)){
            String[] splittedFtpLocation = ftpLocation.split(File.separator,2);
            return splittedFtpLocation[1];
        }else {
            return ftpLocation;
        }

    }
}

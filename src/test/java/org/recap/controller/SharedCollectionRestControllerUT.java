package org.recap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.map.HashedMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.recap.BaseTestCase;
import org.recap.RecapConstants;
import org.recap.RecapCommonConstants;
import org.recap.model.deaccession.DeAccessionItem;
import org.recap.model.deaccession.DeAccessionRequest;
import org.recap.model.submitcollection.SubmitCollectionResponse;
import org.recap.service.common.SetupDataService;
import org.recap.service.submitcollection.SubmitCollectionBatchService;
import org.recap.service.submitcollection.SubmitCollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by premkb on 26/12/16.
 */
@RunWith(MockitoJUnitRunner.class)
public class SharedCollectionRestControllerUT {

    @InjectMocks
    private SharedCollectionRestController sharedCollectionRestController;
    @Mock
    private SubmitCollectionService submitCollectionService;

    @Mock
    private SubmitCollectionBatchService submitCollectionBatchService;

    @Mock
    private SetupDataService setupDataService;

    String updatedMarcXml = "<collection xmlns=\"http://www.loc.gov/MARC21/slim\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.loc.gov/MARC21/slim http://www.loc.gov/standards/marcxml/schema/MARC21slim.xsd\">\n" +
            "<record>\n" +
            "<leader>01011cam a2200289 a 4500</leader>\n" +
            "<controlfield tag=\"001\">115115</controlfield>\n" +
            "<controlfield tag=\"005\">20160503221017.0</controlfield>\n" +
            "<controlfield tag=\"008\">820315s1982 njua b 00110 eng</controlfield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"010\">\n" +
            "<subfield code=\"a\">81008543</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"020\">\n" +
            "<subfield code=\"a\">0132858908</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"035\">\n" +
            "<subfield code=\"a\">(OCoLC)7555877</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"035\">\n" +
            "<subfield code=\"a\">(CStRLIN)NJPG82-B5675</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"035\">\n" +
            "<subfield code=\"9\">AAS9821TS</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"0\" ind2=\" \" tag=\"039\">\n" +
            "<subfield code=\"a\">2</subfield>\n" +
            "<subfield code=\"b\">3</subfield>\n" +
            "<subfield code=\"c\">3</subfield>\n" +
            "<subfield code=\"d\">3</subfield>\n" +
            "<subfield code=\"e\">3</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"0\" ind2=\" \" tag=\"050\">\n" +
            "<subfield code=\"a\">QE28.3</subfield>\n" +
            "<subfield code=\"b\">.S76 1982</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"0\" ind2=\" \" tag=\"082\">\n" +
            "<subfield code=\"a\">551.7</subfield>\n" +
            "<subfield code=\"2\">19</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"1\" ind2=\" \" tag=\"100\">\n" +
            "<subfield code=\"a\">Stokes, William Lee,</subfield>\n" +
            "<subfield code=\"d\">1915-1994.</subfield>\n" +
            "<subfield code=\"0\">(uri)http://id.loc.gov/authorities/names/n50011514</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"1\" ind2=\"0\" tag=\"245\">\n" +
            "<subfield code=\"a\">Essentials of earth history :</subfield>\n" +
            "<subfield code=\"b\">an introduction to historical geology /</subfield>\n" +
            "<subfield code=\"c\">W. Lee Stokes.</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"250\">\n" +
            "<subfield code=\"a\">4th ed.</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"260\">\n" +
            "<subfield code=\"a\">Englewood Cliffs, N.J. :</subfield>\n" +
            "<subfield code=\"b\">Prentice-Hall,</subfield>\n" +
            "<subfield code=\"c\">c1982.</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"300\">\n" +
            "<subfield code=\"a\">xiv, 577 p. :</subfield>\n" +
            "<subfield code=\"b\">ill. ;</subfield>\n" +
            "<subfield code=\"c\">24 cm.</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"504\">\n" +
            "<subfield code=\"a\">Includes bibliographies and index.</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\"0\" tag=\"650\">\n" +
            "<subfield code=\"a\">Historical geology.</subfield>\n" +
            "<subfield code=\"0\">\n" +
            "(uri)http://id.loc.gov/authorities/subjects/sh85061190\n" +
            "</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"998\">\n" +
            "<subfield code=\"a\">03/15/82</subfield>\n" +
            "<subfield code=\"s\">9110</subfield>\n" +
            "<subfield code=\"n\">NjP</subfield>\n" +
            "<subfield code=\"w\">DCLC818543B</subfield>\n" +
            "<subfield code=\"d\">03/15/82</subfield>\n" +
            "<subfield code=\"c\">ZG</subfield>\n" +
            "<subfield code=\"b\">WZ</subfield>\n" +
            "<subfield code=\"i\">820315</subfield>\n" +
            "<subfield code=\"l\">NJPG</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"948\">\n" +
            "<subfield code=\"a\">AACR2</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"911\">\n" +
            "<subfield code=\"a\">19921028</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"912\">\n" +
            "<subfield code=\"a\">19900820000000.0</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\" \" ind2=\" \" tag=\"959\">\n" +
            "<subfield code=\"a\">2000-06-13 00:00:00 -0500</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"0\" ind2=\"0\" tag=\"852\">\n" +
            "<subfield code=\"0\">128532</subfield>\n" +
            "<subfield code=\"b\">rcppa</subfield>\n" +
            "<subfield code=\"h\">QE28.3 .S76 1982</subfield>\n" +
            "<subfield code=\"t\">1</subfield>\n" +
            "<subfield code=\"x\">tr fr sci</subfield>\n" +
            "</datafield>\n" +
            "<datafield ind1=\"0\" ind2=\"0\" tag=\"876\">\n" +
            "<subfield code=\"0\">128532</subfield>\n" +
            "<subfield code=\"a\">123431</subfield>\n" +
            "<subfield code=\"h\"/>\n" +
            "<subfield code=\"j\">Not Charged</subfield>\n" +
            "<subfield code=\"p\">32101068878931</subfield>\n" +
            "<subfield code=\"t\">1</subfield>\n" +
            "<subfield code=\"x\">Shared</subfield>\n" +
            "<subfield code=\"z\">PA</subfield>\n" +
            "</datafield>\n" +
            "</record>\n" +
            "</collection>";

    @Test
    public void submitCollectiontest() throws Exception{
        SubmitCollectionResponse submitCollectionResponse = new SubmitCollectionResponse();
        submitCollectionResponse.setItemBarcode("32101068878931");
        submitCollectionResponse.setMessage("ExceptionRecord");
        String inputRecords = updatedMarcXml;
        String institution = "PUL";
        boolean isCGDProtection = true;
        Map map = new HashMap();
        map.put(1,"PUL");
        Map<String,Object> requestParameters = new HashedMap();
        requestParameters.put(RecapCommonConstants.INPUT_RECORDS,updatedMarcXml);
        requestParameters.put(RecapCommonConstants.INSTITUTION,"PUL");
        requestParameters.put(RecapCommonConstants.IS_CGD_PROTECTED,"true");
        List<Integer> reportRecordNumberList = new ArrayList<>();
        Set<Integer> processedBibIdSet = new HashSet<>();
        List<Map<String, String>> idMapToRemoveIndexList = new ArrayList<>();//Added to remove dummy record in solr
        List<Map<String, String>> bibIdMapToRemoveIndexList = new ArrayList<>();//Added to remove orphan record while unlinking
        Set<String> updatedBoundWithDummyRecordOwnInstBibIdSet = new HashSet<>();
        List<SubmitCollectionResponse> submitCollectionResponseList = new ArrayList<>();
        submitCollectionResponseList.add(submitCollectionResponse);
        ResponseEntity responseEntity = new ResponseEntity(submitCollectionResponse,HttpStatus.OK);
        Mockito.when(setupDataService.getInstitutionCodeIdMap()).thenReturn(map);
        Mockito.when(submitCollectionBatchService.process(institution, inputRecords, processedBibIdSet, idMapToRemoveIndexList, bibIdMapToRemoveIndexList, "", reportRecordNumberList, true, isCGDProtection, updatedBoundWithDummyRecordOwnInstBibIdSet)).thenReturn(submitCollectionResponseList);
        ResponseEntity response = sharedCollectionRestController.submitCollection(requestParameters);
        assertNotNull(response);
    }

    @Test
    public void deAccession() throws Exception {
        DeAccessionRequest deAccessionRequest = new DeAccessionRequest();
        DeAccessionItem deAccessionItem = new DeAccessionItem();
        deAccessionItem.setItemBarcode("1");
        deAccessionItem.setDeliveryLocation("PB");
        deAccessionRequest.setDeAccessionItems(Arrays.asList(deAccessionItem));
        Map<String, String> map = new HashMap<>();
        map.put("Institution","PUL");
        //Mockito.when(deAccessionService.deAccession(deAccessionRequest)).thenReturn(map);
        ResponseEntity result = sharedCollectionRestController.deAccession(deAccessionRequest);
        assertNotNull(result);
    }

}

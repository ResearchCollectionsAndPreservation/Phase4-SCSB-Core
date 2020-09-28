package org.recap.service.partnerservice;

import org.recap.RecapConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by premkb on 18/12/16.
 */
@Service
public class PrincetonService {

    private static final Logger logger = LoggerFactory.getLogger(PrincetonService.class);

    @Value("${ils.princeton.bibdata}")
    private String ilsprincetonBibData;

    @Value("${bibdata.api.connection.timeout}")
    private Integer connectionTimeout;

    @Value("${bibdata.api.read.timeout}")
    private Integer readTimeout;

    public static Logger getLogger() {
        return logger;
    }

    public String getIlsprincetonBibData() {
        return ilsprincetonBibData;
    }

    public RestTemplate getRestTemplate(){
        return new RestTemplate();
    }

    /**
     * This method gets bib data response(marc xml) based on the itemBarcode from ILS for Princeton.
     *
     * @param itemBarcode the item barcode
     * @return the bib data
     */
    public String getBibData(String itemBarcode) {
        RestTemplate restTemplate = getRestTemplate();
        HostnameVerifier verifier = new NullHostnameVerifier();
        SCSBSimpleClientHttpRequestFactory factory = new SCSBSimpleClientHttpRequestFactory(verifier);
        restTemplate.setRequestFactory(factory);
        ((SimpleClientHttpRequestFactory)restTemplate.getRequestFactory()).setConnectTimeout(connectionTimeout);
        ((SimpleClientHttpRequestFactory)restTemplate.getRequestFactory()).setReadTimeout(readTimeout);

        String bibDataResponse;
        String response;
        try {
            getLogger().info("PUL BIBDATA URL = {}" , getIlsprincetonBibData());
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_XML));
            HttpEntity requestEntity = new HttpEntity(headers);
            Map<String, String> params = new HashMap<>();
            params.put("barcode", itemBarcode);
            ResponseEntity<String> responseEntity = restTemplate.exchange(getIlsprincetonBibData(), HttpMethod.GET, requestEntity, String.class, params);
            bibDataResponse = responseEntity.getBody();
        } catch (Exception e) {
            response = String.format("[%s] %s. (%s : %s)", itemBarcode, RecapConstants.ITEM_BARCODE_NOT_FOUND, ilsprincetonBibData, e.getMessage());
            logger.error(response);
            throw new RuntimeException(response);
        }
        return bibDataResponse;
    }
}
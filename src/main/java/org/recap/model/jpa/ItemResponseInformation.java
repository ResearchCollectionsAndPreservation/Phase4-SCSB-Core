package org.recap.model.jpa;


import lombok.Getter;
import lombok.Setter;

/**
 * Created by hemalathas on 1/11/16.
 */
@Setter
@Getter
public class ItemResponseInformation {

    private String patronBarcode;
    private String itemBarcode;
    private String requestType;
    private String deliveryLocation;
    private String requestingInstitution;
    private String bibliographicId;
    private String expirationDate;
    private String screenMessage;
    private boolean success;
    private String emailAddress;
    private Integer startPage;
    private Integer endPage;
    private String titleIdentifier;
    private String bibiid;
    private String dueDate;


}

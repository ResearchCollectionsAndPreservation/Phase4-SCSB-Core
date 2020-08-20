package org.recap.repository;

import org.junit.Test;
import org.recap.BaseTestCase;
import org.recap.RecapConstants;
import org.recap.model.jpa.AccessionEntity;
import org.recap.repository.jpa.AccessionDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static org.junit.Assert.assertNotNull;

/**
 * Created by rajeshbabuk on 9/5/17.
 */
public class AccessionDetailsRepositoryUT extends BaseTestCase {

    @Autowired
    AccessionDetailsRepository accessionDetailsRepository;

    @Test
    public void testSaveAccessionRequest() throws Exception {
        AccessionEntity accessionEntity = new AccessionEntity();
        accessionEntity.setAccessionRequest("[{\"customerCode\":\"PA\",\"itemBarcode\":\"123\"}]");
        accessionEntity.setCreatedDate(new Date());
        accessionEntity.setAccessionStatus(RecapConstants.COMPLETE);
        AccessionEntity savedAccessionEntity = accessionDetailsRepository.save(accessionEntity);
        assertNotNull(savedAccessionEntity);
    }

    @Test
    public void testAccessionEntity(){
        AccessionEntity accessionEntity = new AccessionEntity();
        accessionEntity.setId(1);
        accessionEntity.setAccessionRequest("[{\"customerCode\":\"PA\",\"itemBarcode\":\"123\"}]");
        accessionEntity.setCreatedDate(new Date());
        accessionEntity.setAccessionStatus(RecapConstants.COMPLETE);

        assertNotNull(accessionEntity.getId());
        assertNotNull(accessionEntity.getAccessionRequest());
        assertNotNull(accessionEntity.getAccessionStatus());
        assertNotNull(accessionEntity.getCreatedDate());

    }
}

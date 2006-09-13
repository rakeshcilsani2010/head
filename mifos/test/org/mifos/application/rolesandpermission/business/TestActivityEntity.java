package org.mifos.application.rolesandpermission.business;

import org.mifos.framework.MifosTestCase;
import org.mifos.framework.util.helpers.TestObjectFactory;

public class TestActivityEntity extends MifosTestCase {
	
	private ActivityEntity activityEntity=null;

	public void testGetActivity(){
		activityEntity=getActivityEntity(Short.valueOf("1"));
		assertNull(activityEntity.getActivtyName());
		assertNull(activityEntity.getDescription());
		activityEntity.setLocaleId(Short.valueOf("1"));
		assertEquals("Organization Management",activityEntity.getActivtyName());
		assertEquals("Organization Management",activityEntity.getDescription());
	}

	private ActivityEntity getActivityEntity(Short id) {
		return (ActivityEntity)TestObjectFactory.getObject(ActivityEntity.class,id);
	}

}

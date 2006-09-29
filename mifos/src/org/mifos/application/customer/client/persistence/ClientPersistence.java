/**

* ClientPersistence.java version: 1.0



* Copyright (c) 2005-2006 Grameen Foundation USA

* 1029 Vermont Avenue, NW, Suite 400, Washington DC 20005

* All rights reserved.



* Apache License
* Copyright (c) 2005-2006 Grameen Foundation USA
*

* Licensed under the Apache License, Version 2.0 (the "License"); you may
* not use this file except in compliance with the License. You may obtain
* a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and limitations under the

* License.
*
* See also http://www.apache.org/licenses/LICENSE-2.0.html for an explanation of the license

* and how it is applied.

*

*/

package org.mifos.application.customer.client.persistence;


import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.mifos.application.NamedQueryConstants;
import org.mifos.application.customer.client.business.ClientBO;
import org.mifos.application.customer.util.helpers.CustomerConstants;
import org.mifos.application.productdefinition.business.SavingsOfferingBO;
import org.mifos.application.productdefinition.util.helpers.PrdApplicableMaster;
import org.mifos.framework.exceptions.PersistenceException;
import org.mifos.framework.persistence.Persistence;

public class ClientPersistence extends Persistence {

	public ClientBO getClient(Integer customerId) throws PersistenceException{
		return (ClientBO) getPersistentObject(ClientBO.class,customerId);
	}
	
	public boolean checkForDuplicacyOnGovtId(String governmentId , Integer customerId) throws PersistenceException {
			Map<String, Object> queryParameters = new HashMap<String, Object>();
			queryParameters.put("LEVEL_ID", CustomerConstants.CLIENT_LEVEL_ID);
			queryParameters.put("GOVT_ID",governmentId);
			queryParameters.put("customerId", customerId);
			List queryResult = executeNamedQuery(NamedQueryConstants.GET_CLIENT_BASEDON_GOVTID, queryParameters);
			return ((Integer)queryResult.get(0)).intValue()>0;
	}
	
	public boolean checkForDuplicacyOnName(String name, Date dob, Integer customerId) throws PersistenceException {
			Map<String, Object> queryParameters = new HashMap<String, Object>();
			queryParameters.put("clientName",name);
			queryParameters.put("LEVELID", CustomerConstants.CLIENT_LEVEL_ID);
			queryParameters.put("DATE_OFBIRTH", dob);
			queryParameters.put("customerId", customerId);
			List queryResult = executeNamedQuery(NamedQueryConstants.GET_CLIENT_BASEDON_NAME_DOB, queryParameters);
			return ((Integer)queryResult.get(0)).intValue()>0;
			
	}

	public Blob createBlob(InputStream picture) throws PersistenceException{
		try{
			return Hibernate.createBlob(picture);
		}
		catch(IOException ioe){
			throw new PersistenceException(ioe);
		}
	}
	
	public List<SavingsOfferingBO> retrieveOfferingsApplicableToClient()throws PersistenceException{
		Map<String, Object> queryParameters = new HashMap<String, Object>();
		queryParameters.put("prdApplicableTo",PrdApplicableMaster.CLIENTS.getValue());
		return (List<SavingsOfferingBO>)executeNamedQuery(NamedQueryConstants.GET_ACTIVE_OFFERINGS_FOR_CUSTOMER, queryParameters);
	}	
}

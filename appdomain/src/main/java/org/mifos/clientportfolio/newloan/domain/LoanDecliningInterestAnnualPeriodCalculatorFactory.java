/*
 * Copyright (c) 2005-2011 Grameen Foundation USA
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */

package org.mifos.clientportfolio.newloan.domain;

import org.mifos.accounts.util.helpers.AccountConstants;
import org.mifos.application.meeting.util.helpers.RecurrenceType;
import org.mifos.service.BusinessRuleException;

public class LoanDecliningInterestAnnualPeriodCalculatorFactory {

    public LoanDecliningInterestAnnualPeriodCalculator create(RecurrenceType recurrenceType) {
        switch (recurrenceType) {
        case MONTHLY:
            return new LoanDecliningInterestAnnualPeriodCalculatorForMonthlyRecurrence();
        case WEEKLY:
            return new LoanDecliningInterestAnnualPeriodCalculatorForWeeklyRecurrence();
        case DAILY:
            throw new BusinessRuleException(AccountConstants.NOT_SUPPORTED_DURATION_TYPE);
        default:
            throw new BusinessRuleException(AccountConstants.NOT_SUPPORTED_DURATION_TYPE);
        }
    }

}

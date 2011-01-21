/*
 * Copyright (c) 2005-2010 Grameen Foundation USA
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

package org.mifos.application.servicefacade;

import static org.mifos.accounts.loan.util.helpers.LoanConstants.MIN_DAYS_BETWEEN_DISBURSAL_AND_FIRST_REPAYMENT_DAY;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.mifos.accounts.exceptions.AccountException;
import org.mifos.accounts.fees.business.FeeDto;
import org.mifos.accounts.fund.business.FundBO;
import org.mifos.accounts.fund.persistence.FundDao;
import org.mifos.accounts.loan.business.LoanBO;
import org.mifos.accounts.loan.business.OriginalLoanScheduleEntity;
import org.mifos.accounts.loan.business.ScheduleCalculatorAdaptor;
import org.mifos.accounts.loan.business.service.LoanBusinessService;
import org.mifos.accounts.loan.business.service.OriginalScheduleInfoDto;
import org.mifos.accounts.loan.business.service.validators.InstallmentValidationContext;
import org.mifos.accounts.loan.business.service.validators.InstallmentsValidator;
import org.mifos.accounts.loan.persistance.LoanDao;
import org.mifos.accounts.loan.struts.actionforms.LoanAccountActionForm;
import org.mifos.accounts.loan.struts.uihelpers.PaymentDataHtmlBean;
import org.mifos.accounts.loan.util.helpers.CashFlowDataDto;
import org.mifos.accounts.loan.util.helpers.RepaymentScheduleInstallment;
import org.mifos.accounts.productdefinition.business.CashFlowDetail;
import org.mifos.accounts.productdefinition.business.LoanOfferingBO;
import org.mifos.accounts.productdefinition.business.VariableInstallmentDetailsBO;
import org.mifos.accounts.productdefinition.business.service.LoanPrdBusinessService;
import org.mifos.accounts.productdefinition.persistence.LoanProductDao;
import org.mifos.accounts.servicefacade.UserContextFactory;
import org.mifos.accounts.util.helpers.AccountConstants;
import org.mifos.accounts.util.helpers.AccountState;
import org.mifos.accounts.util.helpers.PaymentData;
import org.mifos.accounts.util.helpers.PaymentDataTemplate;
import org.mifos.application.admin.servicefacade.HolidayServiceFacade;
import org.mifos.application.admin.servicefacade.InvalidDateException;
import org.mifos.application.master.util.helpers.PaymentTypes;
import org.mifos.application.meeting.business.MeetingBO;
import org.mifos.application.meeting.exceptions.MeetingException;
import org.mifos.application.meeting.util.helpers.MeetingType;
import org.mifos.application.meeting.util.helpers.RecurrenceType;
import org.mifos.application.meeting.util.helpers.WeekDay;
import org.mifos.config.persistence.ConfigurationPersistence;
import org.mifos.core.MifosRuntimeException;
import org.mifos.customers.business.CustomerBO;
import org.mifos.customers.persistence.CustomerDao;
import org.mifos.customers.personnel.business.PersonnelBO;
import org.mifos.customers.personnel.persistence.PersonnelDao;
import org.mifos.framework.exceptions.PersistenceException;
import org.mifos.framework.exceptions.ServiceException;
import org.mifos.framework.util.helpers.DateUtils;
import org.mifos.framework.util.helpers.Money;
import org.mifos.platform.cashflow.ui.model.CashFlowForm;
import org.mifos.platform.cashflow.ui.model.MonthlyCashFlowForm;
import org.mifos.platform.util.CollectionUtils;
import org.mifos.platform.validations.Errors;
import org.mifos.security.MifosUser;
import org.mifos.security.util.UserContext;
import org.mifos.service.BusinessRuleException;
import org.springframework.security.core.context.SecurityContextHolder;

public class LoanServiceFacadeWebTier implements LoanServiceFacade {

    private final LoanProductDao loanProductDao;
    private final CustomerDao customerDao;
    private final PersonnelDao personnelDao;
    private final FundDao fundDao;
    private final LoanDao loanDao;
    private final InstallmentsValidator installmentsValidator;
    private final ScheduleCalculatorAdaptor scheduleCalculatorAdaptor;
    private final LoanBusinessService loanBusinessService;
    private final HolidayServiceFacade holidayServiceFacade;
    private final LoanPrdBusinessService loanPrdBusinessService;

    public LoanServiceFacadeWebTier(final LoanProductDao loanProductDao, final CustomerDao customerDao,
                                    PersonnelDao personnelDao, FundDao fundDao, final LoanDao loanDao,
                                    InstallmentsValidator installmentsValidator,
                                    ScheduleCalculatorAdaptor scheduleCalculatorAdaptor, LoanBusinessService loanBusinessService,
                                    HolidayServiceFacade holidayServiceFacade, LoanPrdBusinessService loanPrdBusinessService) {
        this.loanProductDao = loanProductDao;
        this.customerDao = customerDao;
        this.personnelDao = personnelDao;
        this.fundDao = fundDao;
        this.loanDao = loanDao;
        this.installmentsValidator = installmentsValidator;
        this.scheduleCalculatorAdaptor = scheduleCalculatorAdaptor;
        this.loanBusinessService = loanBusinessService;
        this.holidayServiceFacade = holidayServiceFacade;
        this.loanPrdBusinessService = loanPrdBusinessService;
    }

    private CustomerBO loadCustomer(Integer customerId) {
        return customerDao.findCustomerById(customerId);
    }

    private MeetingBO createNewMeetingForRepaymentDay(DateTime disbursementDate,
            final LoanAccountActionForm loanAccountActionForm, final CustomerBO customer) throws NumberFormatException,
            MeetingException {

        MeetingBO newMeetingForRepaymentDay = null;
        Short recurrenceId = Short.valueOf(loanAccountActionForm.getRecurrenceId());

        final int minDaysInterval = new ConfigurationPersistence().getConfigurationKeyValueInteger(
                MIN_DAYS_BETWEEN_DISBURSAL_AND_FIRST_REPAYMENT_DAY).getValue();

        final Date repaymentStartDate = disbursementDate.plusDays(minDaysInterval).toDate();

        if (RecurrenceType.WEEKLY.getValue().equals(recurrenceId)) {
            newMeetingForRepaymentDay = new MeetingBO(WeekDay.getWeekDay(Short.valueOf(loanAccountActionForm
                    .getWeekDay())), Short.valueOf(loanAccountActionForm.getRecurWeek()), repaymentStartDate,
                    MeetingType.LOAN_INSTALLMENT, customer.getCustomerMeeting().getMeeting().getMeetingPlace());
        } else if (RecurrenceType.MONTHLY.getValue().equals(recurrenceId)) {
            if (loanAccountActionForm.getMonthType().equals("1")) {
                newMeetingForRepaymentDay = new MeetingBO(Short.valueOf(loanAccountActionForm.getMonthDay()), Short
                        .valueOf(loanAccountActionForm.getDayRecurMonth()), repaymentStartDate,
                        MeetingType.LOAN_INSTALLMENT, customer.getCustomerMeeting().getMeeting().getMeetingPlace());
            } else {
                newMeetingForRepaymentDay = new MeetingBO(Short.valueOf(loanAccountActionForm.getMonthWeek()), Short
                        .valueOf(loanAccountActionForm.getRecurMonth()), repaymentStartDate,
                        MeetingType.LOAN_INSTALLMENT, customer.getCustomerMeeting().getMeeting().getMeetingPlace(),
                        Short.valueOf(loanAccountActionForm.getMonthRank()));
            }
        }
        return newMeetingForRepaymentDay;
    }

    @Override
    public LoanBO previewLoanRedoDetails(Integer customerId, LoanAccountActionForm loanAccountActionForm, DateTime disbursementDate) {
        CustomerBO customer = loadCustomer(customerId);
        return redoLoan(customer, loanAccountActionForm, disbursementDate);
    }

    private LoanBO redoLoan(CustomerBO customer, LoanAccountActionForm loanAccountActionForm, DateTime disbursementDate) {

        MifosUser mifosUser = (MifosUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserContext userContext = new UserContextFactory().create(mifosUser);

        try {
            boolean isRepaymentIndepOfMeetingEnabled = new ConfigurationPersistence().isRepaymentIndepOfMeetingEnabled();

            MeetingBO newMeetingForRepaymentDay = null;
            if (isRepaymentIndepOfMeetingEnabled) {
                newMeetingForRepaymentDay = createNewMeetingForRepaymentDay(disbursementDate, loanAccountActionForm, customer);
            }

            Short productId = loanAccountActionForm.getPrdOfferingIdValue();

            LoanOfferingBO loanOffering = new LoanPrdBusinessService().getLoanOffering(productId, userContext.getLocaleId());

            Money loanAmount = new Money(loanOffering.getCurrency(), loanAccountActionForm.getLoanAmount());
            Short numOfInstallments = loanAccountActionForm.getNoOfInstallmentsValue();
            boolean isInterestDeductedAtDisbursement = loanAccountActionForm.isInterestDedAtDisbValue();
            Double interest = loanAccountActionForm.getInterestDoubleValue();
            Short gracePeriod = loanAccountActionForm.getGracePeriodDurationValue();
            List<FeeDto> fees = loanAccountActionForm.getFeesToApply();

            Double maxLoanAmount = loanAccountActionForm.getMaxLoanAmountValue();
            Double minLoanAmount = loanAccountActionForm.getMinLoanAmountValue();
            Short maxNumOfInstallments = loanAccountActionForm.getMaxNoInstallmentsValue();
            Short minNumOfShortInstallments = loanAccountActionForm.getMinNoInstallmentsValue();
            String externalId = loanAccountActionForm.getExternalId();
            Integer selectedLoanPurpose = loanAccountActionForm.getBusinessActivityIdValue();
            String collateralNote = loanAccountActionForm.getCollateralNote();
            Integer selectedCollateralType = loanAccountActionForm.getCollateralTypeIdValue();
            AccountState accountState = loanAccountActionForm.getState();
            if (accountState == null) {
                accountState = AccountState.LOAN_PARTIAL_APPLICATION;
            }

            FundBO fund = null;
            Short fundId = loanAccountActionForm.getLoanOfferingFundValue();
            if (fundId != null) {
                fund = this.fundDao.findById(fundId);
            }

            LoanBO redoLoan = LoanBO.redoLoan(userContext, loanOffering, customer, accountState, loanAmount,
                    numOfInstallments, disbursementDate.toDate(), isInterestDeductedAtDisbursement, interest, gracePeriod,
                    fund, fees, maxLoanAmount, minLoanAmount, maxNumOfInstallments,
                    minNumOfShortInstallments, isRepaymentIndepOfMeetingEnabled, newMeetingForRepaymentDay);
            redoLoan.setExternalId(externalId);
            redoLoan.setBusinessActivityId(selectedLoanPurpose);
            redoLoan.setCollateralNote(collateralNote);
            redoLoan.setCollateralTypeId(selectedCollateralType);

            PersonnelBO user = personnelDao.findPersonnelById(userContext.getId());

            redoLoan.changeStatus(AccountState.LOAN_APPROVED, null, "Automatic Status Update (Redo Loan)", user);

            // We're assuming cash disbursal for this situation right now
            redoLoan.disburseLoan(user, PaymentTypes.CASH.getValue(), false);

            List<PaymentDataHtmlBean> paymentDataBeans = loanAccountActionForm.getPaymentDataBeans();
            PaymentData payment;

            for (PaymentDataTemplate template : paymentDataBeans) {
                if (template.hasValidAmount() && template.getTransactionDate() != null) {
                    if (!customer.getCustomerMeeting().getMeeting().isValidMeetingDate(template.getTransactionDate(),
                            DateUtils.getLastDayOfNextYear())) {
                        throw new BusinessRuleException("errors.invalidTxndate");
                    }
                    payment = PaymentData.createPaymentData(template);
                    redoLoan.applyPayment(payment);
                }
            }
            return redoLoan;
        } catch (InvalidDateException ide) {
                throw new BusinessRuleException(ide.getMessage());
        } catch (MeetingException e) {
                throw new MifosRuntimeException(e);
        } catch (ServiceException e2) {
            throw new MifosRuntimeException(e2);
        } catch (PersistenceException e1) {
            throw new MifosRuntimeException(e1);
        } catch (AccountException e) {
            throw new BusinessRuleException(e.getKey(), e);
        }
    }

    @Override
    public Errors validateInputInstallments(Date disbursementDate, VariableInstallmentDetailsBO variableInstallmentDetails,
                                            List<RepaymentScheduleInstallment> installments, Integer customerId) {
        Short officeId = loadCustomer(customerId).getOfficeId();
        InstallmentValidationContext context = new InstallmentValidationContext(disbursementDate, variableInstallmentDetails, holidayServiceFacade, officeId);
        return installmentsValidator.validateInputInstallments(installments, context);
    }

    @Override
    public Errors validateInstallmentSchedule(List<RepaymentScheduleInstallment> installments, VariableInstallmentDetailsBO variableInstallmentDetailsBO) {
        return installmentsValidator.validateInstallmentSchedule(installments, variableInstallmentDetailsBO);
    }

    @Override
    public Errors validateCashFlowForInstallmentsForWarnings(LoanAccountActionForm loanActionForm, Short localeId) throws ServiceException {
        Errors errors = new Errors();
        LoanOfferingBO loanOfferingBO = loanPrdBusinessService.getLoanOffering(loanActionForm.getPrdOfferingIdValue(), localeId);
        if (loanOfferingBO.shouldValidateCashFlowForInstallments()) {
            CashFlowDetail cashFlowDetail = loanOfferingBO.getCashFlowDetail();
            List<CashFlowDataDto> cashFlowDataDtos = loanActionForm.getCashflowDataDtos();
            if (CollectionUtils.isNotEmpty(cashFlowDataDtos)) {
                for (CashFlowDataDto cashflowDataDto : cashFlowDataDtos) {
                    validateCashFlow(errors, cashFlowDetail.getCashFlowThreshold(), cashflowDataDto);
                }
            }
        }
        return errors;
    }

    private void validateCashFlow(Errors errors, Double cashFlowThreshold, CashFlowDataDto cashflowDataDto) {
        String cashFlowAndInstallmentDiffPercent = cashflowDataDto.getDiffCumulativeCashflowAndInstallmentPercent();
        String monthYearAsString = cashflowDataDto.getMonthYearAsString();
        String cumulativeCashFlow = cashflowDataDto.getCumulativeCashFlow();
        if (StringUtils.isNotEmpty(cashFlowAndInstallmentDiffPercent) && Double.valueOf(cashFlowAndInstallmentDiffPercent) > cashFlowThreshold) {
            errors.addError(AccountConstants.BEYOND_CASHFLOW_THRESHOLD, new String[]{monthYearAsString, cashFlowThreshold.toString()});
        }
        if (StringUtils.isNotEmpty(cumulativeCashFlow)) {
            Double cumulativeCashFlowValue = Double.valueOf(cumulativeCashFlow);
            if (cumulativeCashFlowValue < 0) {
                errors.addError(AccountConstants.CUMULATIVE_CASHFLOW_NEGATIVE, new String[]{monthYearAsString});
            } else if (cumulativeCashFlowValue == 0) {
                errors.addError(AccountConstants.CUMULATIVE_CASHFLOW_ZERO, new String[]{monthYearAsString});
            }
        }
    }

    // TODO: Write unit tests for this method. Currently they only exist for repayment capacity related validations
    @Override
    public Errors validateCashFlowForInstallments(List<RepaymentScheduleInstallment> installments, CashFlowForm cashFlowForm, Double repaymentCapacity) {
        Errors errors = new Errors();
        if (cashFlowForm == null) {
            return errors;
        }
        List<MonthlyCashFlowForm> monthlyCashFlows = cashFlowForm.getMonthlyCashFlows();
        if (CollectionUtils.isNotEmpty(installments) && CollectionUtils.isNotEmpty(monthlyCashFlows)) {
            boolean lowerBound = DateUtils.firstLessOrEqualSecond(monthlyCashFlows.get(0).getDate(), installments.get(0).getDueDateValue());
            boolean upperBound = DateUtils.firstLessOrEqualSecond(installments.get(installments.size() - 1).getDueDateValue(), monthlyCashFlows.get(monthlyCashFlows.size() - 1).getDate());

            SimpleDateFormat df = new SimpleDateFormat("MMMM yyyy", installments.get(0).getLocale());

            if (!lowerBound) {
                errors.addError(AccountConstants.INSTALLMENT_BEYOND_CASHFLOW_DATE, new String[]{df.format(installments.get(0).getDueDateValue())});
            }

            if (!upperBound) {
                errors.addError(AccountConstants.INSTALLMENT_BEYOND_CASHFLOW_DATE, new String[]{df.format(installments.get(installments.size() - 1).getDueDateValue())});
            }

        }
        validateForRepaymentCapacity(installments, cashFlowForm, repaymentCapacity, errors);
        return errors;
    }

    @Override
    public OriginalScheduleInfoDto retrieveOriginalLoanSchedule(Integer accountId, Locale locale) throws PersistenceException {
        List<OriginalLoanScheduleEntity> loanScheduleEntities = loanBusinessService.retrieveOriginalLoanSchedule(accountId);
        ArrayList<RepaymentScheduleInstallment> repaymentScheduleInstallments = new ArrayList<RepaymentScheduleInstallment>();
        for (OriginalLoanScheduleEntity loanScheduleEntity : loanScheduleEntities) {
              repaymentScheduleInstallments.add(loanScheduleEntity.toDto(locale));
        }

        LoanBO loan = this.loanDao.findById(accountId);
        return new OriginalScheduleInfoDto(loan.getLoanAmount().toString(),loan.getDisbursementDate(),repaymentScheduleInstallments);
    }

    private void validateForRepaymentCapacity(List<RepaymentScheduleInstallment> installments, CashFlowForm cashFlowForm, Double repaymentCapacity, Errors errors) {
        if (cashFlowForm == null || CollectionUtils.isEmpty(installments) || repaymentCapacity == null || repaymentCapacity == 0) {
            return;
        }
        BigDecimal totalInstallmentAmount = BigDecimal.ZERO;
        for (RepaymentScheduleInstallment installment : installments) {
            totalInstallmentAmount = totalInstallmentAmount.add(installment.getTotalValue().getAmount());
        }
        Double calculatedRepaymentCapacity = cashFlowForm.computeRepaymentCapacity(totalInstallmentAmount).doubleValue();
        if (calculatedRepaymentCapacity < repaymentCapacity) {
            errors.addError(AccountConstants.REPAYMENT_CAPACITY_LESS_THAN_ALLOWED, new String[]{calculatedRepaymentCapacity.toString(), repaymentCapacity.toString()});
        }
    }

}
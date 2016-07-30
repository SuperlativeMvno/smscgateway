/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012. 
 * and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.smsc.slee.services.mt;

import java.util.ArrayList;
import java.util.Date;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.slee.ActivityContextInterface;
import javax.slee.Sbb;
import javax.slee.SbbContext;

import org.mobicents.protocols.ss7.indicator.NatureOfAddress;
import org.mobicents.protocols.ss7.indicator.NumberingPlan;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContext;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextName;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.mobicents.protocols.ss7.map.api.MAPParameterFactory;
import org.mobicents.protocols.ss7.map.api.MAPProvider;
import org.mobicents.protocols.ss7.map.api.MAPSmsTpduParameterFactory;
import org.mobicents.protocols.ss7.map.api.dialog.MAPUserAbortChoice;
import org.mobicents.protocols.ss7.map.api.dialog.ProcedureCancellationReason;
import org.mobicents.protocols.ss7.map.api.dialog.ResourceUnavailableReason;
import org.mobicents.protocols.ss7.map.api.errors.MAPErrorMessage;
import org.mobicents.protocols.ss7.map.api.primitives.AddressNature;
import org.mobicents.protocols.ss7.map.api.primitives.AddressString;
import org.mobicents.protocols.ss7.map.api.primitives.ISDNAddressString;
import org.mobicents.protocols.ss7.map.api.service.sms.SMDeliveryOutcome;
import org.mobicents.protocols.ss7.sccp.impl.parameter.ParameterFactoryImpl;
import org.mobicents.protocols.ss7.sccp.parameter.ParameterFactory;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.asn.comp.Problem;
import org.mobicents.slee.ChildRelationExt;
import org.mobicents.slee.resource.map.MAPContextInterfaceFactory;
import org.mobicents.slee.resource.map.events.DialogAccept;
import org.mobicents.slee.resource.map.events.DialogClose;
import org.mobicents.slee.resource.map.events.DialogDelimiter;
import org.mobicents.slee.resource.map.events.DialogNotice;
import org.mobicents.slee.resource.map.events.DialogProviderAbort;
import org.mobicents.slee.resource.map.events.DialogReject;
import org.mobicents.slee.resource.map.events.DialogRelease;
import org.mobicents.slee.resource.map.events.DialogRequest;
import org.mobicents.slee.resource.map.events.DialogTimeout;
import org.mobicents.slee.resource.map.events.DialogUserAbort;
import org.mobicents.slee.resource.map.events.ErrorComponent;
import org.mobicents.slee.resource.map.events.InvokeTimeout;
import org.mobicents.slee.resource.map.events.RejectComponent;
import org.mobicents.smsc.domain.SmscStatAggregator;
import org.mobicents.smsc.library.CdrGenerator;
import org.mobicents.smsc.library.ErrorAction;
import org.mobicents.smsc.library.ErrorCode;
import org.mobicents.smsc.library.MessageUtil;
import org.mobicents.smsc.library.Sms;
import org.mobicents.smsc.library.SmsSet;
import org.mobicents.smsc.library.TargetAddress;
import org.mobicents.smsc.slee.resources.persistence.PersistenceRAInterface;
import org.mobicents.smsc.slee.resources.scheduler.SchedulerActivity;
import org.mobicents.smsc.slee.resources.scheduler.SchedulerRaSbbInterface;
import org.mobicents.smsc.slee.services.deliverysbb.DeliveryCommonSbb;
import org.mobicents.smsc.slee.services.smpp.server.events.SendRsdsEvent;

/**
 * 
 * @author amit bhayani
 * @author sergey vetyutnev
 * 
 */
public abstract class MtCommonSbb extends DeliveryCommonSbb implements Sbb, ReportSMDeliveryStatusInterface2 {

//	private static final ResourceAdaptorTypeID PERSISTENCE_ID = new ResourceAdaptorTypeID(
//			"PersistenceResourceAdaptorType", "org.mobicents", "1.0");
//	private static final ResourceAdaptorTypeID SCHEDULE_ID = new ResourceAdaptorTypeID("SchedulerResourceAdaptorType",
//			"org.mobicents", "1.0");
//	private static final String PERSISTENCE_LINK = "PersistenceResourceAdaptor";
//	private static final String SCHEDULE_LINK = "SchedulerResourceAdaptor";

	protected static final String MAP_USER_ABORT_CHOICE_USER_SPECIFIC_REASON = "userSpecificReason";
	protected static final String MAP_USER_ABORT_CHOICE_USER_RESOURCE_LIMITATION = "userResourceLimitation";
	protected static final String MAP_USER_ABORT_CHOICE_UNKNOWN = "DialogUserAbort_Unknown";

//	protected static final SmscPropertiesManagement smscPropertiesManagement = SmscPropertiesManagement.getInstance();

//	private final String className;

//	protected Tracer logger;
//	protected SbbContextExt sbbContext;

	protected MAPContextInterfaceFactory mapAcif;
	protected MAPProvider mapProvider;
	protected MAPParameterFactory mapParameterFactory;
	protected MAPSmsTpduParameterFactory mapSmsTpduParameterFactory;
	protected ParameterFactory sccpParameterFact;

	private AddressString serviceCenterAddress;
	private SccpAddress serviceCenterSCCPAddress = null;

//	protected PersistenceRAInterface persistence;
//	protected SchedulerRaSbbInterface scheduler;
	protected SmscStatAggregator smscStatAggregator = SmscStatAggregator.getInstance();

    public MtCommonSbb(String className) {
        super(className);
    }

	public PersistenceRAInterface getStore() {
		return this.persistence;
	}

	public SchedulerRaSbbInterface getScheduler() {
		return this.scheduler;
	}

	/**
	 * MAP Components Events
	 */

	public void onErrorComponent(ErrorComponent event, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onErrorComponent(): CMP smsSet is missed");
            return;
        }

        if (this.logger.isInfoEnabled()) {
            this.logger.info("\nRx :  onErrorComponent " + event + " targetId=" + smsSet.getTargetId() + ", Dialog="
                    + event.getMAPDialog());
        }
	}

	public void onRejectComponent(RejectComponent event, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onErrorComponent(): CMP smsSet is missed");
            return;
        }

        this.logger.severe("\nRx :  onRejectComponent targetId=" + smsSet.getTargetId() + ", " + event);
	}

	protected String getRejectComponentReason(RejectComponent event) {
		Problem problem = event.getProblem();
		String reason = null;
		switch (problem.getType()) {
		case General:
			reason = problem.getGeneralProblemType().toString();
			break;
		case Invoke:
			reason = problem.getInvokeProblemType().toString();
			break;
		case ReturnResult:
			reason = problem.getReturnResultProblemType().toString();
			break;
		case ReturnError:
			reason = problem.getReturnErrorProblemType().toString();
			break;
		default:
			reason = "RejectComponent_unknown_" + problem.getType();
			break;
		}

		try {
			event.getMAPDialog().close(false);
		} catch (Exception e) {
		}

		return reason;
	}

	public void onInvokeTimeout(InvokeTimeout evt, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onInvokeTimeout(): CMP smsSet is missed");
            return;
        }

		if (logger.isWarningEnabled()) {
			this.logger.warning("\nRx : onInvokeTimeout targetId=" + smsSet.getTargetId() + ", " + evt);
		}
	}

	/**
	 * Dialog Events
	 */

	public void onDialogReject(DialogReject evt, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onDialogReject(): CMP smsSet is missed");
            return;
        }

		if (logger.isWarningEnabled()) {
			this.logger.warning("\nRx : onDialogReject targetId=" + smsSet.getTargetId() + ", " + evt);
		}
	}

	public void onDialogProviderAbort(DialogProviderAbort evt, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onDialogProviderAbort(): CMP smsSet is missed");
            return;
        }

		if (logger.isWarningEnabled()) {
			this.logger.warning("\nRx :  onDialogProviderAbort targetId=" + smsSet.getTargetId() + ", " + evt);
		}
	}

	public void onDialogUserAbort(DialogUserAbort evt, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onDialogUserAbort(): CMP smsSet is missed");
            return;
        }

		if (logger.isWarningEnabled()) {
			this.logger.warning("\nRx :  onDialogUserAbort targetId=" + smsSet.getTargetId() + ", " + evt);
		}
	}

	protected String getUserAbortReason(DialogUserAbort evt) {
		MAPUserAbortChoice userReason = evt.getUserReason();
		String reason = null;
		if (userReason.isUserSpecificReason()) {
			reason = MAP_USER_ABORT_CHOICE_USER_SPECIFIC_REASON;
		} else if (userReason.isUserResourceLimitation()) {
			reason = MAP_USER_ABORT_CHOICE_USER_RESOURCE_LIMITATION;
		} else if (userReason.isResourceUnavailableReason()) {
			ResourceUnavailableReason resourceUnavailableReason = userReason.getResourceUnavailableReason();
			reason = resourceUnavailableReason.toString();
		} else if (userReason.isProcedureCancellationReason()) {
			ProcedureCancellationReason procedureCancellationReason = userReason.getProcedureCancellationReason();
			reason = procedureCancellationReason.toString();
		} else {
			reason = MAP_USER_ABORT_CHOICE_UNKNOWN;
		}
		return reason;
	}

	public void onDialogTimeout(DialogTimeout evt, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onDialogTimeout(): CMP smsSet is missed");
            return;
        }

		if (logger.isWarningEnabled()) {
			this.logger.warning("\nRx :  onDialogTimeout targetId=" + smsSet.getTargetId() + ", " + evt);
		}
	}

	public void onDialogDelimiter(DialogDelimiter evt, ActivityContextInterface aci) {
		if (logger.isFineEnabled()) {
			this.logger.fine("\nRx :  onDialogDelimiter " + evt);
		}
	}

	public void onDialogAccept(DialogAccept evt, ActivityContextInterface aci) {
		if (logger.isFineEnabled()) {
			this.logger.fine("\nRx :  onDialogAccept=" + evt);
		}
	}

	public void onDialogClose(DialogClose evt, ActivityContextInterface aci) {
		if (logger.isFineEnabled()) {
			this.logger.fine("\nRx :  onDialogClose=" + evt);
		}
	}

	public void onDialogNotice(DialogNotice evt, ActivityContextInterface aci) {
//        SmsSubmitData smsDeliveryData = this.doGetSmsSubmitData();
//        String targetId = null;
//        if (smsDeliveryData != null) {
//            targetId = smsDeliveryData.getTargetId();
//        }

        SmsSet smsSet = getSmsSet();
        if (smsSet == null) {
            logger.severe("MtCommonSbb.onDialogNotice(): CMP smsSet is missed");
            return;
        }

		if (logger.isWarningEnabled()) {
			this.logger.warning("\nRx :  onDialogNotice targetId=" + smsSet.getTargetId() + ", " + evt);
		}
	}

	public void onDialogRequest(DialogRequest evt, ActivityContextInterface aci) {
		if (logger.isFineEnabled()) {
			this.logger.fine("\nRx :  onDialogRequest=" + evt);
		}
	}

	public void onDialogRelease(DialogRelease evt, ActivityContextInterface aci) {
		if (logger.isInfoEnabled()) {
			this.logger.info("\nRx :  DialogRelease=" + evt);
		}
	}

	/**
	 * Sbb ACI
	 */

	/**
	 * TODO : This is repetitive in each Sbb. Find way to make it static
	 * probably?
	 * 
	 * This is our own number. We are Service Center.
	 * 
	 * @return
	 */
	protected AddressString getServiceCenterAddressString(int networkId) {
        if (networkId == 0) {
            if (this.serviceCenterAddress == null) {
                this.serviceCenterAddress = this.mapParameterFactory.createAddressString(AddressNature.international_number,
                        org.mobicents.protocols.ss7.map.api.primitives.NumberingPlan.ISDN, smscPropertiesManagement.getServiceCenterGt());
            }
            return this.serviceCenterAddress;
        } else {
            return this.mapParameterFactory.createAddressString(AddressNature.international_number,
                    org.mobicents.protocols.ss7.map.api.primitives.NumberingPlan.ISDN, smscPropertiesManagement.getServiceCenterGt(networkId));
        }
	}

	/**
	 * TODO: This should be configurable and static as well
	 * 
	 * This is our (Service Center) SCCP Address for GT
	 * 
	 * @return
	 */
	protected SccpAddress getServiceCenterSccpAddress(int networkId) {
        if (networkId == 0) {
            if (this.serviceCenterSCCPAddress == null) {
                this.serviceCenterSCCPAddress = MessageUtil.getSccpAddress(sccpParameterFact, smscPropertiesManagement.getServiceCenterGt(),
                        NatureOfAddress.INTERNATIONAL.getValue(), NumberingPlan.ISDN_TELEPHONY.getValue(), smscPropertiesManagement.getServiceCenterSsn(),
                        smscPropertiesManagement.getGlobalTitleIndicator(), smscPropertiesManagement.getTranslationType());
            }
            return this.serviceCenterSCCPAddress;
        } else {
            return MessageUtil.getSccpAddress(sccpParameterFact, smscPropertiesManagement.getServiceCenterGt(networkId),
                    NatureOfAddress.INTERNATIONAL.getValue(), NumberingPlan.ISDN_TELEPHONY.getValue(), smscPropertiesManagement.getServiceCenterSsn(),
                    smscPropertiesManagement.getGlobalTitleIndicator(), smscPropertiesManagement.getTranslationType());
        }
	}

	protected ISDNAddressString getCalledPartyISDNAddressString(String destinationAddress, int ton, int npi) {
		return this.mapParameterFactory.createISDNAddressString(AddressNature.getInstance(ton),
				org.mobicents.protocols.ss7.map.api.primitives.NumberingPlan.getInstance(npi), destinationAddress);
	}

    protected void onDeliveryError(SmsSet smsSet, ErrorAction errorAction, ErrorCode smStatus, String reason,
            boolean removeSmsSet, MAPErrorMessage errMessage, boolean isImsiVlrReject) {
        smscStatAggregator.updateMsgOutFailedAll();

        // generating of a temporary failure CDR
        this.generateTemporaryFailureCDR(CdrGenerator.CDR_TEMP_FAILED, reason);
//        long currentMsgNum = this.doGetCurrentMsgNum();
//		Sms smsa = smsSet.getSms(currentMsgNum);
//        if (smsa != null) {
//            CdrGenerator.generateCdr(smsa, CdrGenerator.CDR_TEMP_FAILED, reason, smscPropertiesManagement.getGenerateReceiptCdr(),
//                    MessageUtil.isNeedWriteArchiveMessage(smsa, smscPropertiesManagement.getGenerateCdr()));
//        }

		StringBuilder sb = new StringBuilder();
		sb.append("onDeliveryError: errorAction=");
        sb.append(errorAction);
        sb.append(", smStatus=");
        sb.append(smStatus);
        sb.append(", targetId=");
        sb.append(smsSet.getTargetId());
		sb.append(", smsSet=");
		sb.append(smsSet);
        sb.append(", reason=");
        sb.append(reason);
		if (this.logger.isInfoEnabled())
			this.logger.info(sb.toString());

        // sending of a failure response for transactional mode
//        MessageDeliveryResultResponseInterface.DeliveryFailureReason delReason = MessageDeliveryResultResponseInterface.DeliveryFailureReason.destinationUnavalable;
//        if (errorAction == ErrorAction.temporaryFailure)
//            delReason = MessageDeliveryResultResponseInterface.DeliveryFailureReason.temporaryNetworkError;
//        if (errorAction == ErrorAction.permanentFailure)
//            delReason = MessageDeliveryResultResponseInterface.DeliveryFailureReason.permanentNetworkError;
//        for (long i1 = currentMsgNum; i1 < smsSet.getSmsCount(); i1++) {
//            Sms sms = smsSet.getSms(i1);
//            if (sms != null) {
//                if (sms.getMessageDeliveryResultResponse() != null) {
//                    sms.getMessageDeliveryResultResponse().responseDeliveryFailure(delReason, errMessage);
//                    sms.setMessageDeliveryResultResponse(null);
//                }
//            }
//        }

//		ArrayList<Sms> lstFailured = new ArrayList<Sms>();

		PersistenceRAInterface pers = this.getStore();
        ArrayList<Sms> lstPermFailured = new ArrayList<Sms>();
        ArrayList<Sms> lstTempFailured = new ArrayList<Sms>();
        ArrayList<Sms> lstRerouted = new ArrayList<Sms>();

//		SMDeliveryOutcome smDeliveryOutcome = null;
		TargetAddress lock = pers.obtainSynchroObject(new TargetAddress(smsSet));
		synchronized (lock) {
			try {
				Date curDate = new Date();
//				try {

                smsSet.setStatus(smStatus);
                this.markDeliveringIsEnded(removeSmsSet);
//                    if (removeSmsSet)
//                        SmsSetCache.getInstance().removeProcessingSmsSet(smsSet.getTargetId());

                // creating of failure lists
                this.createFailureLists(lstPermFailured, lstTempFailured, errorAction);

                // sending of a failure response for transactional mode
                this.sendTransactionalResponseFailure(lstPermFailured, lstTempFailured, errorAction, errMessage);

                // mproc rules applying for delivery phase
                this.applyMprocRulesOnFailure(lstPermFailured, lstTempFailured, lstRerouted);

                // Processing messages that were temp or permanent failed or rerouted
                this.postProcessPermFailures(lstPermFailured);
                this.postProcessTempFailures(smsSet, lstTempFailured, (errorAction == ErrorAction.subscriberBusy), true);
                this.postProcessRerouted(lstRerouted);

                // generating CDRs for permanent failure messages
                this.generateCDRs(lstPermFailured, (isImsiVlrReject ? CdrGenerator.CDR_FAILED_IMSI : CdrGenerator.CDR_FAILED), reason);

                // sending of intermediate delivery receipts
                this.generateIntermediateReceipts(smsSet, lstTempFailured);

                // sending of failure delivery receipts
                this.generateFailureReceipts(smsSet, lstPermFailured, null);


//                    switch (errorAction) {
//                    case memoryCapacityExceededFlag:
//                        smDeliveryOutcome = SMDeliveryOutcome.memoryCapacityExceeded;
//                        break;
//
//                    case mobileNotReachableFlag:
//                        smDeliveryOutcome = SMDeliveryOutcome.absentSubscriber;
//                        break;
//
//                    case notReachableForGprs:
//                        smDeliveryOutcome = SMDeliveryOutcome.absentSubscriber;
//                        break;
//                    }

//                long smsCnt;
//
//                switch (errorAction) {
//                    case subscriberBusy:
//                        this.rescheduleSmsSet(smsSet, true, pers, currentMsgNum, lstFailured);
//                        break;
//
//                    case memoryCapacityExceededFlag:
//                    case mobileNotReachableFlag:
//                    case notReachableForGprs:
//                    case temporaryFailure:
//                        this.rescheduleSmsSet(smsSet, false, pers, currentMsgNum, lstFailured);
//                        break;
//
//                    case permanentFailure:
//                        smsCnt = smsSet.getSmsCount();
//                        for (long i1 = currentMsgNum; i1 < smsCnt; i1++) {
//                            Sms sms = smsSet.getSms(i1);
//                            if (sms != null) {
//                                lstFailured.add(sms);
//                            }
//                        }
//                        this.freeSmsSetFailured(smsSet, pers, currentMsgNum);
//                        break;
//                }

//				} catch (PersistenceException e) {
//					this.logger.severe("PersistenceException when onDeliveryError()" + e.getMessage(), e);
//				}

			} finally {
				pers.releaseSynchroObject(lock);
			}
		}


//		if ( errorAction != ErrorAction.permanentFailure &&
//						!smscPropertiesManagement.getReceiptsDisabling() ) {
//				doIntermediateReceipts(smsSet, pers, currentMsgNum, lstFailured);
//		}


//        for (Sms sms : lstFailured) {
//            CdrGenerator.generateCdr(sms, (isImsiVlrReject ? CdrGenerator.CDR_FAILED_IMSI : CdrGenerator.CDR_FAILED), reason,
//                    smscPropertiesManagement.getGenerateReceiptCdr(),
//                    MessageUtil.isNeedWriteArchiveMessage(sms, smscPropertiesManagement.getGenerateCdr()));

//            sms.setDeliveryDate(new Date());
//            try {
//                if (MessageUtil.isNeedWriteArchiveMessage(sms, smscPropertiesManagement.getGenerateArchiveTable())) {
//                    pers.c2_createRecordArchive(sms);
//                }
//            } catch (PersistenceException e) {
//                this.logger.severe("PersistenceException when freeSmsSetFailured(SmsSet smsSet) - c2_createRecordArchive(sms)"
//                        + e.getMessage(), e);
//            }

            // mproc rules applying for delivery phase
//            MProcResult mProcResult = MProcManagement.getInstance().applyMProcDelivery(sms, true);
//            FastList<Sms> addedMessages = mProcResult.getMessageList();
//            if (addedMessages != null) {
//                for (FastList.Node<Sms> n = addedMessages.head(), end = addedMessages.tail(); (n = n.getNext()) != end;) {
//                    Sms smst = n.getValue();
//                    TargetAddress ta = new TargetAddress(smst.getSmsSet().getDestAddrTon(), smst.getSmsSet().getDestAddrNpi(),
//                            smst.getSmsSet().getDestAddr(), smst.getSmsSet().getNetworkId());
//                    TargetAddress lock2 = SmsSetCache.getInstance().addSmsSet(ta);
//                    try {
//                        synchronized (lock2) {
//                            if (smscPropertiesManagement.getDatabaseType() == DatabaseType.Cassandra_1) {
//                            } else {
//                                boolean storeAndForwMode = MessageUtil.isStoreAndForward(smst);
//                                if (!storeAndForwMode) {
//                                    try {
//                                        this.scheduler.injectSmsOnFly(smst.getSmsSet(), true);
//                                    } catch (Exception e) {
//                                        this.logger.severe(
//                                                "Exception when runnung injectSmsOnFly() for applyMProcDelivery created messages in onDeliveryError(): "
//                                                        + e.getMessage(), e);
//                                    }
//                                } else {
//                                    if (smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast) {
//                                        try {
//                                            smst.setStoringAfterFailure(true);
//                                            this.scheduler.injectSmsOnFly(smst.getSmsSet(), true);
//                                        } catch (Exception e) {
//                                            this.logger.severe(
//                                                    "Exception when runnung injectSmsOnFly() for applyMProcDelivery created messages in onDeliveryError(): "
//                                                            + e.getMessage(), e);
//                                        }
//                                    } else {
//                                        smst.setStored(true);
//                                        this.scheduler.setDestCluster(smst.getSmsSet());
//                                        try {
//                                            pers.c2_scheduleMessage_ReschedDueSlot(
//                                                    smst,
//                                                    smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast,
//                                                    true);
//                                        } catch (PersistenceException e) {
//                                            this.logger.severe(
//                                                    "PersistenceException when onDeliveryError(SmsSet smsSet) - adding applyMProcDelivery created messages"
//                                                            + e.getMessage(), e);
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    } finally {
//                        SmsSetCache.getInstance().removeSmsSet(lock2);
//                    }
//                }
//            }

            // adding an error receipt if it is needed
//            int registeredDelivery = sms.getRegisteredDelivery();
//            if (!smscPropertiesManagement.getReceiptsDisabling() && MessageUtil.isReceiptOnFailure(registeredDelivery)) {
//                TargetAddress ta = new TargetAddress(sms.getSourceAddrTon(), sms.getSourceAddrNpi(), sms.getSourceAddr(), smsSet.getNetworkId());
//                lock = SmsSetCache.getInstance().addSmsSet(ta);
//                try {
//                    synchronized (lock) {
//                        try {
//                            Sms receipt;
//                            if (smscPropertiesManagement.getDatabaseType() == DatabaseType.Cassandra_1) {
//                                receipt = MessageUtil.createReceiptSms(sms, false);
//                                SmsSet backSmsSet = pers.obtainSmsSet(ta);
//                                receipt.setSmsSet(backSmsSet);
//                                receipt.setStored(true);
//                                pers.createLiveSms(receipt);
//                                pers.setNewMessageScheduled(receipt.getSmsSet(),
//                                        MessageUtil.computeDueDate(MessageUtil.computeFirstDueDelay(smscPropertiesManagement.getFirstDueDelay())));
//                            } else {
//                                receipt = MessageUtil.createReceiptSms(sms, false, ta, smscPropertiesManagement.getOrigNetworkIdForReceipts());
//                                boolean storeAndForwMode = MessageUtil.isStoreAndForward(sms);
//                                if (!storeAndForwMode) {
//                                    try {
//                                        this.scheduler.injectSmsOnFly(receipt.getSmsSet(), true);
//                                    } catch (Exception e) {
//                                        this.logger.severe("Exception when runnung injectSmsOnFly() for receipt in onDeliveryError(): " + e.getMessage(), e);
//                                    }
//                                } else {
//                                    if (smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast) {
//                                        try {
//                                            receipt.setStoringAfterFailure(true);
//                                            this.scheduler.injectSmsOnFly(receipt.getSmsSet(), true);
//                                        } catch (Exception e) {
//                                            this.logger
//                                                    .severe("Exception when runnung injectSmsOnFly() for receipt in onDeliveryError(): " + e.getMessage(), e);
//                                        }
//                                    } else {
//                                        receipt.setStored(true);
//                                        this.scheduler.setDestCluster(receipt.getSmsSet());
//                                        pers.c2_scheduleMessage_ReschedDueSlot(receipt,
//                                                smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast, true);
//                                    }
//                                }
//                            }
//                            this.logger.info("Adding an error receipt: source=" + receipt.getSourceAddr() + ", dest=" + receipt.getSmsSet().getDestAddr());
//                        } catch (PersistenceException e) {
//                            this.logger.severe("PersistenceException when freeSmsSetFailured(SmsSet smsSet) - adding delivery receipt" + e.getMessage(), e);
//                        }
//                    }
//                } finally {
//                    SmsSetCache.getInstance().removeSmsSet(lock);
//                }
//            }
//		}

        // sending of ReportSMDeliveryStatusRequest if needed
        SMDeliveryOutcome smDeliveryOutcome = null;
        switch (errorAction) {
        case memoryCapacityExceededFlag:
            smDeliveryOutcome = SMDeliveryOutcome.memoryCapacityExceeded;
            break;

        case mobileNotReachableFlag:
            smDeliveryOutcome = SMDeliveryOutcome.absentSubscriber;
            break;

        case notReachableForGprs:
            smDeliveryOutcome = SMDeliveryOutcome.absentSubscriber;
            break;
        }
//        if (smDeliveryOutcome != null && smsSet.getSmsCount() > lstFailured.size() ) {
        if (smDeliveryOutcome != null && lstTempFailured.size() > 0) {
            this.setupReportSMDeliveryStatusRequest(smsSet.getDestAddr(), smsSet.getDestAddrTon(), smsSet.getDestAddrNpi(),
                    smDeliveryOutcome, smsSet.getTargetId(), smsSet.getNetworkId());
        }

//        this.decrementDeliveryActivityCount();
	}

//	private void doIntermediateReceipts(SmsSet smsSet, PersistenceRAInterface pers, long currentMsgNum, ArrayList<Sms> lstFailured) {
//		TargetAddress lock;
//		long smsCnt = smsSet.getSmsCount();
//		for (long i1 = currentMsgNum; i1 < smsCnt; i1++) {
//            Sms sms = smsSet.getSms(i1);
//            int registeredDelivery = sms.getRegisteredDelivery();
//            if (smscPropertiesManagement.getEnableIntermediateReceipts()
//                    && MessageUtil.isReceiptIntermediate(registeredDelivery) && lstFailured.indexOf(sms) == -1) {
//                TargetAddress ta = new TargetAddress(sms.getSourceAddrTon(), sms.getSourceAddrNpi(), sms.getSourceAddr(), smsSet.getNetworkId());
//                lock = SmsSetCache.getInstance().addSmsSet(ta);
//                try {
//                    synchronized (lock) {
//                        try {
//                            Sms receipt;
//                            if (smscPropertiesManagement.getDatabaseType() == DatabaseType.Cassandra_1) {
//                                receipt = MessageUtil.createReceiptSms(sms, false);
//                                SmsSet backSmsSet = pers.obtainSmsSet(ta);
//                                receipt.setSmsSet(backSmsSet);
//                                receipt.setStored(true);
//                                pers.createLiveSms(receipt);
//                                pers.setNewMessageScheduled(receipt.getSmsSet(),
//                                        MessageUtil.computeDueDate(MessageUtil.computeFirstDueDelay(smscPropertiesManagement.getFirstDueDelay())));
//                            } else {
//                                receipt = MessageUtil.createReceiptSms(sms, false, ta, smscPropertiesManagement.getOrigNetworkIdForReceipts(),null,true);
//                                boolean storeAndForwMode = MessageUtil.isStoreAndForward(sms);
//                                if (!storeAndForwMode) {
//                                    try {
//                                        this.scheduler.injectSmsOnFly(receipt.getSmsSet(), true);
//                                    } catch (Exception e) {
//                                        this.logger.severe("Exception when runnung injectSmsOnFly() for receipt in onDeliveryError(): " + e.getMessage(), e);
//                                    }
//                                } else {
//                                    if (smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast) {
//                                        try {
//                                            receipt.setStoringAfterFailure(true);
//                                            this.scheduler.injectSmsOnFly(receipt.getSmsSet(), true);
//                                        } catch (Exception e) {
//                                            this.logger
//                                                    .severe("Exception when runnung injectSmsOnFly() for receipt in onDeliveryError(): " + e.getMessage(), e);
//                                        }
//                                    } else {
//                                        receipt.setStored(true);
//                                        this.scheduler.setDestCluster(receipt.getSmsSet());
//                                        pers.c2_scheduleMessage_ReschedDueSlot(receipt,
//                                                smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast, true);
//                                    }
//                                }
//                            }
//                            this.logger.info("Adding an error receipt: source=" + receipt.getSourceAddr() + ", dest=" + receipt.getSmsSet().getDestAddr());
//                        } catch (PersistenceException e) {
//                            this.logger.severe("PersistenceException when freeSmsSetFailured(SmsSet smsSet) - adding delivery receipt" + e.getMessage(), e);
//                        }
//                    }
//                } finally {
//                    SmsSetCache.getInstance().removeSmsSet(lock);
//                }
//            }
//        }
//	}

    protected void onImsiDrop(SmsSet smsSet, ArrayList<Sms> lstPermFailured, ArrayList<Sms> lstRerouted,
            ISDNAddressString networkNode, String imsiData) {
        Sms sms = this.getCurrentMessage(0);
        StringBuilder sb = new StringBuilder();
        sb.append("onImsiDrop: targetId=");
        sb.append(sms.getSmsSet().getTargetId());
        sb.append(", sms=");
        sb.append(sms);
        if (this.logger.isInfoEnabled())
            this.logger.info(sb.toString());

        smsSet.setStatus(ErrorCode.MPROC_SRI_REQUEST_DROP);

        // sending of a failure response for transactional mode
        this.sendTransactionalResponseFailure(lstPermFailured, null, ErrorAction.mobileNotReachableFlag, null);

        // generating CDRs for permanent failure messages
        this.generateCDRs(lstPermFailured, CdrGenerator.CDR_FAILED_IMSI, "Sri-ImsiRequest: incoming messages are dropped by mProc rules");

        // Processing messages that were temp or permanent failed or rerouted
        this.postProcessPermFailures(lstPermFailured);
        this.postProcessRerouted(lstRerouted);

        // adding an error receipt if it is needed
        StringBuilder extraString = new StringBuilder();
        extraString.append(" imsi:");
        extraString.append(imsiData);
        extraString.append(" nnn_digits:");
        extraString.append(networkNode.getAddress());
        extraString.append(" nnn_an:");
        extraString.append(networkNode.getAddressNature().getIndicator());
        extraString.append(" nnn_np:");
        extraString.append(networkNode.getNumberingPlan().getIndicator());
        this.generateFailureReceipts(smsSet, lstPermFailured, extraString.toString());

        // sending of a failure response for transactional mode
//        MessageDeliveryResultResponseInterface.DeliveryFailureReason delReason = MessageDeliveryResultResponseInterface.DeliveryFailureReason.destinationUnavalable;
//        if (sms.getMessageDeliveryResultResponse() != null) {
//            sms.getMessageDeliveryResultResponse().responseDeliveryFailure(delReason, null);
//            sms.setMessageDeliveryResultResponse(null);
//        }

        // generating CDRs for permanent failure messages
//        CdrGenerator.generateCdr(sms, CdrGenerator.CDR_FAILED_IMSI,
//                "Sri-ImsiRequest: incoming messages are dropped by mProc rules",
//                smscPropertiesManagement.getGenerateReceiptCdr(),
//                MessageUtil.isNeedWriteArchiveMessage(sms, smscPropertiesManagement.getGenerateCdr()));

//        try {
//            pers.c2_updateInSystem(sms, DBOperations_C2.IN_SYSTEM_SENT,
//                    smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast);
//            sms.getSmsSet().setStatus(ErrorCode.MPROC_SRI_REQUEST_DROP);
//            sms.setDeliveryDate(new Date());
//            if (MessageUtil.isNeedWriteArchiveMessage(sms, smscPropertiesManagement.getGenerateArchiveTable())) {
//                pers.c2_createRecordArchive(sms);
//            }
//        } catch (PersistenceException e) {
//            this.logger.severe("PersistenceException when onImsiDrop() - c2_createRecordArchive" + e.getMessage(), e);
//        }

        // adding an error receipt if it is needed
//        int registeredDelivery = sms.getRegisteredDelivery();
//        if (!smscPropertiesManagement.getReceiptsDisabling() && MessageUtil.isReceiptOnFailure(registeredDelivery)) {
//            TargetAddress ta = new TargetAddress(sms.getSourceAddrTon(), sms.getSourceAddrNpi(), sms.getSourceAddr(), sms
//                    .getSmsSet().getNetworkId());
//            TargetAddress lock = SmsSetCache.getInstance().addSmsSet(ta);
//            try {
//                synchronized (lock) {
//                    try {
//                        Sms receipt;
//                        StringBuilder extraString = new StringBuilder();
//                        extraString.append(" imsi:");
//                        extraString.append(imsiData);
//                        extraString.append(" nnn_digits:");
//                        extraString.append(networkNode.getAddress());
//                        extraString.append(" nnn_an:");
//                        extraString.append(networkNode.getAddressNature().getIndicator());
//                        extraString.append(" nnn_np:");
//                        extraString.append(networkNode.getNumberingPlan().getIndicator());
//                        if (smscPropertiesManagement.getDatabaseType() == DatabaseType.Cassandra_1) {
//                            receipt = MessageUtil.createReceiptSms(sms, false);
//                            SmsSet backSmsSet = pers.obtainSmsSet(ta);
//                            receipt.setSmsSet(backSmsSet);
//                            receipt.setStored(true);
//                            pers.createLiveSms(receipt);
//                            pers.setNewMessageScheduled(receipt.getSmsSet(), MessageUtil.computeDueDate(MessageUtil
//                                    .computeFirstDueDelay(smscPropertiesManagement.getFirstDueDelay())));
//                        } else {
//                            receipt = MessageUtil.createReceiptSms(sms, false, ta,
//                                    smscPropertiesManagement.getOrigNetworkIdForReceipts(), extraString.toString());
//                            boolean storeAndForwMode = MessageUtil.isStoreAndForward(sms);
//                            if (!storeAndForwMode) {
//                                try {
//                                    this.scheduler.injectSmsOnFly(receipt.getSmsSet(), true);
//                                } catch (Exception e) {
//                                    this.logger.severe(
//                                            "Exception when runnung injectSmsOnFly() for receipt in onImsiDrop(): "
//                                                    + e.getMessage(), e);
//                                }
//                            } else {
//                                if (smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast) {
//                                    try {
//                                        receipt.setStoringAfterFailure(true);
//                                        this.scheduler.injectSmsOnFly(receipt.getSmsSet(), true);
//                                    } catch (Exception e) {
//                                        this.logger.severe(
//                                                "Exception when runnung injectSmsOnFly() for receipt in onImsiDrop(): "
//                                                        + e.getMessage(), e);
//                                    }
//                                } else {
//                                    receipt.setStored(true);
//                                    this.scheduler.setDestCluster(receipt.getSmsSet());
//                                    pers.c2_scheduleMessage_ReschedDueSlot(receipt,
//                                            smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast, true);
//                                }
//                            }
//                        }
//                        this.logger.info("Adding an error receipt: source=" + receipt.getSourceAddr() + ", dest="
//                                + receipt.getSmsSet().getDestAddr());
//                    } catch (PersistenceException e) {
//                        this.logger.severe(
//                                "PersistenceException when onImsiDrop(Set) - adding delivery receipt"
//                                        + e.getMessage(), e);
//                    }
//                }
//            } finally {
//                SmsSetCache.getInstance().removeSmsSet(lock);
//            }
//        }
    }

    /**
     * Get Rsds child SBB
     * 
     * @return
     */
    public abstract ChildRelationExt getRsdsSbb();

    public abstract void fireSendRsdsEvent(SendRsdsEvent event, ActivityContextInterface aci, javax.slee.Address address);


    private RsdsSbbLocalObject getRsdsSbbObject() {
        ChildRelationExt relation = getRsdsSbb();

        RsdsSbbLocalObject ret = (RsdsSbbLocalObject) relation.get(ChildRelationExt.DEFAULT_CHILD_NAME);
        if (ret == null) {
            try {
                ret = (RsdsSbbLocalObject) relation.create(ChildRelationExt.DEFAULT_CHILD_NAME);
            } catch (Exception e) {
                if (this.logger.isSevereEnabled()) {
                    this.logger.severe("Exception while trying to creat RsdsSbb child", e);
                }
            }
        }
        return ret;
    }

    protected void setupReportSMDeliveryStatusRequest(String destinationAddress, int ton, int npi,
            SMDeliveryOutcome sMDeliveryOutcome, String targetId, int networkId) {
        RsdsSbbLocalObject rsdsSbbLocalObject = this.getRsdsSbbObject();

        if (rsdsSbbLocalObject != null) {
            ActivityContextInterface schedulerActivityContextInterface = this.getSchedulerActivityContextInterface();
            schedulerActivityContextInterface.attach(rsdsSbbLocalObject);

            SendRsdsEvent event = new SendRsdsEvent();
            event.setMsisdn(this.getCalledPartyISDNAddressString(destinationAddress, ton, npi));
            event.setServiceCentreAddress(getServiceCenterAddressString(networkId));
            event.setSMDeliveryOutcome(sMDeliveryOutcome);
            event.setDestAddress(this.convertAddressFieldToSCCPAddress(destinationAddress, ton, npi));
            event.setMapApplicationContext(this.getSRIMAPApplicationContext(MAPApplicationContextVersion.getInstance(this.getSriMapVersion())));
            event.setTargetId(targetId);
            event.setNetworkId(networkId);

            this.fireSendRsdsEvent(event, schedulerActivityContextInterface, null);
        }
    }

    protected SccpAddress convertAddressFieldToSCCPAddress(String address, int ton, int npi) {
        return MessageUtil.getSccpAddress(sccpParameterFact, address, ton, npi, smscPropertiesManagement.getHlrSsn(),
                smscPropertiesManagement.getGlobalTitleIndicator(), smscPropertiesManagement.getTranslationType());
    }

    protected MAPApplicationContext getSRIMAPApplicationContext(MAPApplicationContextVersion applicationContextVersion) {
        MAPApplicationContext mapApplicationContext = MAPApplicationContext.getInstance(
                MAPApplicationContextName.shortMsgGatewayContext, applicationContextVersion);
        this.setSriMapVersion(applicationContextVersion.getVersion());
        return mapApplicationContext;
    }


//	/**
//	 * Mark a message that its delivery has been started
//	 * 
//	 * @param sms
//	 */
//	protected void startMessageDelivery(Sms sms) {
//
//		try {
//			this.getStore().setDeliveryStart(sms);
//		} catch (PersistenceException e) {
//			this.logger.severe("PersistenceException when setDeliveryStart(sms)" + e.getMessage(), e);
//		}
//	}

//	/**
//	 * remove smsSet from LIVE database after all messages has been delivered
//	 * 
//	 * @param smsSet
//	 */
//	protected void freeSmsSetSucceded(SmsSet smsSet, PersistenceRAInterface pers) {
//        try {
//            this.decrementDeliveryActivityCount();
//
//            smsSet.setStatus(ErrorCode.SUCCESS);
//            SmsSetCache.getInstance().removeProcessingSmsSet(smsSet.getTargetId());
//        } catch (PersistenceException e) {
//            this.logger.severe("PersistenceException when freeSmsSetSucceded(SmsSet smsSet)" + e.getMessage(), e);
//        }
//	}

//	/**
//	 * remove smsSet from LIVE database after permanent delivery failure
//	 * 
//	 * @param smsSet
//	 * @param pers
//	 */
//	protected void freeSmsSetFailured(SmsSet smsSet, PersistenceRAInterface pers, long currentMsgNum) {
//
//		TargetAddress lock = pers.obtainSynchroObject(new TargetAddress(smsSet));
//		try {
//			synchronized (lock) {
//				try {
//                    if (smscPropertiesManagement.getDatabaseType() == DatabaseType.Cassandra_1) {
//                        pers.fetchSchedulableSms(smsSet, false);
//                        long cnt = smsSet.getSmsCount();
//                        for (int i1 = 0; i1 < cnt; i1++) {
//                            Sms sms = smsSet.getSms(i1);
//                            pers.archiveFailuredSms(sms);
//                        }
//
//                        pers.deleteSmsSet(smsSet);
//                    } else {
//                        for (long i1 = currentMsgNum; i1 < smsSet.getSmsCount(); i1++) {
//                            Sms sms = smsSet.getSms(i1);
//                            pers.c2_updateInSystem(sms, DBOperations_C2.IN_SYSTEM_SENT,
//                                    smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast);
//                            sms.setDeliveryDate(new Date());
//                            if (MessageUtil.isNeedWriteArchiveMessage(sms, smscPropertiesManagement.getGenerateArchiveTable())) {
//                                pers.c2_createRecordArchive(sms);
//                            }
//                        }
//                    }
//				} catch (PersistenceException e) {
//					this.logger.severe("PersistenceException when freeSmsSetFailured(SmsSet smsSet)" + e.getMessage(),
//							e);
//				}
//			}
//		} finally {
//			pers.releaseSynchroObject(lock);
//		}
//	}

//	/**
//	 * make new schedule time for smsSet after temporary failure
//	 * 
//	 * @param smsSet
//	 */
//    protected void rescheduleSmsSet(SmsSet smsSet, boolean busySuscriber, PersistenceRAInterface pers, long currentMsgNum,
//            ArrayList<Sms> lstFailured) {
//		TargetAddress lock = pers.obtainSynchroObject(new TargetAddress(smsSet));
//		try {
//			synchronized (lock) {
//
//				try {
//					int prevDueDelay = smsSet.getDueDelay();
//					int newDueDelay;
//					if (busySuscriber) { // ................
//                        newDueDelay = MessageUtil.computeDueDelaySubscriberBusy(smscPropertiesManagement.getSubscriberBusyDueDelay());
//					} else {
//                        newDueDelay = MessageUtil.computeNextDueDelay(prevDueDelay, smscPropertiesManagement.getSecondDueDelay(),
//                                smscPropertiesManagement.getDueDelayMultiplicator(), smscPropertiesManagement.getMaxDueDelay());
//					}
//
//					Date newDueDate = new Date(new Date().getTime() + newDueDelay * 1000);
//					newDueDate = MessageUtil.checkScheduleDeliveryTime(smsSet, newDueDate); // ................
//                    smsSet.setDueDate(newDueDate);
//                    smsSet.setDueDelay(newDueDelay);
//                    long dueSlot = this.getStore().c2_getDueSlotForTime(newDueDate);
//                    for (long i1 = currentMsgNum; i1 < smsSet.getSmsCount(); i1++) {
//                        Sms sms = smsSet.getSms(i1);
//                        pers.c2_scheduleMessage_NewDueSlot(sms, dueSlot, lstFailured,
//                                smscPropertiesManagement.getStoreAndForwordMode() == StoreAndForwordMode.fast);
//                    }
//				} catch (PersistenceException e) {
//					this.logger.severe("PersistenceException when rescheduleSmsSet(SmsSet smsSet)" + e.getMessage(), e);
//				}
//			}
//		} finally {
//			pers.releaseSynchroObject(lock);
//		}
//	}

	/**
	 * Get the Scheduler Activity
	 * 
	 * @return
	 */
	protected ActivityContextInterface getSchedulerActivityContextInterface() {
		ActivityContextInterface[] acis = this.sbbContext.getActivities();
		for (int count = 0; count < acis.length; count++) {
			ActivityContextInterface aci = acis[count];
			Object activity = aci.getActivity();
			if (activity instanceof SchedulerActivity) {
				return aci;
			}
		}

		return null;
	}

//	private void decrementDeliveryActivityCount() {
//		try {
//			ActivityContextInterface schedulerActivityContextInterface = this.getSchedulerActivityContextInterface();
//			SchedulerActivity schedulerActivity = (SchedulerActivity) schedulerActivityContextInterface.getActivity();
//
//			schedulerActivity.endActivity();
//		} catch (Exception e) {
//			this.logger.severe("Error while decrementing DeliveryActivityCount", e);
//		}
//	}

    /**
     * CMPs
     */

	public abstract void setSriMapVersion(int sriMapVersion);

    public abstract int getSriMapVersion();

    /**
     * Life cycle methods
     */

    @Override
    public void setSbbContext(SbbContext sbbContext) {
        super.setSbbContext(sbbContext);

        try {
            Context ctx = (Context) new InitialContext().lookup("java:comp/env");
            this.mapAcif = (MAPContextInterfaceFactory) ctx.lookup("slee/resources/map/2.0/acifactory");
            this.mapProvider = (MAPProvider) ctx.lookup("slee/resources/map/2.0/provider");
            this.mapParameterFactory = this.mapProvider.getMAPParameterFactory();
            this.mapSmsTpduParameterFactory = this.mapProvider.getMAPSmsTpduParameterFactory();
            this.sccpParameterFact = new ParameterFactoryImpl();
        } catch (Exception ne) {
            logger.severe("Could not set SBB context:", ne);
        }
    }

    @Override
    public void sbbLoad() {
        super.sbbLoad();
    }

    @Override
    public void sbbStore() {
        super.sbbStore();
    }


//    public enum ErrorAction {
//        subscriberBusy,
//        memoryCapacityExceededFlag, // MNRF
//        mobileNotReachableFlag, // MNRF
//        notReachableForGprs, // MNRG
//        permanentFailure,
//        temporaryFailure,
//    }

}

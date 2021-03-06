package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract

import org.pillarone.riskanalytics.core.parameterization.AbstractParameterObjectClassifier
import org.pillarone.riskanalytics.core.parameterization.TableMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.IParameterObjectClassifier
import org.pillarone.riskanalytics.core.parameterization.IParameterObject
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.limit.LimitStrategyType

import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.allocation.PremiumAllocationType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.limit.ILimitStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.retrospective.UnifiedADCLPTBase
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.retrospective.UnifiedAdcLptContractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.QuotaShareContractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.WXLConstractStrategy

import org.pillarone.riskanalytics.core.parameterization.AbstractMultiDimensionalParameter

import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.TrivialContractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.commission.param.ICommissionStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.commission.param.CommissionStrategyType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.StopLossBase

import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.CXLConstractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.WCXLConstractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.StopLossConstractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.SurplusContractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.allocation.IRIPremiumSplitStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stabilization.StabilizationStrategyType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stabilization.IStabilizationStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.XLPremiumBase
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter
import org.pillarone.riskanalytics.domain.utils.constraint.DoubleConstraints
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.TermWXLConstractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.TermCXLConstractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional.TermWCXLConstractStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.lossparticipation.ILossParticipationStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.lossparticipation.LossParticipationStrategyType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.lossparticipation.NoParticipationStrategy

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
class ReinsuranceContractType extends AbstractParameterObjectClassifier {

    public static final ReinsuranceContractType QUOTASHARE = new ReinsuranceContractType("quota share", "QUOTASHARE",
            ["quotaShare": 0d,
                    "lossParticipation": LossParticipationStrategyType.noParticipation,
                    "limit": LimitStrategyType.getDefault(),
                    'commission': CommissionStrategyType.getNoCommission()])
    public static final ReinsuranceContractType SURPLUS = new ReinsuranceContractType("surplus", "SURPLUS",
            ["retention": 0d, "lines": 0d, "defaultCededLossShare": 0d, 'commission': CommissionStrategyType.getNoCommission()])
    public static final ReinsuranceContractType WXL = new ReinsuranceContractType("wxl", "WXL", [
            "attachmentPoint": 0d, "limit": 0d, "aggregateDeductible": 0d, "aggregateLimit": 0d,
            "stabilization": StabilizationStrategyType.getDefault(),
            "premiumBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
            "reinstatementPremiums": new ConstrainedMultiDimensionalParameter([], ['Reinstatement Premium'],
                    ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER)),
            "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),])
    public static final ReinsuranceContractType CXL = new ReinsuranceContractType("cxl", "CXL", [
            "attachmentPoint": 0d, "limit": 0d, "aggregateDeductible": 0d, "aggregateLimit": 0d,
            "stabilization": StabilizationStrategyType.getDefault(),
            "premiumBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
            "reinstatementPremiums": new ConstrainedMultiDimensionalParameter([], ['Reinstatement Premium'],
                    ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER)),
            "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),])
    public static final ReinsuranceContractType WCXL = new ReinsuranceContractType("wcxl", "WCXL", [
            "attachmentPoint": 0d, "limit": 0d, "aggregateDeductible": 0d, "aggregateLimit": 0d,
            "stabilization": StabilizationStrategyType.getDefault(),
            "premiumBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
            "reinstatementPremiums": new ConstrainedMultiDimensionalParameter([], ['Reinstatement Premium'],
                    ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER)),
            "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),])
    public static final ReinsuranceContractType WXLTERM = new ReinsuranceContractType("wxl", "WXL", [
            "attachmentPoint": 0d, "limit": 0d, "aggregateDeductible": 0d, "aggregateLimit": 0d,
            "termDeductible": 0d, "termLimit": 0d, "stabilization": StabilizationStrategyType.getDefault(),
            "premiumBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
            "reinstatementPremiums": new ConstrainedMultiDimensionalParameter([], ['Reinstatement Premium'],
                    ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER)),
            "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),])
    public static final ReinsuranceContractType CXLTERM = new ReinsuranceContractType("cxl", "CXL", [
            "attachmentPoint": 0d, "limit": 0d, "aggregateDeductible": 0d, "aggregateLimit": 0d,
            "termDeductible": 0d, "termLimit": 0d, "stabilization": StabilizationStrategyType.getDefault(),
            "premiumBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
            "reinstatementPremiums": new ConstrainedMultiDimensionalParameter([], ['Reinstatement Premium'],
                    ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER)),
            "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),])
    public static final ReinsuranceContractType WCXLTERM = new ReinsuranceContractType("wcxl", "WCXL", [
            "attachmentPoint": 0d, "limit": 0d, "aggregateDeductible": 0d, "aggregateLimit": 0d,
            "termDeductible": 0d, "termLimit": 0d, "stabilization": StabilizationStrategyType.getDefault(),
            "premiumBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
            "reinstatementPremiums": new ConstrainedMultiDimensionalParameter([], ['Reinstatement Premium'],
                    ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER)),
            "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),])
    public static final ReinsuranceContractType STOPLOSS = new ReinsuranceContractType("stop loss", "STOPLOSS",
            ["stopLossContractBase": StopLossBase.ABSOLUTE, "attachmentPoint": 0d, "limit": 0d, "premium": 0d,
                    "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:])])
    public static final ReinsuranceContractType UNIFIEDADCLPT = new ReinsuranceContractType("unified ADC and LPT", "UNIFIEDADCLPT",
            ["cededShare": 0d, "contractBase": UnifiedADCLPTBase.ABSOLUTE, "attachmentPoint": 0d, "limit": 0d, "premium": 0d])
    public static final ReinsuranceContractType TRIVIAL = new ReinsuranceContractType("trivial", "TRIVIAL", [:])
    public static final ReinsuranceContractType AGGREGATEXL = new ReinsuranceContractType("aggregate xl", "AggregateXL",
            ["attachmentPoint": 0d, "limit": 0d, "contractBase": XLPremiumBase.ABSOLUTE,
                    "riPremiumSplit": PremiumAllocationType.getStrategy(PremiumAllocationType.PREMIUM_SHARES, [:]),
                    "premium": 0d, "claimClass": ClaimType.AGGREGATED_EVENT])
    public static final ReinsuranceContractType GOLDORAK = new ReinsuranceContractType("goldorak", "GOLDORAK",
            ["cxlAttachmentPoint": 0d, "cxlLimit": 0d,
                    "cxlAggregateDeductible": 0d, "cxlAggregateLimit": 0d, "contractBase": XLPremiumBase.ABSOLUTE, "premium": 0d,
                    "slAttachmentPoint": 0d, "slLimit": 0d, "goldorakSlThreshold": 0d,
                    "reinstatementPremiums": new TableMultiDimensionalParameter([0.0], ['Reinstatement Premium'])])

    public static final all = [QUOTASHARE, SURPLUS, WXL, CXL, WCXL, STOPLOSS, TRIVIAL, UNIFIEDADCLPT]

    protected static Map types = [:]
    static {
        ReinsuranceContractType.all.each {
            ReinsuranceContractType.types[it.toString()] = it
        }
    }

    private ReinsuranceContractType(String displayName, String typeName, Map parameters) {
        super(displayName, typeName, parameters)
    }

    public static ReinsuranceContractType valueOf(String type) {
        types[type]
    }

    public List<IParameterObjectClassifier> getClassifiers() {
        return all
    }

    public IParameterObject getParameterObject(Map parameters) {
        return getStrategy(this, parameters)
    }

    static IReinsuranceContractStrategy getDefault() {
        return new TrivialContractStrategy()
    }

    static IReinsuranceContractStrategy getStrategy(ReinsuranceContractType type, Map parameters) {
        switch (type) {
            case ReinsuranceContractType.QUOTASHARE:
                return new QuotaShareContractStrategy(
                        quotaShare: (double) parameters[QuotaShareContractStrategy.QUOTASHARE],
                        lossParticipation: (ILossParticipationStrategy) parameters[QuotaShareContractStrategy.LOSSPARTICIPATION] ?: new NoParticipationStrategy(),
                        limit: (ILimitStrategy) parameters[QuotaShareContractStrategy.LIMIT],
                        commission: (ICommissionStrategy) parameters[QuotaShareContractStrategy.COMMISSION])
            case ReinsuranceContractType.WXL:
                return new WXLConstractStrategy(
                        attachmentPoint: (double) parameters[WXLConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[WXLConstractStrategy.LIMIT],
                        aggregateLimit: (double) parameters[WXLConstractStrategy.AGGREGATE_LIMIT],
                        aggregateDeductible: (double) parameters[WXLConstractStrategy.AGGREGATE_DEDUCTIBLE],
                        premiumBase: (XLPremiumBase) parameters[WXLConstractStrategy.PREMIUM_BASE],
                        premium: (double) parameters[WXLConstractStrategy.PREMIUM],
                        riPremiumSplit: (IRIPremiumSplitStrategy) parameters[WXLConstractStrategy.PREMIUM_ALLOCATION],
                        reinstatementPremiums: (AbstractMultiDimensionalParameter) parameters[WXLConstractStrategy.REINSTATEMENT_PREMIUMS],
                        stabilization: (IStabilizationStrategy) parameters[WXLConstractStrategy.STABILIZATION])
            case ReinsuranceContractType.CXL:
                return new CXLConstractStrategy(
                        attachmentPoint: (double) parameters[CXLConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[CXLConstractStrategy.LIMIT],
                        aggregateLimit: (double) parameters[CXLConstractStrategy.AGGREGATE_LIMIT],
                        aggregateDeductible: (double) parameters[CXLConstractStrategy.AGGREGATE_DEDUCTIBLE],
                        premiumBase: (XLPremiumBase) parameters[CXLConstractStrategy.PREMIUM_BASE],
                        premium: (double) parameters[CXLConstractStrategy.PREMIUM],
                        riPremiumSplit: (IRIPremiumSplitStrategy) parameters[CXLConstractStrategy.PREMIUM_ALLOCATION],
                        reinstatementPremiums: (AbstractMultiDimensionalParameter) parameters[CXLConstractStrategy.REINSTATEMENT_PREMIUMS],
                        stabilization: (IStabilizationStrategy) parameters[WXLConstractStrategy.STABILIZATION])
                break
            case ReinsuranceContractType.WCXL:
                return new WCXLConstractStrategy(
                        attachmentPoint: (double) parameters[WCXLConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[WCXLConstractStrategy.LIMIT],
                        aggregateLimit: (double) parameters[WCXLConstractStrategy.AGGREGATE_LIMIT],
                        aggregateDeductible: (double) parameters[WCXLConstractStrategy.AGGREGATE_DEDUCTIBLE],
                        premiumBase: (XLPremiumBase) parameters[WCXLConstractStrategy.PREMIUM_BASE],
                        premium: (double) parameters[WCXLConstractStrategy.PREMIUM],
                        riPremiumSplit: (IRIPremiumSplitStrategy) parameters[WCXLConstractStrategy.PREMIUM_ALLOCATION],
                        reinstatementPremiums: (AbstractMultiDimensionalParameter) parameters[WCXLConstractStrategy.REINSTATEMENT_PREMIUMS],
                        stabilization: (IStabilizationStrategy) parameters[WXLConstractStrategy.STABILIZATION])
            case ReinsuranceContractType.WXLTERM:
                return new TermWXLConstractStrategy(
                        attachmentPoint: (double) parameters[WXLConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[WXLConstractStrategy.LIMIT],
                        aggregateLimit: (double) parameters[WXLConstractStrategy.AGGREGATE_LIMIT],
                        aggregateDeductible: (double) parameters[WXLConstractStrategy.AGGREGATE_DEDUCTIBLE],
                        termDeductible: parameters[TermWXLConstractStrategy.TERM_DEDUCTIBLE],
                        termLimit: parameters[TermWXLConstractStrategy.TERM_LIMIT],
                        premiumBase: (XLPremiumBase) parameters[WXLConstractStrategy.PREMIUM_BASE],
                        premium: (double) parameters[WXLConstractStrategy.PREMIUM],
                        riPremiumSplit: (IRIPremiumSplitStrategy) parameters[WXLConstractStrategy.PREMIUM_ALLOCATION],
                        reinstatementPremiums: (AbstractMultiDimensionalParameter) parameters[WXLConstractStrategy.REINSTATEMENT_PREMIUMS],
                        stabilization: (IStabilizationStrategy) parameters[WXLConstractStrategy.STABILIZATION])
            case ReinsuranceContractType.CXLTERM:
                return new TermCXLConstractStrategy(
                        attachmentPoint: (double) parameters[CXLConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[CXLConstractStrategy.LIMIT],
                        aggregateLimit: (double) parameters[CXLConstractStrategy.AGGREGATE_LIMIT],
                        aggregateDeductible: (double) parameters[CXLConstractStrategy.AGGREGATE_DEDUCTIBLE],
                        termDeductible: parameters[TermCXLConstractStrategy.TERM_DEDUCTIBLE],
                        termLimit: parameters[TermCXLConstractStrategy.TERM_LIMIT],
                        premiumBase: (XLPremiumBase) parameters[CXLConstractStrategy.PREMIUM_BASE],
                        premium: (double) parameters[CXLConstractStrategy.PREMIUM],
                        riPremiumSplit: (IRIPremiumSplitStrategy) parameters[CXLConstractStrategy.PREMIUM_ALLOCATION],
                        reinstatementPremiums: (AbstractMultiDimensionalParameter) parameters[CXLConstractStrategy.REINSTATEMENT_PREMIUMS],
                        stabilization: (IStabilizationStrategy) parameters[WXLConstractStrategy.STABILIZATION])
            case ReinsuranceContractType.WCXLTERM:
                return new TermWCXLConstractStrategy(
                        attachmentPoint: (double) parameters[WCXLConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[WCXLConstractStrategy.LIMIT],
                        aggregateLimit: (double) parameters[WCXLConstractStrategy.AGGREGATE_LIMIT],
                        aggregateDeductible: (double) parameters[WCXLConstractStrategy.AGGREGATE_DEDUCTIBLE],
                        termDeductible: parameters[TermWXLConstractStrategy.TERM_DEDUCTIBLE],
                        termLimit: parameters[TermWXLConstractStrategy.TERM_LIMIT],
                        premiumBase: (XLPremiumBase) parameters[WCXLConstractStrategy.PREMIUM_BASE],
                        premium: (double) parameters[WCXLConstractStrategy.PREMIUM],
                        riPremiumSplit: (IRIPremiumSplitStrategy) parameters[WCXLConstractStrategy.PREMIUM_ALLOCATION],
                        reinstatementPremiums: (AbstractMultiDimensionalParameter) parameters[WCXLConstractStrategy.REINSTATEMENT_PREMIUMS],
                        stabilization: (IStabilizationStrategy) parameters[WXLConstractStrategy.STABILIZATION])
            case ReinsuranceContractType.STOPLOSS:
                return new StopLossConstractStrategy(
                        stopLossContractBase: (StopLossBase) parameters[StopLossConstractStrategy.CONTRACT_BASE],
                        attachmentPoint: (double) parameters[StopLossConstractStrategy.ATTACHMENT_POINT],
                        limit: (double) parameters[StopLossConstractStrategy.LIMIT],
                        premium: (double) parameters[StopLossConstractStrategy.PREMIUM],
                        premiumAllocation: (IRIPremiumSplitStrategy) parameters[StopLossConstractStrategy.PREMIUM_ALLOCATION])
            case ReinsuranceContractType.UNIFIEDADCLPT:
                return new UnifiedAdcLptContractStrategy(
                        cededShare: parameters[UnifiedAdcLptContractStrategy.CEDED_SHARE],
                        contractBase: parameters[UnifiedAdcLptContractStrategy.CONTRACT_BASE],
                        attachmentPoint: parameters[UnifiedAdcLptContractStrategy.ATTACHMENT_POINT],
                        limit: parameters[UnifiedAdcLptContractStrategy.LIMIT],
                        premium: parameters[UnifiedAdcLptContractStrategy.PREMIUM])
            case ReinsuranceContractType.SURPLUS:
                return new SurplusContractStrategy(
                        retention: (double) parameters[SurplusContractStrategy.RETENTION],
                        lines: (double) parameters[SurplusContractStrategy.LINES],
                        defaultCededLossShare: (double) parameters[SurplusContractStrategy.DEFAULTCEDEDLOSSSHARE],
                        commission: (ICommissionStrategy) parameters[SurplusContractStrategy.COMMISSION])
            case ReinsuranceContractType.TRIVIAL:
                return new TrivialContractStrategy()
            default: throw new IllegalArgumentException("$type is not implemented")
        }
    }
}

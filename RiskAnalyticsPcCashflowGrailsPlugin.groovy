import org.pillarone.riskanalytics.core.output.DrillDownMode
import org.pillarone.riskanalytics.domain.pc.cf.output.SplitAndFilterCollectionModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.pattern.PatternTableConstraints
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory
import org.pillarone.riskanalytics.domain.pc.cf.indexing.AnnualIndexTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.indexing.SeverityIndexSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.indexing.FrequencyIndexSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.indexing.PremiumIndexSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.indexing.PolicyIndexSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.legalentity.LegalEntityPortionConstraints
import org.pillarone.riskanalytics.domain.pc.cf.indexing.DeterministicIndexTableConstraints

import org.pillarone.riskanalytics.domain.pc.cf.claim.generator.validation.PMLClaimsGeneratorStrategyValidator
import org.pillarone.riskanalytics.core.parameterization.validation.ValidatorRegistry
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.CoverMap
import org.pillarone.riskanalytics.domain.utils.constraint.DoubleConstraints
import org.pillarone.riskanalytics.domain.utils.constraint.DateTimeConstraints
import org.pillarone.riskanalytics.core.util.ResourceBundleRegistry
import org.pillarone.riskanalytics.domain.pc.cf.pattern.validation.RecoveryPatternStrategyValidator
import org.pillarone.riskanalytics.domain.pc.cf.pattern.validation.PatternStrategyValidator
import org.pillarone.riskanalytics.domain.pc.cf.indexing.LinkRatioIndexTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.indexing.ReservesIndexSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.claim.generator.validation.ClaimsGeneratorScalingValidator
import org.pillarone.riskanalytics.domain.pc.cf.claim.allocation.validation.RiskAllocationValidator
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimTypeSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.dependency.validation.CopulaValidator
import org.pillarone.riskanalytics.domain.pc.cf.dependency.validation.MultipleProbabilitiesCopulaValidator
import org.pillarone.riskanalytics.core.output.aggregation.PacketAggregatorRegistry
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimPacketAggregator
import org.pillarone.riskanalytics.domain.pc.cf.discounting.YieldCurveTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateSplitPerSourceCollectingModeStrategy
import org.pillarone.riskanalytics.core.output.CollectingModeFactory
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.validation.XLStrategyValidator
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacketAggregator
import org.pillarone.riskanalytics.domain.utils.constraint.SegmentPortion
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateUltimateClaimCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateUltimateReportedPaidClaimCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateUltimatePaidClaimCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateSplitByInceptionDateCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregatePremiumReserveRiskCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateIncludingPremiumReserveRiskCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregatePremiumReserveRiskTriangleCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.structure.validation.ClaimTypeStructuringValidator
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateSplitPerSourceReducedCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.output.AggregateUltimateReportedClaimCollectingModeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.indexing.RunOffIndexSelectionTableConstraints
import org.pillarone.riskanalytics.domain.pc.cf.output.SingleUltimatePaidClaimCollectingModeStrategy

class RiskAnalyticsPcCashflowGrailsPlugin {
    // the plugin version
    def version = "0.5.6-kti"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def author = "Intuitive Collaboration AG"
    def authorEmail = "info (at) intuitive-collaboration (dot) com"
    def title = "Property Casualty Library for Cashflow Models"
    def description = '''\\
'''

    // URL to the plugin's documentation
    def documentation = "http://www.pillarone.org"

    def groupId = "org.pillarone"


    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
        ConstraintsFactory.registerConstraint(new AnnualIndexTableConstraints())
        ConstraintsFactory.registerConstraint(new LinkRatioIndexTableConstraints())
        ConstraintsFactory.registerConstraint(new DeterministicIndexTableConstraints())
        ConstraintsFactory.registerConstraint(new PolicyIndexSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new PremiumIndexSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new FrequencyIndexSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new SeverityIndexSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new RunOffIndexSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new ReservesIndexSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new LegalEntityPortionConstraints())
        ConstraintsFactory.registerConstraint(new PatternTableConstraints())
        ConstraintsFactory.registerConstraint(new CoverMap())
        ConstraintsFactory.registerConstraint(new DoubleConstraints())
        ConstraintsFactory.registerConstraint(new DateTimeConstraints())
        ConstraintsFactory.registerConstraint(new ClaimTypeSelectionTableConstraints())
        ConstraintsFactory.registerConstraint(new YieldCurveTableConstraints())
        ConstraintsFactory.registerConstraint(new SegmentPortion())

        ValidatorRegistry.addValidator(new PMLClaimsGeneratorStrategyValidator())
        ValidatorRegistry.addValidator(new PatternStrategyValidator())
        ValidatorRegistry.addValidator(new RecoveryPatternStrategyValidator())
        ValidatorRegistry.addValidator(new ClaimsGeneratorScalingValidator())
        ValidatorRegistry.addValidator(new RiskAllocationValidator())
        ValidatorRegistry.addValidator(new CopulaValidator())
        ValidatorRegistry.addValidator(new MultipleProbabilitiesCopulaValidator())
        ValidatorRegistry.addValidator(new XLStrategyValidator())
        ValidatorRegistry.addValidator(new ClaimTypeStructuringValidator())

        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.claim.generator.validation.pMLClaimsGeneratorStrategyValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.pattern.validation.patternStrategyValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.pattern.validation.recoveryPatternStrategyValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.claim.generator.validation.claimsGeneratorScalingValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.claim.allocation.validation.riskAllocationValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.dependency.validation.copulaValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.dependency.validation.multipleProbabilitiesCopulaValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.validation.xlStrategyValidator")
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.VALIDATION, "org.pillarone.riskanalytics.domain.pc.cf.structure.validation.claimTypeStructuringValidator")

        // add resource bundle for exceptions
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.RESOURCE, "org.pillarone.riskanalytics.domain.pc.cf.exceptionResources")
        // doc urls
        ResourceBundleRegistry.addBundle(ResourceBundleRegistry.HELP, "org/pillarone/riskanalytics/domain/pc/cf/ComponentHelp")

        PacketAggregatorRegistry.registerAggregator(ClaimCashflowPacket, new ClaimPacketAggregator())
        PacketAggregatorRegistry.registerAggregator(UnderwritingInfoPacket, new UnderwritingInfoPacketAggregator())

        /* Collectors should also be registered  in the PCCashflowBootstrap   */
        CollectingModeFactory.registerStrategy(new AggregateSplitPerSourceCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateSplitPerSourceReducedCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregatePremiumReserveRiskCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateIncludingPremiumReserveRiskCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregatePremiumReserveRiskTriangleCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateSplitByInceptionDateCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateUltimateClaimCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateUltimateReportedPaidClaimCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateUltimateReportedClaimCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new AggregateUltimatePaidClaimCollectingModeStrategy())

        CollectingModeFactory.registerStrategy(new SingleUltimatePaidClaimCollectingModeStrategy())
        CollectingModeFactory.registerStrategy(new SplitAndFilterCollectionModeStrategy([],[]))
        CollectingModeFactory.registerStrategy(new SplitAndFilterCollectionModeStrategy([DrillDownMode.BY_PERIOD],[]))
        CollectingModeFactory.registerStrategy(new SplitAndFilterCollectionModeStrategy([DrillDownMode.BY_SOURCE],[]))
        CollectingModeFactory.registerStrategy(new SplitAndFilterCollectionModeStrategy([DrillDownMode.BY_SOURCE, DrillDownMode.BY_PERIOD],[ClaimCashflowPacket.REPORTED_INDEXED, ClaimCashflowPacket.PAID_INDEXED]))
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
}

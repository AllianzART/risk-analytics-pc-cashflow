package org.pillarone.riskanalytics.domain.pc.cf.claim.generator

import org.pillarone.riskanalytics.domain.pc.cf.claim.GrossClaimRoot
import org.joda.time.DateTime
import org.pillarone.riskanalytics.domain.pc.cf.pattern.PatternPacket
import org.pillarone.riskanalytics.domain.pc.cf.pattern.PatternPacketTests
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimRoot
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType

/**
*   author simon.parten @ art-allianz . com
 */
class TestClaimUtils {

    public static GrossClaimRoot getGrossClaim(List<Integer> patternMonths, List<Double> pattern, double ultimate, DateTime exposureStart, DateTime patternStart, DateTime occurenceDate) {
        PatternPacket patternPacket = PatternPacketTests.getPattern(patternMonths, pattern, false)
        IClaimRoot claimRoot = new ClaimRoot(ultimate, ClaimType.SINGLE, exposureStart, occurenceDate)
        GrossClaimRoot grossClaimRoot = new GrossClaimRoot(claimRoot, patternPacket, patternStart)
        return grossClaimRoot
    }

}
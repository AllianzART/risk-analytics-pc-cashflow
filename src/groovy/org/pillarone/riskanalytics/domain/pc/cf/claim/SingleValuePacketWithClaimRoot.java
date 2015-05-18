package org.pillarone.riskanalytics.domain.pc.cf.claim;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.packets.SingleValuePacket;
import org.pillarone.riskanalytics.domain.pc.claims.Claim;
import org.pillarone.riskanalytics.domain.pc.reserves.cashflow.ClaimDevelopmentPacket;

/**
 * Created with IntelliJ IDEA.
 * User: palbini
 * Date: 01/05/15
 * Time: 15:26
 *
 * AR-111
 *
 * Extending SingleValuePacket[s] with the ability to track the claim that originated them
 * This for trying to split claim cashflows by update date in Artisan1 with minimal reworking
 * on the business logic.
 *
 * Plan is to roll this out only on the channel we know we're going to track
 * - i.e. outClaimCashflows
 */

public class SingleValuePacketWithClaimRoot extends SingleValuePacket {

    private final IClaimRoot baseIClaimRoot; //Art 2
    private final Claim baseClaim; //Art 1

    public SingleValuePacketWithClaimRoot() { //Integration test requires the empty constructor
        super();
        baseClaim = null;
        baseIClaimRoot = null;
    }

    public SingleValuePacketWithClaimRoot(IClaimRoot claim) {
        super();
        this.baseIClaimRoot = claim;
        this.baseClaim = null;
    }

    public SingleValuePacketWithClaimRoot(Claim claim) {
        super();
        this.baseClaim = claim;
        this.baseIClaimRoot = null;
    }

    public SingleValuePacketWithClaimRoot(ClaimCashflowPacket claimCashflow) {
        this(claimCashflow.getBaseClaim());
        this.setDate(claimCashflow.getDate());
    }

    public SingleValuePacketWithClaimRoot(ClaimDevelopmentPacket claimDevelopmentP) {
        this(claimDevelopmentP.getOriginalClaim());
        this.setDate(claimDevelopmentP.getDate());
    }


    public DateTime getBaseClaimIncurredDate(){
        if (baseClaim == null) {
            return baseIClaimRoot.getOccurrenceDate();
        } else {
            return baseClaim.getDate();
        }
    }

}

package org.pillarone.riskanalytics.domain.pc.cf.accounting.experienceAccounting;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.packets.MultiValuePacket;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.utils.datetime.DateTimeUtilities;

import java.util.Map;

/**
 * This object exists to propagate commutation information, it's state and expected behaviour.
 *
 * @author simon.parten (at) art-allianz (dot) com
 */

public class CommutationState extends MultiValuePacket {
    private final boolean commuted;
    private final double profitShare;
    private final CommutationBehaviour commutationBehaviour;
    private final double discountRate;
    private final int commutationPeriod;
    private final DateTime commutationDate;
    private final boolean commuteThisPeriod;

    public CommutationState() {
        this(
                false,
                1d,
                CommutationBehaviour.DEFAULT,
                1d,
                -1,
                new DateTime(1900, 1, 1, 1, 0, 0, 0),
                false
        );
    }

    public CommutationState(double profitShare) {
        this(
                false,
                profitShare,
                CommutationBehaviour.DEFAULT,
                1d,
                -1,
                new DateTime(1900, 1, 1, 1, 0, 0, 0),
                false
        );
    }

    public CommutationState(DateTime reportingDate) {
        this(
                false,
                1d,
                CommutationBehaviour.DEFAULT,
                1d,
                -1,
                reportingDate,
                false
        );
    }

    public CommutationState(DateTime reportingDate, double profitShare) {
        this(
                false,
                profitShare,
                CommutationBehaviour.DEFAULT,
                1d,
                -1,
                reportingDate,
                false
        );
    }

    public CommutationState(Boolean commuted, Double profitShare, CommutationBehaviour commutationBehaviour, Double discountRate, Integer commutationPeriod, DateTime commutationDate, Boolean commuteThisPeriod) {
        this.commuted = commuted;
        this.profitShare = profitShare;
        this.commutationBehaviour = commutationBehaviour;
        this.discountRate = discountRate;
        this.commutationPeriod = commutationPeriod;
        this.commutationDate = commutationDate;
        this.commuteThisPeriod = commuteThisPeriod;
        super.setDate(commutationDate);
    }

    public boolean isCommuted() {
        return commuted;
    }

    public double getProfitShare() {
        return profitShare;
    }

    public CommutationBehaviour getCommutationBehaviour() {
        return commutationBehaviour;
    }

    public double getDiscountRate() {
        return discountRate;
    }

    public int getCommutationPeriod() {
        return commutationPeriod;
    }

    public DateTime getCommutationDate() {
        return commutationDate;
    }

    public boolean isCommuteThisPeriod() {
        return commuteThisPeriod;
    }

    /**
     * Answers the question ' Do we want the simulation to continue ?
     * True if we are not commuted, false if commuted in prior period
     *
     * @param periodScope periodSCope
     * @return True unless commuted in a prior period
     */
    public boolean checkCommutation(PeriodScope periodScope) {
        return !isCommuted() || isCommuted() && getCommutationPeriod() == periodScope.getCurrentPeriod();
    }
    public Map<String, Number> getValuesToSave() throws IllegalAccessException {
        Map<String, Number> valuesToSave = super.getValuesToSave();
        valuesToSave.put("commuted", commuted ? 1 : 0);
        valuesToSave.put("profitShare", profitShare);
        valuesToSave.put("commutationPeriod", profitShare);

        return valuesToSave;
    }




        @Override
    public String toString() {
        return "CommutationState{" +
                "commuted=" + commuted +
                ", profitShare=" + profitShare +
                ", discountRate=" + discountRate +
                ", commutationPeriod=" + commutationPeriod +
                ", commutationDate=" + DateTimeUtilities.formatDate.print( commutationDate ) +
                ", commuteThisPeriod=" + commuteThisPeriod +
                '}';
    }
}

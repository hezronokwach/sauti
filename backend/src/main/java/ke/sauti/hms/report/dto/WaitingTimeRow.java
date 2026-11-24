package ke.sauti.hms.report.dto;

/**
 * Waiting times for one lane and hour of arrival, in minutes: the median and the 90th
 * percentile of each stage. A stage no visit reached yet is null.
 *
 * @param lane the arrival lane, or NO_TICKET for visits opened without one
 * @param hour the hour of arrival, facility time (0 to 23)
 * @param visits how many visits arrived in it
 * @param deskWait from arrival to being called to the desk
 * @param registration from being called to being sent to triage
 * @param triageWait from waiting for triage to the nurse taking the patient
 * @param doctorWait from waiting for a doctor to the doctor taking the patient
 * @param doorToDoctor from arrival to the doctor taking the patient
 */
public record WaitingTimeRow(
        String lane,
        int hour,
        int visits,
        Stage deskWait,
        Stage registration,
        Stage triageWait,
        Stage doctorWait,
        Stage doorToDoctor) {

    /**
     * One stage's spread, in minutes.
     *
     * @param median half the visits waited less than this
     * @param p90 nine in ten visits waited less than this
     */
    public record Stage(Double median, Double p90) {}
}

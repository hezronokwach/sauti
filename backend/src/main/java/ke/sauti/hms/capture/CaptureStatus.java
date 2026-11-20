package ke.sauti.hms.capture;

/** A recording session: recording, stopped by the user, complete (all pieces transcribed) or erased. */
public enum CaptureStatus {
    RECORDING,
    STOPPED,
    COMPLETE,
    ERASED
}

package ke.sauti.hms.note.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ke.sauti.hms.note.NoteSections;
import org.jspecify.annotations.Nullable;

/**
 * A draft save. The version is the one the editor loaded; a different current version means
 * someone else saved first (another tab or doctor), and the save is refused with 409.
 *
 * @param version the note version the editor holds; absent for the first save
 * @param presentingComplaint why the patient came
 * @param history history of the complaint
 * @param examination examination findings
 * @param assessment assessment or diagnosis
 * @param plan treatment, tests and follow-up
 * @param aiAssisted whether AI help was used in this text; once true it stays true
 */
public record SaveNoteRequest(
        @Nullable @PositiveOrZero Long version,
        @Nullable @Size(max = 20_000) String presentingComplaint,
        @Nullable @Size(max = 20_000) String history,
        @Nullable @Size(max = 20_000) String examination,
        @Nullable @Size(max = 20_000) String assessment,
        @Nullable @Size(max = 20_000) String plan,
        @Nullable Boolean aiAssisted) {

    public NoteSections sections() {
        return new NoteSections(presentingComplaint, history, examination, assessment, plan);
    }

    public boolean usedAi() {
        return Boolean.TRUE.equals(aiAssisted);
    }
}

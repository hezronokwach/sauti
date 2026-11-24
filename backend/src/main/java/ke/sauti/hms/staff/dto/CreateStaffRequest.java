package ke.sauti.hms.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ke.sauti.hms.staff.StaffRole;
import org.jspecify.annotations.Nullable;

/**
 * A new staff account, created by an admin. The server chooses the one-time password.
 *
 * @param fullName as shown in the interface and on signed records
 * @param email the login email, unique regardless of case
 * @param phone optional contact number
 * @param role the one role this person has
 * @param licenceNumber required for doctors and nurses
 */
public record CreateStaffRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Nullable @Size(max = 20) String phone,
        @NotNull StaffRole role,
        @Nullable @Size(max = 50) String licenceNumber) {}

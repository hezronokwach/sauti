package ke.sauti.hms.visit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.staff.StaffRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every (from, to, role) combination against the expected flow, written out here
 * independently of {@link VisitWorkflow#ALLOWED}.
 */
class VisitWorkflowTest {

    private static final Map<String, Set<StaffRole>> EXPECTED = Map.of(
            "AT_RECEPTION>WAITING_TRIAGE", Set.of(StaffRole.RECEPTIONIST),
            "WAITING_TRIAGE>IN_TRIAGE", Set.of(StaffRole.NURSE),
            "IN_TRIAGE>WAITING_DOCTOR", Set.of(StaffRole.NURSE),
            "WAITING_DOCTOR>WITH_DOCTOR", Set.of(StaffRole.DOCTOR),
            "WITH_DOCTOR>WAITING_PAYMENT", Set.of(StaffRole.DOCTOR),
            "WAITING_PAYMENT>DONE", Set.of(StaffRole.CASHIER),
            "AT_RECEPTION>CANCELLED", Set.of(StaffRole.RECEPTIONIST),
            "WAITING_TRIAGE>CANCELLED", Set.of(StaffRole.RECEPTIONIST),
            "WAITING_DOCTOR>CANCELLED", Set.of(StaffRole.RECEPTIONIST));

    static Stream<Arguments> everyCombination() {
        List<Arguments> all = new ArrayList<>();
        for (VisitStatus from : VisitStatus.values()) {
            for (VisitStatus to : VisitStatus.values()) {
                for (StaffRole role : StaffRole.values()) {
                    all.add(Arguments.of(from, to, role));
                }
            }
        }
        return all.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} by {2}")
    @MethodSource("everyCombination")
    void onlyTheExpectedMovesByTheExpectedRolesPass(VisitStatus from, VisitStatus to, StaffRole role) {
        Set<StaffRole> roles = EXPECTED.get(from + ">" + to);

        if (roles == null) {
            assertThatThrownBy(() -> VisitWorkflow.check(from, to, role)).isInstanceOf(ConflictException.class);
        } else if (roles.contains(role)) {
            assertThatCode(() -> VisitWorkflow.check(from, to, role)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> VisitWorkflow.check(from, to, role)).isInstanceOf(ForbiddenException.class);
        }
    }
}

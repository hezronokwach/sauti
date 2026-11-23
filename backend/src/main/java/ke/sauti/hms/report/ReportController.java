package ke.sauti.hms.report;

import java.time.LocalDate;
import java.util.List;
import ke.sauti.hms.common.time.FacilityTime;
import ke.sauti.hms.report.dto.WaitingTimeRow;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports for the administrator: timings only, no patient data. */
@RestController
public class ReportController {

    private final WaitingTimeReport waitingTimes;
    private final FacilityTime facilityTime;

    public ReportController(WaitingTimeReport waitingTimes, FacilityTime facilityTime) {
        this.waitingTimes = waitingTimes;
        this.facilityTime = facilityTime;
    }

    /** Median and 90th-percentile waits per lane and hour; both dates default to today. */
    @GetMapping("/api/admin/reports/waiting-times")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WaitingTimeRow> waitingTimes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = facilityTime.today();
        return waitingTimes.between(from == null ? today : from, to == null ? today : to);
    }
}

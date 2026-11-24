package ke.sauti.hms.archfixture;

import ke.sauti.hms.facility.FacilitySettingsRepository;

/** Breaks "features use only their own repositories": reads the facility feature's table. */
public class CrossFeatureService {

    private final FacilitySettingsRepository facilityRepository;

    public CrossFeatureService(FacilitySettingsRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    public boolean facilityExists() {
        return facilityRepository.findCurrent().isPresent();
    }
}

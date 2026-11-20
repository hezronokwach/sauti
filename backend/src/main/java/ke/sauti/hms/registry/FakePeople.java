package ke.sauti.hms.registry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ke.sauti.hms.patient.Sex;

/**
 * Twenty made-up people for the fake registry and eligibility service. None is real. Their
 * national IDs run from 30000001 to 30000020; odd ones are SHA-eligible, even ones are not.
 * Anyone else is unknown to both. The ID {@value #UNAVAILABLE_ID} makes either fake act as
 * if the service were down.
 */
public final class FakePeople {

    /** Typing this ID shows the "service not answering" screens. */
    public static final String UNAVAILABLE_ID = "39999999";

    /**
     * One made-up person.
     *
     * @param nationalId their national ID
     * @param person what the registry returns
     */
    record Entry(String nationalId, ClientRegistry.Person person) {}

    private static final List<Entry> PEOPLE = List.of(
            entry("30000001", "Achieng Atieno Otieno", "1985-03-14", Sex.FEMALE),
            entry("30000002", "Brian Kiprotich Kemboi", "1990-07-02", Sex.MALE),
            entry("30000003", "Wanjiru Njeri Kamau", "1978-11-23", Sex.FEMALE),
            entry("30000004", "Hassan Omar Abdalla", "1969-01-30", Sex.MALE),
            entry("30000005", "Mercy Chebet Rotich", "2001-05-18", Sex.FEMALE),
            entry("30000006", "Kevin Otieno Ouma", "1995-09-09", Sex.MALE),
            entry("30000007", "Faith Wambui Mwangi", "1988-12-01", Sex.FEMALE),
            entry("30000008", "Joseph Mutua Musyoka", "1957-04-22", Sex.MALE),
            entry("30000009", "Amina Fatuma Ali", "1999-08-15", Sex.FEMALE),
            entry("30000010", "Daniel Kipchoge Langat", "1983-02-27", Sex.MALE),
            entry("30000011", "Grace Akinyi Odhiambo", "1972-06-06", Sex.FEMALE),
            entry("30000012", "Peter Kamau Gitau", "1964-10-10", Sex.MALE),
            entry("30000013", "Esther Nyambura Wairimu", "2003-03-03", Sex.FEMALE),
            entry("30000014", "Samuel Ochieng Okoth", "1991-11-11", Sex.MALE),
            entry("30000015", "Halima Zawadi Juma", "1986-07-19", Sex.FEMALE),
            entry("30000016", "John Mwenda Kirimi", "1975-05-05", Sex.MALE),
            entry("30000017", "Lucy Wanjiku Ndungu", "1993-01-12", Sex.FEMALE),
            entry("30000018", "Ali Said Mohamed", "1980-09-29", Sex.MALE),
            entry("30000019", "Ruth Moraa Nyaboke", "1998-04-04", Sex.FEMALE),
            entry("30000020", "Elijah Kibet Cheruiyot", "1967-12-24", Sex.MALE));

    static final Map<String, ClientRegistry.Person> BY_NATIONAL_ID =
            PEOPLE.stream().collect(Collectors.toUnmodifiableMap(Entry::nationalId, Entry::person, (a, b) -> a));

    private FakePeople() {}

    private static Entry entry(String id, String name, String born, Sex sex) {
        return new Entry(id, new ClientRegistry.Person(name, LocalDate.parse(born), sex));
    }

    /** Whether this made-up person is SHA-eligible: odd IDs are. */
    static boolean eligible(String nationalId) {
        return (nationalId.charAt(nationalId.length() - 1) - '0') % 2 == 1;
    }
}

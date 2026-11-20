package ke.sauti.hms.prereg.dto;

/**
 * The only thing the public page ever gets back: the new ticket's code.
 *
 * @param ticketCode show this at the desk, such as P-004
 */
public record PreRegisteredResponse(String ticketCode) {}

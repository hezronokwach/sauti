# Ten-minute demo script

Everything here is made up. Do not enter real patient information.

## Before the demo

1. Open the site a minute early. The free Render instance sleeps and takes about a minute to wake.
2. Log in as the admin and create one account each for a receptionist, nurse, doctor and cashier. The demo has no shared password: each account gets a one-time password, then its owner sets a new password and an authenticator.
3. Keep these made-up people to hand:

| National ID | Who | Use |
| --- | --- | --- |
| 30000001 to 30000020 | The fake registry's people, not yet registered | Registering a new patient |
| 31000001 | Demo Neema Wafula, allergic to penicillin | The allergy block |
| 31000002 to 31000020 | Other seeded patients | Searching |
| 39999999 | Makes the fake registry act as if it were down | Optional failure demo |

## The script

1. **Pre-register on a phone (1 min).** Open `/pre-register`, fill the form and show the ticket code.
2. **Reception (1 min).** Type ID 30000002 and let the registry fill in the identity. Then find Demo Neema Wafula (31000001), start a visit, and record the reason for visit by voice. Confirm the draft.
3. **Triage (1 min).** As the nurse, enter vitals. Show the penicillin allergy on the record.
4. **Consultation (2 min).** As the doctor, take consent and record. The transcript appears. This uses real transcription, so speak yourself; never record a patient.
5. **Summary (1 min).** Press Summarise. Show one claim marked supported and one that is blocked.
6. **Prescribe (1 min).** Prescribe amoxicillin. The allergy block stops it. Choose a medicine outside the penicillin group.
7. **Sign and speak (1 min).** Sign with an authenticator code and play the Swahili instructions.
8. **Payment (1 min).** As the cashier, take M-Pesa. Until Daraja keys are set this uses the fake gateway, so say so.
9. **Report (1 min).** As the admin, open the waiting-time report and the system status.

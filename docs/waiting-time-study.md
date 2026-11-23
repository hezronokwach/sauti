# Registration timing study

How long registration takes at the desk, typing only, compared with the registry lookup
(#38) and voice drafts (#39). The point is to show the difference with numbers, not claim it.

**Status: not run yet.** It needs two people (a receptionist and a "patient" reading a
script) and the demo or a local build with the Groq key. The results tables below are empty
until then.

## Method

1. Use a local or demo build with `sauti.ai.provider=groq` and the fake registry (#38).
2. Prepare 20 scripted patients: 10 new (made-up people from the fake registry, national IDs
   30000001 to 30000010) and 10 returning. Each script has: name, date of birth, sex, phone,
   national ID, residence, next of kin name, and a one-sentence reason for visit mixing
   Swahili and English.
3. **Round A, typing only:** for each script, give a ticket, call it, register the patient
   (or find the returning one), open the visit and type the reason, residence and next of
   kin. Do not use the registry lookup or recording.
4. **Round B, with help:** same scripts in a new order. Use "Look up in the registry" for the
   national ID, and record while asking the reason, residence and next of kin, using the
   drafts where they are right.
5. Times come from the system, not a stopwatch: the waiting-time report (Administration
   page) shows registration time per lane, from the ticket being called to the visit being
   sent to triage. Run each round on its own day, or note the hours used.
6. After each round, compare what was saved with the script and count **remaining errors**:
   any field that differs from the script (misspelled name, wrong date, wrong residence).

## Results

### Registration time (minutes)

| Round | New: median | New: 90th percentile | Returning: median | Returning: 90th percentile |
|---|---|---|---|---|
| A, typing only | | | | |
| B, registry and voice drafts | | | | |

### Remaining errors (fields differing from the script)

| Round | Name | Date of birth | Residence | Next of kin | Reason for visit |
|---|---|---|---|---|---|
| A, typing only | | | | | |
| B, registry and voice drafts | | | | | |

### Notes

Write down anything that slowed a round down: drafts that were wrong, the registry being
unavailable, network trouble.

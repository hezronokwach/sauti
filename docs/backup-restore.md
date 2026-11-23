# Backups and restore

A backup only counts once it has been restored. Do the restore below before the demo, then
monthly in any pilot, and add a row to the record at the end.

## What is backed up

- **Included:** the whole PostgreSQL database, except login sessions. After a restore, everyone logs in again.
- **Not included:** audio files in object storage. If that bucket is lost, recordings cannot be
  played, but transcripts and notes are safe.
- **Kept elsewhere:** `APP_ENCRYPTION_KEY` and `HASH_KEY`. Without them, restored patient data
  cannot be read. Keep them in a password manager, apart from the backups.

## Set up the nightly backup

1. Make a key pair: `docker run --rm sauti-backup age-keygen`.
2. Save the whole output (the private key) in a password manager. It must never go on the server or in GitHub.
3. Create a **separate private bucket**, with an access key that can reach only that bucket.
4. In GitHub, add these repository secrets:
   `BACKUP_DATABASE_URL` (the database owner), `BACKUP_AGE_RECIPIENT` (the `age1...` public key),
   `BACKUP_S3_ENDPOINT`, `BACKUP_BUCKET`, `BACKUP_ACCESS_KEY`, `BACKUP_SECRET_KEY`.
5. Add the repository variable `BACKUP_ENABLED` with the value `true`.
6. Run **Nightly backup** once from the Actions tab and check that a file appears in the bucket.

It then runs at 02:00 Nairobi time, keeps 35 days of files, and writes each result to `backup_run`.

## Restore

Time this from step 2 to step 6.

1. Build the tools: `docker build -t sauti-backup ops/backup`.
2. Create a **fresh, empty** database. The script refuses a database that already has tables.
3. Put the private key in a file outside the repository, then run:

   ```bash
   docker run --rm -v "$PWD/identity.txt:/run/identity.txt:ro" -e BACKUP_AGE_IDENTITY_FILE=/run/identity.txt -e RESTORE_DATABASE_URL -e BACKUP_S3_ENDPOINT -e BACKUP_BUCKET -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto sauti-backup /opt/backup/restore.sh
   ```

   This restores the newest file. Set `BACKUP_FILE` to restore a particular one.
4. Create the app's login on the restored database, with a new password:

   ```sql
   create role sauti_login login password 'choose-a-new-password' in role sauti_app;
   ```

5. Start the app against it, with `DATABASE_USERNAME=sauti_login` and `FLYWAY_USERNAME` set to the owner.
6. Log in, open a visit and read a note.
7. Delete the private key file.

## Neon's restore window

Neon's Free plan keeps a [6-hour history window](https://neon.com/docs/postgres/backup-restore/history-window),
capped at [1 GB of change history](https://neon.com/faqs/free-plan-limits-and-quotas). It helps
with a mistake noticed the same morning, but it is a second safety net and does not replace
these backups. Checked on 2026-11-23; plans change, so check again before relying on it.

## Restore record

| Date | Environment | Backup size | Restore only | Restore to reading a note | Result |
| --- | --- | --- | --- | --- | --- |
| 2026-11-23 | Local containers, PostgreSQL 18.7, small test database | 115 KB | 3.3 s | 25.3 s | Passed: a doctor logged in through the app on the restricted database login and read the original signed note |

The first restore used a very small database, so expect a real one to take longer.

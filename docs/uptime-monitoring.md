# Uptime monitoring

The admin home page shows jobs, backups and refused requests. It cannot warn anyone when the
whole app is down, so an outside monitor does that.

Set this up once the app has its public address:

1. Create a free account with an uptime service such as UptimeRobot or Better Stack.
2. Add an HTTP monitor for `https://YOUR-ADDRESS/actuator/health`, checked every 5 minutes.
3. Alert when the response is not 200, and send alerts to an email address someone reads.
4. Stop the app briefly and confirm the email arrives.

The health address shows only whether the app is up. It reveals no patient or system details.

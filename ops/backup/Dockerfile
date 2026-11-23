# The backup and restore tools. The PostgreSQL version must match the database.
FROM postgres:18.7
RUN apt-get update \
    && apt-get install -y --no-install-recommends age awscli ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY backup.sh restore.sh grants.sql /opt/backup/
RUN sed -i 's/\r$//' /opt/backup/backup.sh /opt/backup/restore.sh /opt/backup/grants.sql \
    && chmod 555 /opt/backup/backup.sh /opt/backup/restore.sh
USER postgres
ENV HOME=/tmp
ENTRYPOINT []
CMD ["/opt/backup/backup.sh"]

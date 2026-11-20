-- Voice capture: a recording session, its audio pieces (encrypted in object storage) and
-- the transcript segments made from them (text encrypted by the application).

create table capture_session
(
    id              uuid        not null default gen_random_uuid(),
    visit_id        uuid        not null,
    purpose         varchar(30) not null,
    started_by      uuid        not null,
    status          varchar(20) not null,
    -- For example "sw" or "en"; a hint for transcription, not a guarantee.
    language_hint   varchar(10),
    -- Set when recording stops: how many pieces the browser sent.
    expected_pieces integer,
    started_at      timestamptz not null,
    stopped_at      timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint      not null default 0,
    constraint pk_capture_session primary key (id),
    constraint fk_capture_session_visit foreign key (visit_id) references visit (id),
    constraint fk_capture_session_started_by foreign key (started_by) references staff (id),
    constraint ck_capture_session_purpose check (purpose in ('RECORD_RECEPTION', 'RECORD_CONSULTATION')),
    constraint ck_capture_session_status check (status in ('RECORDING', 'STOPPED', 'COMPLETE', 'ERASED')),
    constraint ck_capture_session_expected check (expected_pieces is null or expected_pieces >= 0)
);

-- One recording at a time per visit and purpose.
create unique index ux_capture_session_one_recording on capture_session (visit_id, purpose)
    where status = 'RECORDING';
create index ix_capture_session_visit on capture_session (visit_id);

create table audio_piece
(
    id          uuid         not null default gen_random_uuid(),
    session_id  uuid         not null,
    sequence_no integer      not null,
    storage_key varchar(200) not null,
    duration_ms integer      not null,
    offset_ms   integer      not null,
    -- SHA-256 of the plain audio, hex: detects a corrupted or repeated upload.
    sha256      varchar(64)  not null,
    status      varchar(20)  not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0,
    constraint pk_audio_piece primary key (id),
    constraint fk_audio_piece_session foreign key (session_id) references capture_session (id),
    constraint ux_audio_piece_session_sequence unique (session_id, sequence_no),
    constraint ck_audio_piece_sequence check (sequence_no >= 0),
    constraint ck_audio_piece_times check (duration_ms >= 0 and offset_ms >= 0),
    constraint ck_audio_piece_status check (status in ('STORED', 'TRANSCRIBED', 'FAILED', 'ERASED'))
);

create table transcript_segment
(
    id                uuid        not null default gen_random_uuid(),
    piece_id          uuid        not null,
    session_id        uuid        not null,
    order_no          integer     not null,
    start_ms          integer     not null,
    end_ms            integer     not null,
    -- Encrypted by the application.
    text              bytea       not null,
    confidence        varchar(10) not null,
    -- Whisper's own numbers, kept for tuning the confidence thresholds.
    avg_logprob       double precision,
    no_speech_prob    double precision,
    compression_ratio double precision,
    created_at        timestamptz not null default now(),
    constraint pk_transcript_segment primary key (id),
    constraint fk_transcript_segment_piece foreign key (piece_id) references audio_piece (id),
    constraint fk_transcript_segment_session foreign key (session_id) references capture_session (id),
    constraint ck_transcript_segment_times check (start_ms >= 0 and end_ms >= start_ms),
    constraint ck_transcript_segment_confidence check (confidence in ('HIGH', 'REVIEW', 'LOW'))
);

create index ix_transcript_segment_session on transcript_segment (session_id, order_no);

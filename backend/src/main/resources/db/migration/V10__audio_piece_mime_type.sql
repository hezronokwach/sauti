-- The audio format of each piece, passed to the transcriber.
alter table audio_piece add column mime_type varchar(50) not null default 'audio/webm';

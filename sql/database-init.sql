SET schema 'ssrs';

-- FUNCTION: RowCreateTimestamp()

-- DROP FUNCTION IF EXISTS "RowCreateTimestamp"();

CREATE OR REPLACE FUNCTION "RowCreateTimestamp"()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
AS $BODY$
BEGIN
  NEW.create_instant = NOW();
  RETURN NEW;
END;
$BODY$;

COMMENT ON FUNCTION "RowCreateTimestamp"()
    IS 'Sets the create_instant to the current time on row creation';


-- FUNCTION: RowUpdateTimestamp()

-- DROP FUNCTION IF EXISTS "RowUpdateTimestamp"();

CREATE OR REPLACE FUNCTION "RowUpdateTimestamp"()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF
AS $BODY$
BEGIN
  NEW.update_instant = NOW();
  RETURN NEW;
END;
$BODY$;

COMMENT ON FUNCTION "RowUpdateTimestamp"()
    IS 'Sets the update_instant column to the current time on row update';


-- Table: audio

-- DROP TABLE IF EXISTS audio;

CREATE TABLE IF NOT EXISTS audio
(
    audio_file_name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    audio_oid oid NOT NULL,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT audio_pkey PRIMARY KEY (audio_file_name)
)

TABLESPACE pg_default;


-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON audio;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON audio
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON audio;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON audio
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();


-- Table: images

-- DROP TABLE IF EXISTS images;

CREATE TABLE IF NOT EXISTS images
(
    image_name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    image_oid oid NOT NULL,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT images_pkey PRIMARY KEY (image_name)
)

TABLESPACE pg_default;


-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON images;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON images
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON images;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON images
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();



-- Table: users

-- DROP TABLE IF EXISTS users;

CREATE TABLE IF NOT EXISTS users
(
    id bigint NOT NULL,
    username character varying(255) COLLATE pg_catalog."default" NOT NULL,
    password character varying(255) COLLATE pg_catalog."default" NOT NULL,
    account_expiration timestamp without time zone,
    credential_expiration timestamp without time zone,
    locked boolean,
    enabled boolean,
    create_instant timestamp without time zone,
    update_instant timestamp without time zone,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT username UNIQUE (username)
)

TABLESPACE pg_default;

-- SEQUENCE: users_id_seq1

-- DROP SEQUENCE IF EXISTS users_id_seq1;

CREATE SEQUENCE IF NOT EXISTS users_id_seq1
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1
    OWNED BY users.id;

ALTER TABLE IF EXISTS users
    ALTER COLUMN id SET DEFAULT nextval('users_id_seq1'::regclass);

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON users;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON users
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON users;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON users
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();

-- Table: words

-- DROP TABLE IF EXISTS words;

CREATE TABLE IF NOT EXISTS words
(
    id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    owner character varying(255) COLLATE pg_catalog."default",
	kana character varying(255) COLLATE pg_catalog."default" DEFAULT ''::character varying,
    meaning character varying(255) COLLATE pg_catalog."default" DEFAULT ''::character varying,
    kanji character varying(255) COLLATE pg_catalog."default" DEFAULT ''::character varying,
    alt_kanji character varying(255) COLLATE pg_catalog."default" DEFAULT ''::character varying,
    accent character varying(255) COLLATE pg_catalog."default" DEFAULT ''::character varying,
    attributes character varying(255) COLLATE pg_catalog."default",
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    create_seq_num bigint NOT NULL,
    CONSTRAINT word_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON words;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON words
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON words;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON words
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();

-- SEQUENCE: words_create_seq_num_seq

-- DROP SEQUENCE IF EXISTS words_create_seq_num_seq;

CREATE SEQUENCE IF NOT EXISTS words_create_seq_num_seq
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 2147483647
    CACHE 1
    OWNED BY words.create_seq_num;

ALTER TABLE IF EXISTS words
    ALTER COLUMN create_seq_num SET DEFAULT nextval('words_create_seq_num_seq'::regclass);


-- Table: word_audio

-- DROP TABLE IF EXISTS word_audio;

CREATE TABLE IF NOT EXISTS word_audio
(
    word_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    audio_file_name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT word_audio_pkey PRIMARY KEY (word_id, audio_file_name),
    CONSTRAINT word_id FOREIGN KEY (word_id)
        REFERENCES words (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON word_audio;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON word_audio
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON word_audio;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON word_audio
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();


-- Table: lexicon_header

-- DROP TABLE IF EXISTS lexicon_header;

CREATE TABLE IF NOT EXISTS lexicon_header
(
    id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    title character varying(127) COLLATE pg_catalog."default",
    lang integer,
    description character varying(255) COLLATE pg_catalog."default",
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    image_file_name character varying(255) COLLATE pg_catalog."default",
    owner character varying(255) COLLATE pg_catalog."default",
    CONSTRAINT lexicon_header_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON lexicon_header;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON lexicon_header
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON lexicon_header;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON lexicon_header
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();


-- Table: lexicon_review_history

-- DROP TABLE IF EXISTS lexicon_review_history;

CREATE TABLE IF NOT EXISTS lexicon_review_history
(
    lexicon_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    word_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    username character varying(64) COLLATE pg_catalog."default" NOT NULL,
    learned boolean,
    most_recent_test_time timestamp with time zone,
    current_test_delay_sec bigint,
    most_recent_test_relationship_id character varying(64) COLLATE pg_catalog."default",
    current_boost real,
    current_boost_expiration_delay_sec bigint,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT lexicon_review_history_pkey PRIMARY KEY (lexicon_id, word_id, username),
    CONSTRAINT username FOREIGN KEY (username)
        REFERENCES users (username) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT word_id FOREIGN KEY (word_id)
        REFERENCES words (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS lexicon_review_history
    OWNER to postgres;
-- Index: username_lexicon_learned

-- DROP INDEX IF EXISTS username_lexicon_learned;

CREATE INDEX IF NOT EXISTS username_lexicon_learned
    ON lexicon_review_history USING btree
    (username COLLATE pg_catalog."default" ASC NULLS LAST, lexicon_id COLLATE pg_catalog."default" ASC NULLS LAST, learned ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;
-- Index: word_id

-- DROP INDEX IF EXISTS word_id;

CREATE INDEX IF NOT EXISTS word_id
    ON lexicon_review_history USING btree
    (word_id COLLATE pg_catalog."default" ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON lexicon_review_history;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON lexicon_review_history
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON lexicon_review_history;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON lexicon_review_history
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();


-- Table: lexicon_word_test_history

-- DROP TABLE IF EXISTS lexicon_word_test_history;

CREATE TABLE IF NOT EXISTS lexicon_word_test_history
(
    lexicon_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    word_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    relationship_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    username character varying(255) COLLATE pg_catalog."default" NOT NULL,
    total_tests bigint,
    correct_tests bigint,
    correct_streak bigint,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT lexicon_word_test_history_pkey PRIMARY KEY (lexicon_id, word_id, relationship_id, username),
    CONSTRAINT username FOREIGN KEY (username)
        REFERENCES users (username) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT word_id FOREIGN KEY (word_id)
        REFERENCES words (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS lexicon_word_test_history
    OWNER to postgres;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON lexicon_word_test_history;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON lexicon_word_test_history
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON lexicon_word_test_history;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON lexicon_word_test_history
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();


-- Table: scheduled_review

-- DROP TABLE IF EXISTS scheduled_review;

CREATE TABLE IF NOT EXISTS scheduled_review
(
    id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    lexicon_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    word_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    scheduled_test_time timestamp with time zone NOT NULL,
    completed boolean,
    test_delay_ms bigint,
    test_relationship_id character varying(64) COLLATE pg_catalog."default",
    review_type character varying(64) COLLATE pg_catalog."default",
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    owner character varying(255) COLLATE pg_catalog."default",
    CONSTRAINT scheduled_review_pkey PRIMARY KEY (id),
    CONSTRAINT word_id FOREIGN KEY (word_id)
        REFERENCES words (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS scheduled_review
    OWNER to postgres;
-- Index: lexiconId-scheduledTestTime-completed

-- DROP INDEX IF EXISTS "lexiconId-scheduledTestTime-completed";

CREATE INDEX IF NOT EXISTS "lexiconId-scheduledTestTime-completed"
    ON scheduled_review USING btree
    (lexicon_id COLLATE pg_catalog."default" ASC NULLS LAST, scheduled_test_time ASC NULLS LAST, completed ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON scheduled_review;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON scheduled_review
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON scheduled_review;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON scheduled_review
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();



-- Table: review_events

-- DROP TABLE IF EXISTS review_events;

CREATE TABLE IF NOT EXISTS review_events
(
    event_id bigint NOT NULL DEFAULT nextval('review_events_id_seq1'::regclass),
    lexicon_id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    word_id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    review_type character varying(255) COLLATE pg_catalog."default",
    review_mode character varying(255) COLLATE pg_catalog."default",
    test_on character varying(255) COLLATE pg_catalog."default",
    prompt_with character varying(255) COLLATE pg_catalog."default",
    correct boolean,
    near_miss boolean,
    elapsed_time_ms bigint,
    processed boolean,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    username character varying(255) COLLATE pg_catalog."default",
    event_instant timestamp with time zone,
    override boolean,
    scheduled_review_id character varying(64) COLLATE pg_catalog."default",
    CONSTRAINT review_events_pkey PRIMARY KEY (event_id),
    CONSTRAINT scheduled_review_id FOREIGN KEY (scheduled_review_id)
        REFERENCES scheduled_review (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT word_id FOREIGN KEY (word_id)
        REFERENCES words (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS review_events
    OWNER to postgres;
-- Index: username_lexicon_processed

-- DROP INDEX IF EXISTS username_lexicon_processed;

CREATE INDEX IF NOT EXISTS username_lexicon_processed
    ON review_events USING btree
    (username COLLATE pg_catalog."default" ASC NULLS LAST, lexicon_id COLLATE pg_catalog."default" ASC NULLS LAST, processed ASC NULLS LAST)
    TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON review_events;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON review_events
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON review_events;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON review_events
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();
    
    
-- Table: user_config

-- DROP TABLE IF EXISTS user_config;

CREATE TABLE IF NOT EXISTS user_config
(
    username character varying(64) COLLATE pg_catalog."default" NOT NULL,
    setting_name character varying(64) COLLATE pg_catalog."default" NOT NULL,
    setting_value character varying(255) COLLATE pg_catalog."default",
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT user_config_pkey PRIMARY KEY (username, setting_name),
    CONSTRAINT username FOREIGN KEY (username)
        REFERENCES users (username) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON user_config;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON user_config
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON user_config;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE 
    ON user_config
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();
    
    
-- Table: user_notepad

-- DROP TABLE IF EXISTS user_notepad;

CREATE TABLE IF NOT EXISTS user_notepad
(
    username character varying(64) COLLATE pg_catalog."default" NOT NULL,
    notepad_text character varying(1048576) COLLATE pg_catalog."default",
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    CONSTRAINT user_notepad_pkey PRIMARY KEY (username),
    CONSTRAINT username FOREIGN KEY (username)
        REFERENCES users (username) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON user_notepad;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON user_notepad
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON user_notepad;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE 
    ON user_notepad
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();
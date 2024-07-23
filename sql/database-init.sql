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


-- Table: language

-- DROP TABLE IF EXISTS language;

CREATE TABLE IF NOT EXISTS language
(
    id numeric NOT NULL,
    display_name character varying(128) COLLATE pg_catalog."default",
    font character varying(64) COLLATE pg_catalog."default",
    audio_file_regex character varying(64) COLLATE pg_catalog."default",
    tests_to_double numeric,
    CONSTRAINT language_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;


-- Table: word_elements

-- DROP TABLE IF EXISTS word_elements;

CREATE TABLE IF NOT EXISTS word_elements
(
    id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    name character varying(64) COLLATE pg_catalog."default" NOT NULL,
    abbreviation character varying(16) COLLATE pg_catalog."default",
    weight numeric,
    apply_language_font boolean,
    test_time_multiplier numeric,
    CONSTRAINT word_elements_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;


-- Table: language_elements

-- DROP TABLE IF EXISTS language_elements;

CREATE TABLE IF NOT EXISTS language_elements
(
    language_id numeric NOT NULL,
    word_element_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    required boolean,
    core boolean,
    ordinal numeric,
    dedupe boolean,
    CONSTRAINT language_elements_pkey PRIMARY KEY (language_id, word_element_id),
    CONSTRAINT language_id FOREIGN KEY (language_id)
        REFERENCES language (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT word_element_id FOREIGN KEY (word_element_id)
        REFERENCES word_elements (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;


-- Table: language_test_relationships

-- DROP TABLE IF EXISTS language_test_relationships;

CREATE TABLE IF NOT EXISTS language_test_relationships
(
    id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    language_id numeric NOT NULL,
    test_on character varying(64) COLLATE pg_catalog."default" NOT NULL,
    prompt_with character varying(64) COLLATE pg_catalog."default" NOT NULL,
    show_after_test character varying(64) COLLATE pg_catalog."default",
    ordinal numeric NOT NULL,
    display_name character varying(255) COLLATE pg_catalog."default",
    fallback_id character varying(64) COLLATE pg_catalog."default",
    is_review_relationship boolean,
    CONSTRAINT language_review_types_pkey PRIMARY KEY (id),
    CONSTRAINT language_id FOREIGN KEY (language_id)
        REFERENCES language (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT prompt_with FOREIGN KEY (language_id, prompt_with)
        REFERENCES language_elements (language_id, word_element_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT show_after_test FOREIGN KEY (language_id, show_after_test)
        REFERENCES language_elements (language_id, word_element_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT test_on FOREIGN KEY (language_id, test_on)
        REFERENCES language_elements (language_id, word_element_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;




-- Table: language_sequence

-- DROP TABLE IF EXISTS language_sequence;

CREATE TABLE IF NOT EXISTS language_sequence
(
    seq_id bigint NOT NULL,
    language_id bigint,
    review_type character varying(64) COLLATE pg_catalog."default",
    ordinal bigint,
    review_mode character varying(64) COLLATE pg_catalog."default",
    option_count bigint,
    record_event boolean,
    relationship_id character varying(64) COLLATE pg_catalog."default",
    CONSTRAINT language_sequence_pkey PRIMARY KEY (seq_id),
    CONSTRAINT language_id FOREIGN KEY (language_id)
        REFERENCES language (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT relationship_id FOREIGN KEY (relationship_id)
        REFERENCES language_test_relationships (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

-- SEQUENCE: language_sequence_id_seq1

-- DROP SEQUENCE IF EXISTS language_sequence_id_seq1;

CREATE SEQUENCE IF NOT EXISTS language_sequence_id_seq1
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1
    OWNED BY language_sequence.seq_id;

ALTER TABLE IF EXISTS language_sequence
    ALTER COLUMN seq_id SET DEFAULT nextval('language_sequence_id_seq1'::regclass);

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


-- Table: lexicon_words

-- DROP TABLE IF EXISTS lexicon_words;

CREATE TABLE IF NOT EXISTS lexicon_words
(
    lexicon_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    word_id character varying(64) COLLATE pg_catalog."default" NOT NULL,
    create_instant timestamp with time zone,
    update_instant timestamp with time zone,
    create_seq_num bigint NOT NULL,
    CONSTRAINT lexicon_word_pkey PRIMARY KEY (lexicon_id, word_id)
)

TABLESPACE pg_default;

-- Index: lexicon_words_create_instant_idx

-- DROP INDEX IF EXISTS lexicon_words_create_instant_idx;

CREATE INDEX IF NOT EXISTS lexicon_words_create_instant_idx
    ON lexicon_words USING btree
    (create_instant ASC NULLS LAST)
    TABLESPACE pg_default;

-- Trigger: RowCreateTimestamp

-- DROP TRIGGER IF EXISTS "RowCreateTimestamp" ON lexicon_words;

CREATE OR REPLACE TRIGGER "RowCreateTimestamp"
    BEFORE INSERT
    ON lexicon_words
    FOR EACH ROW
    EXECUTE FUNCTION "RowCreateTimestamp"();

-- Trigger: RowUpdateTimestamp

-- DROP TRIGGER IF EXISTS "RowUpdateTimestamp" ON lexicon_words;

CREATE OR REPLACE TRIGGER "RowUpdateTimestamp"
    BEFORE INSERT OR UPDATE
    ON lexicon_words
    FOR EACH ROW
    EXECUTE FUNCTION "RowUpdateTimestamp"();


-- SEQUENCE: ssrs_prod.lexicon_words_create_seq_num_seq

-- DROP SEQUENCE IF EXISTS ssrs_prod.lexicon_words_create_seq_num_seq;

CREATE SEQUENCE IF NOT EXISTS lexicon_words_create_seq_num_seq
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1
    OWNED BY lexicon_words.create_seq_num;

ALTER TABLE IF EXISTS lexicon_words
    ALTER COLUMN create_seq_num SET DEFAULT nextval('lexicon_words_create_seq_num_seq'::regclass);


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
    CONSTRAINT lexicon_words FOREIGN KEY (lexicon_id, word_id)
        REFERENCES lexicon_words (lexicon_id, word_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT relationship_id FOREIGN KEY (most_recent_test_relationship_id)
        REFERENCES language_test_relationships (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT username FOREIGN KEY (username)
        REFERENCES users (username) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

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
    CONSTRAINT lexicon_word_id FOREIGN KEY (lexicon_id, word_id)
        REFERENCES lexicon_words (lexicon_id, word_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT relationship_id FOREIGN KEY (relationship_id)
        REFERENCES language_test_relationships (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT username FOREIGN KEY (username)
        REFERENCES users (username) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

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
    CONSTRAINT scheduled_review_pkey PRIMARY KEY (id),
    CONSTRAINT lexicon_word FOREIGN KEY (lexicon_id, word_id)
        REFERENCES lexicon_words (lexicon_id, word_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT test_relationship FOREIGN KEY (test_relationship_id)
        REFERENCES language_test_relationships (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

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
    event_id bigint NOT NULL,
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
    CONSTRAINT lexicon_word_id FOREIGN KEY (lexicon_id, word_id)
        REFERENCES lexicon_words (lexicon_id, word_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT scheduled_review_id FOREIGN KEY (scheduled_review_id)
        REFERENCES scheduled_review (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

-- SEQUENCE: review_events_id_seq1

-- DROP SEQUENCE IF EXISTS review_events_id_seq1;

CREATE SEQUENCE IF NOT EXISTS review_events_id_seq1
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1
    OWNED BY review_events.event_id;

ALTER TABLE IF EXISTS review_events
    ALTER COLUMN event_id SET DEFAULT nextval('review_events_id_seq1'::regclass);

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
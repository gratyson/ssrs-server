SET schema 'ssrs';

INSERT INTO language(
	id, display_name, font, audio_file_regex, tests_to_double)
	VALUES (1, 'Japanese', 'Hiragino Kaku Gothic Pro, Meiryo', '(^%kana%_[0-9]+\..*)|(^%kana%_\(%kanji%\)_[0-9]+\..*)', 3);

INSERT INTO word_elements(
	id, name, abbreviation, weight, apply_language_font, test_time_multiplier, validation_regex, description)
	VALUES ('kana', 'Kana', 'Kana', 2, true, null, null, null);

INSERT INTO word_elements(
	id, name, abbreviation, weight, apply_language_font, test_time_multiplier, validation_regex, description)
	VALUES ('kanji', 'Kanji', 'Kanji', 2, true, 2, null, null);

INSERT INTO word_elements(
	id, name, abbreviation, weight, apply_language_font, test_time_multiplier, validation_regex, description)
	VALUES ('alt_kanji', 'Alternate Kanji', 'Alt Kanji', 2, true, null, null, null);

INSERT INTO word_elements(
	id, name, abbreviation, weight, apply_language_font, test_time_multiplier, validation_regex, description)
	VALUES ('meaning', 'Meaning', 'Meaning', 5, false, null, null, null);

INSERT INTO word_elements(
	id, name, abbreviation, weight, apply_language_font, test_time_multiplier, validation_regex, description)
	VALUES ('accent', 'Accent', 'Accent', 1, false, null, '^\d+(,\d+)*$', 'The position in the word where the accent occurs. 0 indicates a flat accent. In the case of multiple valid accents, the positions are comma seperated and ordered from most common to least common.');

INSERT INTO language_elements(
	language_id, word_element_id, required, core, ordinal, dedupe, show_in_overview)
	VALUES (1, 'kana', true, true, 1, true, true);

INSERT INTO language_elements(
	language_id, word_element_id, required, core, ordinal, dedupe, show_in_overview)
	VALUES (1, 'meaning', true, true, 2, false, true);

INSERT INTO language_elements(
	language_id, word_element_id, required, core, ordinal, dedupe, show_in_overview)
	VALUES (1, 'kanji', false, true, 3, true, true);

INSERT INTO language_elements(
	language_id, word_element_id, required, core, ordinal, dedupe, show_in_overview)
	VALUES (1, 'alt_kanji', false, false, 4, false, true);

INSERT INTO language_elements(
	language_id, word_element_id, required, core, ordinal, dedupe, show_in_overview)
	VALUES (1, 'alt_kanji', false, false, 4, false, true);

INSERT INTO language_elements(
	language_id, word_element_id, required, core, ordinal, dedupe, show_in_overview)
	VALUES (1, 'accent', false, false, 5, false, false);

INSERT INTO language_test_relationships(
	id, language_id, test_on, prompt_with, show_after_test, ordinal, display_name, fallback_id, is_review_relationship)
	VALUES ('meaning-to-kana', 1, 'kana', 'meaning', 'kanji', 1, 'Meaning => Kana', null, true);

INSERT INTO language_test_relationships(
	id, language_id, test_on, prompt_with, show_after_test, ordinal, display_name, fallback_id, is_review_relationship)
	VALUES ('meaning-to-kanji', 1, 'kanji', 'meaning', 'kana', 2, 'Meaning => Kanji', 'meaning-to-kana', true);

INSERT INTO language_test_relationships(
	id, language_id, test_on, prompt_with, show_after_test, ordinal, display_name, fallback_id, is_review_relationship)
	VALUES ('kanji-to-kana', 1, 'kana', 'kanji', 'meaning', 3, 'Kanji => Kana', 'meaning-to-kana', true);

INSERT INTO language_test_relationships(
	id, language_id, test_on, prompt_with, show_after_test, ordinal, display_name, fallback_id, is_review_relationship)
	VALUES ('kana-to-meaning', 1, 'meaning', 'kana', 'kanji', 4, 'Kana => Meaning', null, false);

INSERT INTO language_test_relationships(
	id, language_id, test_on, prompt_with, show_after_test, ordinal, display_name, fallback_id, is_review_relationship)
	VALUES ('kanji-to-meaning', 1, 'meaning', 'kanji', 'kana', 5, 'Kanji => Meaning', 'kana-to-meaning', false);

INSERT INTO language_test_relationships(
	id, language_id, test_on, prompt_with, show_after_test, ordinal, display_name, fallback_id, is_review_relationship)
	VALUES ('kana-to-kanji', 1, 'kanji', 'kana', 'meaning', 6, 'Kana => Kanji', 'kana-to-meaning', false);

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 1, 'WordOverview', null, false, null);

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 2, 'MultipleChoiceTest', 4, false, 'meaning-to-kana');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 3, 'MultipleChoiceTest', 6, false, 'kana-to-meaning');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 4, 'TypingTest', null, false, 'meaning-to-kana');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 5, 'MultipleChoiceTest', 4, false, 'meaning-to-kanji');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 6, 'MultipleChoiceTest', 6, false, 'kanji-to-meaning');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 7, 'TypingTest', null, false, 'meaning-to-kanji');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 8, 'MultipleChoiceTest', 6, false, 'kanji-to-kana');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 9, 'MultipleChoiceTest', 8, false, 'kana-to-kanji');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 10, 'TypingTest', null, false, 'kanji-to-kana');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 11, 'TypingTest', null, false, 'meaning-to-kana');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 12, 'TypingTest', null, false, 'meaning-to-kanji');

INSERT INTO language_sequence(
	language_id, review_type, ordinal, review_mode, option_count, record_event, relationship_id)
	VALUES (1, 'Learn', 13, 'TypingTest', null, true, 'kanji-to-kana');



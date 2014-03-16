# Destination schema

# --- !Ups

CREATE SEQUENCE destination_id_seq;
CREATE TABLE destination (
  id integer NOT NULL DEFAULT nextval('destination_id_seq'),
  originalUrl text NOT NULL,
  shortUrlHash text NOT NULL,
  fileName text NOT NULL,
  contentType text NOT NULL
);

# --- !Downs

DROP TABLE destination;
DROP SEQUENCE destination_id_seq;
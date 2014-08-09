# Destination schema

# --- !Ups

CREATE SEQUENCE destination_id_seq;
CREATE TABLE destination (
  id integer NOT NULL DEFAULT nextval('destination_id_seq'),
  userSeqId integer NOT NULL,
  originalUrl text NOT NULL,
  shortUrlHash text NOT NULL,
  fileName text NOT NULL,
  contentType text NOT NULL,
  expirationTime timestamp,
  isExpired boolean default false
);

CREATE SEQUENCE p_user_id_seq;
CREATE TABLE p_user (
  seqId integer NOT NULL DEFAULT nextval('p_user_id_seq'),
  userId text NOT NULL,
  providerId text NOT NULL,
  firstName text,
  lastName text,
  fullName text,
  email text,
  oAuth1Token text,
  oAuth1Secret text,
  oAuth2AccessToken text,
  oAuth2TokenType text,
  oAuth2ExpiresIn integer,
  oAuth2RefreshToken text,
  avatarUrl text,
  passwordHasher text,
  password text,
  passwordSalt text,
  authenticationMethod text,
  isAdmin boolean default false
);

CREATE TABLE token (
  uuid text NOT NULL,
  email text NOT NULL,
  creationTime timestamp,
  expirationTime timestamp,
  isSignUp boolean
);

# --- !Downs

DROP TABLE destination;
DROP SEQUENCE destination_id_seq;

DROP TABLE user;
DROP TABLE token;
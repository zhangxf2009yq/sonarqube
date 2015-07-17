CREATE TABLE "PROJECT_MEASURES" (
  "ID" BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "VALUE" DOUBLE,
  "METRIC_ID" INTEGER NOT NULL,
  "SNAPSHOT_ID" INTEGER,
  "RULE_ID" INTEGER,
  "RULES_CATEGORY_ID" INTEGER,
  "TEXT_VALUE" VARCHAR(4000),
  "TENDENCY" INTEGER,
  "MEASURE_DATE" TIMESTAMP,
  "PROJECT_ID" INTEGER,
  "ALERT_STATUS" VARCHAR(5),
  "ALERT_TEXT" VARCHAR(4000),
  "URL" VARCHAR(2000),
  "DESCRIPTION" VARCHAR(4000),
  "RULE_PRIORITY" INTEGER,
  "CHARACTERISTIC_ID" INTEGER,
  "PERSON_ID" INTEGER,
  "VARIATION_VALUE_1" DOUBLE,
  "VARIATION_VALUE_2" DOUBLE,
  "VARIATION_VALUE_3" DOUBLE,
  "VARIATION_VALUE_4" DOUBLE,
  "VARIATION_VALUE_5" DOUBLE,
  "MEASURE_DATA" BINARY(167772150)
);

CREATE TABLE "METRICS" (
  "ID" INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "NAME" VARCHAR(64) NOT NULL,
  "DESCRIPTION" VARCHAR(255),
  "DIRECTION" INTEGER NOT NULL DEFAULT 0,
  "DOMAIN" VARCHAR(64),
  "SHORT_NAME" VARCHAR(64),
  "QUALITATIVE" BOOLEAN NOT NULL DEFAULT FALSE,
  "VAL_TYPE" VARCHAR(8),
  "USER_MANAGED" BOOLEAN DEFAULT FALSE,
  "ENABLED" BOOLEAN DEFAULT TRUE,
  "ORIGIN" VARCHAR(3),
  "WORST_VALUE" DOUBLE,
  "BEST_VALUE" DOUBLE,
  "OPTIMIZED_BEST_VALUE" BOOLEAN,
  "HIDDEN" BOOLEAN,
  "DELETE_HISTORICAL_DATA" BOOLEAN
);

CREATE TABLE "SNAPSHOTS" (
  "ID" INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "CREATED_AT" TIMESTAMP,
  "BUILD_DATE" TIMESTAMP,
  "PROJECT_ID" INTEGER NOT NULL,
  "PARENT_SNAPSHOT_ID" INTEGER,
  "STATUS" VARCHAR(4) NOT NULL DEFAULT 'U',
  "PURGE_STATUS" INTEGER,
  "ISLAST" BOOLEAN NOT NULL DEFAULT FALSE,
  "SCOPE" VARCHAR(3),
  "QUALIFIER" VARCHAR(10),
  "ROOT_SNAPSHOT_ID" INTEGER,
  "VERSION" VARCHAR(500),
  "PATH" VARCHAR(500),
  "DEPTH" INTEGER,
  "ROOT_PROJECT_ID" INTEGER,
  "PERIOD1_MODE" VARCHAR(100),
  "PERIOD1_PARAM" VARCHAR(100),
  "PERIOD1_DATE" TIMESTAMP,
  "PERIOD2_MODE" VARCHAR(100),
  "PERIOD2_PARAM" VARCHAR(100),
  "PERIOD2_DATE" TIMESTAMP,
  "PERIOD3_MODE" VARCHAR(100),
  "PERIOD3_PARAM" VARCHAR(100),
  "PERIOD3_DATE" TIMESTAMP,
  "PERIOD4_MODE" VARCHAR(100),
  "PERIOD4_PARAM" VARCHAR(100),
  "PERIOD4_DATE" TIMESTAMP,
  "PERIOD5_MODE" VARCHAR(100),
  "PERIOD5_PARAM" VARCHAR(100),
  "PERIOD5_DATE" TIMESTAMP
);
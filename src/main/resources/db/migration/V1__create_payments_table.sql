CREATE TABLE payments (
                          id              UUID            PRIMARY KEY,
                          payer_id        VARCHAR(100)    NOT NULL,
                          payee_id        VARCHAR(100)    NOT NULL,
                          amount          NUMERIC(19, 2)  NOT NULL,
                          currency        VARCHAR(3)      NOT NULL,
                          status          VARCHAR(20)     NOT NULL,
                          created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
                          updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
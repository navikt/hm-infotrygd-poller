-- Siden nais-cli dobbelt quoter brukernavnet, så prøver vi her i stede.
alter default privileges in schema public grant select on tables to "bigquery";

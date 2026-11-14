SELECT count(*) AS transactions,
       round(extract(epoch FROM (max(timestamp) - min(timestamp)))::numeric, 1) AS span_s,
       round(count(*) / extract(epoch FROM (max(timestamp) - min(timestamp)))::numeric, 1) AS tx_per_sec
FROM transactions;

SELECT count(*) AS alerts,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY ms)::numeric, 1) AS p50_ms,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY ms)::numeric, 1) AS p95_ms,
       round(max(ms)::numeric, 1) AS max_ms
FROM (SELECT extract(epoch FROM (a.created_at - t.timestamp)) * 1000 AS ms
      FROM alerts a JOIN transactions t ON t.id = a.transaction_id) x;

SELECT rule_name, count(*) FROM alerts GROUP BY rule_name ORDER BY rule_name;
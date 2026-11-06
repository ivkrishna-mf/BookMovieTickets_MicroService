-- Distributed token bucket (atomic in Redis; one key per limited resource).
-- KEYS[1] = bucket key
-- ARGV[1] = capacity (max burst)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = tokens requested
-- returns { allowed (1|0), tokens_remaining (floor), retry_after_ms }
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local t = redis.call('TIME')                       -- use Redis clock: no skew between pods
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])
if tokens == nil then tokens = capacity end
if ts == nil then ts = now end

local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + (elapsed * rate / 1000))

local allowed = 0
local retry_after = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry_after = math.ceil((requested - tokens) * 1000 / rate)
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
-- idle buckets disappear once they would be full again
redis.call('PEXPIRE', key, math.ceil(capacity * 1000 / rate) + 1000)
return { allowed, math.floor(tokens), retry_after }

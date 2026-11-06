-- All-or-nothing multi-seat hold. Every key shares the {showId} hash tag => same Redis Cluster slot,
-- so this multi-key script is legal in ElastiCache cluster mode and executes atomically.
-- KEYS[i] = seat-hold:{showId}:<seatNumber>
-- ARGV[1] = holdRef (booking id)   ARGV[2] = ttl in ms
-- returns the list of conflicting keys (empty list = success)
local conflicts = {}
for _, key in ipairs(KEYS) do
  local owner = redis.call('GET', key)
  if owner and owner ~= ARGV[1] then
    table.insert(conflicts, key)
  end
end
if #conflicts > 0 then
  return conflicts
end
for _, key in ipairs(KEYS) do
  redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
end
return {}

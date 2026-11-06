-- Compare-and-delete: only the owner of a hold may release it.
-- KEYS[i] = seat-hold:{showId}:<seatNumber>   ARGV[1] = holdRef
local released = 0
for _, key in ipairs(KEYS) do
  if redis.call('GET', key) == ARGV[1] then
    redis.call('DEL', key)
    released = released + 1
  end
end
return released

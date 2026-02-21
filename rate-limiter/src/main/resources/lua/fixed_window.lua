--[[
  Fixed Window Rate Limiter — Lua Script
  -------------------------------------------------------
  KEYS[1]   : storage key  (e.g. "rl:10.0.0.1:GET:/api/test")
  ARGV[1]   : maxRequests  (int)
  ARGV[2]   : windowMs     (long, milliseconds)
  ARGV[3]   : nowMs        (long, milliseconds — current epoch)
  ARGV[4]   : ttlSeconds   (int, key TTL = windowSizeInSeconds * 2)

  Returns a 3-element array:
    [1] count        (int)   — request count AFTER this request
    [2] windowStart  (long)  — epoch ms of the current window start
    [3] allowed      (int)   — 1 if allowed, 0 if blocked
--]]

local key         = KEYS[1]
local maxRequests = tonumber(ARGV[1])
local windowMs    = tonumber(ARGV[2])
local nowMs       = tonumber(ARGV[3])
local ttlSeconds  = tonumber(ARGV[4])

-- Read existing state
local count       = tonumber(redis.call('HGET', key, 'count'))       or 0
local windowStart = tonumber(redis.call('HGET', key, 'windowStart')) or nowMs

-- Check if window has expired → reset
if (nowMs - windowStart) >= windowMs then
    count       = 0
    windowStart = nowMs
end

-- Increment
count = count + 1

-- Persist
redis.call('HSET',   key, 'count', count, 'windowStart', windowStart)
redis.call('EXPIRE', key, ttlSeconds)

-- Decision
local allowed = (count <= maxRequests) and 1 or 0

return {count, windowStart, allowed}

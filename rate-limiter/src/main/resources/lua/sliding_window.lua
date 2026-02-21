--[[
  Sliding Window Log Rate Limiter — Lua Script
  -------------------------------------------------------
  Uses a Redis Sorted Set where each member is a unique request ID
  (e.g. nowMs + random suffix via table index) and the score is the
  timestamp in milliseconds.

  KEYS[1]   : storage key
  ARGV[1]   : maxRequests  (int)
  ARGV[2]   : windowMs     (long, milliseconds)
  ARGV[3]   : nowMs        (long, milliseconds)
  ARGV[4]   : ttlSeconds   (int)

  Returns a 3-element array:
    [1] count    (int)  — window count AFTER this request
    [2] oldest   (long) — timestamp of oldest entry in the window (ms), or nowMs if empty
    [3] allowed  (int)  — 1 if allowed, 0 if blocked
--]]

local key         = KEYS[1]
local maxRequests = tonumber(ARGV[1])
local windowMs    = tonumber(ARGV[2])
local nowMs       = tonumber(ARGV[3])
local ttlSeconds  = tonumber(ARGV[4])

local cutoff = nowMs - windowMs

-- Remove timestamps that have fallen outside the sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)

-- Count entries currently in the window
local count = tonumber(redis.call('ZCARD', key))

local allowed = 0
if count < maxRequests then
    -- Add this request's timestamp as both score and member (unique via counter)
    local member = nowMs .. '-' .. redis.call('INCR', key .. ':seq')
    redis.call('ZADD', key, nowMs, member)
    -- Keep the sequence counter TTL in sync
    redis.call('EXPIRE', key .. ':seq', ttlSeconds)
    count   = count + 1
    allowed = 1
end

-- Set TTL on the sorted set
redis.call('EXPIRE', key, ttlSeconds)

-- Oldest in-window entry (for reset time calculation)
local oldestEntries = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
local oldest = nowMs
if #oldestEntries >= 2 then
    oldest = tonumber(oldestEntries[2])
end

return {count, oldest, allowed}

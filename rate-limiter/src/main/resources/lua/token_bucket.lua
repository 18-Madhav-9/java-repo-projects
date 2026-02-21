--[[
  Token Bucket Rate Limiter — Lua Script
  -------------------------------------------------------
  Lazy refill: tokens are added proportionally to elapsed time on each
  request, calculated on demand (no background refill thread).

  KEYS[1]   : storage key
  ARGV[1]   : maxTokens      (int  — bucket capacity)
  ARGV[2]   : refillRate     (float — tokens per second)
  ARGV[3]   : nowMs          (long — current epoch ms)
  ARGV[4]   : ttlSeconds     (int)

  Returns a 3-element array:
    [1] tokensFloor  (int)   — floor of available tokens AFTER this request
    [2] allowed      (int)   — 1 if allowed, 0 if blocked
    [3] lastRefillMs (long)  — lastRefillTimestamp stored
--]]

local key        = KEYS[1]
local maxTokens  = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local nowMs      = tonumber(ARGV[3])
local ttlSeconds = tonumber(ARGV[4])

-- Read existing state
local tokens    = tonumber(redis.call('HGET', key, 'tokens'))    or maxTokens
local lastRefMs = tonumber(redis.call('HGET', key, 'lastRefMs')) or nowMs

-- Lazy refill
local elapsedSec = (nowMs - lastRefMs) / 1000.0
local refilled   = tokens + (elapsedSec * refillRate)
if refilled > maxTokens then
    refilled = maxTokens
end
lastRefMs = nowMs

-- Try to consume one token
local allowed = 0
if refilled >= 1.0 then
    refilled = refilled - 1.0
    allowed  = 1
end

-- Persist new state
redis.call('HSET',   key, 'tokens', tostring(refilled), 'lastRefMs', lastRefMs)
redis.call('EXPIRE', key, ttlSeconds)

local tokensFloor = math.floor(refilled)
return {tokensFloor, allowed, lastRefMs}

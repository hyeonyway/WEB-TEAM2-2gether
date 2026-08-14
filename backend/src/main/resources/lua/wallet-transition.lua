-- KEYS: wallet balance hash, idempotency result key, timeline stream
-- ARGV: eventId, eventType, userId, amount, idempotencyKey, requestHash, now, maxTransactionAmount, maxBalance
-- eventType: wallet.charged.v1 | wallet.refunded.v1 | wallet.settled.v1 | wallet.cancel-refunded.v1
local function integerString(value)
    return string.format('%.0f', value)
end

local existing = redis.call('GET', KEYS[2])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[6] then
        return 'REJECTED|IDEMPOTENCY_CONFLICT'
    end
    return string.sub(existing, separator + 1) .. '|true'
end

local available = tonumber(redis.call('HGET', KEYS[1], 'availableBalance'))
local frozen = tonumber(redis.call('HGET', KEYS[1], 'frozenBalance'))
local amount = tonumber(ARGV[4])
local maxTransactionAmount = tonumber(ARGV[8])
local maxBalance = tonumber(ARGV[9])
if not available or not frozen or not amount or not maxTransactionAmount or not maxBalance or amount <= 0 then
    return 'REJECTED|STATE_MISSING'
end

local debit = ARGV[2] == 'wallet.refunded.v1'
local externallyRequested = ARGV[2] == 'wallet.charged.v1' or debit
if externallyRequested and amount > maxTransactionAmount then return 'REJECTED|AMOUNT_LIMIT_EXCEEDED' end
if debit and available < amount then return 'REJECTED|INSUFFICIENT_BALANCE' end
local nextAvailable = debit and (available - amount) or (available + amount)
if not debit and nextAvailable + frozen > maxBalance then return 'REJECTED|BALANCE_LIMIT_EXCEEDED' end

local version = redis.call('HINCRBY', KEYS[1], 'walletVersion', 1)
local nextAvailableString = integerString(nextAvailable)
local frozenString = integerString(frozen)
local amountString = integerString(amount)
local versionString = integerString(version)
redis.call('HSET', KEYS[1], 'availableBalance', nextAvailableString, 'frozenBalance', frozenString)

local streamId = redis.call('XADD', KEYS[3], '*',
    'schemaVersion', '2', 'eventId', ARGV[1], 'eventType', ARGV[2], 'userId', ARGV[3],
    'walletVersion', versionString, 'availableBalance', nextAvailableString, 'frozenBalance', frozenString,
    'transactionType', debit and 'REFUND' or (ARGV[2] == 'wallet.charged.v1' and 'CHARGE' or
        (ARGV[2] == 'wallet.settled.v1' and 'ORDER_SETTLEMENT' or 'ORDER_CANCEL_REFUND')),
    'transactionAmount', amountString, 'idempotencyKey', ARGV[5], 'occurredAt', ARGV[7])
local result = 'ACCEPTED|' .. streamId .. '|' .. nextAvailableString .. '|' .. frozenString .. '|' .. versionString
redis.call('SET', KEYS[2], ARGV[6] .. '|' .. result, 'EX', 86400)
return result .. '|false'

import http from 'node:http'
import https from 'node:https'

const args = new Map()
for (let index = 2; index < process.argv.length; index += 2) {
  args.set(process.argv[index], process.argv[index + 1])
}

const targetUrl = new URL(args.get('--url') || 'http://127.0.0.1:8080/api/v1/events')
const targetConnections = Number(args.get('--connections') || 1000)
const rampMs = Number(args.get('--ramp-ms') || 30000)
const holdMs = Number(args.get('--hold-ms') || 60000)

if (!Number.isInteger(targetConnections) || targetConnections < 1 || targetConnections > 50000) {
  throw new Error('--connections must be an integer between 1 and 50000')
}
if (!Number.isFinite(rampMs) || rampMs < 0 || !Number.isFinite(holdMs) || holdMs < 1000) {
  throw new Error('--ramp-ms must be non-negative and --hold-ms must be at least 1000')
}

const transport = targetUrl.protocol === 'https:' ? https : http
const agent = new transport.Agent({ keepAlive: false, maxSockets: targetConnections })
const requests = new Map()
const stats = {
  opened: 0,
  accepted: 0,
  active: 0,
  readyEvents: 0,
  heartbeats: 0,
  updateEvents: 0,
  statusErrors: 0,
  networkErrors: 0,
  reconnects: 0,
  bytes: 0
}
let stopping = false

const report = () => {
  const memoryMb = Math.round(process.memoryUsage().rss / 1024 / 1024)
  process.stdout.write(`${new Date().toISOString()} active=${stats.active} accepted=${stats.accepted} ` +
    `ready=${stats.readyEvents} updates=${stats.updateEvents} statusErrors=${stats.statusErrors} ` +
    `networkErrors=${stats.networkErrors} rssMb=${memoryMb}\n`)
}

const openConnection = (slot) => {
  if (stopping) return
  stats.opened += 1
  let active = false
  let finished = false
  let buffer = ''

  const retry = () => {
    if (finished) return
    finished = true
    requests.delete(slot)
    if (active) stats.active -= 1
    if (!stopping) {
      stats.reconnects += 1
      setTimeout(() => openConnection(slot), 500 + Math.floor(Math.random() * 1000))
    }
  }

  const request = transport.get(targetUrl, {
    agent,
    headers: { Accept: 'text/event-stream', 'Cache-Control': 'no-cache' }
  }, (response) => {
    if (response.statusCode !== 200) {
      stats.statusErrors += 1
      response.resume()
      response.once('end', retry)
      return
    }

    active = true
    stats.accepted += 1
    stats.active += 1
    response.setEncoding('utf8')
    response.on('data', (chunk) => {
      stats.bytes += Buffer.byteLength(chunk)
      buffer += chunk
      let boundary
      while ((boundary = buffer.search(/\r?\n\r?\n/)) >= 0) {
        const block = buffer.slice(0, boundary)
        const separatorLength = buffer.slice(boundary).startsWith('\r\n\r\n') ? 4 : 2
        buffer = buffer.slice(boundary + separatorLength)
        if (block.includes('event:ready') || block.includes('event: ready')) stats.readyEvents += 1
        if (block.includes(':heartbeat') || block.includes(': heartbeat')) stats.heartbeats += 1
        if (block.includes('event:resource-update') || block.includes('event: resource-update')) stats.updateEvents += 1
      }
    })
    response.once('end', retry)
    response.once('close', retry)
    response.once('error', () => {
      if (!stopping) stats.networkErrors += 1
      retry()
    })
  })

  request.setTimeout(0)
  request.once('error', () => {
    if (!stopping) stats.networkErrors += 1
    retry()
  })
  requests.set(slot, request)
}

const stop = () => {
  if (stopping) return
  stopping = true
  clearInterval(reportTimer)
  for (const request of requests.values()) request.destroy()
  requests.clear()
  agent.destroy()
  setTimeout(() => {
    report()
    process.stdout.write(`${JSON.stringify(stats, null, 2)}\n`)
    process.exit(stats.statusErrors || stats.networkErrors ? 1 : 0)
  }, 250)
}

process.stdout.write(`Opening ${targetConnections} SSE connections to ${targetUrl} over ${rampMs}ms; holding for ${holdMs}ms.\n`)
const spacing = targetConnections === 1 ? 0 : rampMs / (targetConnections - 1)
for (let slot = 0; slot < targetConnections; slot += 1) {
  setTimeout(() => openConnection(slot), Math.round(slot * spacing))
}
const reportTimer = setInterval(report, 5000)
setTimeout(stop, rampMs + holdMs)
process.once('SIGINT', stop)
process.once('SIGTERM', stop)

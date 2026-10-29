// Local fixtures derived from AWS SDK operation contracts; no cloud IAM claim.
const http = require('node:http');
http.createServer(async (req, res) => {
  const target = req.headers['x-amz-target'] || '';
  const reply = (body, code = 200) => { res.writeHead(code, { 'Content-Type': 'application/x-amz-json-1.0' }); res.end(JSON.stringify(body)); };
  let body = req.body;
  if (Buffer.isBuffer(body) || body instanceof Uint8Array) body = Buffer.from(body).toString('utf8');
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch { body = {}; } }
  if (!body || typeof body !== 'object') {
    let raw = ''; for await (const chunk of req) raw += chunk;
    try { body = JSON.parse(raw || '{}'); } catch { return reply({ __type: 'ValidationException' }, 400); }
  }
  if (target.includes('PutItem')) {
    if (body.TableName === 'unavailable') return reply({ __type: 'com.amazonaws.dynamodb.v20120810#ResourceNotFoundException', message: 'Injected unavailable table' }, 400);
    if (!body.Item?.pk?.S?.match(/^TICKET#[a-f0-9]{64}$/)) return reply({ __type: 'ValidationException' }, 400);
    await pm.state.set('realtime-aws.ticketWritten', true);
    return reply({});
  }
  if (target.includes('SendMessage')) {
    if ((body.QueueUrl || '').includes('unavailable')) return reply({ __type: 'com.amazonaws.sqs#QueueDoesNotExist', message: 'Injected unavailable queue' }, 400);
    const envelope = JSON.parse(body.MessageBody || '{}');
    if (!envelope.recipientId || !envelope.notificationId || envelope.version !== 1) return reply({ __type: 'InvalidParameterValue' }, 400);
    await pm.state.set('realtime-aws.published', true);
    return reply({ MessageId: 'fixture-message-id', MD5OfMessageBody: require('node:crypto').createHash('md5').update(body.MessageBody).digest('hex') });
  }
  return reply({ message: 'Not modeled by realtime SDK probe' }, 501);
}).listen(process.env.PORT);

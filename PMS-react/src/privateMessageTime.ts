import type {Row} from "./api";

const partsFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
  hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
});

function parseTime(value: unknown) {
  if (!value) return null;
  // Backend LocalDateTime values have no offset and represent server time in China.
  let input = String(value).trim().replace(" ", "T");
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(input)) input += "+08:00";
  const date = new Date(input);
  if (!Number.isFinite(date.getTime())) return null;
  const fields = Object.fromEntries(partsFormatter.formatToParts(date).map(p => [p.type, p.value]));
  const {year, month, day, hour, minute, second} = fields;
  return {
    timestamp: date.getTime(), iso: date.toISOString(), year, month, day,
    dayKey: `${year}-${month}-${day}`, clock: `${hour}:${minute}`,
    full: `${year}-${month}-${day} ${hour}:${minute}:${second}`,
    calendarDay: Date.UTC(Number(year), Number(month) - 1, Number(day)),
  };
}

export function privateMessageTimeline(messages: Row[], now = new Date()) {
  const today = parseTime(now.toISOString())!;
  let previous: ReturnType<typeof parseTime> = null;
  return messages.map(message => {
    const current = parseTime(message.createTime);
    let separator = "";
    if (current && (!previous || current.dayKey !== previous.dayKey || current.timestamp - previous.timestamp >= 5 * 60_000)) {
      if (current.dayKey === today.dayKey) separator = current.clock;
      else if (today.calendarDay - current.calendarDay === 86_400_000) separator = `昨天 ${current.clock}`;
      else separator = `${current.year === today.year ? "" : `${current.year}年`}${Number(current.month)}月${Number(current.day)}日 ${current.clock}`;
    }
    previous = current;
    return {message, separator, fullTime: current?.full || "", dateTime: current?.iso};
  });
}

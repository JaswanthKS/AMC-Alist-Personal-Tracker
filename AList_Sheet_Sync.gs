// A-List Tracker -> Google Sheets sync.
// 1) Change SECRET below to any long random text (you will paste the same text into the app).
// 2) Deploy > New deployment > type "Web app" > Execute as: Me > Who has access: Anyone > Deploy.
// 3) Copy the Web app URL into the app (Lists > gear > Google Sheets live sync).
const SECRET = 'CHANGE-THIS-TO-A-LONG-RANDOM-TEXT';

function doGet() { return out({ ok: true, hello: 'A-List sync is deployed' }); }

function doPost(e) {
  try {
    const d = JSON.parse(e.postData.contents);
    if (d.token !== SECRET) return out({ ok: false, error: 'bad token' });
    if (d.action === 'pull') return out(pullData(SpreadsheetApp.getActiveSpreadsheet()));
    if (!d.movies || d.movies.length === 0) return out({ ok: false, error: 'empty' });
    const ss = SpreadsheetApp.getActiveSpreadsheet();

    // ---- Movie Log: columns A, C, D, E, G..N, O "3D" (B "Day" and F "Total" keep their formulas) ----
    const log = ss.getSheetByName('Movie Log');
    const n = d.movies.length;
    const last = Math.max(log.getLastRow(), n + 1, 986);
    log.getRange(2, 1, last - 1, 1).clearContent();
    log.getRange(2, 3, last - 1, 3).clearContent();
    log.getRange(2, 7, last - 1, 8).clearContent();
    log.getRange(2, 1, n, 1).setValues(d.movies.map(m => [toDate(m.date)]));
    log.getRange(2, 3, n, 3).setValues(d.movies.map(m => [m.title, m.ticket, m.fee]));
    log.getRange(2, 7, n, 8).setValues(d.movies.map(m =>
      [m.format, m.language, m.theater, m.screen, toTime(m.showtime), m.alist, m.notes, m.swag]));

    // 3D tick box column (O). Header is created once, copying the look of column N.
    if (String(log.getRange(1, 15).getValue()).trim() === '') {
      log.getRange(1, 14).copyTo(log.getRange(1, 15), SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);
      log.getRange(1, 15).setValue('3D');
      log.setColumnWidth(15, 60);
    }
    const o3 = log.getRange(2, 15, last - 1, 1);
    o3.clearContent(); o3.clearDataValidations();
    o3.insertCheckboxes();
    log.getRange(2, 15, n, 1).setValues(d.movies.map(m => [m.threeD === true]));

    // ---- Other Theatres: A, C, D, E, F, G (B "Day" keeps its formula) ----
    const oth = ss.getSheetByName('Other Theatres');
    if (oth) {
      const o = d.other || [];
      const lastO = Math.max(oth.getLastRow(), o.length + 1, 100);
      oth.getRange(2, 1, lastO - 1, 1).clearContent();
      oth.getRange(2, 3, lastO - 1, 5).clearContent();
      if (o.length) {
        oth.getRange(2, 1, o.length, 1).setValues(o.map(m => [toDate(m.date)]));
        oth.getRange(2, 3, o.length, 5).setValues(o.map(m => [m.title, m.price, m.format, m.theater, m.notes]));
      }
    }

    // ---- Subscription costs grid on Dashboard (years in P5:P9, Jan..Dec in Q..AB) ----
    const dash = ss.getSheetByName('Dashboard');
    if (dash && d.subs) {
      const years = dash.getRange('P5:P9').getValues().map(r => String(r[0]));
      d.subs.forEach(s => {
        const i = years.indexOf(String(s.year));
        if (i >= 0) dash.getRange(5 + i, 17 + (s.month - 1)).setValue(s.amount);
      });
    }
    return out({ ok: true, movies: n });
  } catch (err) {
    return out({ ok: false, error: String(err) });
  }
}

function toDate(s) { return Utilities.parseDate(String(s), SpreadsheetApp.getActiveSpreadsheet().getSpreadsheetTimeZone(), 'yyyy-MM-dd'); }
function toTime(s) { if (!s) return ''; const p = String(s).split(':'); return ((+p[0]) * 60 + (+p[1])) / 1440; }
function out(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

// ---- Sheet -> app: read what is in the tabs so the app can merge it (read-only, changes nothing) ----
function pullData(ss) {
  const tz = ss.getSpreadsheetTimeZone();
  const log = ss.getSheetByName('Movie Log');
  const n = Math.max(log.getLastRow() - 1, 1);
  const rng = log.getRange(2, 1, n, 15);
  const vals = rng.getValues(), disp = rng.getDisplayValues();
  const movies = [];
  vals.forEach((r, i) => {
    const title = String(r[2]).trim(), date = fmtDate(r[0], tz);
    if (!title || !date) return;
    movies.push({ date: date, title: title, ticket: num(r[3]), fee: num(r[4]), format: str(r[6]), language: str(r[7]),
      theater: str(r[8]), screen: r[9] === '' ? '' : num(r[9]), showtime: parseTime(disp[i][10]),
      alist: str(r[11]), notes: str(r[12]), swag: str(r[13]),
      threeD: r[14] === true || /^(true|yes|3d)$/i.test(str(r[14])) });
  });
  const other = [];
  const oth = ss.getSheetByName('Other Theatres');
  if (oth) {
    const ov = oth.getRange(2, 1, Math.max(oth.getLastRow() - 1, 1), 7).getValues();
    ov.forEach(r => {
      const title = String(r[2]).trim(), date = fmtDate(r[0], tz);
      if (!title || !date) return;
      other.push({ date: date, title: title, price: num(r[3]), format: str(r[4]), theater: str(r[5]), notes: str(r[6]) });
    });
  }
  const subs = [];
  const dash = ss.getSheetByName('Dashboard');
  if (dash) {
    const g = dash.getRange('P5:AB9').getValues();
    g.forEach(row => {
      const y = parseInt(row[0], 10);
      if (!y) return;
      for (let m = 1; m <= 12; m++) { const a = num(row[m]); if (a > 0) subs.push({ year: y, month: m, amount: a }); }
    });
  }
  return { ok: true, movies: movies, other: other, subs: subs };
}
function str(v) { return v === null || v === undefined ? '' : String(v).trim(); }
function num(v) { if (typeof v === 'number') return v; const x = parseFloat(String(v).replace(/[$,]/g, '')); return isNaN(x) ? 0 : x; }
function fmtDate(v, tz) {
  if (v instanceof Date && !isNaN(v)) return Utilities.formatDate(v, tz, 'yyyy-MM-dd');
  if (typeof v === 'string' && v.trim()) { const d = new Date(v); if (!isNaN(d)) return Utilities.formatDate(d, tz, 'yyyy-MM-dd'); }
  return '';
}
function parseTime(s) {
  const m = /^(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp][Mm])?$/.exec(String(s).trim());
  if (!m) return '';
  let h = parseInt(m[1], 10); const ap = (m[3] || '').toLowerCase();
  if (ap === 'pm' && h < 12) h += 12;
  if (ap === 'am' && h === 12) h = 0;
  return ('0' + h).slice(-2) + ':' + m[2];
}

// A-List Tracker -> Google Sheets sync.
// 1) Change SECRET below to any long random text (you will paste the same text into the app).
// 2) Deploy > New deployment > type "Web app" > Execute as: Me > Who has access: Anyone > Deploy.
// 3) Copy the Web app URL into the app (Lists > gear > Google Sheets live sync).
const SECRET = 'CHANGE-THIS-TO-A-LONG-RANDOM-TEXT';
const PLACEHOLDER = 'CHANGE-THIS-TO-A-LONG-RANDOM-TEXT'; // leave this line alone: the script refuses to run while SECRET is still this text

// ---- Dropdown lists ----
// One line per list. key = the name the app uses for it (and the field on each movie), header = its column title on the
// "Lists" tab, logCol = its column on "Movie Log". New theaters / formats / languages / swag coming from the app are added
// to the Lists tab automatically, so the dropdowns and the Dashboard pick them up.
// To add another list later: add one line here and one line in the app (data/Lists.kt).
const LISTS = [
  { key: 'language', header: 'Language', logCol: 8 },
  { key: 'theater',  header: 'Theater',  logCol: 9 },
  { key: 'format',   header: 'Format',   logCol: 7 },
  { key: 'swag',     header: 'Swag',     logCol: 14 },
];
const LIST_FIRST_ROW = 2, LIST_LAST_ROW = 30; // the dropdowns and the Dashboard are linked to rows 2-30 of the Lists tab
const MAX_BODY = 2000000;                     // largest request accepted, in characters
const MAX_ROWS = 5000;                        // most movies accepted in one sync

function doGet() { return out({ ok: true, hello: 'A-List sync is deployed' }); }

function doPost(e) {
  try {
    if (SECRET === PLACEHOLDER) return out({ ok: false, error: 'Set a SECRET in the script first' });
    const body = e && e.postData && typeof e.postData.contents === 'string' ? e.postData.contents : '';
    if (body.length > MAX_BODY) return out({ ok: false, error: 'request too large' });
    let d;
    try { d = JSON.parse(body); } catch (x) { return out({ ok: false, error: 'bad request' }); }
    if (!d || !safeEqual(String(d.token || ''), SECRET)) return out({ ok: false, error: 'bad token' });
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    if (d.action === 'pull') return out(pullData(ss));
    if (!Array.isArray(d.movies) || d.movies.length === 0) return out({ ok: false, error: 'empty' });
    const o = Array.isArray(d.other) ? d.other : [];
    if (d.movies.length > MAX_ROWS || o.length > MAX_ROWS) return out({ ok: false, error: 'too many rows' });
    const log = ss.getSheetByName('Movie Log');
    if (!log) return out({ ok: false, error: 'This sheet has no "Movie Log" tab' });

    // ---- 1) Build every row first. If anything in the request is wrong, it fails here and the sheet is not touched. ----
    const n = d.movies.length;
    const colA  = d.movies.map(m => [toDate(m.date)]);
    const colCE = d.movies.map(m => [txt(m.title), amount(m.ticket), amount(m.fee)]);
    const colGN = d.movies.map(m => [txt(m.format), txt(m.language), txt(m.theater), screenNo(m.screen), toTime(m.showtime),
      txt(m.alist), txt(m.notes), txt(m.swag)]);
    const col3D = d.movies.map(m => [m.threeD === true]);
    const colRating = d.movies.map(m => [m.rating > 0 ? m.rating : '']);
    const colConc = d.movies.map(m => [m.concessions > 0 ? m.concessions : '', txt(m.concessionsPay || '')]);
    const othA  = o.map(m => [toDate(m.date)]);
    const othCG = o.map(m => [txt(m.title), amount(m.price), txt(m.format), txt(m.theater), txt(m.notes)]);

    // ---- 2) New theaters / formats / languages / swag go onto the Lists tab before the log is written. ----
    const listsFull = syncLists(ss, d);

    // ---- 3) Movie Log: columns A, C, D, E, G..N, O "3D" (B "Day" and F "Total" keep their formulas) ----
    const last = Math.max(log.getLastRow(), n + 1, 986);
    ensureRows(log, last);
    replaceBlocks(log, 2, last - 1, [
      { col: 1, width: 1, values: colA },
      { col: 3, width: 3, values: colCE },
      { col: 7, width: 8, values: colGN },
    ]);

    // 3D tick box column (O). Header is created once, copying the look of column N.
    if (String(log.getRange(1, 15).getValue()).trim() === '') {
      log.getRange(1, 14).copyTo(log.getRange(1, 15), SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);
      log.getRange(1, 15).setValue('3D');
      log.setColumnWidth(15, 60);
    }
    const o3 = log.getRange(2, 15, last - 1, 1);
    o3.clearContent(); o3.clearDataValidations();
    o3.insertCheckboxes();
    log.getRange(2, 15, n, 1).setValues(col3D);

    // Rating (P), Concessions (Q) and Paid with (R). Each is only written if its header is ours (or the column is empty),
    // so anything you keep in those columns is never overwritten.
    if (ensureHeader(log, 16, 'Rating', 70)) {
      log.getRange(2, 16, last - 1, 1).clearContent();
      log.getRange(2, 16, n, 1).setValues(colRating);
    }
    if (ensureHeader(log, 17, 'Concessions', 95) && ensureHeader(log, 18, 'Paid with', 100)) {
      log.getRange(2, 17, last - 1, 2).clearContent();
      log.getRange(2, 17, n, 2).setValues(colConc);
    }

    // ---- Other Theatres: A, C, D, E, F, G (B "Day" keeps its formula) ----
    const oth = ss.getSheetByName('Other Theatres');
    if (oth) {
      const lastO = Math.max(oth.getLastRow(), o.length + 1, 100);
      ensureRows(oth, lastO);
      replaceBlocks(oth, 2, lastO - 1, [
        { col: 1, width: 1, values: othA },
        { col: 3, width: 5, values: othCG },
      ]);
    }

    // ---- Subscription costs grid on Dashboard (years down column P from row 5, Jan..Dec in Q..AB) ----
    // A new year (e.g. 2029) gets its own row right under the last year, but only if that row is completely empty.
    const dash = ss.getSheetByName('Dashboard');
    if (dash && Array.isArray(d.subs)) {
      let years = subsYears(dash);
      const wanted = {};
      d.subs.forEach(s => { const y = Number(s.year); if (y >= 2000 && y <= 2100) wanted[y] = true; });
      Object.keys(wanted).map(Number).sort().forEach(y => {
        if (years.indexOf(String(y)) >= 0) return;
        const row = 5 + years.length;
        if (row > 60) return;
        const target = dash.getRange(row, 16, 1, 13);
        if (!target.getValues()[0].every(v => String(v).trim() === '')) return; // something else is there: leave it alone
        if (years.length) dash.getRange(4 + years.length, 16, 1, 13).copyTo(target, SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);
        dash.getRange(row, 16).setValue(y);
        years = years.concat(String(y));
      });
      d.subs.forEach(s => {
        const i = years.indexOf(String(s.year));
        const mo = Number(s.month);
        if (i >= 0 && mo >= 1 && mo <= 12) dash.getRange(5 + i, 17 + (mo - 1)).setValue(amount(s.amount));
      });
    }
    return out({ ok: true, movies: n, listsFull: listsFull });
  } catch (err) {
    return out({ ok: false, error: String(err) });
  }
}

// Years listed down column P from row 5 (stops at the first cell that is not a year).
function subsYears(dash) {
  const out = [];
  const vals = dash.getRange(5, 16, 56, 1).getValues();
  for (let i = 0; i < vals.length; i++) {
    const y = parseInt(vals[i][0], 10);
    if (!(y >= 2000 && y <= 2100)) break;
    out.push(String(y));
  }
  return out;
}

// Adds anything new from the app (its own lists, plus whatever is on the movies) to the matching column of the "Lists" tab.
// Only empty cells in rows 2-30 are filled; nothing already there is changed or removed.
// Returns the headers of lists that had no free row left (the app shows that as a note; the sync itself still succeeds).
function syncLists(ss, d) {
  const full = [];
  const sh = ss.getSheetByName('Lists');
  if (!sh) return full;
  const heads = listHeads(sh);
  LISTS.forEach(L => {
    const col = heads.indexOf(L.header.toLowerCase()) + 1;
    if (!col) return;
    const want = [];
    if (d.lists && Array.isArray(d.lists[L.key])) d.lists[L.key].forEach(v => want.push(v));
    d.movies.forEach(m => want.push(m[L.key]));
    const vals = sh.getRange(LIST_FIRST_ROW, col, LIST_LAST_ROW - LIST_FIRST_ROW + 1, 1).getValues().map(r => String(r[0]).trim());
    const have = {};
    vals.forEach(v => { if (v) have[v.toLowerCase()] = true; });
    let noRoom = false;
    want.forEach(w => {
      const v = w === null || w === undefined ? '' : String(w).trim();
      if (!v || have[v.toLowerCase()]) return;
      const i = vals.indexOf('');
      if (i < 0) { noRoom = true; return; }
      vals[i] = v; have[v.toLowerCase()] = true;
      sh.getRange(LIST_FIRST_ROW + i, col).setValue(txt(v));
    });
    if (noRoom) full.push(L.header);
  });
  return full;
}
function listHeads(sh) {
  return sh.getRange(1, 1, 1, Math.max(sh.getLastColumn(), 1)).getValues()[0].map(h => String(h).trim().toLowerCase());
}

// Replaces several column blocks of one tab as a single step: if any write fails, everything is put back as it was.
// blocks = [{ col, width, values }]; rows firstRow .. firstRow+clearRows-1 are cleared, then values are written from firstRow.
function replaceBlocks(sh, firstRow, clearRows, blocks) {
  const areas = blocks.map(b => sh.getRange(firstRow, b.col, clearRows, b.width));
  const before = areas.map(a => a.getValues());
  try {
    areas.forEach(a => a.clearContent());
    blocks.forEach(b => { if (b.values.length) put(sh, firstRow, b.col, b.values); });
  } catch (err) {
    areas.forEach((a, i) => {
      try { a.setValues(before[i].map(r => r.map(v => typeof v === 'string' ? txt(v) : v))); } catch (e2) { /* keep restoring the rest */ }
    });
    throw err;
  }
}
// Writes a block. If a dropdown set to "reject input" refuses a value that is not in its list, those dropdowns are
// switched to "show a warning" and the block is written again, so a new value can never stop a sync.
function put(sh, row, col, values) {
  const r = sh.getRange(row, col, values.length, values[0].length);
  try { r.setValues(values); }
  catch (err) {
    if (!relaxDropdowns(sh.getRange(row, col, sh.getMaxRows() - row + 1, values[0].length))) throw err;
    r.setValues(values);
  }
}
function relaxDropdowns(range) {
  let changed = false;
  const rules = range.getDataValidations().map(rw => rw.map(rule => {
    if (rule && !rule.getAllowInvalid()) { changed = true; return rule.copy().setAllowInvalid(true).build(); }
    return rule;
  }));
  if (changed) range.setDataValidations(rules);
  return changed;
}
function ensureRows(sh, rows) {
  const have = sh.getMaxRows();
  if (have < rows) sh.insertRowsAfter(have, rows - have);
}

// True if column [col] has our header (creating it first when the column is completely empty). False = leave the column alone.
function ensureHeader(log, col, name, width) {
  const cell = log.getRange(1, col);
  const cur = String(cell.getValue()).trim();
  if (cur === name) return true;
  if (cur !== '') return false;
  const rows = Math.max(log.getLastRow() - 1, 1);
  if (log.getRange(2, col, rows, 1).getValues().some(r => String(r[0]).trim() !== '')) return false;
  log.getRange(1, 14).copyTo(cell, SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);
  cell.setValue(name);
  log.setColumnWidth(col, width);
  return true;
}
// Compares two strings without stopping at the first difference.
function safeEqual(a, b) {
  let diff = a.length ^ b.length;
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}
// Text for a cell. Anything that Sheets would run as a formula (starts with = + - @) is stored as plain text instead.
function txt(v) {
  const s = v === null || v === undefined ? '' : String(v);
  return /^[=+\-@]/.test(s) ? "'" + s : s;
}
function amount(v) { const x = typeof v === 'number' ? v : parseFloat(v); return isFinite(x) ? x : 0; }
function screenNo(v) { if (v === '' || v === null || v === undefined) return ''; const x = Number(v); return isFinite(x) ? x : ''; }
function toDate(s) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(s))) throw new Error('bad date: ' + String(s).slice(0, 20));
  return Utilities.parseDate(String(s), SpreadsheetApp.getActiveSpreadsheet().getSpreadsheetTimeZone(), 'yyyy-MM-dd');
}
function toTime(s) {
  if (!s) return '';
  const p = String(s).split(':'); const t = ((+p[0]) * 60 + (+p[1])) / 1440;
  return isFinite(t) && t >= 0 && t < 1 ? t : '';
}
function out(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

// ---- Sheet -> app: read what is in the tabs so the app can merge it (read-only, changes nothing) ----
function pullData(ss) {
  const tz = ss.getSpreadsheetTimeZone();
  const log = ss.getSheetByName('Movie Log');
  const n = Math.max(log.getLastRow() - 1, 1);
  const rng = log.getRange(2, 1, n, 18);
  const vals = rng.getValues(), disp = rng.getDisplayValues();
  const movies = [];
  vals.forEach((r, i) => {
    const title = String(r[2]).trim(), date = fmtDate(r[0], tz);
    if (!title || !date) return;
    movies.push({ date: date, title: title, ticket: num(r[3]), fee: num(r[4]), format: str(r[6]), language: str(r[7]),
      theater: str(r[8]), screen: r[9] === '' ? '' : num(r[9]), showtime: parseTime(disp[i][10]),
      alist: str(r[11]), notes: str(r[12]), swag: str(r[13]),
      threeD: r[14] === true || /^(true|yes|3d)$/i.test(str(r[14])),
      rating: Math.max(0, Math.min(5, Math.round(num(r[15])))),
      concessions: num(r[16]), concessionsPay: str(r[17]) });
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
    const n = subsYears(dash).length;
    const g = n ? dash.getRange(5, 16, n, 13).getValues() : [];
    g.forEach(row => {
      const y = parseInt(row[0], 10);
      if (!y) return;
      for (let m = 1; m <= 12; m++) { const a = num(row[m]); if (a > 0) subs.push({ year: y, month: m, amount: a }); }
    });
  }
  // What is on the Lists tab, so anything you add there shows up in the app's lists too.
  const lists = {};
  const ls = ss.getSheetByName('Lists');
  if (ls) {
    const heads = listHeads(ls);
    LISTS.forEach(L => {
      const col = heads.indexOf(L.header.toLowerCase()) + 1;
      if (!col) return;
      lists[L.key] = ls.getRange(LIST_FIRST_ROW, col, LIST_LAST_ROW - LIST_FIRST_ROW + 1, 1).getValues()
        .map(r => String(r[0]).trim()).filter(v => v !== '');
    });
  }
  // hasRating tells the app these columns exist and are ours, so their values (including blanks) can be trusted.
  const hasRating = String(log.getRange(1, 16).getValue()).trim() === 'Rating' && String(log.getRange(1, 17).getValue()).trim() === 'Concessions' &&
    String(log.getRange(1, 18).getValue()).trim() === 'Paid with';
  return { ok: true, hasRating: hasRating, movies: movies, other: other, subs: subs, lists: lists };
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

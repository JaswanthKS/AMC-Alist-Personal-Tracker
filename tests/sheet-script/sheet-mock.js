// Small stand-in for Google Sheets so the sync script can be run and checked on a normal computer.
// It copies the behaviours that matter here: typed-in parsing ("=..." becomes a formula, a leading ' forces text),
// dropdown rules that either warn or reject, row limits, and errors on out-of-range writes.
const fs = require('fs'), vm = require('vm');

function colNum(s) { let n = 0; for (const ch of s) n = n * 26 + (ch.charCodeAt(0) - 64); return n; }

class Rule {
  constructor(listRange, allowInvalid) { this.listRange = listRange; this.allowInvalid = allowInvalid; }
  getAllowInvalid() { return this.allowInvalid; }
  copy() { const r = new Rule(this.listRange, this.allowInvalid); r.setAllowInvalid = v => { r.allowInvalid = v; return r; }; r.build = () => new Rule(r.listRange, r.allowInvalid); return r; }
  allowed() { return this.listRange.getValues().map(r => String(r[0]).trim()).filter(v => v); }
}

class Sheet {
  constructor(name, rows, cols) {
    this.name = name; this.maxRows = rows; this.maxCols = cols;
    this.cells = {}; this.rules = {}; this.formulas = []; this.writes = 0;
  }
  key(r, c) { return r + ':' + c; }
  get(r, c) { const v = this.cells[this.key(r, c)]; return v === undefined ? '' : v; }
  getMaxRows() { return this.maxRows; }
  insertRowsAfter(after, howMany) { this.maxRows += howMany; }
  getLastRow() { let m = 0; for (const k in this.cells) { if (this.cells[k] !== '') m = Math.max(m, +k.split(':')[0]); } return m; }
  getLastColumn() { let m = 0; for (const k in this.cells) { if (this.cells[k] !== '') m = Math.max(m, +k.split(':')[1]); } return m; }
  setColumnWidth() {}
  getRange(a, b, c, d) {
    if (typeof a === 'string') {
      const m = /^([A-Z]+)(\d+)(?::([A-Z]+)(\d+))?$/.exec(a);
      const r1 = +m[2], c1 = colNum(m[1]), r2 = m[4] ? +m[4] : r1, c2 = m[3] ? colNum(m[3]) : c1;
      return new Range(this, r1, c1, r2 - r1 + 1, c2 - c1 + 1);
    }
    return new Range(this, a, b, c === undefined ? 1 : c, d === undefined ? 1 : d);
  }
}

class Range {
  constructor(sh, r, c, nr, nc) {
    if (r < 1 || c < 1 || nr < 1 || nc < 1) throw new Error('The starting row/number of rows of the range is too small.');
    if (r + nr - 1 > sh.maxRows) throw new Error('The coordinates of the range are outside the dimensions of the sheet.');
    this.sh = sh; this.r = r; this.c = c; this.nr = nr; this.nc = nc;
  }
  each(fn) { for (let i = 0; i < this.nr; i++) for (let j = 0; j < this.nc; j++) fn(this.r + i, this.c + j, i, j); }
  getValues() { const o = []; for (let i = 0; i < this.nr; i++) { const row = []; for (let j = 0; j < this.nc; j++) row.push(this.sh.get(this.r + i, this.c + j)); o.push(row); } return o; }
  getDisplayValues() {
    return this.getValues().map(row => row.map(v => {
      if (typeof v === 'number' && v > 0 && v < 1) { const mins = Math.round(v * 1440); let h = Math.floor(mins / 60); const m = mins % 60; const ap = h >= 12 ? 'PM' : 'AM'; h = h % 12 || 12; return h + ':' + ('0' + m).slice(-2) + ' ' + ap; }
      return String(v);
    }));
  }
  getValue() { return this.sh.get(this.r, this.c); }
  setValue(v) { return this.setValues([[v]]); }
  setValues(vals) {
    if (vals.length !== this.nr || vals.some(r => r.length !== this.nc)) throw new Error('The number of rows/columns in the data does not match the range.');
    // parse like something typed in
    const parsed = vals.map(row => row.map(v => {
      if (typeof v !== 'string') return { v };
      if (v.startsWith("'")) return { v: v.slice(1) };
      if (v.length > 1 && (v.startsWith('=') || /^\+[A-Za-z]/.test(v))) return { v, formula: true };
      return { v };
    }));
    // dropdowns that reject input refuse the whole write
    this.each((r, c, i, j) => {
      const rule = this.sh.rules[this.sh.key(r, c)]; const p = parsed[i][j];
      if (rule && !rule.allowInvalid && p.v !== '' && !rule.allowed().includes(String(p.v).trim()))
        throw new Error('The data you entered in cell ' + r + ',' + c + ' violates the data validation rules set on this cell.');
    });
    if (this.sh.failWrites > 0) { this.sh.failWrites--; throw new Error('Service error: Spreadsheets'); }
    this.each((r, c, i, j) => { const p = parsed[i][j]; this.sh.cells[this.sh.key(r, c)] = p.v; if (p.formula) this.sh.formulas.push(p.v); });
    this.sh.writes++;
    return this;
  }
  clearContent() { this.each((r, c) => { delete this.sh.cells[this.sh.key(r, c)]; }); return this; }
  clearDataValidations() { this.each((r, c) => { delete this.sh.rules[this.sh.key(r, c)]; }); return this; }
  insertCheckboxes() { return this; }
  copyTo() {}
  getDataValidations() { const o = []; for (let i = 0; i < this.nr; i++) { const row = []; for (let j = 0; j < this.nc; j++) row.push(this.sh.rules[this.sh.key(this.r + i, this.c + j)] || null); o.push(row); } return o; }
  setDataValidations(rules) { this.each((r, c, i, j) => { if (rules[i][j]) this.sh.rules[this.sh.key(r, c)] = rules[i][j]; else delete this.sh.rules[this.sh.key(r, c)]; }); }
  setDataValidation(rule) { this.each((r, c) => { this.sh.rules[this.sh.key(r, c)] = rule; }); }
}

function ymd(s) { const p = s.split('-').map(Number); return new Date(Date.UTC(p[0], p[1] - 1, p[2], 12)); }

/** A sheet laid out like the real tracker: Dashboard, Movie Log (with dropdowns), Lists, Other Theatres. */
function makeBook(opt) {
  opt = opt || {};
  const book = { sheets: {} };
  const add = (name, rows) => (book.sheets[name] = new Sheet(name, rows || 1000, 40));
  const log = add('Movie Log', opt.logRows), lists = add('Lists'), dash = add('Dashboard'), oth = add('Other Theatres');
  ['Date', 'Day', 'Movie Title', 'Ticket Value', 'Conv. Fee', 'Total Value', 'Format', 'Language', 'Theater', 'Screen', 'Showtime', 'A-List', 'Notes', 'Swag / Freebies']
    .forEach((h, i) => { log.cells[log.key(1, i + 1)] = h; });
  const L = {
    1: ['Language', 'English', 'Telugu', 'Tamil', 'Hindi'],
    2: ['Theater', 'AMC Empire 25', 'AMC Lincoln Square 13', 'AMC Newport 11', 'AMC 34th Street 14'],
    3: ['Format', 'IMAX', 'IMAX 70mm', '70mm', 'Dolby Cinema', 'Prime', 'Standard', 'XL', 'Vistavision'],
    4: ['A-List', 'Yes', 'No', 'Gift Card', 'Voucher'],
    5: ['Swag', 'Poster', 'Pin', 'Collectible Ticket', 'Popcorn Bucket', 'Key Chain', 'Other'],
  };
  for (const c in L) L[c].forEach((v, i) => { lists.cells[lists.key(i + 1, +c)] = v; });
  if (opt.noListsTab) delete book.sheets['Lists'];
  else {
    const strict = !!opt.strict;
    [[7, 3], [8, 1], [9, 2], [12, 4], [14, 5]].forEach(([logCol, listCol]) => {
      const rule = new Rule(lists.getRange(2, listCol, 29, 1), !strict);
      log.getRange(2, logCol, Math.min(985, log.maxRows - 1), 1).setDataValidation(rule);
    });
  }
  [2024, 2025, 2026, 2027, 2028].forEach((y, i) => { dash.cells[dash.key(5 + i, 16)] = y; });
  ['Date', 'Day', 'Movie Name', 'Ticket Price', 'Format', 'Location', 'Notes'].forEach((h, i) => { oth.cells[oth.key(1, i + 1)] = h; });
  return book;
}

/** Loads the real script file against a mock book. Returns { post(obj), book }. */
function load(scriptPath, book, secret) {
  let src = fs.readFileSync(scriptPath, 'utf8');
  src = src.replace("const SECRET = 'CHANGE-THIS-TO-A-LONG-RANDOM-TEXT';", "const SECRET = '" + secret + "';");
  const ss = {
    getSheetByName: n => book.sheets[n] || null,
    getSpreadsheetTimeZone: () => 'America/New_York',
  };
  const sandbox = {
    SpreadsheetApp: { getActiveSpreadsheet: () => ss, CopyPasteType: { PASTE_FORMAT: 1 } },
    Utilities: {
      parseDate: (s) => { const d = ymd(s); if (isNaN(d)) throw new Error('Unparseable date'); return new (vm.runInContext('Date', sandbox))(d.getTime()); },
      formatDate: (d) => d.toISOString().slice(0, 10),
    },
    ContentService: { MimeType: { JSON: 'json' }, createTextOutput: t => ({ text: t, setMimeType() { return this; } }) },
  };
  vm.createContext(sandbox);
  vm.runInContext(src + '\nthis.doPost = doPost; this.doGet = doGet; this.LISTS = typeof LISTS === "undefined" ? [] : LISTS;', sandbox);
  return {
    book,
    post: (obj, raw) => JSON.parse(sandbox.doPost({ postData: { contents: raw !== undefined ? raw : JSON.stringify(obj) } }).text),
    LISTS: sandbox.LISTS,
  };
}

module.exports = { makeBook, load, Rule };

const { makeBook, load } = require('./sheet-mock');
const SCRIPT = process.argv[2];
let pass = 0, fail = 0;
function eq(name, got, want) { const g = JSON.stringify(got), w = JSON.stringify(want); if (g === w) pass++; else { fail++; console.log('FAIL ' + name + '\n   got : ' + g + '\n   want: ' + w); } }
const T = 'secret-for-tests';
const mv = (o) => Object.assign({ date: '2026-11-10', title: 'Movie', ticket: 20.49, fee: 2.69, format: 'Standard', threeD: false, language: 'English',
  theater: 'AMC Empire 25', screen: 5, showtime: '19:00', alist: 'Yes', notes: '', swag: '', rating: 0, concessions: 0, concessionsPay: '' }, o);
const base = [mv({ title: 'One', date: '2026-01-05' }), mv({ title: 'Two', date: '2026-02-06', format: 'IMAX', theater: 'AMC Lincoln Square 13', language: 'Telugu', swag: 'Poster', rating: 4 })];
const listCol = (book, c) => book.sheets['Lists'].getRange(2, c, 29, 1).getValues().map(r => r[0]).filter(v => v !== '');
const logRow = (book, r) => book.sheets['Movie Log'].getRange(r, 1, 1, 18).getValues()[0];
const fresh = (opt) => { const s = load(SCRIPT, makeBook(opt), T); eq('seed push ok', s.post({ token: T, movies: base }).ok, true); return s; };

// 1. The app that is on Play today (sends no lists) still works exactly as before
{
  const s = fresh();
  eq('old app: rows written', [logRow(s.book, 2)[2], logRow(s.book, 3)[2], logRow(s.book, 3)[6], logRow(s.book, 3)[8]], ['One', 'Two', 'IMAX', 'AMC Lincoln Square 13']);
  eq('old app: lists untouched', [listCol(s.book, 1).length, listCol(s.book, 2).length, listCol(s.book, 3).length, listCol(s.book, 5).length], [4, 4, 8, 6]);
  const p = s.post({ token: T, action: 'pull' });
  eq('old app: pull round trip', p.movies.map(m => [m.date, m.title, m.format, m.language, m.theater, m.showtime, m.rating]), [['2026-01-05', 'One', 'Standard', 'English', 'AMC Empire 25', '19:00', 0], ['2026-02-06', 'Two', 'IMAX', 'Telugu', 'AMC Lincoln Square 13', '19:00', 4]]);
  eq('pull returns lists', [p.lists.theater.length, p.lists.format.length, p.lists.language.length, p.lists.swag.length], [4, 8, 4, 6]);
}
// 2. New theater / format / language / swag used on a movie -> written and added to the Lists tab
for (const strict of [false, true]) {
  const s = fresh({ strict });
  const r = s.post({ token: T, movies: base.concat([mv({ title: 'New', date: '2026-11-14', theater: 'AMC Garden State 16', format: 'ScreenX', language: 'Korean', swag: 'Tote Bag' })]),
    lists: { theater: ['AMC Empire 25', 'AMC Kips Bay 15'], format: ['IMAX'], language: ['English'], swag: [] } });
  const tag = strict ? ' (reject-input dropdowns)' : ' (warning dropdowns)';
  eq('new values: ok' + tag, [r.ok, r.movies, r.listsFull], [true, 3, []]);
  eq('new values: row' + tag, [logRow(s.book, 4)[2], logRow(s.book, 4)[6], logRow(s.book, 4)[7], logRow(s.book, 4)[8], logRow(s.book, 4)[13]], ['New', 'ScreenX', 'Korean', 'AMC Garden State 16', 'Tote Bag']);
  eq('new values: theater list' + tag, listCol(s.book, 2), ['AMC Empire 25', 'AMC Lincoln Square 13', 'AMC Newport 11', 'AMC 34th Street 14', 'AMC Kips Bay 15', 'AMC Garden State 16']);
  eq('new values: format list' + tag, listCol(s.book, 3).slice(-1), ['ScreenX']);
  eq('new values: language list' + tag, listCol(s.book, 1).slice(-1), ['Korean']);
  eq('new values: swag list' + tag, listCol(s.book, 5).slice(-1), ['Tote Bag']);
  eq('A-List list never touched' + tag, listCol(s.book, 4), ['Yes', 'No', 'Gift Card', 'Voucher']);
  // syncing again adds nothing twice
  s.post({ token: T, movies: base.concat([mv({ title: 'New', date: '2026-11-14', theater: 'amc garden state 16', format: 'SCREENX', language: 'Korean', swag: 'Tote Bag' })]) });
  eq('no duplicates on re-sync (case ignored)' + tag, [listCol(s.book, 2).length, listCol(s.book, 3).length], [6, 9]);
  eq('pull shows new list values' + tag, s.post({ token: T, action: 'pull' }).lists.theater.slice(-2), ['AMC Kips Bay 15', 'AMC Garden State 16']);
}
// 3. List is full (29 names): sync still succeeds, the app is told which list is full
{
  const s = fresh({ strict: true });
  const many = []; for (let i = 0; i < 40; i++) many.push('Theater ' + i);
  const r = s.post({ token: T, movies: base.concat([mv({ title: 'Far', theater: 'Theater 39' })]), lists: { theater: many } });
  eq('full list: still ok', [r.ok, r.listsFull], [true, ['Theater']]);
  eq('full list: 29 rows used', listCol(s.book, 2).length, 29);
  eq('full list: movie still written', logRow(s.book, 4)[8], 'Theater 39');
  eq('full list: other rows intact', [logRow(s.book, 2)[2], logRow(s.book, 3)[2]], ['One', 'Two']);
}
// 4. Names that look like formulas are stored as text, and come back unchanged
{
  const s = fresh({ strict: true });
  const r = s.post({ token: T, movies: base.concat([mv({ title: '=HYPERLINK("http://x","y")', theater: '+AMC Plus', format: '@Home', notes: '-5 min late', swag: '=1+1' })]), lists: { theater: ['=IMPORTXML("u","x")'] } });
  eq('formula guard: ok', r.ok, true);
  eq('formula guard: nothing became a formula', [s.book.sheets['Movie Log'].formulas, s.book.sheets['Lists'].formulas], [[], []]);
  const p = s.post({ token: T, action: 'pull' }).movies[2];
  eq('formula guard: round trip', [p.title, p.theater, p.format, p.notes, p.swag], ['=HYPERLINK("http://x","y")', '+AMC Plus', '@Home', '-5 min late', '=1+1']);
}
// 5. Bad requests never touch the sheet
{
  const s = fresh();
  const before = JSON.stringify(s.book.sheets['Movie Log'].cells) + JSON.stringify(s.book.sheets['Lists'].cells);
  const same = () => JSON.stringify(s.book.sheets['Movie Log'].cells) + JSON.stringify(s.book.sheets['Lists'].cells) === before;
  eq('wrong secret', [s.post({ token: 'nope', movies: base }).error, same()], ['bad token', true]);
  eq('no movies', [s.post({ token: T, movies: [] }).error, same()], ['empty', true]);
  eq('movies not a list', [s.post({ token: T, movies: 'x' }).error, same()], ['empty', true]);
  eq('not json', [s.post(null, '{oops').error, same()], ['bad request', true]);
  eq('bad date in row 2', [s.post({ token: T, movies: [mv({ theater: 'Brand New Place' }), mv({ date: '11/14/2026' })] }).ok, same()], [false, true]);
  eq('too big', [s.post(null, JSON.stringify({ token: T, movies: base, pad: 'x'.repeat(2100000) })).error, same()], ['request too large', true]);
  const lots = []; for (let i = 0; i < 5001; i++) lots.push(mv({ title: 'M' + i }));
  eq('too many rows', [s.post({ token: T, movies: lots }).error, same()], ['too many rows', true]);
  eq('odd values are tolerated', s.post({ token: T, movies: [mv({ ticket: 'abc', fee: null, screen: 'x', showtime: 'late', theater: null, swag: undefined, format: 42 })] }).ok, true);
  eq('odd values stored safely', logRow(s.book, 2).slice(3, 5).concat(logRow(s.book, 2).slice(6, 11)), [0, 0, '42', 'English', '', '', '']);
}
// 6. A write that fails half-way puts the old rows back
{
  const s = fresh();
  const snap = JSON.stringify(s.book.sheets['Movie Log'].getRange(2, 1, 20, 14).getValues());
  s.book.sheets['Movie Log'].failWrites = 1;   // the first write to the log fails (after the old rows were cleared)
  const r = s.post({ token: T, movies: [mv({ title: 'Replacement' })] });
  s.book.sheets['Movie Log'].failWrites = 0;
  eq('failed write reported', r.ok, false);
  eq('failed write: old rows restored', JSON.stringify(s.book.sheets['Movie Log'].getRange(2, 1, 20, 14).getValues()) === snap, true);
}
// 7. Sheet shapes: no Lists tab, short sheet, other theatres, subscriptions
{
  const s = fresh({ noListsTab: true });
  eq('no Lists tab: ok', s.post({ token: T, movies: base.concat([mv({ theater: 'Somewhere New' })]) }).ok, true);
  eq('no Lists tab: pull ok', s.post({ token: T, action: 'pull' }).lists, {});
  const s2 = load(SCRIPT, makeBook({ logRows: 50 }), T);
  eq('short sheet grows', [s2.post({ token: T, movies: base }).ok, s2.book.sheets['Movie Log'].getMaxRows() >= 986], [true, true]);
  const s3 = fresh();
  const r = s3.post({ token: T, movies: base, other: [{ date: '2026-03-03', title: 'Elsewhere', price: 12.5, format: 'IMAX 3D', theater: 'Regal Union Square', notes: '' }],
    subs: [{ year: 2026, month: 11, amount: 27.99 }, { year: 2026, month: 13, amount: 1 }, { year: 1999, month: 1, amount: 1 }] });
  eq('other + subs ok', r.ok, true);
  const p = s3.post({ token: T, action: 'pull' });
  eq('other theatres round trip', p.other, [{ date: '2026-03-03', title: 'Elsewhere', price: 12.5, format: 'IMAX 3D', theater: 'Regal Union Square', notes: '' }]);
  eq('subs round trip', p.subs, [{ year: 2026, month: 11, amount: 27.99 }]);
  eq('non-AMC theater not pushed into Lists', listCol(s3.book, 2).includes('Regal Union Square'), false);
  // fewer movies than before: old rows are cleared, not left behind
  s3.post({ token: T, movies: [base[0]] });
  eq('shrinking log clears old rows', s3.post({ token: T, action: 'pull' }).movies.length, 1);
}
// 8. A list added to LISTS in future is handled by the same code
{
  const book = makeBook({ strict: true });
  book.sheets['Lists'].cells[book.sheets['Lists'].key(1, 7)] = 'Seat';
  const fs = require('fs'), os = require('os'), path = require('path');
  const tmp = path.join(os.tmpdir(), 'future.gs');
  fs.writeFileSync(tmp, fs.readFileSync(SCRIPT, 'utf8').replace("{ key: 'swag',     header: 'Swag',     logCol: 14 },", "{ key: 'swag',     header: 'Swag',     logCol: 14 },\n  { key: 'seat', header: 'Seat', logCol: 19 },"));
  const s = load(tmp, book, T);
  eq('future list present', s.LISTS.length, 5);
  eq('future list ok', s.post({ token: T, movies: base, lists: { seat: ['H12', 'J7'] } }).ok, true);
  eq('future list filled', listCol(book, 7), ['H12', 'J7']);
  eq('future list pulled', s.post({ token: T, action: 'pull' }).lists.seat, ['H12', 'J7']);
}
// 8. Payments grid: new years get their own row under the last one
{
  const s = fresh();
  const r = s.post({ token: T, movies: base, subs: [{ year: 2028, month: 12, amount: 29.99 }, { year: 2029, month: 1, amount: 31.99 }, { year: 2030, month: 6, amount: 33.5 }] });
  eq('new years: ok', r.ok, true);
  const p = s.post({ token: T, action: 'pull' });
  eq('new years: round trip', p.subs, [{ year: 2028, month: 12, amount: 29.99 }, { year: 2029, month: 1, amount: 31.99 }, { year: 2030, month: 6, amount: 33.5 }]);
  eq('new years: rows 10 and 11', [s.book.sheets['Dashboard'].getRange(10, 16, 1, 1).getValues()[0][0], s.book.sheets['Dashboard'].getRange(11, 16, 1, 1).getValues()[0][0]], [2029, 2030]);
  const again = s.post({ token: T, movies: base, subs: [{ year: 2029, month: 1, amount: 31.99 }] });
  eq('new years: second push does not add a second row', [again.ok, s.book.sheets['Dashboard'].getRange(12, 16, 1, 1).getValues()[0][0]], [true, '']);
  // something else sits right under the grid: never overwritten, other payments still sync
  const s2 = fresh();
  s2.book.sheets['Dashboard'].cells[s2.book.sheets['Dashboard'].key(10, 16)] = 'Notes';
  const r2 = s2.post({ token: T, movies: base, subs: [{ year: 2028, month: 1, amount: 29.99 }, { year: 2029, month: 1, amount: 31.99 }] });
  eq('blocked row: ok and untouched', [r2.ok, s2.book.sheets['Dashboard'].getRange(10, 16, 1, 1).getValues()[0][0]], [true, 'Notes']);
  eq('blocked row: other years still sync', s2.post({ token: T, action: 'pull' }).subs, [{ year: 2028, month: 1, amount: 29.99 }]);
}
console.log('Sheet script tests: ' + pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);

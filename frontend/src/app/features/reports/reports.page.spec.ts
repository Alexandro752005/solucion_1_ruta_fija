import { describe, expect, it } from 'vitest';

import { escapeCsvCell } from './reports.page';

describe('escapeCsvCell', () => {
  it('neutraliza formulas antes de crear un CSV', () => {
    expect(escapeCsvCell('=HYPERLINK("https://malicious.example")')).toBe(
      '"\'=HYPERLINK(""https://malicious.example"")"',
    );
    expect(escapeCsvCell(' +SUM(A1:A2)')).toBe('"\' +SUM(A1:A2)"');
  });

  it('conserva los valores ordinarios y escapa comillas', () => {
    expect(escapeCsvCell('Terminal "Norte"')).toBe('"Terminal ""Norte"""');
  });
});

/*
 * accent-fold.js — self-contained helper shared by the participant-tag filters
 * on events/detail.html and rehearsals/detail.html.  Lowercase a string and strip
 * Latin diacritics (with an explicit Polish fallback for letters NFD does NOT
 * decompose: ł, ś, ż, ć, ń) so that "Trąbka" and "trabka" compare equal.
 */
(function (root) {
    'use strict';
    if (root.windbandAccentFold) return;   // already defined — leave it alone

    var POLISH_FALLBACK = {
        'ł': 'l', 'Ł': 'L', 'ś': 's', 'Ś': 'S', 'ż': 'z', 'Ż': 'Z',
        'ć': 'c', 'Ć': 'C', 'ń': 'n', 'Ń': 'N'
    };

    root.windbandAccentFold = function (text) {
        if (text == null) return '';
        var out = String(text).toLowerCase();
        // First pass: strip combining marks wherever NFD is well-defined.
        out = out.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
        // Second pass: Polish-specific letters that survive NFD (they are a
        // single code point, not base+combining accent).
        return out.replace(/[łśżćńŁŚŻĆŃ]/g, function (ch) {
            var m = POLISH_FALLBACK[ch];
            return typeof m === 'string' ? m : ch;
        });
    };
})(window);

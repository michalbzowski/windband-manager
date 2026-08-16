# Analysis for Issue #112: Team name not visible in header on light theme

## Problem
When using the light theme, the team name in the dashboard header is not visible. The font has the same color as in the dark theme (light color), making it invisible against the light background.

## Root Cause
The `.team-name-display` CSS class (in `/src/main/resources/static/css/app.css`, lines 1542-1546) uses:
```css
.team-name-display {
    color: var(--pico-color) !important;
    -webkit-text-fill-color: var(--pico-color);
    text-shadow: none;
}
```

The issue is with `-webkit-text-fill-color`. This WebKit-specific property overrides the standard `color` property. When CSS variables change via media queries (`@media (prefers-color-scheme: dark)`), the `-webkit-text-fill-color` value may not be re-evaluated properly in all browsers, causing it to retain the dark theme value (light color `#c2c7d0`) even in light theme.

Additionally, `var(--pico-color)` resolves to:
- Light theme: `#373c44` (dark)
- Dark theme: `#c2c7d0` (light)

But `-webkit-text-fill-color` with a CSS variable doesn't reliably update when the media query changes.

## Evidence
The `.nav-team-label` class (same file, lines 392-438) correctly handles this by using **hardcoded explicit colors** with explicit dark/light overrides:
```css
.nav-team-label {
    color: #1a1a1a;  /* explicit dark color for light theme */
}
[data-theme="dark"] .nav-team-label {
    color: #e6e6e6;  /* explicit light color for dark theme */
}
@media (prefers-color-scheme: dark) {
    .nav-team-label {
        color: #e6e6e6;
    }
}
```

This pattern works reliably because the browser re-evaluates the color property when media queries match.

## Solution
Update `.team-name-display` to follow the same pattern as `.nav-team-label`:
1. Remove `-webkit-text-fill-color` (it's not needed for this use case)
2. Use explicit hardcoded colors for light theme
3. Add `[data-theme="dark"]` and `@media (prefers-color-scheme: dark)` overrides with explicit light colors

## Implementation Plan
1. Modify `/src/main/resources/static/css/app.css` - Update `.team-name-display` class (lines 1542-1546)
2. Use color values consistent with PicoCSS:
   - Light theme: `#1a1a1a` (same as `.nav-team-label`)
   - Dark theme: `#e6e6e6` (same as `.nav-team-label`)
3. Test both themes locally
4. Run existing tests
5. Commit and push

## Files to Modify
- `/home/mbzowski/windband-manager/src/main/resources/static/css/app.css` - Lines 1542-1546
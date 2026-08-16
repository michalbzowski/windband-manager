# Analysis: Issue #114 - "Aktywni członkowie" Button Doesn't Work

## Root Cause

The JavaScript toggle functionality for switching between active and inactive/resigned members is defined **outside** the `#members-content` fragment that gets swapped via HTMX.

### Technical Details

1. **Template Structure** (`src/main/resources/templates/members/list.html`):
   - The `#members-content` div (line 11) is the HTMX swap target
   - The toggle button with `onclick="toggleMemberView()"` is **inside** `#members-content`
   - The JavaScript IIFE defining `toggleMemberView()` and `isShowingInactive` state variable is at lines 213-248, **OUTSIDE** `#members-content`

2. **Controller** (`MemberPageController.java`):
   - `/members` endpoint returns full page `"members/list"`
   - `/members/list` endpoint returns fragment `"members/list :: #members-content"`

3. **HTMX Flow**:
   - Initial page load: Full page renders, script executes, `isShowingInactive` initialized from `data-show-inactive` attribute
   - User clicks "Pokaż nieaktywnych" → `toggleMemberView()` called → HTMX fetches `/members/list?showInactive=true`
   - Server returns `#members-content` fragment with updated `data-show-inactive="true"`
   - HTMX swaps `#members-content` innerHTML
   - **PROBLEM**: The script at bottom of page does NOT re-execute (it's outside swap target)
   - `isShowingInactive` closure variable retains old value (`false`)
   - User clicks "Aktywni członkowie" → `toggleMemberView()` uses stale `isShowingInactive=false`
   - Calculates `newShowState = !false = true` → fetches `/members/list?showInactive=true` again (wrong!)
   - User sees no change (still showing inactive members)

## Implementation Plan

### Fix: Move JavaScript Inside Fragment

**File**: `src/main/resources/templates/members/list.html`

**Steps**:
1. Move the `<script>` block (lines 213-248) from after `</main>` (line 206) to **inside** the `#members-content` div, at the end (before line 205 `</div>`)
2. This ensures the script re-executes on every HTMX fragment swap
3. The IIFE will re-initialize `isShowingInactive` from the fresh `data-show-inactive` attribute

### Verification Steps

1. Start application
2. Navigate to `/members`
3. Click "Pokaż nieaktywnych" → Verify inactive members load
4. Click "Aktywni członkowie" → Verify active members load (this was broken)
5. Toggle back and forth multiple times → Verify consistent behavior
6. Test keyboard shortcut (Ctrl+T) → Verify it works after swaps

### Alternative Considered (Not Recommended)

Using HTMX's `hx-on::after-swap` to re-initialize - more complex, less reliable than moving script inside fragment.

## Files to Modify

1. `src/main/resources/templates/members/list.html` - Move script block inside `#members-content`

## Testing Checklist

- [ ] Active → Inactive toggle works
- [ ] Inactive → Active toggle works (the bug)
- [ ] Multiple consecutive toggles work
- [ ] Keyboard shortcut (Ctrl+T) works after HTMX swaps
- [ ] Button text updates correctly ("Pokaż nieaktywnych" / "Aktywni członkowie")
- [ ] Inactive count badge updates correctly
- [ ] Focus member ID preserved across toggles
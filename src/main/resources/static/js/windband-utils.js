/**
 * Wind Band Manager — global browser utilities.
 *
 * Loaded on every page via fragments/layout.html. Provides:
 * - Toast: global notification system (success/error/info)
 * - fetchWithToast: fetch() wrapper that shows a toast on response
 *
 * Extracted from fragments/layout :: footer-scripts so the functions are
 * available in form fragments loaded by htmx (where the footer-scripts
 * fragment is NOT included). Without this, form submit handlers in
 * members/events/instruments/rehearsals forms would fail with
 * "ReferenceError: fetchWithToast is not defined" (silently swallowed by
 * window.onerror in layout.html).
 */
(function (global) {
    'use strict';

    var Toast = {
        container: null,

        init: function () {
            this.container = document.getElementById('toast-container');
        },

        show: function (message, type, duration, action) {
            if (!this.container) this.init();
            if (!this.container) return; // no toast container on page

            var toast = document.createElement('div');
            toast.className = 'toast ' + (type || 'info');

            // Icon based on type
            var icon = '';
            if (type === 'success') icon = '✓ ';
            else if (type === 'error') icon = '✕ ';
            else icon = 'ℹ ';

            var content = icon + message;
            if (action) {
                content += ' <a href="' + action.url + '" onclick="event.stopPropagation();">' + action.label + '</a>';
            }
            toast.innerHTML = content;

            this.container.appendChild(toast);

            // Auto-hide for success and info (not for errors)
            var autoHide = (type !== 'error');
            var hideDuration = duration || (type === 'success' ? 3000 : 5000);

            if (autoHide) {
                setTimeout(function () {
                    toast.classList.add('hiding');
                    setTimeout(function () {
                        if (toast.parentNode) {
                            toast.parentNode.removeChild(toast);
                        }
                    }, 300);
                }, hideDuration);
            }
        },

        success: function (message, action) {
            this.show(message, 'success', 3000, action);
        },

        error: function (message, action) {
            this.show(message, 'error', null, action); // Error toasts don't auto-hide
        },

        info: function (message, action) {
            this.show(message, 'info', 5000, action);
        }
    };

    /**
     * fetch() wrapper that shows a toast notification on response.
     *
     * @param {string} url - The URL to fetch
     * @param {Object} options - Standard fetch options, plus:
     *   - toastMessage {string} - Message to show in a toast on response
     *   - showSuccessToast {boolean} - If false, suppresses the success toast (default true)
     * @returns {Promise<Response>} The fetch response
     */
    function fetchWithToast(url, options) {
        var originalOptions = options || {};
        var toastMessage = originalOptions.toastMessage;
        var showSuccessToast = originalOptions.showSuccessToast !== false;

        // Add CSRF token header if present
        var csrfToken = getCookie('XSRF-TOKEN');
        console.log('fetchWithWorkflow: CSRF token from cookie:', csrfToken);
        if (csrfToken) {
            var headers = originalOptions.headers || {};
            headers['X-XSRF-TOKEN'] = csrfToken;
            originalOptions.headers = headers;
            console.log('fetchWithWorkflow: Headers after adding CSRF:', originalOptions.headers);
        }

        return fetch(url, originalOptions).then(function (response) {
            console.log('fetchWithWorkflow: Response status:', response.status);
            if (toastMessage) {
                if (response.ok) {
                    if (showSuccessToast) {
                        Toast.success(toastMessage);
                    }
                } else {
                    Toast.error(toastMessage + ' (' + response.status + ')');
                }
            }
            return response;
        }).catch(function (error) {
            if (toastMessage) {
                Toast.error(toastMessage + ': ' + (error.message || 'błąd sieci'));
            }
            console.error('fetchWithWorkflow: Error:', error);
            throw error;
        });
    }

    /**
     * Returns the value of the cookie with the given name, or null if not found.
     */
    function getCookie(name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for(var i=0;i < ca.length;i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length, c.length);
        }
        return null;
    }

    /**
     * Scroll a list row into view and apply a transient green highlight.
     * Shared by the members / events / rehearsals lists so a freshly
     * created or edited entity pulses green and is scrolled into view.
     *
     * @param {string} prefix - row id prefix, e.g. 'member' -> 'member-123'
     * @param {string|number} id - entity id
     */
    function focusEntityRow(prefix, id) {
        if (id == null || id === '' || id === 'null' || id === '0') return;
        var row = document.getElementById(prefix + '-' + id);
        if (!row) {
            row = document.querySelector('[data-entity-id="' + id + '"]');
        }
        if (!row) return;
        row.classList.add('highlight-row');
        setTimeout(function () { row.classList.remove('highlight-row'); }, 3000);
        row.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    /**
     * Reads data-focus-id / data-focus-prefix from any container present in the
     * DOM and focuses the matching row. Runs on page load (DOMContentLoaded)
     * and after every HTMX swap (htmx:afterSwap) so it works for both full
     * navigations (event/rehearsal/member create -> list page reload) and
     * in-page HTMX reloads (member edit). Guarded per-container so a row is
     * only highlighted once.
     */
    function initFocusHighlight() {
        document.querySelectorAll('[data-focus-id]').forEach(function (container) {
            var focusId = container.getAttribute('data-focus-id');
            if (!focusId || focusId === 'null' || focusId === '0') return;
            if (container.dataset.focusHandled === '1') return;
            container.dataset.focusHandled = '1';
            var prefix = container.getAttribute('data-focus-prefix') || 'entity';
            focusEntityRow(prefix, focusId);
        });
    }

    /**
     * Bind event-detail page handlers (invite member/group, delete, response,
     * instrument, send, payment). Called on DOMContentLoaded AND on every
     * htmx:afterSwap so handlers survive HTMX navigation to event details
     * and the in-page reloads the detail page triggers after each action.
     */
    function bindEventDetailHandlers() {
    // Initialize from DOM attributes (works after HTMX swap)
    var container = document.querySelector('#events-content[data-event-id]');
    if (!container) return;
    if (container.dataset.detailBound === '1') return;
    container.dataset.detailBound = '1';
    
    // Set selected options for instrument selects based on data-name attribute
    document.querySelectorAll('.instrument-select').forEach(function(select) {
        var currentInstrument = select.getAttribute('data-current-instrument');
        if (currentInstrument) {
            var option = select.querySelector('option[value="' + currentInstrument + '"]');
            if (option) option.selected = true;
        }
    });
    
    // Set selected options for response selects based on data-response attribute
    document.querySelectorAll('.response-select').forEach(function(select) {
        var currentResponse = select.getAttribute('data-response');
        if (currentResponse) {
            select.value = currentResponse;
        }
    });
    
    // Set selected options for payment status selects based on data-status attribute
    document.querySelectorAll('.payment-status-select').forEach(function(select) {
        var currentStatus = select.getAttribute('data-status');
        if (currentStatus) {
            select.value = currentStatus;
        }
    });
    var eventId = container.dataset.eventId;
    var paymentType = container.dataset.paymentType;
    var paymentAmount = container.dataset.paymentAmount;
    var confirmedCount = parseInt(container.dataset.confirmedCount) || 0;
    var isPaidSplit = (paymentType === 'PAID_SPLIT');
    console.log("Event detail script loaded, eventId:", eventId);

    // Show/hide payout column based on payment type
    function updatePayoutColumns() {
        var cols = document.querySelectorAll('.payout-col');
        cols.forEach(function(col) {
            col.style.display = isPaidSplit ? '' : 'none';
        });
    }

    // Calculate and display per-member payout
    function updatePayoutAmounts() {
        if (!isPaidSplit || !paymentAmount || confirmedCount === 0) {
            document.querySelectorAll('.payout-amount').forEach(function(td) {
                td.textContent = '-';
            });
            return;
        }
        var perPerson = (parseFloat(paymentAmount) / confirmedCount).toFixed(2);
        document.querySelectorAll('.payout-amount').forEach(function(td) {
            td.textContent = perPerson + ' PLN';
        });
    }

    // Update table when confirmed count changes
    function handleResponseChange() {
        // Recalculate confirmed count
        var selects = document.querySelectorAll('.response-select');
        var newConfirmed = 0;
        selects.forEach(function(s) {
            if (s.value === 'CONFIRMED') newConfirmed++;
        });
        confirmedCount = newConfirmed;
        updatePayoutAmounts();
    }

    updatePayoutColumns();
    updatePayoutAmounts();

    // Get invited member IDs from existing participant rows
    var invitedMemberIds = new Set();
    document.querySelectorAll('#participants-table tbody tr[data-member-id]').forEach(function(row) {
        invitedMemberIds.add(parseInt(row.dataset.memberId));
    });

    // Open the multi-member invite modal
    var openInviteBtn = document.getElementById('open-invite-modal-btn');
    if (openInviteBtn) {
        openInviteBtn.addEventListener('click', function() {
            if (typeof openAppModal === 'function') {
                openAppModal('invite-members-modal');
            } else {
                var fallbackDlg = document.getElementById('invite-members-modal');
                if (fallbackDlg && fallbackDlg.showModal) fallbackDlg.showModal();
            }
        });
    }

    // Invite all checked members from the modal
    var inviteSelectedBtn = document.getElementById('invite-selected-btn');
    if (inviteSelectedBtn) {
        inviteSelectedBtn.addEventListener('click', function() {
            var modal = document.getElementById('invite-members-modal');
            var checkboxes = modal ? modal.querySelectorAll('.invite-checkbox:checked') : [];
            var selected = [];
            checkboxes.forEach(function(cb) { selected.push(parseInt(cb.value)); });
            console.log('Invite selected clicked, count=' + selected.length + ', eventId=' + eventId);
            if (selected.length === 0) {
                if (window.Toast) Toast.info('Zaznacz przynajmniej jedną osobę');
                return;
            }
            var csrf = getCookie('XSRF-TOKEN');
            var promises = selected.map(function(mid) {
                var headers = {'Content-Type': 'application/json'};
                if (csrf) headers['X-XSRF-TOKEN'] = csrf;
                return fetch('/api/events/' + eventId + '/invite', {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify({eventId: parseInt(eventId), memberId: mid})
                });
            });
            Promise.all(promises).then(function() {
                if (typeof closeAppModal === 'function') closeAppModal(modal);
                else if (modal && modal.close) modal.close();
                Toast.success('Zaproszono ' + selected.length + (selected.length === 1 ? ' osobę' : ' osób'));
                htmx.ajax('GET', '/events/' + eventId, {target: '#events-content[data-event-id]', swap: 'outerHTML transition:true'});
                // One-time handler: highlight + scroll to all newly invited members
                var handler = function(evt) {
                    if (evt.detail && evt.detail.target && evt.detail.target.id === 'events-content') {
                        setTimeout(function() {
                            selected.forEach(function(mid) {
                                var row = document.querySelector('#participants-table tbody tr[data-member-id="' + mid + '"]');
                                if (row) {
                                    row.classList.add('highlight-row');
                                    setTimeout(function() { row.classList.remove('highlight-row'); }, 3000);
                                    row.scrollIntoView({behavior: 'smooth', block: 'center'});
                                }
                            });
                        }, 50);
                        document.body.removeEventListener('htmx:afterSettle', handler);
                    }
                };
                document.body.addEventListener('htmx:afterSettle', handler);
            }).catch(function(err) {
                console.error('Invite selected error:', err);
                if (window.Toast) Toast.error('Błąd podczas zapraszania');
            });
        });
    }

    // Invite entire group
    document.getElementById('invite-group-btn').addEventListener('click', function() {
        var select = document.getElementById('invite-group-select');
        var groupId = select.value;
        if (groupId) {
            // Snapshot current member IDs to detect new ones after reload
            var beforeIds = new Set(invitedMemberIds);
            fetchWithToast('/api/events/' + eventId + '/invite-group', { toastMessage: 'Zaproszono grupę',
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({eventId: eventId, groupId: parseInt(groupId)})
            }).then(function(response) {
                if (!response.ok) return;
                htmx.ajax('GET', '/events/' + eventId, {target: '#events-content[data-event-id]', swap: 'outerHTML transition:true'});
                // After reload, highlight rows that weren't in beforeIds
                // We use a one-time afterSettle handler to compute the diff
                var handler = function(evt) {
                    if (evt.detail && evt.detail.target && evt.detail.target.id === 'events-content') {
                        // Small delay to ensure DOM is fully settled
                        setTimeout(function() {
                            var scrolled = false;
                            document.querySelectorAll('#participants-table tbody tr[data-member-id]').forEach(function(row) {
                                var mid = parseInt(row.dataset.memberId);
                                if (!beforeIds.has(mid)) {
                                    console.log('Highlighting and scrolling to new member row, memberId=' + mid);
                                    row.classList.add('highlight-row');
                                    setTimeout(function() { row.classList.remove('highlight-row'); }, 3000);
                                    if (!scrolled) {
                                        row.scrollIntoView({behavior: 'smooth', block: 'center'});
                                        scrolled = true;
                                    }
                                }
                            });
                        }, 50);
                        document.body.removeEventListener('htmx:afterSettle', handler);
                    }
                };
                document.body.addEventListener('htmx:afterSettle', handler);
            }).catch(function(err) { console.error('Invite error:', err); });
        }
    });

    // Record response
    document.querySelectorAll('.response-select').forEach(function(select) {
        select.addEventListener('change', function() {
            var eid = this.dataset.eventId;
            var mid = this.dataset.memberId;
            var response = this.value;

            fetchWithToast('/api/events/' + eventId + '/response', { toastMessage: 'Zaktualizowano status',
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({eventId: parseInt(eid), memberId: parseInt(mid), response: response})
            }).then(function(response) {
                if (!response.ok) {
                    console.error('Failed to update response');
                    return;
                }
                // Save scroll position before HTMX swap (no scrolling for response changes)
                var scrollY = window.scrollY;
                htmx.ajax('GET', '/events/' + eventId, {target: '#events-content[data-event-id]', swap: 'outerHTML transition:true'});
                // Restore scroll position after swap settles
                var settleHandler = function(evt) {
                    if (evt.detail && evt.detail.target && evt.detail.target.id === 'events-content') {
                        window.scrollTo(0, scrollY);
                        document.body.removeEventListener('htmx:afterSettle', settleHandler);
                    }
                };
                document.body.addEventListener('htmx:afterSettle', settleHandler);
            }).catch(function(err) { console.error('Response update error:', err); });
        });
    });

    // Change instrument for this event participation (not member's default)
    document.querySelectorAll('.instrument-select').forEach(function(select) {
        select.addEventListener('change', function() {
            var memberId = this.dataset.memberId;
            var instrumentId = this.value;
            fetchWithToast('/api/events/' + eventId + '/participation-instrument', { toastMessage: 'Zmieniono instrument na to wydarzenie',
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({memberId: parseInt(memberId), instrumentId: instrumentId ? parseInt(instrumentId) : null})
            }).then(function(response) {
                if (!response.ok) {
                    console.error('Failed to update instrument');
                    return;
                }
                htmx.ajax('GET', '/events/' + eventId, {target: '#events-content[data-event-id]', swap: 'outerHTML transition:true'});
                // Scroll to the edited member after swap settles (list may have reordered by instrument)
                var settleHandler = function(evt) {
                    if (evt.detail && evt.detail.target && evt.detail.target.id === 'events-content') {
                        setTimeout(function() {
                            var row = document.querySelector('#participants-table tbody tr[data-member-id="' + memberId + '"]');
                            if (row) {
                                row.classList.add('highlight-row');
                                setTimeout(function() { row.classList.remove('highlight-row'); }, 2000);
                                row.scrollIntoView({behavior: 'smooth', block: 'center'});
                            }
                        }, 50);
                        document.body.removeEventListener('htmx:afterSettle', settleHandler);
                    }
                };
                document.body.addEventListener('htmx:afterSettle', settleHandler);
            }).catch(function(err) { console.error('Instrument update error:', err); });
        });
    });

    // Instrument inline edit: toggle between display name and select
    document.querySelectorAll('.instrument-display').forEach(function(display) {
        var nameEl = display.querySelector('.instrument-name');
        var editBtn = display.querySelector('.instrument-edit-btn');
        var selectEl = display.querySelector('.instrument-select');

        editBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            // Close any other open selects
            document.querySelectorAll('.instrument-display').forEach(function(other) {
                var otherName = other.querySelector('.instrument-name');
                var otherBtn = other.querySelector('.instrument-edit-btn');
                var otherSelect = other.querySelector('.instrument-select');
                if (other !== display) {
                    otherSelect.style.display = 'none';
                    otherName.style.display = 'inline';
                    otherBtn.style.display = 'inline-block';
                }
            });
            nameEl.style.display = 'none';
            editBtn.style.display = 'none';
            selectEl.style.display = 'inline-block';
            selectEl.focus();
        });

        // Hide select on blur or after change (change handler reloads page anyway)
        selectEl.addEventListener('blur', function() {
            selectEl.style.display = 'none';
            nameEl.style.display = 'inline';
            editBtn.style.display = 'inline-block';
        });
    });

    // Click outside to close open instrument selects
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.instrument-display')) {
            document.querySelectorAll('.instrument-display').forEach(function(display) {
                var nameEl = display.querySelector('.instrument-name');
                var editBtn = display.querySelector('.instrument-edit-btn');
                var selectEl = display.querySelector('.instrument-select');
                selectEl.style.display = 'none';
                nameEl.style.display = 'inline';
                editBtn.style.display = 'inline-block';
            });
        }
    });

    // Payment status change
    document.querySelectorAll('.payment-status-select').forEach(function(select) {
        select.addEventListener('change', function() {
            var eventId = this.dataset.eventId;
            var memberId = this.dataset.memberId;
            var status = this.value;
            fetchWithToast('/api/events/' + eventId + '/payment-status', { toastMessage: 'Zaktualizowano płatność',
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({eventId: parseInt(eventId), memberId: parseInt(memberId), status: status})
            }).then(function(response) {
                if (!response.ok) {
                    console.error('Failed to update payment status');
                    return;
                }
            }).catch(function(err) { console.error('Invite error:', err); });
        });
    });

    // Delete event
    try {
        document.getElementById('delete-event-btn').addEventListener('click', function() {
            var modal = document.getElementById('delete-event-modal');
            if (typeof openAppModal === 'function') {
                openAppModal('delete-event-modal');
            } else if (modal.showModal) {
                modal.showModal();
            }
        });
    } catch (e) { console.error('delete-event-btn handler error:', e); }

    try {
        document.getElementById('delete-event-cancel-btn').addEventListener('click', function() {
            var modal = document.getElementById('delete-event-modal');
            if (typeof closeAppModal === 'function') {
                closeAppModal(modal);
            } else if (modal.close) {
                modal.close();
            }
        });
    } catch (e) { console.error('delete-event-cancel-btn handler error:', e); }

    try {
        document.getElementById('delete-event-confirm-btn').addEventListener('click', function() {
            var container = document.querySelector('#events-content[data-event-id]');
            var eventId = container ? container.dataset.eventId : window.location.pathname.split('/').pop();
            console.log('DELETE confirm clicked, eventId=', eventId);
            var modal = document.getElementById('delete-event-modal');
            fetch('/api/events/' + eventId, {
                method: 'DELETE'
            }).then(function(response) {
                console.log('DELETE response status=', response.status);
                if (response.ok) {
                    Toast.success('Usunięto wydarzenie');
                } else {
                    Toast.error('Nie udało się usunąć wydarzenia');
                }
            }).catch(function(err) {
                console.error('Delete error:', err);
            }).finally(function() {
                try {
                    if (typeof closeAppModal === 'function') {
                        closeAppModal(modal);
                    } else if (modal.close) {
                        modal.close();
                    }
                } catch (e) {
                    console.error('Modal close error:', e);
                }
                console.log('Redirecting to /events');
                window.location.replace('/events');
            });
        });
    } catch (e) { console.error('delete-event-confirm-btn handler error:', e); }

    // Send invitation to single member
    document.querySelectorAll('.send-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            var eid = this.dataset.eventId;
            var mid = this.dataset.memberId;
            var status = this.dataset.status;
            
            // Don't send if already sent
            if (status === 'SENT') return;
            
            fetchWithToast('/api/events/' + eid + '/send/' + mid, {
                toastMessage: 'Wysłano zaproszenie',
                method: 'POST'
            }).then(function(response) {
                if (response.ok) {
                    // Reload the event detail page to show updated status
                    htmx.ajax('GET', '/events/' + eid, {target: '#events-content[data-event-id]', swap: 'outerHTML transition:true'});
                }
            }).catch(function(err) { console.error('Send error:', err); });
        });
    });

    // Send invitations to all members
    var sendAllBtn = document.getElementById('send-all-btn');
    if (sendAllBtn) {
        sendAllBtn.addEventListener('click', function() {
            var eid = document.querySelector('#events-content[data-event-id]').dataset.eventId;
            var resultSpan = document.getElementById('send-all-result');
            
            this.disabled = true;
            resultSpan.textContent = 'Wysyłanie...';
            
            fetchWithToast('/api/events/' + eid + '/send-all', {
                toastMessage: 'Wysłano zaproszenia',
                method: 'POST'
            }).then(function(response) {
                if (response.ok) {
                    response.json().then(function(data) {
                        resultSpan.textContent = '✅ Wysłano ' + data.sent + ' zaproszeń';
                    });
                    // Reload to show updated statuses
                    setTimeout(function() {
                        htmx.ajax('GET', '/events/' + eid, {target: '#events-content[data-event-id]', swap: 'outerHTML transition:true'});
                    }, 1000);
                } else {
                    resultSpan.textContent = '❌ Błąd wysyłki';
                    sendAllBtn.disabled = false;
                }
            }).catch(function(err) {
                console.error('Send all error:', err);
                resultSpan.textContent = '❌ Błąd wysyłki';
                sendAllBtn.disabled = false;
            });
        });
    }

    // Sticky stats bar (runs after main init)
    var statsBar = document.querySelector('.event-stats');
    var nav = document.querySelector('.top-nav');
    if (!statsBar || !nav) return;

    function updateStatsPosition() {
        var navHeight = nav.offsetHeight;
        var scrollY = window.scrollY;
        if (scrollY > navHeight) {
            statsBar.style.position = 'fixed';
            statsBar.style.top = navHeight + 'px';
            statsBar.style.zIndex = '100';
            statsBar.style.width = statsBar.offsetWidth + 'px';
        } else {
            statsBar.style.position = '';
            statsBar.style.top = '';
            statsBar.style.zIndex = '';
            statsBar.style.width = '';
        }
    }

    window.addEventListener('scroll', updateStatsPosition, { passive: true });
    updateStatsPosition();
    }

    global.bindEventDetailHandlers = bindEventDetailHandlers;
    global.initFocusHighlight = initFocusHighlight;
    // Expose globally
    global.Toast = Toast;
    global.fetchWithToast = fetchWithToast;
    global.getCookie = getCookie;
})(window);

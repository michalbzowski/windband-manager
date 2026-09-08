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
    /**
     * Refresh the derived state (select values + counts) on the event-detail
     * page without re-attaching listeners. Used when bindEventDetailHandlers
     * is called again on an already-bound node: we don't want to double-bind,
     * but we DO want the current DOM's select values / confirmed count to be
     * kept in sync (e.g. after the user changes a response and the page
     * re-renders the same container).
     */
    function refreshEventDetailDerivedState() {
        // Re-set .instrument-select selected options from data-current-instrument.
        document.querySelectorAll('.instrument-select').forEach(function(select) {
            var currentInstrument = select.getAttribute('data-current-instrument');
            if (currentInstrument) {
                var option = select.querySelector('option[value="' + currentInstrument + '"]');
                if (option && document.activeElement !== select) option.selected = true;
            }
        });
        // Re-set .response-select values from data-response.
        document.querySelectorAll('.response-select').forEach(function(select) {
            var currentResponse = select.getAttribute('data-response');
            if (currentResponse && document.activeElement !== select) {
                select.value = currentResponse;
            }
        });
    }

     function bindEventDetailHandlers() {
         // Idempotent per-DOM-copy: the guard lives on the container element,
         // which is REPLACED by an HTMX outerHTML swap (fresh copy → binding
         // proceeds). A re-invocation on the SAME node (e.g. afterSwap AND
         // afterSettle both fire) becomes a no-op — no double listeners.
         var containerEvt = document.querySelector('#events-content[data-event-id]');
         if (!containerEvt) return;
         if (containerEvt.dataset.detailBound === 'yes') {
             // Still refresh the response-select values + confirmed count from
             // the current DOM so re-applied data is accurate.
             refreshEventDetailDerivedState();
             return;
         }
         containerEvt.dataset.detailBound = 'yes';

     // Initialize from DOM attributes (works after HTMX swap)
     var container = document.querySelector('#events-content[data-event-id]');
     if (!container) return;

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

    // The old two-modal invite flow ("Zaproś uczestników" + "Zaproś grupę") was
    // replaced by the unified invite modal (t_f03a9819) wired directly in
    // events/detail.html (t_c9b13437). The previous handlers for
    // #open-invite-modal-btn / #invite-selected-btn / #open-invite-group-modal-btn
    // / #invite-group-selected-btn were removed from this file; the new button
    // binding lives on the page itself and outlives HTMX swaps via a
    // MutationObserver re-binder.

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

    /**
     * Bind group-detail page handlers (add members via modal). Called on DOMContentLoaded AND on every
     * htmx:afterSwap so handlers survive HTMX navigation to group details and the in-page
     * reloads the detail page triggers after each action.
     */
    function bindGroupDetailHandlers() {
        var container = document.querySelector('#groups-content[data-group-id]');
        if (!container) return;
        if (container.dataset.detailBound === '1') return;
        container.dataset.detailBound = '1';

        var groupId = container.dataset.groupId;
        console.log("Group detail script loaded, groupId:", groupId);

        // Open the add-members modal
        var openAddMembersBtn = document.getElementById('open-add-members-modal-btn');
        if (openAddMembersBtn) {
            openAddMembersBtn.addEventListener('click', function() {
                var modal = document.getElementById('add-members-to-group-modal');
                if (modal && typeof modal.showModal === 'function') {
                    modal.showModal();
                } else if (modal) {
                    modal.setAttribute('open', '');
                    modal.classList.add('app-modal-fallback');
                }
            });
        }

        // Add all checked members from the modal
        var addSelectedBtn = document.getElementById('add-selected-members-btn');
        if (addSelectedBtn) {
            addSelectedBtn.addEventListener('click', function() {
                var modal = document.getElementById('add-members-to-group-modal');
                var checkboxes = modal ? modal.querySelectorAll('.add-member-checkbox:checked') : [];
                var selected = [];
                checkboxes.forEach(function(cb) { selected.push(parseInt(cb.value)); });
                console.log('Add selected clicked, count=' + selected.length + ', groupId=' + groupId);
                if (selected.length === 0) {
                    if (window.Toast) Toast.info('Zaznacz przynajmniej jedną osobę');
                    return;
                }
                var csrf = getCookie('XSRF-TOKEN');
                var promises = selected.map(function(mid) {
                    var headers = {'Content-Type': 'application/json'};
                    if (csrf) headers['X-XSRF-TOKEN'] = csrf;
                    return fetch('/api/groups/' + groupId + '/members/' + mid, {
                        method: 'POST',
                        headers: headers
                    });
                });
                Promise.all(promises).then(function(responses) {
                    var allOk = Array.from(responses).every(function(r) { return r.ok; });
                    if (allOk) {
                        if (typeof closeAppModal === 'function') closeAppModal(modal);
                        else if (modal && modal.close) modal.close();
                        Toast.success('Dodano ' + selected.length + (selected.length === 1 ? ' osobę' : ' osób'));
                        htmx.ajax('GET', '/groups/' + groupId, {
            target: document.querySelector('#groups-content'),
            swap: 'outerHTML',
            headers: {'HX-Request': 'true'}
        });
                    } else {
                        console.error('Add members error: some requests failed');
                        if (window.Toast) Toast.error('Błąd podczas dodawania członków');
                    }
                }).catch(function(err) {
                    console.error('Add members error:', err);
                    if (window.Toast) Toast.error('Błąd podczas dodawania członków');
                });
            });
        }
        }

        /**
         * Bind rehearsal-detail page handlers (invite member/group, quick attendance).
         * Called on DOMContentLoaded AND on every htmx:afterSwap so handlers survive
         * HTMX navigation to rehearsal details and the in-page reloads the detail
         * page triggers after each action (e.g., after quick attendance completes).
         */
        function bindRehearsalDetailHandlers() {
            // Initialize from DOM attributes (works after HTMX swap)
            var container = document.querySelector('#rehearsals-content[data-rehearsal-id]');
            if (!container) return;
    
            // For innerHTML swaps, the container element persists but content changes.
            // We need to re-bind handlers every time because buttons/modals are recreated.
            // Use a timestamp-based check to avoid double-binding on the same swap cycle.
            var now = Date.now();
            if (container.dataset.detailBoundAt && (now - parseInt(container.dataset.detailBoundAt)) < 100) return;
            container.dataset.detailBoundAt = now.toString();

            var rehearsalId = container.dataset.rehearsalId;
            console.log("Rehearsal detail script loaded, rehearsalId:", rehearsalId);

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
                    console.log('Invite selected clicked, count=' + selected.length + ', rehearsalId=' + rehearsalId);
                    if (selected.length === 0) {
                        if (window.Toast) Toast.info('Zaznacz przynajmniej jedną osobę');
                        return;
                    }
                    var csrf = getCookie('XSRF-TOKEN');
                    var promises = selected.map(function(mid) {
                        var headers = {'Content-Type': 'application/json'};
                        if (csrf) headers['X-XSRF-TOKEN'] = csrf;
                        return fetch('/api/rehearsals/' + rehearsalId + '/invite', {
                            method: 'POST',
                            headers: headers,
                            body: JSON.stringify({rehearsalId: parseInt(rehearsalId), memberId: mid})
                        });
                    });
                    Promise.all(promises).then(function() {
                        if (typeof closeAppModal === 'function') closeAppModal(modal);
                        else if (modal && modal.close) modal.close();
                        if (window.Toast) Toast.success('Zaproszono ' + selected.length + (selected.length === 1 ? ' osobę' : ' osób'));
                        // Reload the detail fragment to render the new attendance rows
                        htmx.ajax('GET', '/rehearsals/' + rehearsalId, {target: '#rehearsals-content[data-rehearsal-id]', swap: 'outerHTML transition:true'});
                        // One-time handler: highlight + scroll to all newly invited members
                        var handler = function(evt) {
                            if (evt.detail && evt.detail.target && evt.detail.target.id === 'rehearsals-content') {
                                setTimeout(function() {
                                    selected.forEach(function(mid) {
                                        var row = document.querySelector('#rehearsals-content tbody tr[data-member-id="' + mid + '"]');
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

            // Open the group invite modal
            var openInviteGroupBtn = document.getElementById('open-invite-group-modal-btn');
            if (openInviteGroupBtn) {
                openInviteGroupBtn.addEventListener('click', function() {
                    if (typeof openAppModal === 'function') {
                        openAppModal('invite-group-modal');
                    } else {
                        var fallbackDlg = document.getElementById('invite-group-modal');
                        if (fallbackDlg && fallbackDlg.showModal) fallbackDlg.showModal();
                    }
                });
            }

            // Invite all checked groups from the modal
            var inviteGroupSelectedBtn = document.getElementById('invite-group-selected-btn');
            if (inviteGroupSelectedBtn) {
                inviteGroupSelectedBtn.addEventListener('click', function() {
                    var modal = document.getElementById('invite-group-modal');
                    var checkboxes = modal ? modal.querySelectorAll('.invite-group-checkbox:checked') : [];
                    var selected = [];
                    checkboxes.forEach(function(cb) { selected.push(parseInt(cb.value)); });
                    console.log('Invite group selected clicked, count=' + selected.length + ', rehearsalId=' + rehearsalId);
                    if (selected.length === 0) {
                        if (window.Toast) Toast.info('Zaznacz przynajmniej jedną grupę');
                        return;
                    }
                    // Snapshot current member IDs to detect new ones after reload
                    var beforeIds = new Set();
                    document.querySelectorAll('#rehearsals-content tbody tr[data-member-id]').forEach(function(row) {
                        beforeIds.add(parseInt(row.dataset.memberId));
                    });
                    var csrf = getCookie('XSRF-TOKEN');
                    var promises = selected.map(function(gid) {
                        var headers = {'Content-Type': 'application/json'};
                        if (csrf) headers['X-XSRF-TOKEN'] = csrf;
                        return fetch('/api/rehearsals/' + rehearsalId + '/invite-group', {
                            method: 'POST',
                            headers: headers,
                            body: JSON.stringify({rehearsalId: parseInt(rehearsalId), groupId: gid})
                        });
                    });
                    Promise.all(promises).then(function(responses) {
                        var allOk = Array.from(responses).every(function(r) { return r.ok; });
                        if (allOk) {
                            if (typeof closeAppModal === 'function') closeAppModal(modal);
                            else if (modal && modal.close) modal.close();
                            if (window.Toast) Toast.success('Zaproszono zaznaczone grupy');
                            htmx.ajax('GET', '/rehearsals/' + rehearsalId, {target: '#rehearsals-content[data-rehearsal-id]', swap: 'outerHTML transition:true'});
                            // After reload, highlight rows that weren't in beforeIds
                            var handler = function(evt) {
                                if (evt.detail && evt.detail.target && evt.detail.target.id === 'rehearsals-content') {
                                    setTimeout(function() {
                                        var scrolled = false;
                                        document.querySelectorAll('#rehearsals-content tbody tr[data-member-id]').forEach(function(row) {
                                            var mid = parseInt(row.dataset.memberId);
                                            if (!beforeIds.has(mid)) {
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
                        }
                    }).catch(function(err) {
                        console.error('Invite group error:', err);
                        if (window.Toast) Toast.error('Błąd podczas zapraszania grupy');
                    });
                });
            }
            // Inline "Edytuj" — loads the rehearsal edit form into
            // #rehearsals-content via HTMX (same as the old hx-get on the
            // button). URL is provided by the template through a data attr.
            var editBtn = document.getElementById('edit-rehearsal-btn');
            if (editBtn) {
                editBtn.addEventListener('click', function() {
                    var url = editBtn.getAttribute('data-edit-url');
                    if (!url) return;
                    htmx.ajax('GET', url, {target: '#rehearsals-content', swap: 'innerHTML transition:true'});
                });
            }
        }

        global.bindEventDetailHandlers = bindEventDetailHandlers;
    global.bindGroupDetailHandlers = bindGroupDetailHandlers;
    global.bindRehearsalDetailHandlers = bindRehearsalDetailHandlers;
    global.initFocusHighlight = initFocusHighlight;

    /**
     * Detail actions bar (unified header on event/rehearsal detail pages).
     *
     * The fragment (fragments/detail-page-actions-bar.html) renders:
     *   [back-icon]  Title                  [Edytuj icon] [⋮ → Usuń …]
     *
     * This binder wires the behaviour:
     *   - toggles the ⋮ overflow menu open/close (one instance at a time);
     *   - "Edytuj"   → dispatches CustomEvent 'windband:detail-edit' on body,
     *                  OR clicks element[data-detail-edit-target] if present;
     *   - "Usuń"     → dispatches 'windband:detail-delete',
     *                  OR clicks element[data-detail-delete-target] (the page's
     *                  own delete button whose handler opens the confirm modal).
     *
     * Delegation on document so it survives htmx:afterSwap of #content and
     * any full-page reload. Registered ONCE here at script load; layout.html
     * also re-invokes bindDetailActionsOverflow() after htmx:afterSwap for
     * the pages that swap their detail fragment in-place.
     */
    function bindDetailActionsOverflow(root) {
        var scope = root || document;
        var bars = scope.querySelectorAll('nav.detail-actions-bar[data-detail-bar]');
        if (!bars.length) return;

        bars.forEach(function(bar) {
            if (bar.dataset.overflowBound === '1') return;
            bar.dataset.overflowBound = '1';

            var trigger = bar.querySelector('[data-detail-action="toggle-more"]');
            var menu = bar.querySelector('.overflow-menu');
            var editBtn = bar.querySelector('[data-detail-action="inline-edit"]');
            var deleteItem = bar.querySelector('[data-detail-action="open-delete"]');

            function closeMenu() {
                if (!menu) return;
                menu.classList.remove('open');
                if (trigger) trigger.setAttribute('aria-expanded', 'false');
            }

            if (trigger && menu) {
                trigger.addEventListener('click', function(evt) {
                    evt.stopPropagation();
                    var open = !menu.classList.contains('open');
                    // Only one overflow menu open at a time.
                    document.querySelectorAll('.overflow-menu.open').forEach(function(other) {
                        if (other !== menu) {
                            other.classList.remove('open');
                            var trig = other.parentElement;
                            var t = trig ? trig.querySelector('[data-detail-action="toggle-more"]') : null;
                            if (t) t.setAttribute('aria-expanded', 'false');
                        }
                    });
                    menu.classList.toggle('open', open);
                    trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
                });
            }

            function dispatchOrClick(eventName, targetAttrName) {
                // 1) Generic event for JS-only consumers.
                bar.dispatchEvent(new CustomEvent(eventName, {bubbles: true}));
                document.body.dispatchEvent(new CustomEvent(eventName, {bubbles: true}));
                // 2) Optional DOM hook — if the page exposed a "click me" id,
                //    drive it so existing inline scripts keep working.
                var targetId = bar.getAttribute(targetAttrName);
                if (targetId) {
                    var el = document.getElementById(targetId);
                    if (el && typeof el.click === 'function') el.click();
                }
            }

            if (editBtn) {
                editBtn.addEventListener('click', function(evt) {
                    evt.stopPropagation();
                    dispatchOrClick('windband:detail-edit', 'data-detail-edit-target');
                });
            }
            if (deleteItem) {
                deleteItem.addEventListener('click', function(evt) {
                    evt.stopPropagation();
                    closeMenu();
                    dispatchOrClick('windband:detail-delete', 'data-detail-delete-target');
                });
            }

            // Close the menu on outside click / Escape.
            bar._outsideClose = function(evt) {
                if (menu && menu.classList.contains('open') && !bar.contains(evt.target)) {
                    closeMenu();
                }
            };
            document.addEventListener('click', bar._outsideClose, true);
            bar._escClose = function(evt) {
                if (evt.key === 'Escape' && menu && menu.classList.contains('open')) closeMenu();
            };
            document.addEventListener('keydown', bar._escClose, true);
        });
    }

    // Clean up listener references when the bar is swapped out, so we don't leak.
    (function() {
        var _originalRemove = Element.prototype.remove;
        // Lightweight leak guard: remove external listeners attached by this
        // binder whenever a nav.detail-actions-bar leaves the DOM via .remove().
        // (htmx innerswap also calls .remove() on removed nodes.)
        Element.prototype.remove = function(...args) {
            var el = this;
            try {
                if (el.nodeType === 1 && el.matches && el.matches('nav.detail-actions-bar[data-detail-bar]')) {
                    if (typeof el._outsideClose === 'function') document.removeEventListener('click', el._outsideClose, true);
                    if (typeof el._escClose === 'function')   document.removeEventListener('keydown', el._escClose, true);
                }
            } catch (e) { /* intentionally ignored */ }
            return _originalRemove.apply(this, args);
        };
    })();

    global.bindDetailActionsOverflow = bindDetailActionsOverflow;


    /**
     * Send welcome/consent email for a member.
     * Uses fetchWithToast to show success/error notification.
     * Then reloads the member detail fragment to update consent status.
     */
    function sendWelcomeEmail(btn) {
        var memberId = btn.dataset.memberId;
        if (!memberId) return;
        
        btn.disabled = true;
        btn.textContent = 'Wysyłanie...';
        
        fetchWithToast('/api/members/' + memberId + '/resend-welcome', {
            toastMessage: 'Wysłano prośbę o zgodę',
            method: 'POST'
        }).then(function(response) {
            if (response.ok) {
                // Reload the member detail page to show updated consent status
                htmx.ajax('GET', '/members/' + memberId + '/detail', {
                    target: '#member-detail-content', 
                    swap: 'outerHTML transition:true'
                });
            } else {
                btn.disabled = false;
                btn.textContent = '📧 Wyślij prośbę o zgodę';
            }
        }).catch(function(err) {
            console.error('Send welcome error:', err);
            btn.disabled = false;
            btn.textContent = '📧 Wyślij prośbę o zgodę';
        });
    }

    global.sendWelcomeEmail = sendWelcomeEmail;

    /**
     * Rehearsal form submit handler. Registered ONCE at page load using
     * event delegation on document, so it survives htmx swaps of the
     * form fragment (an inline <script> in the fragment would only run
     * after the next swap and could miss a quick submit click — that
     * is the bug fixed by hoisting this here).
     */
    document.addEventListener('submit', function(e) {
        var form = e.target;
        if (!form || form.id !== 'rehearsal-form') return;
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        var formData = new FormData(form);
        var data = {};
        formData.forEach(function(value, key) { data[key] = value; });
        var saveAndAddBtn = form.querySelector('button[name="saveAndAddAnother"]');
        var saveAndAddAnother = saveAndAddBtn && saveAndAddBtn === document.activeElement;

        fetchWithToast('/api/rehearsals', {
            toastMessage: 'Zapisano spotkanie',
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        }).then(function(response) {
            console.log('[REHEARSAL] response status:', response.status);
            return response.json();
        }).then(function(rehearsal) {
            if (saveAndAddAnother) {
                form.reset();
                var now = new Date();
                var today = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
                var dateInput = form.querySelector("input[name='date']");
                if (dateInput) dateInput.value = today;
                var startTimeInput = form.querySelector("input[name='startTime']");
                if (startTimeInput) startTimeInput.value = '18:00';
                var endTimeInput = form.querySelector("input[name='endTime']");
                if (endTimeInput) endTimeInput.value = '20:00';
            } else {
                if (rehearsal && rehearsal.id) {
                    window.location.href = '/rehearsals/' + rehearsal.id;
                } else {
                    window.location.href = '/rehearsals';
                }
            }
        }).catch(function(err) {
            console.log('[REHEARSAL] fetch error:', err);
        });
    }, true); // capture phase — runs BEFORE htmx default submit

    // Expose globally
    global.Toast = Toast;
    global.fetchWithToast = fetchWithToast;
    global.getCookie = getCookie;

    /**
         * Rehearsal attendance auto-save. Registered ONCE at page load using
         * event delegation on document, so it survives htmx swaps of the
         * #rehearsals-content fragment (an inline listener on each <select>
         * would be lost on swap). The `change` event is dispatched by both
         * human and Selenium-driven dropdown changes.
         *
         * Replaces the old "Zapisz obecność" button + window.saveRehearsalAttendance() handler.
         * NO_RESPONSE is still allowed — admin can reset a member's status
         * back to "no response" by re-selecting it.
         */
        document.addEventListener('change', function(e) {
            var target = e.target;
            if (!target || !target.classList || !target.classList.contains('status-select')) return;
            var container = target.closest('#rehearsals-content');
            if (!container) return;
            var rehearsalId = container.getAttribute('data-rehearsal-id');
            var memberId = target.getAttribute('data-member-id');
            var status = target.value;
            if (!rehearsalId || !memberId || !status) return;
            fetchWithToast('/api/rehearsals/' + rehearsalId + '/attendance', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({rehearsalId: parseInt(rehearsalId), memberId: parseInt(memberId), status: status}),
                toastMessage: 'Zapisano obecność',
                showSuccessToast: true
            }).then(function(response) {
                if (!response.ok) {
                    console.error('[REHEARSAL] auto-save failed:', response.status);
                    return;
                }
                // Reload the detail fragment to refresh filter counts (like events do)
                var scrollY = window.scrollY;
                htmx.ajax('GET', '/rehearsals/' + rehearsalId, {target: '#rehearsals-content[data-rehearsal-id]', swap: 'outerHTML transition:true'});
                // Restore scroll position after swap settles
                var settleHandler = function(evt) {
                    if (evt.detail && evt.detail.target && evt.detail.target.id === 'rehearsals-content') {
                        window.scrollTo(0, scrollY);
                        document.body.removeEventListener('htmx:afterSettle', settleHandler);
                    }
                };
                document.body.addEventListener('htmx:afterSettle', settleHandler);
            }).catch(function(err) {
                console.error('[REHEARSAL] auto-save failed:', err);
                if (window.Toast) Toast.error('Błąd zapisu obecności');
            });
        });

        /**
         * Re-bind detail-page handlers after HTMX fragment swaps.
         *
         * After changing a member's response/status, the page reloads just
         * the content fragment (#events-content or #rehearsals-content) via
         * htmx.ajax(..., {swap: 'outerHTML'}). That new DOM is a FRESH copy:
         * any inline per-row listeners on <select class="response-select"> /
         * <select class="status-select"> are attached to the old (removed)
         * elements and do NOT carry over. This global afterSwap hook re-runs
         * the bind*Handlers functions which querySelector the newly swapped
         * DOM and attach listeners only to FRESH nodes (guarded by
         * data-binders-attached flags so we never double-bind on the same node).
         */
        function scheduleRebindAfterSwap(evt) {
            if (!evt || !evt.detail || !evt.detail.target) return;
            var id = evt.detail.target.id || '';
            if (id === 'rehearsals-content') {
                setTimeout(function() {
                    bindRehearsalDetailHandlers();
                    // Re-run the idempotent inline filter binders on the fresh nodes.
                    if (window.rebindRehearsalDetailFilters) {
                        window.rebindRehearsalDetailFilters();
                    }
                }, 0);
            } else if (id === 'events-content') {
                // Re-bind event detail handlers on the freshly swapped DOM.
                setTimeout(function() {
                    bindEventDetailHandlers();
                    // Also re-bind the inline filter handlers defined in this page.
                    // They are not exposed globally by windband-utils.js — call
                    // window-level wrappers we install below if present, else
                    // let DOMContentLoaded-time binding of the fresh node handle it.
                    if (window.rebindEventDetailFilters) {
                        window.rebindEventDetailFilters();
                    }
                }, 0);
            }
        }
        // Belt-and-suspenders: re-bind on BOTH afterSwap AND afterSettle so we
        // catch the swap whichever event fires first. HTMX dispatches both.
        document.body.addEventListener('htmx:afterSettle', scheduleRebindAfterSwap, true);
        document.body.addEventListener('htmx:afterSwap', scheduleRebindAfterSwap, true);

    })(window);


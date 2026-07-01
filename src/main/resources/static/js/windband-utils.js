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

    // Expose globally
    global.Toast = Toast;
    global.fetchWithToast = fetchWithToast;
    global.getCookie = getCookie;
})(window);

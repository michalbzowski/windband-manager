<#--
  Band Manager — Keycloak OTP Login Theme
  Two-factor authentication page, styled to match the main app.
-->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — Weryfikacja dwuetapowa</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<div class="login-card">
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">Weryfikacja dwuetapowa</p>
    </div>

    <h2>Podaj kod weryfikacyjny</h2>

    <#if auth.hasAuthenticationExecution && auth.authenticationExecution.helpText?has_content>
        <div class="info-box" role="status">
            <span>${msg(auth.authenticationExecution.helpText)}</span>
        </div>
    </#if>

    <#if message?has_content && message.type == 'error'>
        <div class="alert-error" role="alert">
            <p>${msg(message.summary)}</p>
        </div>
    </#if>

    <form id="kc-otp-form" action="${url.loginAction}" method="post">
        <div class="mb-3">
            <label for="totp" class="${properties.kcLabelClass!}">${msg('loginOtpOneTimeCode')}</label>
            <input type="text"
                   id="totp"
                   name="totp"
                   class="${properties.kcInputClass!}"
                   autocomplete="one-time-code"
                   autofocus
                   placeholder="123456"
                   aria-label="${msg('loginOtpOneTimeCode')}"/>
        </div>

        <#if user.loginOtpUserOtpCredentials?size gt 1>
            <div class="mb-3">
                <label for="totp-select" class="${properties.kcLabelClass!}">${msg('loginOtpOneTimeCodeCredential')}</label>
                <select id="totp-select" class="${properties.kcInputClass!}" name="selectedCredentialId">
                    <#list user.loginOtpUserOtpCredentials as credential>
                        <option value="${credential.id}"
                                <#if credential.id == auth.selectedCredential>selected</#if>>
                            ${credential.userLabel}
                        </option>
                    </#list>
                </select>
            </div>
        </#if>

        <#if auth.rememberMeEnabled??>
            <div class="remember-me-row mb-3">
                <#if auth.rememberMe??>
                    <input id="rememberMe" name="rememberMe" type="checkbox" checked>
                <#else>
                    <input id="rememberMe" name="rememberMe" type="checkbox">
                </#if>
                <label for="rememberMe">${msg('rememberMe')}</label>
            </div>
        </#if>

        <div class="mb-3">
            <button type="submit" tabindex="1"
                    class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">
                ${msg('doLogIn')}
            </button>
        </div>

        <div style="text-align:center; margin-top:0.75rem;">
            <a href="${url.loginUrl}">${msg('loginOtpTryAgain')}</a>
        </div>

        <input type="hidden" id="id-hidden-input" name="credentialId"/>
    </form>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>
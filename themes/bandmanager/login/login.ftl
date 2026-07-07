<#--
  Band Manager — Keycloak Login Theme
  Matches the main application's visual identity (purple accent, dark background, PicoCSS).
-->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — Logowanie</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<#if message?has_content && message.type == 'error'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-error" role="alert">
            <h3>Błąd logowania</h3>
            <p>${msg(message.summary)}</p>
            <#if message.detail?has_content>
                <p style="margin-top:0.5rem; font-size:0.8rem; opacity:0.8;">${msg(message.detail)}</p>
            </#if>
        </div>
    </div>
</#if>

<#if message?has_content && message.type == 'warning'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-warning" role="alert">
            <p>${msg(message.summary)}</p>
        </div>
    </div>
</#if>

<#if message?has_content && message.type == 'success'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-success" role="alert">
            <p>${msg(message.summary)}</p>
        </div>
    </div>
</#if>

<div class="login-card">
    <!-- Branding -->
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">Zaloguj się, aby kontynuować</p>
    </div>

    <h2>Logowanie</h2>

    <#if auth.passwordRequired?? && auth.passwordRequired == false>
        <#-- Passwordless / OTP-only mode -->
        <form id="kc-otp-login-form" action="${url.loginAction}" method="post">
            <div class="mb-3">
                <label for="otp" class="form-label">Kod jednorazowy</label>
                <input type="text" id="otp" name="otp" class="form-control"
                       autocomplete="one-time-code" autofocus
                       placeholder="123456"
                       aria-label="Kod jednorazowy"/>
            </div>
            <div class="mb-3">
                <button type="submit" class="btn-primary w-100" name="submit" id="kc-login-otp-btn">
                    Zaloguj
                </button>
            </div>
            <input type="hidden" id="id-hidden-input" name="credentialId"/>
        </form>
    </#if>

    <#if !auth.passwordRequired?? || auth.passwordRequired>
        <form id="kc-form-login"
              onsubmit="document.getElementById('${properties.kcButtonId!}').disabled=true;document.getElementById('${properties.kcButtonId!}').value='${msg('loggingIn')}';return true;"
              action="${url.loginAction}" method="post">

            <#if properties.kcFormGroupClass?has_content>
                <div class="${properties.kcFormGroupClass!}">
            </#if>

            <div class="mb-3">
                <label for="username" class="${properties.kcLabelClass!}">
                    <#if !realm.loginWithEmailAllowed>
                        ${msg('loginUsername')}
                    <#elseif !realm.registrationEmailAsUsername>
                        ${msg('loginUsernameOrEmail')}
                    <#else>
                        ${msg('loginEmail')}
                    </#if>
                </label>
                <input tabindex="1"
                       id="username"
                       class="${properties.kcInputClass!}"
                       name="username"
                       value="${(login.username!'')}"
                       type="text"
                       autofocus
                       autocomplete="username"
                       aria-label="${msg('loginUsername')}"/>
            </div>

            <div class="mb-3">
                <label for="password" class="${properties.kcLabelClass!}">${msg('loginPassword')}</label>
                <input tabindex="2"
                       id="password"
                       class="${properties.kcInputClass!}"
                       name="password"
                       type="password"
                       autocomplete="current-password"
                       aria-label="${msg('loginPassword')}"/>
            </div>

            <#if auth.rememberMeEnabled??>
                <div class="remember-me-row mb-3">
                    <#if auth.rememberMe??>
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" checked>
                    <#else>
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox">
                    </#if>
                    <label for="rememberMe">${msg('rememberMe')}</label>
                </div>
            </#if>

            <#if properties.kcFormGroupClass?has_content>
                </div>
            </#if>

            <div class="mb-3">
                <input tabindex="4"
                       class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                       name="submit"
                       id="kc-login-btn"
                       type="submit"
                       value="${msg('doLogIn')}"/>
            </div>

            <#if realm.resetPasswordAllowed>
                <div class="mb-3" style="text-align:center;">
                    <a tabindex="5" href="${url.loginResetCredentialsUrl}">${msg('doForgotPassword')}</a>
                </div>
            </#if>

            <#if realm.registrationAllowed>
                <div class="mb-3" style="text-align:center;">
                    <span>${msg('noAccount')} <a tabindex="6" href="${url.registrationUrl}">${msg('doRegister')}</a></span>
                </div>
            </#if>

            <input type="hidden" id="id-hidden-input" name="credentialId"
                   <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
        </form>
    </#if>

    <#if realm.internationalizationEnabled && locale?? && locale.supported??>
        <div class="locale-selector">
            <#list locale.supported as l>
                <a href="${l.url}" class="${properties.kcLocaleButtonClass!} ${properties.kcLocaleButtonPrimaryClass!}">${l.label}</a>
            </#list>
        </div>
    </#if>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>
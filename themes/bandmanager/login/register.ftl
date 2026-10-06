<#--
  Band Manager — Rejestracja (register page)
  URL: /realms/{realm}/login-actions/registration
  SAMOWYSTARCZALNY szablon: pola renderowane ręcznie (jak login-update-password.ftl),
  BO importowanie makr bazowych (user-profile-commons.ftl / register-commons.ftl)
  powoduje w Keycloak 26.6.cisnący fallback na nieostylowany szablon base.
--->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — ${msg("registerTitle")}</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<#if message?has_content && message.type == 'error'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-error" role="alert">
            <h3>${msg("errorTitle")}</h3>
            <p>${kcSanitize(message.summary)?no_esc}</p>
            <#if message.detail?has_content>
                <p style="margin-top:0.5rem; font-size:0.8rem; opacity:0.8;">${kcSanitize(message.detail)?no_esc}</p>
            </#if>
    </div>
</div>
</#if>

<#if message?has_content && message.type == 'warning'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-warning" role="alert">
            <p>${kcSanitize(message.summary)?no_esc}</p>
        </div>
    </div>
</#if>

<#if message?has_content && message.type == 'success'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-success" role="alert">
            <p>${kcSanitize(message.summary)?no_esc}</p>
        </div>
    </div>
</#if>

<div class="login-card">
    <!-- Branding -->
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <#if messageHeader??>
            <p class="brand-subtitle">${msg(messageHeader)}</p>
        <#else>
            <p class="brand-subtitle">${msg("doRegister")}</p>
        </#if>
    </div>

    <h2>${msg("registerTitle")}</h2>

    <form id="kc-register-form" action="${url.registrationAction}" method="post">

        <#-- Nazwa użytkownika (l. e-mail jako nazwa, zależnie od realmu) -->
        <div class="mb-3">
            <label for="username" tabindex="1" class="${properties.kcLabelClass!}">
                <#if !realm.loginWithEmailAllowed>
                    ${msg("loginUsername")}
                <#elseif !realm.registrationEmailAsUsername>
                    ${msg("loginUsernameOrEmail")}
                <#else>
                    ${msg("loginEmail")}
                </#if>
            </label>
            <input tabindex="2"
                   id="username"
                   name="username"
                   type="text"
                   class="${properties.kcInputClass!}"
                   value="${(auth.attemptedUsername!'')}"
                   autofocus
                   autocomplete="<#if realm.registrationEmailAsUsername>email<#else>username</#if>"
                   aria-label="${msg("loginUsername")}"/>
            <#if messagesPerField.existsError('username')>
                <span id="input-error-username" style="color:#ef4444; font-size:0.8rem;" aria-live="polite">
                    ${kcSanitize(messagesPerField.get('username'))?no_esc}
                </span>
            </#if>
        </div>

        <#-- Hasło -->
        <#if passwordRequired??>
            <div class="mb-3">
                <label for="password" class="${properties.kcLabelClass!}">${msg("password")} <span style="color:#ef4444;">*</span></label>
                <input tabindex="3"
                       id="password"
                       name="password"
                       type="password"
                       autocomplete="new-password"
                       aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                       class="${properties.kcInputClass!}"/>
                <#if messagesPerField.existsError('password')>
                    <span id="input-error-password" style="color:#ef4444; font-size:0.8rem;" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('password'))?no_esc}
                    </span>
                </#if>
            </div>

            <#-- Potwierdź hasło -->
            <div class="mb-3">
                <label for="password-confirm" class="${properties.kcLabelClass!}">${msg("passwordConfirm")} <span style="color:#ef4444;">*</span></label>
                <input tabindex="4"
                       id="password-confirm"
                       name="password-confirm"
                       type="password"
                       autocomplete="new-password"
                       aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                       class="${properties.kcInputClass!}"/>
                <#if messagesPerField.existsError('password-confirm')>
                    <span id="input-error-password-confirm" style="color:#ef4444; font-size:0.8rem;" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                    </span>
                </#if>
            </div>
        </#if>

        <#-- Akceptacja regulaminu (jeśli wymagana w realmie) -->
        <#if termsAcceptanceRequired?? && termsAcceptanceRequired>
            <div class="mb-3">
                <p style="margin-bottom:0.75rem;">${msg("termsTitle")}</p>
                <div id="kc-registration-terms-text" style="font-size:0.9rem; padding:1rem; background:rgba(0,0,0,0.3); border-radius:8px;">
                    ${kcSanitize(msg("termsText"))?no_esc}
                </div>
            </div>
            <div class="mb-3">
                <label style="display:inline-flex; align-items:center; gap:0.5rem;">
                    <input type="checkbox" id="termsAccepted" name="termsAccepted"
                           aria-invalid="<#if messagesPerField.existsError('termsAccepted')>true</#if>"/>
                    ${msg("acceptTerms")}
                </label>
            </div>
        </#if>

        <input type="hidden" id="id-hidden-input" name="credentialId"/>

        <div class="mb-3">
            <input tabindex="5"
                   class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                   name="submit" type="submit" value="${msg("doRegister")}"/>
        </div>

        <#if realm.registrationAllowed>
            <div class="mb-3" style="text-align:center;">
                <a href="${url.loginUrl}">${msg("backToLogin")}</a>
            </div>
        </#if>
    </form>

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
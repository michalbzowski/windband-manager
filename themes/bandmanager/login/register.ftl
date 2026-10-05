<#--
  Band Manager — Keycloak Login Theme
  Rejestracja (register page) — centered card, matches login.ftl visual identity.
  Fields are rendered by the base theme's userProfileFormFields macro so the
  template stays correct across Keycloak versions and user-profile configs.
-->
<#import "user-profile-commons.ftl" as userProfileCommons>
<#import "register-commons.ftl" as registerCommons>
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
        <p class="brand-subtitle">${msg("doRegister")}</p>
    </div>

    <h2>${msg("registerTitle")}</h2>

    <form id="kc-register-form" action="${url.registrationAction}" method="post">

        <@userProfileCommons.userProfileFormFields; callback, attribute>
            <#if callback == "afterField">
            <#-- render password fields just under the username or email (if used as username) -->
                <#if passwordRequired?? && (attribute.name == 'username' || (attribute.name == 'email' && realm.registrationEmailAsUsername))>
                    <div class="mb-3">
                        <label for="password">${msg("password")} <span class="required">*</span></label>
                        <input type="password" id="password" name="password"
                               autocomplete="new-password"
                               aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"/>
                        <#if messagesPerField.existsError('password')>
                            <span id="input-error-password" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('password'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <div class="mb-3">
                        <label for="password-confirm">${msg("passwordConfirm")} <span class="required">*</span></label>
                        <input type="password" id="password-confirm" name="password-confirm"
                               autocomplete="new-password"
                               aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"/>
                        <#if messagesPerField.existsError('password-confirm')>
                            <span id="input-error-password-confirm" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>
            </#if>
        </@userProfileCommons.userProfileFormFields>

        <@registerCommons.termsAcceptance/>

        <div class="mb-3">
            <input type="submit" id="kc-register-btn" name="submit" value="${msg("doRegister")}"/>
        </div>

        <div class="mb-3" style="text-align:center;">
            <span><a href="${url.loginUrl}">${msg("backToLogin")}</a></span>
        </div>
    </form>

    <#if realm.internationalizationEnabled && locale?? && locale.supported??>
        <div class="locale-selector">
            <#list locale.supported as l>
                <a href="${l.url}">${l.label}</a>
            </#list>
        </div>
    </#if>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>

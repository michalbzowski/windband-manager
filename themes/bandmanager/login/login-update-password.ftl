<#--
  Band Manager — Ustaw nowe hasło (login-update-password.ftl)
  URL: /realms/{realm}/login-actions/reset-password
  Kolejny krok po "Nie pamiętasz hasła?" — formularz z nowym hasłem i potwierdzeniem.
  Styl identyczny jak login.ftl.
-->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — Nowe hasło</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<div class="login-card">
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">Twoje konto</p>
    </div>

    <h2>${msg('updatePasswordTitle')}</h2>

    <#if message?has_content && message.type == 'error'>
        <div class="alert-error" role="alert" style="margin-bottom:1rem;">
            <p>${msg(message.summary)}</p>
            <#if message.detail?has_content>
                <p style="margin-top:0.5rem; font-size:0.8rem; opacity:0.8;">${msg(message.detail)}</p>
            </#if>
        </div>
    </#if>

    <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">
        <div class="mb-3">
            <label for="password-new" class="${properties.kcLabelClass!}">${msg('passwordNew')}</label>
            <input tabindex="1"
                   id="password-new"
                   name="password-new"
                   class="${properties.kcInputClass!}"
                   type="password"
                   autofocus
                   autocomplete="new-password"
                   aria-label="${msg('passwordNew')}"/>
        </div>

        <div class="mb-3">
            <label for="password-confirm" class="${properties.kcLabelClass!}">${msg('passwordConfirm')}</label>
            <input tabindex="2"
                   id="password-confirm"
                   name="password-confirm"
                   class="${properties.kcInputClass!}"
                   type="password"
                   autocomplete="new-password"
                   aria-label="${msg('passwordConfirm')}"/>
        </div>

        <#if messagesPerField.existsError('password')>
            <p style="color:#b91c1c; font-size:0.85rem;">${kcSanitize(messagesPerField.get('password'))?no_esc}</p>
        </#if>
        <#if messagesPerField.existsError('password-confirm')>
            <p style="color:#b91c1c; font-size:0.85rem;">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</p>
        </#if>

        <div class="mb-3">
            <input tabindex="3"
                   class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                   name="submit" type="submit" value="${msg('doSubmit')}"/>
        </div>

        <#if isAppInitiatedAction?? && isAppInitiatedAction>
            <div class="mb-3">
                <button tabindex="4" type="submit" name="cancel-aia" value="true"
                        style="width:100%; padding:0.6rem; background:transparent; color:#374151; border:1px solid #ccc; border-radius:8px; cursor:pointer;">
                    ${msg('doCancel')}
                </button>
            </div>
        </#if>

        <input type="hidden" id="id-hidden-input" name="credentialId"/>
    </form>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>

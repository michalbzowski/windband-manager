<#--
  Band Manager — Reset hasła / nowe hasło (login-reset-password.ftl)
  URL: /realms/{realm}/reset-credentials ("Nie pamiętasz hasła?")
  Formularz: nazwa użytkownika/e-mail + link do logowania.
  Styl identyczny jak login.ftl.
-->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — Reset hasła</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<#if message?has_content && message.type == 'error'>
    <div class="login-card" style="margin-top:3rem;">
        <div class="alert-error" role="alert">
            <h3>${msg('errorTitle')}</h3>
            <p>${msg(message.summary)}</p>
            <#if message.detail?has_content>
                <p style="margin-top:0.5rem; font-size:0.8rem; opacity:0.8;">${msg(message.detail)}</p>
            </#if>
        </div>
    </div>
</#if>

<div class="login-card">
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">Zaloguj się, aby kontynuować</p>
    </div>

    <h2>${msg('emailForgotTitle')}</h2>

    <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
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
                   name="username"
                   class="${properties.kcInputClass!}"
                   value="${(auth.attemptedUsername!'')}"
                   type="text"
                   autofocus
                   autocomplete="off"
                   aria-label="${msg('loginUsername')}"/>
        </div>

        <p style="color:var(--pico-muted-color); font-size:0.85rem; margin-bottom:1rem;">
            <#if realm.duplicateEmailsAllowed>
                ${msg('emailInstructionUsername')}
            <#else>
                ${msg('emailInstruction')}
            </#if>
        </p>

        <div class="mb-3">
            <input tabindex="2"
                   class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                   name="login" id="kc-login" type="submit" value="${msg('doSubmit')}"/>
        </div>

        <div class="mb-3" style="text-align:center;">
            <a tabindex="3" href="${url.loginUrl}">${kcSanitize(msg('backToLogin'))?no_esc}</a>
        </div>

        <input type="hidden" id="id-hidden-input" name="credentialId"/>
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

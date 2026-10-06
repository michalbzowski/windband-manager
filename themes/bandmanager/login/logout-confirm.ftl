<#--
  Band Manager — Potwierdzenie wylogowania (logout confirmation)
  Serwisowane przez Keycloak (POST logoutConfirm -> /logout/confirm).
  SAMOWYSTARCZALNY: ręczny button + guardy ??, żadnego <#import "…-commons.ftl"> —
  bo na KC 26 import makr bazowych gubi stylowanie i wymusza fallback do base.
--->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — ${msg("logoutConfirmTitle")}</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<div class="login-card">
    <!-- Brand -->
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">${msg("logoutConfirmHeader")}</p>
    </div>

    <h2>${msg("logoutConfirmTitle")}</h2>

    <form action="${url.logoutConfirmAction}" method="post">
        <input type="hidden" name="session_code" value="${(logoutConfirm.code)}"/>
        <div class="mb-3">
            <button type="submit"
                    name="confirmLogout"
                    class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">
                ${msg("doLogout")}
            </button>
        </div>

        <#-- "Powrót do aplikacji": atrybut baseUrl klienta (jeśli zawarty) → fallback na domenę app -->
        <#assign backUrl = "https://app.bandmanager.pl/">
        <#assign backAttr = (client.attributes.baseUrl)!"">
        <#if backAttr?has_content>
          <#assign backUrl = backAttr>
        </#if>
        <p style="text-align:center; margin-top:1rem; margin-bottom:0;">
            <a href="${backUrl}" rel="noopener">
                ${msg("backToApplication")}
            </a>
        </p>
    </form>

    <#-- JEDNA lista języków — bez przycisku z podwojonym bieżącym językiem (patrz zgłoszenie) -->
    <#if realm.internationalizationEnabled && locale?? && locale.supported??>
        <div class="locale-selector">
            <#list locale.supported as l>
                <a href="${l.url}" class="${properties.kcLocaleButtonClass!} ${properties.kcLocaleLinkClass!}">${l.label}</a>
            </#list>
        </div>
    </#if>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>

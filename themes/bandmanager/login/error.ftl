<#--
  Band Manager — Keycloak Error Theme
  Shown when an authentication error occurs.
-->
<!DOCTYPE html>
<html lang="${locale.locale}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — Błąd</title>
    <link rel="stylesheet" href="${url.resourcesCommonPath}/node_modules/patternfly/patternfly/patternfly.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<div class="login-card" style="margin-top:3rem;">
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">Coś poszło nie tak</p>
    </div>

    <h2 style="color:#ef4444;">⚠️ Błąd</h2>

    <#if error?has_content>
        <div class="alert-error" role="alert">
            <p>${msg('${error}')}</p>
        </div>
    </#if>

    <#if client??>
        <div style="text-align:center; margin-top:1.25rem;">
            <a href="${client.baseUrl}" class="btn btn-secondary">
                ← Powrót do ${client.clientName}
            </a>
        </div>
    </#if>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>
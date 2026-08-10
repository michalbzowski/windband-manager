<!--
  Band Manager — Keycloak Logout Success Theme
  Shown after user logs out successfully.
-->
<#assign htmlLang = (locale.currentLanguageTag)!"pl">
<!DOCTYPE html>
<html lang="${htmlLang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Band Manager — Wylogowano</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/pico.min.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/custom.css">
</head>
<body>

<div class="login-card">
    <div class="brand-header">
        <h1>🎵 Band Manager</h1>
        <p class="brand-subtitle">Wylogowano pomyślnie</p>
    </div>

    <h2 style="color:#86efac; margin-bottom:1.5rem;">✅ Jesteś wylogowany</h2>

    <!-- Jeśli user jest z aplikacji windband-manager, pokazujemy link powrotny -->
    <#if client?? && client.clientName??>
        <div style="text-align:center; margin-top:1.25rem;">
            <a href="${client.baseUrl!'/'}" class="btn btn-secondary">← Powrót do ${client.clientName}</a>
        </div>
    </#if>

    <!-- Fallback message -->
    <p style="text-align:center; color: var(--pico-muted-color); margin-top:1.25rem;">Do zobaczenia!</p>
</div>

<div class="login-footer">
    Band Manager &copy; 2025
</div>

</body>
</html>

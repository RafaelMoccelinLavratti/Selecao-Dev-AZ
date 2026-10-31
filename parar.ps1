# ============================================================================
# PARAR O PROJETO
# ============================================================================
#
# Derruba a API e o frontend que o "iniciar.ps1" subiu.
#
# O PostgreSQL NAO e' derrubado: ele guarda os dados, e derrubar e' subir
# de novo e nao vale o trabalho. Se quiser derrubar tambem, use:
#
#     C:\Users\Rafael\Documents\AZ\_tools\pgsql\bin\pg_ctl.exe `
#         -D C:\Users\Rafael\Documents\AZ\_tools\pgdata stop
#
# ============================================================================

$ErrorActionPreference = "SilentlyContinue"

# As portas que indicatem o que derrubar.
$Portas = @{
    8081 = "API (Spring Boot)"
    8080 = "Frontend (Vue)"
}

foreach ($porta in $Portas.Keys) {

    $conexoes = Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue

    if (-not $conexoes) {
        Write-Host "  [OK]   $($Portas[$porta]) nao esta rodando" -ForegroundColor DarkGreen
        continue
    }

    $pids = $conexoes | Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($processId in $pids) {
        $info = Get-Process -Id $processId -ErrorAction SilentlyContinue
        $nome = if ($info) { $info.ProcessName } else { "?" }

        # No Windows, o Maven e o npm sao lancados por cmd.exe. E' preciso
        # derrubar a arvore inteira, senao o processo "java" ou "node"
        # continua vivo segurando a porta.
        Write-Host "  Derrubando $($Portas[$porta]) (pid $processId, $nome)..." -ForegroundColor Yellow

        taskkill /PID $processId /T /F 2>&1 | Out-Null
    }

    Start-Sleep -Seconds 1

    if (Test-NetConnection -ComputerName localhost -Port $porta -InformationLevel Quiet -WarningAction SilentlyContinue) {
        Write-Host "  [ERRO] $($Portas[$porta]) continua na porta $porta" -ForegroundColor Red
    } else {
        Write-Host "  [OK]   $($Portas[$porta]) parado" -ForegroundColor DarkGreen
    }
}

Write-Host ""
Write-Host "  O PostgreSQL continua no ar (porta 5432), com os dados." -ForegroundColor DarkGray

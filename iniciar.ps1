# ============================================================================
# INICIAR O PROJETO
# ============================================================================
#
# Sobe o banco, a API e o frontend, com a configuracao ja ajustada para
# esta maquina (JDK 11 e o PostgreSQL local, sem Docker).
#
# Uso:
#     .\iniciar.ps1              -> sobe tudo
#     .\iniciar.ps1 -SemLogin    -> sobe sem exigir login na tela
#
# Para derrubar tudo:
#     .\parar.ps1
#
# ============================================================================

param(
    # Sem Login: a API sobe com a autenticacao desligada (o enunciado pede
    # que o avaliador navegue sem login). Com Login (padrao), a tela de
    # login aparece e as credenciais estao na tela.
    [switch]$SemLogin,

    # Nao abre o navegador ao terminar.
    [switch]$SemNavegador
)

$ErrorActionPreference = "Stop"

# Pasta do projeto (a raiz do repositorio).
$Raiz = Split-Path -Parent $MyInvocation.MyCommand.Path

# Onde estao as ferramentas nesta maquina.
$Maven   = Join-Path (Split-Path -Parent $Raiz) "_tools\apache-maven-3.9.16\bin\mvn.cmd"
$PgBin   = Join-Path (Split-Path -Parent $Raiz) "_tools\pgsql\bin"
$PgData  = Join-Path (Split-Path -Parent $Raiz) "_tools\pgdata"

# O projeto e' Spring Boot 2.7 / Java 11. O Lombok que vem com ele nao
# compila no JDK 21 (erro "NoSuchFieldError: JCImport.qualid"), entao a
# variavel JAVA_HOME e' apontada para o 11 aqui, so nesta sessao.
$JavaHome = "C:\Program Files\Microsoft\jdk-11.0.32.101-hotspot"

function Escrever-Cabecalho {
    # Em funcao, o primeiro argumento vem em "$args[0]" - o "$1" so'
    # funciona em script.
    Write-Host ""
    Write-Host "============================================" -ForegroundColor DarkCyan
    Write-Host " $($args[0])" -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor DarkCyan
}

function Conferir-Porta {
    param([int]$Porta, [string]$Nome)

    $emUso = Test-NetConnection -ComputerName localhost -Port $Porta -InformationLevel Quiet -WarningAction SilentlyContinue

    if ($emUso) {
        Write-Host "  [OK]   $Nome ja' esta no ar (porta $Porta)" -ForegroundColor DarkGreen
        return $true
    }

    return $false
}

# ----------------------------------------------------------------------------
# 1) Java
# ----------------------------------------------------------------------------
Escrever-Cabecalho "1/4  Java"

if (-not (Test-Path $JavaHome)) {
    Write-Host "  [ERRO] JDK 11 nao encontrado em $JavaHome" -ForegroundColor Red
    Write-Host "         Instale o JDK 11 ou ajuste a variavel `$JavaHome neste script." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

Write-Host "  [OK]   JAVA_HOME = $JavaHome" -ForegroundColor DarkGreen

# O "java -version" escreve no stderr. O "2>&1" redireciona, mas o
# PowerShell ainda marca como erro; o "$ErrorActionPreference = SilentlyContinue"
# evita que isso interrompa o script.
$ErrorActionPreference = "SilentlyContinue"
$versaoJava = & "$JavaHome\bin\java.exe" -version 2>&1 | Select-Object -First 1
$ErrorActionPreference = "Stop"
Write-Host "         $versaoJava" -ForegroundColor DarkGray

# ----------------------------------------------------------------------------
# 2) Banco de dados
# ----------------------------------------------------------------------------
Escrever-Cabecalho "2/4  PostgreSQL"

$bancoNoAr = Conferir-Porta -Porta 5432 -Nome "PostgreSQL"

if (-not $bancoNoAr) {
    Write-Host "  Subindo o PostgreSQL local..." -ForegroundColor Yellow

    $log = Join-Path $env:TEMP "az-leilao-postgres.log"

    # O "pg_ctl start" e' disparado sem esperar: o "-Wait" segura o
    # script indefinidamente, porque o postgres-server filho herda os
    # handles do processo. O banco ja' fica no ar em menos de 2
    # segundos, e a checagem de porta logo abaixo confirma.
    $processoPg = Start-Process -FilePath (Join-Path $PgBin "pg_ctl.exe") `
        -ArgumentList @("-D", $PgData, "-l", $log, "start") `
        -PassThru -WindowStyle Hidden

    # Espera o banco ficar de pe (ou desistir).
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        if (Test-NetConnection -ComputerName localhost -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue) {
            break
        }
    }

    $bancoNoAr = Conferir-Porta -Porta 5432 -Nome "PostgreSQL"

    if (-not $bancoNoAr) {
        Write-Host "  [ERRO] O PostgreSQL nao subiu. Veja o log em $log" -ForegroundColor Red
        Get-Content $log -Tail 10 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "         $_" -ForegroundColor DarkGray }
        exit 1
    }
}

# Garante que o schema e a massa de dados existam.
$psql = Join-Path $PgBin "psql.exe"
$env:PGPASSWORD = "postgres"

# O psql e' chamado com o output capturado em arquivo, e nao pelo
# pipeline: pelo pipeline, o processo filho herda os handles e o script
# pode ficar esperando.
$saidaPsql = Join-Path $env:TEMP "az-leilao-psql.out.log"

$temTabelas = (& $psql -h localhost -U postgres -d az -tAc "SELECT count(*) FROM information_schema.tables WHERE table_name = 'tb_empresa'" 2>$null | Out-String).Trim()

if ($temTabelas -ne "1") {
    Write-Host "  Aplicando o schema (01_ddl.sql) e a massa de dados (02_dml.sql)..." -ForegroundColor Yellow

    $sql = Join-Path $Raiz "leilao-api\src\main\resources\sql"
    & $psql -h localhost -U postgres -d az -v ON_ERROR_STOP=1 -f (Join-Path $sql "01_ddl.sql") *> $saidaPsql
    & $psql -h localhost -U postgres -d az -v ON_ERROR_STOP=1 -f (Join-Path $sql "02_dml.sql") *>> $saidaPsql

    Write-Host "  [OK]   Schema aplicado" -ForegroundColor DarkGreen
} else {
    Write-Host "  [OK]   Schema ja' existe" -ForegroundColor DarkGreen
}

$total = (& $psql -h localhost -U postgres -d az -tAc "SELECT (SELECT count(*) FROM tb_empresa) || ' empresas, ' || (SELECT count(*) FROM tb_leilao) || ' leiloes, ' || (SELECT count(*) FROM tb_lote) || ' lotes'" 2>$null | Out-String).Trim()
Write-Host "  [OK]   $total" -ForegroundColor DarkGreen

# ----------------------------------------------------------------------------
# 3) API
# ----------------------------------------------------------------------------
Escrever-Cabecalho "3/4  API (Spring Boot, porta 8081)"

if (Conferir-Porta -Porta 8081 -Nome "API") {
    Write-Host "         Ja' existe uma API rodando. Se ela estiver desatualizada, derrube com .\parar.ps1" -ForegroundColor Yellow
} else {
    $env:DATABASE_URL = "jdbc:postgresql://localhost:5432/az"
    $env:DATABASE_USER = "postgres"
    $env:DATABASE_PASSWORD = "postgres"

    if ($SemLogin) {
        $env:SECURITY_ENABLED = "false"
        Write-Host "  Modo: SEM LOGIN (rotas abertas)" -ForegroundColor Yellow
    } else {
        $env:SECURITY_ENABLED = "true"
        $env:SECURITY_MODE = "jwt"
        $env:SECURITY_USERNAME = "admin"
        $env:SECURITY_PASSWORD = "SenhaForteAdmin123"
        Write-Host "  Modo: COM LOGIN (a tela de login aparece)" -ForegroundColor Cyan
        Write-Host "  Credenciais da empresa: admin.aurora / Senha@1" -ForegroundColor DarkGray
    }

    # A chave que fecha a trilha de auditoria.
    #
    # Sem ela, a aplicacao gera uma chave aleatoria na subida. O
    # funcionamento fica correto, mas a trilha gravada antes de um
    # reinicio nao da mais para verificar - e a API avisa isso com
    # todas as letras.
    #
    # Aqui a chave e' FIXA de proposito: e' o mesmo desenvolvimento de
    # sempre, a mesma senha do admin, e e' assim que a trilha sobrevive
    # ao reinicio. Em producao, essa chave vem de um secret do
    # orquestrador - nunca de um arquivo no Git.
    #
    # 64 caracteres hex = 32 bytes, o minimo que a OWASP pede para
    # uma chave HMAC.
    if (-not $env:AUDIT_HMAC_KEY) {
        $env:AUDIT_HMAC_KEY = "9f2c4a7b1e8d5f3a6c9b2e4d7a1f8b5c3e9d6a2f7b4c1e8d3a6f9b2c5e7d1a4f8b"
        Write-Host "  Chave HMAC da auditoria: a de desenvolvimento (a trilha sobrevive ao reinicio)" -ForegroundColor DarkGray
    }

    # A trilha so verifica se os registros foram selados com a MESMA
    # chave. Quando a chave muda (ou quando e' a primeira vez), os
    # registros antigos nao batem - e a verificacao acusaria
    # "adulteracao" onde nao houve.
    #
    # Numa auditoria real, a chave NUNCA muda: e' por isso que a
    # adulteracao e' detectavel. Em desenvolvimento, a chave pode
    # mudar - e ai o certo e recomecar a trilha, com o aviso claro.
    $ArqEstadoChave = Join-Path $PSScriptRoot ".audit-hmac.state"
    $ChaveDoArquivo = ""

    if (Test-Path $ArqEstadoChave) {
        $ChaveDoArquivo = (Get-Content $ArqEstadoChave -Raw -ErrorAction SilentlyContinue).Trim()
    }

    if ($ChaveDoArquivo -ne $env:AUDIT_HMAC_KEY) {
        Write-Host "  A trilha de auditoria vai recomecar (a chave HMAC mudou)" -ForegroundColor Yellow
        $Psql = Join-Path (Split-Path -Parent $PSScriptRoot) "_tools\pgsql\bin\psql.exe"
        if (-not (Test-Path $Psql)) {
            $Psql = "C:\Users\Rafael\Documents\AZ\_tools\pgsql\bin\psql.exe"
        }

        $env:PGPASSWORD = "postgres"
        try {
            & $Psql -h 127.0.0.1 -U postgres -d az -c "TRUNCATE TABLE tb_auditoria RESTART IDENTITY;" 2>&1 | Out-Null
            Write-Host "  Trilha limpa. Os registros novos serao selados com a chave atual." -ForegroundColor DarkGray
        } catch {
            Write-Host "  (nao deu para limpar a trilha - a API vai avisar que a chave e' efemera)" -ForegroundColor DarkGray
        }

        Set-Content -Path $ArqEstadoChave -Value $env:AUDIT_HMAC_KEY -Encoding ascii -NoNewline
    } else {
        Write-Host "  Trilha de auditoria preservada (a chave HMAC nao mudou)" -ForegroundColor DarkGray
    }

    # O Maven fica em uma janela propria, para o log ficar limpo e o
    # processo poder ser derrubado com "parar.ps1".
    $apiLog = Join-Path $env:TEMP "az-leilao-api.log"

    $Argumentos = @("spring-boot:run")
    if (-not $SemLogin) {
        # As variaveis ja' estao no ambiente deste processo, que e'
        # herdado pelo processo filho.
    }

    $processo = Start-Process -FilePath $Maven `
        -ArgumentList $Argumentos `
        -WorkingDirectory (Join-Path $Raiz "leilao-api") `
        -RedirectStandardOutput $apiLog `
        -RedirectStandardError (Join-Path $env:TEMP "az-leilao-api.err.log") `
        -PassThru `
        -WindowStyle Minimized

    Write-Host "  [OK]   API subindo (pid $($processo.Id)). Log: $apiLog" -ForegroundColor DarkGreen
    Write-Host "         Log em janela propria, ou veja: Get-Content $apiLog -Wait" -ForegroundColor DarkGray
}

# ----------------------------------------------------------------------------
# 4) Frontend
# ----------------------------------------------------------------------------
Escrever-Cabecalho "4/4  Frontend (Vue, porta 8080)"

if (Conferir-Porta -Porta 8080 -Nome "Frontend") {
    Write-Host "         Ja' existe um frontend rodando." -ForegroundColor Yellow
} else {
    $frontLog = Join-Path $env:TEMP "az-leilao-front.log"

    $npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source

    $processoFront = Start-Process -FilePath $npm `
        -ArgumentList @("run", "serve") `
        -WorkingDirectory (Join-Path $Raiz "leilao-app") `
        -RedirectStandardOutput $frontLog `
        -RedirectStandardError (Join-Path $env:TEMP "az-leilao-front.err.log") `
        -PassThru `
        -WindowStyle Minimized

    Write-Host "  [OK]   Frontend subindo (pid $($processoFront.Id)). Log: $frontLog" -ForegroundColor DarkGreen
}

# ----------------------------------------------------------------------------
# 5) Aguarda e mostra os enderecos
# ----------------------------------------------------------------------------
Escrever-Cabecalho "Pronto"

Write-Host "  Aguardando a API subir (ate 90 segundos)..." -ForegroundColor DarkGray

$pronto = $false
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 1
    if (Test-NetConnection -ComputerName localhost -Port 8081 -InformationLevel Quiet -WarningAction SilentlyContinue) {
        $pronto = $true
        break
    }
}

if ($pronto) {
    Write-Host ""
    Write-Host "  API      http://localhost:8081" -ForegroundColor White
    Write-Host "  Swagger  http://localhost:8081/swagger-ui.html" -ForegroundColor White
    Write-Host "  Frontend http://localhost:8080" -ForegroundColor White
    Write-Host ""

    if ($SemLogin) {
        Write-Host "  Entre em http://localhost:8080 e navegue direto." -ForegroundColor Cyan
    } else {
        Write-Host "  Entre em http://localhost:8080 e faca login com:" -ForegroundColor Cyan
        Write-Host "      admin.aurora / Senha@1   (empresa, ve so os proprios dados)" -ForegroundColor White
        Write-Host "      admin      / SenhaForteAdmin123   (administrador, ve tudo)" -ForegroundColor White
    }

    if (-not $SemNavegador) {
        Start-Process "http://localhost:8080"
    }
} else {
    Write-Host ""
    Write-Host "  A API nao respondeu a tempo. Veja o log:" -ForegroundColor Red
    Write-Host "      Get-Content $env:TEMP\az-leilao-api.log -Tail 40" -ForegroundColor Yellow
}

# Mostra o banco de dados de um jeito legivel, sem depender de
# interface grafica.
#
# Por que um script e' melhor que o "\d" do psql na hora da
# apresentacao:
#
#   1. O "\d" desenha os cabecalhos em portugues ("Ordenacao", "Pode
#      ser nulo") traduzidos pelo locale do Windows, que e' CP850. O
#      resultado e' "Ordena?a?o" - lixo na tela, exatamente na hora em
#      que voce esta' mostrando pro avaliador.
#   2. As consultas SQL aqui tem os rotulos em portugues no proprio
#      SQL, entao sai certo em qualquer terminal.
#   3. Nao precisa de DBeaver nem pgAdmin instalado.
#
# Uso:
#   .\scripts\mostrar-banco.ps1              # tudo
#   .\scripts\mostrar-banco.ps1 -Parte tabelas

param(
    [ValidateSet('tudo', 'tabelas', 'estrutura', 'dados', 'auditoria', 'constraint')]
    [string]$Parte = 'tudo'
)

$ErrorActionPreference = 'Stop'

$Psql = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '_tools\pgsql\bin\psql.exe'

if (-not (Test-Path $Psql)) {
    $Psql = 'C:\Users\Rafael\Documents\AZ\_tools\pgsql\bin\psql.exe'
}

$env:PGPASSWORD = 'postgres'
$env:PGCLIENTENCODING = 'UTF8'

function Invoke-Sql {
    param([string]$Comando)

    # O separador de campo " | " e' a virgula de dentro das aspas.
    $saida = & $Psql -h 127.0.0.1 -U postgres -d az -t -A -F ' | ' -c $Comando 2>&1
    return ($saida | Out-String).TrimEnd()
}

function Write-Titulo {
    param([string]$Texto)

    Write-Host ''
    Write-Host ('=' * 72)
    Write-Host "  $Texto"
    Write-Host ('=' * 72)
}

if ($Parte -eq 'tudo' -or $Parte -eq 'tabelas') {
    Write-Titulo 'AS TABELAS DO BANCO'
    Write-Host ''
    Write-Host (Invoke-Sql @'
SELECT '  ' || table_name
    || '  (' || (SELECT count(*) FROM information_schema.columns c
                  WHERE c.table_name = t.table_name AND c.table_schema = 'public')
    || ' colunas, '
    || (SELECT count(*) FROM pg_indexes i
         JOIN pg_class c2 ON c2.relname = i.tablename
        WHERE i.tablename = t.table_name AND c2.relkind = 'r')
    || ' indices)'
  FROM information_schema.tables t
 WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
 ORDER BY t.table_name
'@)

    Write-Host ''
    Write-Host '  --- QUANTOS REGISTROS TEM CADA UMA ---'
    Write-Host ''
    foreach ($t in @('tb_unidade', 'tb_empresa', 'tb_leilao', 'tb_lote', 'tb_comprador',
            'tb_refresh_token', 'tb_auditoria', 'tb_contador_seguranca')) {
        try {
            $n = Invoke-Sql "SELECT count(*) FROM $t"
            Write-Host ("  {0,-26} {1}" -f $t, $n)
        } catch {
            Write-Host ("  {0,-26} (nao existe)" -f $t)
        }
    }
}

if ($Parte -eq 'tudo' -or $Parte -eq 'estrutura') {
    Write-Titulo 'A ESTRUTURA DO tb_empresa'
    Write-Host ''
    Write-Host (Invoke-Sql @'
SELECT '  ' || rpad(column_name, 18)
    || ' ' || rpad(
           CASE data_type
               WHEN 'character varying' THEN 'varchar(' || character_maximum_length || ')'
               WHEN 'timestamp without time zone' THEN 'timestamp'
               ELSE data_type
           END, 26)
    || CASE WHEN is_nullable = 'NO' THEN ' NOT NULL' ELSE '' END
    || CASE WHEN column_default IS NOT NULL
            THEN '  (' || replace(replace(column_default, 'nextval(''', ''), '''::regclass)', '') || ')'
            ELSE '' END
  FROM information_schema.columns
 WHERE table_name = 'tb_empresa' AND table_schema = 'public'
 ORDER BY ordinal_position
'@)

    Write-Host ''
    Write-Host '  --- AS CHAVES (as que importam; o Postgres cria um NOT NULL por coluna) ---'
    Write-Host ''
    Write-Host (Invoke-Sql @'
SELECT '  ' || tc.constraint_type || ': ' || tc.constraint_name
    || CASE WHEN tc.constraint_type = 'FOREIGN KEY'
            THEN ' (' || kcu.column_name || ' -> '
                 || ccu.table_name || '.' || ccu.column_name || ')'
            WHEN tc.constraint_type = 'UNIQUE'
            THEN ' (' || kcu.column_name || ')'
            ELSE '' END
  FROM information_schema.table_constraints tc
  LEFT JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
  LEFT JOIN information_schema.constraint_column_usage ccu
    ON tc.constraint_name = ccu.constraint_name
 WHERE tc.table_name = 'tb_empresa' AND tc.table_schema = 'public'
   AND tc.constraint_type <> 'CHECK'
   AND tc.constraint_name NOT LIKE '%_not_null'
 ORDER BY tc.constraint_type, tc.constraint_name
'@)
}

if ($Parte -eq 'tudo' -or $Parte -eq 'dados') {
    Write-Titulo 'UMA AMOSTRA DOS DADOS'
    Write-Host ''
    Write-Host '  --- empresas (id, razao social, cnpj, usuario) ---'
    Write-Host ''
    Write-Host (Invoke-Sql @'
SELECT '  ' || rpad(id::text, 5)
    || ' ' || rpad(left(razao_social, 32), 33)
    || ' ' || rpad(cnpj, 19)
    || ' ' || usuario
  FROM tb_empresa ORDER BY id LIMIT 6
'@)

    Write-Host ''
    Write-Host '  --- leiloes (id, codigo, descricao, total dos lotes) ---'
    Write-Host ''
    Write-Host (Invoke-Sql @'
SELECT '  ' || rpad(l.id::text, 5)
    || ' ' || rpad(l.codigo::text, 7)
    || ' ' || rpad(left(l.descricao, 30), 31)
    || ' ' || COALESCE((SELECT SUM(lo.quantidade * lo.valor_inicial)
                         FROM tb_lote lo WHERE lo.leilao = l.id), 0)
  FROM tb_leilao l ORDER BY l.id LIMIT 6
'@)
}

if ($Parte -eq 'tudo' -or $Parte -eq 'constraint') {
    Write-Titulo 'A CONSTRAINT QUE IMPORTA (o ON DELETE RESTRICT)'
    Write-Host ''
    Write-Host (Invoke-Sql @'
SELECT '  ' || c.relname || '  ->  ' || pg_get_constraintdef(con.oid)
  FROM pg_constraint con
  JOIN pg_class c ON c.oid = con.conrelid
 WHERE c.relname = 'tb_leilao' AND con.contype = 'f'
'@)
    Write-Host ''
    Write-Host "  O 'RESTRICT' e' deliberado: apagar a vendedora quebraria o"
    Write-Host '  historico dos leiloes que ela vendeu.'
}

if ($Parte -eq 'tudo' -or $Part -eq 'auditoria' -or $Parte -eq 'auditoria') {
    Write-Titulo 'A TRILHA DE AUDITORIA (a prova de que nao foi adulterada)'
    Write-Host ''
    try {
        Write-Host (Invoke-Sql @'
SELECT '  ' || rpad(sequencia::text, 6)
    || ' ' || rpad(acao, 8)
    || ' ' || rpad(usuario, 14)
    || ' ' || rpad(status_http::text, 6)
    || ' ' || substr(hash_anterior, 1, 16) || '...'
  FROM tb_auditoria ORDER BY sequencia DESC LIMIT 5
'@)
    } catch {
        Write-Host '  (a tabela de auditoria ainda nao tem registros)'
    }

    Write-Host ''
    Write-Host "  --- a cadeia esta' intacta? (a API responde isto) ---"
    Write-Host ''
    Write-Host '  O endpoint exige admin - e certo: a resposta diz quantos registros'
    Write-Host '  a trilha tem e quais usuarios a usaram.'
    Write-Host ''
    try {
        # Login como admin para pegar o token.
        $login = (curl.exe -s -X POST http://localhost:8081/auth/login `
            -H 'Content-Type: application/json' `
            -d '{\"usuario\":\"admin\",\"senha\":\"SenhaForteAdmin123\"}' 2>&1 | Out-String)

        $token = ($login | ConvertFrom-Json).tokenAcesso

        if ($token) {
            $r = (curl.exe -s -H "Authorization: Bearer $token" `
                http://localhost:8081/auditoria/verificar 2>&1 | Out-String).Trim()
            Write-Host "  $r"
        } else {
            Write-Host '  (nao deu para obter o token - a API esta no ar?)'
        }
    } catch {
        Write-Host '  (a API precisa estar no ar)'
    }
}

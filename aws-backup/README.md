# Backup na AWS EC2

Esta pasta concentra scripts simples para **enviar** e **baixar** o backup do acervo (PDFs e, opcionalmente, dump do banco MySQL/MariaDB) usando sua instância **EC2 da AWS Academy**.

## 1. Configuração (uma única vez)

```bash
cd aws-backup
cp backup.env.example backup.env
nano backup.env
```

Preencha com o IP público da sua EC2 e o caminho da chave `.pem`. Se ainda não ajustou a permissão da chave:

```bash
chmod 400 ~/Downloads/labsuser.pem
```

## 2. Enviar backup para a EC2

```bash
./enviar-backup.sh
```

O script envia todos os PDFs de `../storage/` para `/home/admin/backup-repositorio/arquivos/` na EC2 (e o `.sql.gz` em `/banco/` se `BACKUP_DB=true`).

## 3. Baixar backup da EC2

```bash
./baixar-backup.sh
```

Os arquivos são baixados em `aws-backup/restaurado/` (ou na pasta que você passar como argumento, ex.: `./baixar-backup.sh ../storage`).

## Alternativa rápida (1 comando manual, sem script)

**Copiar pasta para a EC2:**
```bash
scp -i ~/Downloads/labsuser.pem -r ./storage admin@IP_DA_EC2:/home/admin/backup-repositorio
```

**Baixar pasta da EC2:**
```bash
scp -i ~/Downloads/labsuser.pem -r admin@IP_DA_EC2:/home/admin/backup-repositorio ./backup-baixado
```

# Pasta de envio de livros / PDFs para a EC2

1. Copie ou arraste seus arquivos `.pdf` para dentro desta pasta (`livros/`).
2. *(Opcional)* Se quiser definir título, autores, curso, orientador, ano, tipo ou resumo personalizados para cada arquivo, preencha o arquivo `livros/catalogo.csv`.
   - Se não preencher o CSV, o script usa automaticamente o nome do arquivo como título, cria/usa o curso padrão (`Acervo Geral`) e orientador padrão (`Coordenação do Acervo`), e publica o PDF direto no acervo!
3. Execute o script de envio:
   - **Windows:** `.\ec2\enviar-livros.bat`
   - **Linux / Mac:** `./ec2/enviar-livros.sh`
   - *(Para testar contra o servidor rodando localmente: `.\ec2\enviar-livros.bat -Url http://localhost:8080`)*

> PDFs duplicados (mesmo hash SHA-256) são detectados e pulados automaticamente — você pode rodar o script quantas vezes quiser sem duplicar arquivos.

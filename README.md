# qrSerial

Aplicativo web para reunir números de série de produtos e gerar uma folha A4 com o texto de cada serial e seu QR Code. Permite visualizar, imprimir pelo navegador ou baixar o resultado em PDF.

A interface funciona no computador e no celular pelo navegador, sem precisar instalar um aplicativo Android ou utilizar Node.js.

## Requisitos

- JDK 17 ou superior, compatível com a versão do Spring Boot declarada no `pom.xml`.
- Maven instalado e disponível no terminal.
- Navegador atualizado.
- Leitor de códigos configurado como teclado, caso queira automatizar a entrada. Também é possível digitar os seriais manualmente.

Na primeira execução, o Maven precisa de acesso à internet para baixar as dependências.

## Como executar

Na pasta do projeto, execute:

```bash
mvn spring-boot:run
```

Com o servidor iniciado, abra:

```text
http://localhost:8080
```

Outra opção é executar a classe `QrSerialApplication` pela IDE. Para encerrar a execução no terminal, pressione `Ctrl+C`.

### Executar como arquivo JAR

Gere o pacote:

```bash
mvn clean package
```

Execute o arquivo gerado:

```bash
java -jar target/qrSerial-0.0.1-SNAPSHOT.jar
```

### Acessar pelo celular

1. Conecte o celular e o computador à mesma rede.
2. No Windows, execute `ipconfig` e identifique o endereço IPv4 da conexão utilizada.
3. No navegador do celular, abra `http://IP-DO-COMPUTADOR:8080`. Por exemplo: `http://192.168.1.100:8080`.
4. Mantenha o computador ligado e o aplicativo em execução. Caso necessário, permita o acesso à porta 8080 no firewall da rede privada.

No celular, `localhost` aponta para o próprio celular; utilize o IP do computador que executa o servidor. O aplicativo não possui autenticação, portanto utilize uma rede de confiança.

## Como utilizar

1. Clique ou toque no campo **Serial**.
2. Digite o número de série ou faça a leitura com um leitor configurado como teclado.
3. Pressione **Enter** ou selecione **Adicionar**. Se o leitor enviar Enter ao final da leitura, a inclusão será automática.
4. Confira o serial na lista. O campo fica livre para a próxima leitura e o contador indica quantos itens foram adicionados.
5. Repita a operação para os demais produtos. Para corrigir um item, selecione **Remover** e adicione o serial correto.
6. Preencha **Título**, se desejar identificar o lote no cabeçalho da folha.
7. Escolha a ação desejada:

| Ação | Funcionamento |
| --- | --- |
| **Visualizar** | Gera e exibe a prévia da folha na própria página. |
| **Imprimir** | Gera a prévia e solicita a impressão pelo navegador. A seleção da impressora depende do sistema e do navegador. |
| **Baixar** | Gera e baixa o PDF do lote atual. |
| **Novo** | Solicita confirmação e, se confirmada, apaga a lista e o título para iniciar outro lote. |

A inclusão do serial é imediata, sem uma etapa de confirmação. Visualizar, imprimir e baixar não apagam o lote. Alterar o título ou a lista invalida os arquivos anteriores na interface; use novamente a ação desejada para obter a versão atualizada.

O aplicativo recebe texto pelo campo de entrada; não realiza leitura pela câmera do celular.

## Regras dos seriais e do título

- Cada lote aceita de **1 a 500 seriais** para geração do arquivo.
- Cada serial deve conter de **1 a 120 caracteres ASCII imprimíveis**, como letras sem acentos, números, espaços e pontuação. Valores formados apenas por espaços são rejeitados.
- Seriais idênticos são bloqueados no mesmo lote. A comparação diferencia maiúsculas e minúsculas e preserva espaços: `ABC123` e `abc123` são valores diferentes.
- O título é opcional e aceita até **120 caracteres**, sem quebras de linha. Acentos portugueses são aceitos; símbolos não suportados pela fonte do PDF são rejeitados.
- Cada QR Code contém exatamente o serial informado, sem adicionar links ou outros dados.

Exemplo de lote:

```text
Título: Conferência de produtos

SN000123
SN000124
SN000125
```

## Como funciona a folha gerada

O resultado sempre ocupa **uma única página A4 na vertical**, com uma tabela de três colunas:

- A primeira coluna apresenta o serial em texto.
- O QR Code fica na segunda coluna na primeira linha e na terceira na linha seguinte, alternando a posição nas demais linhas.
- O título, quando preenchido, aparece centralizado no cabeçalho e pode ocupar mais de uma linha.

A altura das linhas, o tamanho dos QR Codes e a fonte dos seriais são reduzidos automaticamente conforme a quantidade de itens aumenta. Seriais longos também têm a fonte ajustada para caber na primeira coluna.

**O limite de 500 itens é um limite de entrada, não uma garantia de leitura na impressão.** Lotes grandes podem produzir códigos e textos pequenos demais. Faça uma impressão de teste e valide a leitura com o equipamento utilizado; se necessário, divida os itens em lotes menores. O layout não é calibrado para folhas comerciais de etiquetas.

O download usa o nome `seriais.pdf` quando não há título. Com título, utiliza `<título> seriais.pdf`, removendo caracteres proibidos em nomes de arquivo.

## Armazenamento dos dados

A lista e o título são salvos no `localStorage` do navegador e recuperados ao reabrir a página no mesmo endereço e perfil, quando o armazenamento está disponível.

- Os lotes não são compartilhados entre computadores, celulares, navegadores ou perfis.
- Acessar pelo IP e por `localhost` utiliza armazenamentos separados, mesmo no mesmo computador.
- Limpar os dados do site, utilizar navegação privada ou bloquear o armazenamento pode impedir a recuperação do lote.
- Se o salvamento falhar, a interface informa o problema; mantenha a página aberta até concluir a operação.
- O servidor recebe o lote para gerar o PDF ou a prévia e não mantém um banco de dados dos seriais.

Baixe o PDF quando precisar guardar o resultado fora do navegador. O aplicativo não oferece importação de lotes a partir de PDF.

## Funcionamento interno

1. A interface em HTML, CSS e JavaScript registra os seriais, verifica os limites e duplicidades e salva o lote no navegador.
2. Ao solicitar um arquivo, o navegador envia os seriais e o título em JSON para a API Spring Boot.
3. O `PrintService` valida novamente os dados recebidos.
4. O `QrCodeService` utiliza ZXing para gerar cada QR Code com margem branca.
5. O `PrintService` utiliza Apache PDFBox para montar a tabela e os códigos em uma página A4.
6. Para visualização e impressão pelo navegador, o servidor renderiza a página do PDF como imagem PNG a **200 DPI**. Para download, retorna o próprio PDF.

### API

| Método | Endpoint | Resposta |
| --- | --- | --- |
| `POST` | `/api/print` | PDF para download (`application/pdf`). |
| `POST` | `/api/print/preview` | Prévia em PNG (`image/png`). |

Ambos recebem `Content-Type: application/json` com o mesmo corpo:

```json
{
  "serials": ["SN000123", "SN000124"],
  "title": "Conferência de produtos"
}
```

O campo `title` é opcional. Dados inválidos retornam HTTP `400`; falhas na geração retornam HTTP `500`. As respostas de erro incluem o campo `detail` com a descrição do problema.

## Estrutura do projeto

```text
src/main/java/br/com/pichau/qrSerial/
├── QrSerialApplication.java     # Inicialização do Spring Boot
├── controller/                  # Endpoints de PDF e prévia
├── service/                     # Validação, QR Codes e composição do PDF
├── exception/                   # Exceções de validação e geração
└── handller/                    # Tratamento centralizado de erros HTTP

src/main/resources/
├── application.yaml             # Configuração da aplicação
└── static/
    ├── index.html               # Interface
    ├── app.js                   # Entrada, armazenamento e ações do lote
    └── style.css                # Estilos

src/test/java/                   # Testes automatizados
```

O nome `handller` corresponde ao pacote existente no projeto.

## Testes

Execute:

```bash
mvn test
```

A impressão deve ser conferida também no navegador, no dispositivo e na impressora utilizados, incluindo a leitura dos QR Codes no tamanho final.

## Problemas comuns

| Problema | O que verificar |
| --- | --- |
| A página não abre | Confirme se o servidor está em execução e se o endereço e a porta estão corretos. |
| O celular não acessa | Confira o IP do computador, a conexão à mesma rede e a liberação da porta 8080 no firewall. |
| O leitor não adiciona automaticamente | Mantenha o foco no campo Serial e configure o leitor para enviar Enter ao final, ou use Adicionar. |
| Mensagem de serial inválido | Confira o limite de 120 caracteres e a presença de acentos, quebras de linha ou caracteres não permitidos. |
| Mensagem de serial duplicado | O mesmo valor já está na lista; remova o item anterior se precisar registrá-lo novamente. |
| Falha de conexão ao gerar | Verifique se o servidor continua ativo e acessível pelo navegador. |
| O QR Code impresso não é lido | Reduza o tamanho do lote e confira a qualidade e a escala da impressão. |
| O lote não reaparece | Verifique se está usando o mesmo endereço, navegador e perfil e se os dados do site foram preservados. |

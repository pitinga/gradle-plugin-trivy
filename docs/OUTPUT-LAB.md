# Experimento: relatórios nativos do Trivy sem configurar o terminal

Branch: `experiment/output-formats-console-file` — **não publicado**. A branch `main` e a versão pública permanecem intactas.

## Objetivo

Usar apenas as saídas **nativas** do Trivy: `table` e `json`, com a opção de salvar os resultados em arquivo. Nenhum usuário precisa executar `chcp 65001`, modificar o PowerShell ou alterar a configuração do Eclipse.

As tentativas anteriores demonstraram que a tabela original funciona ao usar UTF-8 no terminal, mas falha em terminais Windows com code page CP850. O Gradle também documenta essa limitação de Unicode no seu cliente Windows (gradle/gradle#29356).

### Como funcionam os relatórios

- **Console Windows, formato table:** o Trivy gera a sua **tabela nativa** em UTF-8. O plugin lê a tabela e adapta **somente a representação no console** para caracteres ASCII, que funcionam no Windows PowerShell 7, CMD, Eclipse e ferramentas que não suportam Unicode. As colunas, valores, CVEs e estruturas permanecem gerados pelo Trivy; **não existe template customizado**.
- **Console Windows, formato json:** o Trivy gera o JSON nativo; o plugin representa caracteres não ASCII como sequências JSON `\uXXXX`, conservando os valores e gerando JSON válido mesmo em console legado.
- **Console Linux/macOS:** o Trivy escreve o resultado nativo, sem alteração.
- **Arquivo em qualquer sistema:** utiliza `--output` do Trivy. O arquivo contém os **bytes nativos UTF-8**, inclusive bordas Unicode da tabela, sem nenhuma conversão. Assim como no CLI original, quando existe arquivo a saída completa vai para ele, não para o console.

O plugin não altera `[Console]::OutputEncoding`, a code page do Windows, o daemon Gradle, o IDE ou variáveis de ambiente globais. A apresentação no console Windows é **portável**, mas as bordas ASCII não são os caracteres Unicode originais. Para ver a tabela Unicode exata, abra o arquivo gerado.

## Parâmetros disponíveis

| Gradle DSL | PowerShell 7 | Padrão |
| --- | --- | --- |
| `format = 'table'` | `-PtrivyFormat=table` | `table` |
| `format = 'json'` | `-PtrivyFormat=json` | — |
| `outputFile = 'build/reports/trivy.txt'` | `-PtrivyOutput=build/reports/trivy.txt` | vazio (console) |

O formato `custom` foi removido **somente nesta branch**. A opção experimental `outputStrategy` também foi removida. O comportamento de build (`bootJar`/`jar` + `rootfs`), `target`, `skipDirs`, severidade e demais filtros não mudou.

## Testes rápidos (PowerShell)

Na pasta `scripts` do projeto:

~~~powershell
# 1. Tabela nativa, exibida no console sem configurar codificação
gradle -p ..\lab-output trivy

# 2. Mesmo relatório, gravado com Unicode original em UTF-8
gradle -p ..\lab-output trivy '-PtrivyOutput=build/reports/trivy.txt'

# 3. JSON original em arquivo
gradle -p ..\lab-output trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'

# 4. JSON no console (válido mesmo em code page legada)
gradle -p ..\lab-output trivy '-PtrivyFormat=json'
~~~

Você também pode definir em `lab-output/build.gradle`:

~~~groovy
trivy {
    format = 'table'
    outputFile = '' // deixe vazio para console; caminho para gravar em UTF-8
}
~~~

### Matriz completa automatizada

~~~powershell
# Na pasta scripts:
.\Test-Trivy-Output.ps1

# Sem o comando gradle no PATH:
.\Test-Trivy-Output.ps1 -GradleExecutable 'C:\Caminho\MeuProjeto\gradlew.bat'
~~~

Executa cinco cenários: tabela padrão no console, tabela explícita no console, tabela em arquivo, JSON no console e JSON em arquivo. Gera arquivos de evidência separados e `summary.csv` em `lab-output/build/trivy-output-lab/<timestamp>/`.

A matriz compara conjuntos de IDs CVE para garantir que o formato ou a apresentação não alterem o relatório. No Windows, testa que a saída do console contém apenas ASCII (e que a versão em arquivo mantém as bordas Unicode nativas). O relatório JSON de arquivo é analisado como JSON.

### Como rodar em outro projeto Gradle/Eclipse

No `settings.gradle` do projeto consumidor, use o checkout desta branch como plugin composto:

~~~groovy
pluginManagement {
    includeBuild('C:/DBServer/eclipse-workspace/gradle-plugin-trivy')
}
~~~

Aplique `id 'io.github.pitinga.trivy'` no `plugins` existente **sem a versão publicada**, atualize o Gradle no Eclipse e execute a task `trivy` como de costume, sem alterar o terminal.

## Limites conhecidos e decisões

- UTF-8 Unicode nativo **não pode ser exibido fielmente em qualquer console legado** apenas por uma tarefa executada dentro do Gradle Daemon. O fallback ASCII é deliberado: portabilidade tem precedência sobre a tipografia das bordas.
- Alguns caracteres não latinos nas descrições podem aparecer como `?` na **saída do console Windows**, mas seus valores completos permanecem nos arquivos JSON/tabela.
- A variante Windows usa um arquivo temporário privado para receber os dados UTF-8 do Trivy, depois o apaga. Não mantém o relatório em memória inteira para gravação do arquivo final. 
- No modo `outputFile` o arquivo do Trivy substitui o anterior para evitar relatórios obsoletos; não altere o `target` para o mesmo caminho da saída.
- A opção `-PtrivyOutputStrategy` não existe nesta versão simplificada.
- O plugin não oferece `--format template` nesta branch. Seu único formato console padrão é a tabela gerada pelo Trivy.

**Próximo passo:** validar a tabela diretamente no Console do Eclipse e no terminal PowerShell **sem executar comandos de ajuste de codificação**. Só após aprovação devemos considerar integrar a branch à `main`.

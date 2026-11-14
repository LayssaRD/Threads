# Trabalho de Sockets – Sistemas Distribuídos (CC57E)

Cada exercício está em um pacote `ExercicioNN`. É preciso ter Java 17 ou superior.

## Compilar

Dentro da pasta `TrabalhoSockets`:

```
javac -encoding UTF-8 -d out Exercicio01\*.java Exercicio02\*.java Exercicio03\*.java Exercicio04\*.java Exercicio05\*.java Exercicio06\*.java Exercicio07\*.java
```

Para rodar, abra um terminal para o servidor e outro (ou vários) para os clientes, sempre na pasta `TrabalhoSockets`.
Os clientes aceitam `host` e `porta` como argumentos (padrão: `localhost`).

| Ex. | O que é | Servidor | Cliente |
|-----|---------|----------|---------|
| 1 | Servidor de Fortunes (porta 5001) | `java -cp out Exercicio01.Servidor` | `java -cp out Exercicio01.Cliente` |
| 2 | Operação com inteiros, um cliente por vez (5002) | `java -cp out Exercicio02.Servidor` | `java -cp out Exercicio02.Cliente 10 20 5 SOMA` (ou sem argumentos, modo interativo) |
| 3 | Jogo da forca (5003) | `java -cp out Exercicio03.Servidor` | `java -cp out Exercicio03.Cliente` |
| 4 | Banco com n clientes (5004) | `java -cp out Exercicio04.Servidor` | `java -cp out Exercicio04.Cliente` |
| 5 | Rede de lojas (5005) | `java -cp out Exercicio05.Central` | `java -cp out Exercicio05.Simulacao` |
| 6 | Servidor de arquivos (5006) | `java -cp out Exercicio06.Servidor` | `java -cp out Exercicio06.Cliente` |
| 7 | Chat multicast (230.0.0.1:4446) | não tem servidor | `java -cp out Exercicio07.Chat Ana` (um terminal por pessoa) |

## Detalhes de cada exercício

**1 – Fortunes.** Comandos em texto: `GET-FORTUNE`, `ADD-FORTUNE <frase>`, `UPD-FORTUNE <pos> <frase>`
(a posição começa em 1, como no `LST-FORTUNE`) e `LST-FORTUNE`. As frases ficam salvas em `fortunes.txt`.

**2 – Operação com inteiros.** O cliente envia os números, um por linha, e por último a operação
(`SOMA`, `SUBTRACAO`, `MULTIPLICACAO`, `DIVISAO`, `MEDIA`, `MAIOR`, `MENOR`). Depois fecha a escrita do socket
(`shutdownOutput`). O servidor só calcula quando detecta o *end of file* e atende um cliente de cada vez.

**3 – Forca.** O servidor sorteia a palavra e informa o tamanho dela. A cada letra, responde com as posições
acertadas ou com a parte do boneco desenhada (cabeça, tronco, braços, pernas). São 6 erros até perder.

**4 – Banco.** Contas de exemplo: `1001` (R$ 1.000,00), `1002` (R$ 500,00) e `1003`. Um número novo cria uma conta.
Abra dois clientes na mesma conta: quando um faz depósito ou saque, o outro recebe um aviso na hora com o saldo novo.
As operações são `synchronized` na conta, então não há conflito. Para provar isso:
`java -cp out Exercicio04.TesteConcorrencia 1002 10 200` (10 terminais fazendo 200 depósitos e saques ao mesmo tempo;
o saldo final tem que ser igual ao inicial).

**5 – Rede de lojas.** A `Central` espera 5 filiais (`java -cp out Exercicio05.Central <n>` muda a quantidade). A `Simulacao`
dispara as 5 filiais em paralelo, cada uma com 1500 compras e vendas e um atraso aleatório entre elas.
Também dá para rodar uma filial sozinha, em outro terminal ou máquina: `java -cp out Exercicio05.Filial <id> [ocorrências] [delay ms] [host]`.
No final, a central imprime o relatório consolidado e cada filial confere se os totais batem.

**6 – Servidor de arquivos.** Usuários de exemplo em `usuarios.txt`: `aluno` / `1234` e `professor` / `abcd`.
Só permite upload e download. Cada usuário tem a sua pasta em `armazenamento/<usuario>/`, e os downloads vão para `downloads/`.

**7 – Chat multicast.** Abra dois ou mais terminais com nomes diferentes e use `/sair` para sair. Funciona em máquinas
diferentes da mesma rede local. Se a interface de rede escolhida não for a certa, passe o nome dela como 4º argumento:
`java -cp out Exercicio07.Chat Ana 230.0.0.1 4446 wlan0`.

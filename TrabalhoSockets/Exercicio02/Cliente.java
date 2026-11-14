package Exercicio02;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cliente: envia inteiros + operação e fecha a escrita (EOF) para o servidor calcular.
 *
 * Uso:
 *   java Exercicio02.Cliente                 (modo interativo)
 *   java Exercicio02.Cliente 10 20 5 SOMA    (valores pela linha de comando)
 * Para usar outro servidor: -Dhost=192.168.0.10 -Dporta=5002
 */
public class Cliente {

    public static void main(String[] args) throws IOException {
        String host = System.getProperty("host", "localhost");
        int porta = Integer.parseInt(System.getProperty("porta", "5002"));

        List<String> valores = new ArrayList<>(Arrays.asList(args));
        if (valores.isEmpty()) {
            valores = lerDoTeclado();
        }

        try (Socket socket = new Socket(host, porta)) {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            for (String v : valores) {
                out.println(v);
            }
            out.flush();
            socket.shutdownOutput(); // gera o end of file no servidor
            System.out.println("Enviado: " + valores + "  (EOF enviado)");

            String resposta = in.readLine();
            if (resposta == null) {
                System.out.println("Servidor não respondeu.");
            } else if (resposta.startsWith("OK")) {
                System.out.println("Resultado: " + resposta.substring(2).trim());
            } else {
                System.out.println("Erro: " + resposta.replaceFirst("^ERRO\\s*", ""));
            }
        }
    }

    static List<String> lerDoTeclado() throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charsetConsole()));
        List<String> valores = new ArrayList<>();
        System.out.println("Digite inteiros, um por linha. Termine com a operação:");
        System.out.println("  SOMA, SUBTRACAO, MULTIPLICACAO, DIVISAO, MEDIA, MAIOR ou MENOR");
        String linha;
        while ((linha = teclado.readLine()) != null) {
            linha = linha.trim();
            if (linha.isEmpty()) {
                continue;
            }
            valores.add(linha);
            if (!linha.matches("-?\\d+")) {
                break; // a operação encerra a sequência
            }
        }
        return valores;
    }

    static Charset charsetConsole() {
        Console c = System.console();
        return c != null ? c.charset() : Charset.defaultCharset();
    }
}

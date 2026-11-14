package Exercicio04;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Terminal do banco. Uma thread fica só lendo o que chega do servidor (respostas e avisos de
 * outros terminais), enquanto a thread principal lê o menu do teclado.
 */
public class Cliente {

    public static void main(String[] args) throws IOException, InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5004;

        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charsetConsole()));
        try (Socket socket = new Socket(host, porta)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // login (síncrono)
            String resposta;
            do {
                System.out.print("Número da conta (ex.: 1001): ");
                String numero = teclado.readLine();
                if (numero == null) {
                    return;
                }
                out.println("LOGIN " + numero.trim());
                resposta = in.readLine();
                if (resposta == null) {
                    return;
                }
                System.out.println(formatar(resposta));
            } while (!resposta.startsWith("OK"));

            // a partir daqui as mensagens do servidor chegam a qualquer momento
            Thread leitor = new Thread(() -> {
                try {
                    String linha;
                    while ((linha = in.readLine()) != null) {
                        System.out.println(formatar(linha));
                    }
                } catch (IOException ignored) {
                    // conexão fechada
                }
            });
            leitor.start();

            mostrarMenu();
            String opcao;
            while ((opcao = teclado.readLine()) != null) {
                switch (opcao.trim()) {
                    case "1" -> {
                        System.out.print("Valor do depósito: ");
                        String v = teclado.readLine();
                        if (v != null) out.println("DEPOSITO " + v.trim());
                    }
                    case "2" -> {
                        System.out.print("Valor do saque: ");
                        String v = teclado.readLine();
                        if (v != null) out.println("SAQUE " + v.trim());
                    }
                    case "3" -> out.println("SALDO");
                    case "0" -> {
                        out.println("SAIR");
                        leitor.join(3000);
                        return;
                    }
                    case "" -> mostrarMenu();
                    default -> System.out.println("Opção inválida.");
                }
            }
            out.println("SAIR");
            leitor.join(3000);
        }
    }

    static void mostrarMenu() {
        System.out.println("\n[1] Depósito  [2] Saque  [3] Saldo  [0] Sair   (Enter mostra o menu)");
    }

    static String formatar(String linha) {
        if (linha.startsWith("AVISO ")) {
            return "\n  *** " + linha.substring(6) + " ***";
        }
        if (linha.startsWith("ERRO ")) {
            return "  Erro: " + linha.substring(5);
        }
        return "  " + linha.replaceFirst("^OK\\s*", "");
    }

    static Charset charsetConsole() {
        Console c = System.console();
        return c != null ? c.charset() : Charset.defaultCharset();
    }
}

package Exercicio01;

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
 * Cliente do servidor de Fortunes.
 * Digite os comandos (GET-FORTUNE, ADD-FORTUNE ..., UPD-FORTUNE ..., LST-FORTUNE) ou SAIR.
 */
public class Cliente {

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5001;

        try (Socket socket = new Socket(host, porta);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charsetConsole()))) {

            System.out.println("Conectado ao servidor de Fortunes em " + host + ":" + porta);
            System.out.println("Comandos:");
            System.out.println("  GET-FORTUNE");
            System.out.println("  ADD-FORTUNE <nova frase>");
            System.out.println("  UPD-FORTUNE <pos> <nova frase>");
            System.out.println("  LST-FORTUNE");
            System.out.println("  SAIR");

            while (true) {
                System.out.print("\n> ");
                String comando = teclado.readLine();
                if (comando == null || comando.trim().equalsIgnoreCase("SAIR")) {
                    break;
                }
                if (comando.isBlank()) {
                    continue;
                }
                out.println(comando.trim());

                String resposta = in.readLine();
                if (resposta == null) {
                    System.out.println("Servidor encerrou a conexão.");
                    break;
                }
                boolean ok = resposta.startsWith("OK");
                String texto = resposta.replaceFirst("^(OK|ERRO)\\s*", "");

                if (ok && comando.trim().toUpperCase().startsWith("LST-FORTUNE")) {
                    int n = Integer.parseInt(texto.trim());
                    System.out.println(n + " frase(s) cadastrada(s):");
                    for (int i = 0; i < n; i++) {
                        System.out.println("  " + in.readLine());
                    }
                } else if (ok && comando.trim().toUpperCase().startsWith("GET-FORTUNE")) {
                    System.out.println("Sua sorte: \"" + texto + "\"");
                } else {
                    System.out.println((ok ? "" : "Erro: ") + texto);
                }
            }
        }
        System.out.println("Até mais!");
    }

    static Charset charsetConsole() {
        Console c = System.console();
        return c != null ? c.charset() : Charset.defaultCharset();
    }
}

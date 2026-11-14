package Exercicio04;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Demonstra que não há conflito: abre N terminais na MESMA conta ao mesmo tempo e cada um faz
 * várias vezes "deposita 1,00 / saca 1,00". No fim o saldo tem que ser igual ao inicial.
 *
 * Uso: java Exercicio04.TesteConcorrencia [conta] [terminais] [repeticoes]
 */
public class TesteConcorrencia {

    public static void main(String[] args) throws Exception {
        String conta = args.length > 0 ? args[0] : "1001";
        int terminais = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int repeticoes = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        String antes = consultarSaldo(conta);
        System.out.println("Saldo inicial: " + antes);
        System.out.println(terminais + " terminais x " + repeticoes + " (depósito + saque) na conta " + conta + "...");

        Thread[] threads = new Thread[terminais];
        for (int t = 0; t < terminais; t++) {
            threads[t] = new Thread(() -> {
                try (Socket s = new Socket("localhost", 5004)) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                    out.println("LOGIN " + conta);
                    resposta(in);
                    for (int i = 0; i < repeticoes; i++) {
                        out.println("DEPOSITO 1,00");
                        resposta(in);
                        out.println("SAQUE 1,00");
                        resposta(in);
                    }
                    out.println("SAIR");
                    resposta(in);
                } catch (IOException e) {
                    System.out.println("Falha: " + e.getMessage());
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        String depois = consultarSaldo(conta);
        System.out.println("Saldo final:   " + depois);
        System.out.println(antes.equals(depois) ? "OK: nenhum conflito, saldo preservado." : "ERRO: saldo divergente!");
    }

    /** Lê até a resposta da própria operação, ignorando os avisos dos outros terminais. */
    static String resposta(BufferedReader in) throws IOException {
        String linha;
        while ((linha = in.readLine()) != null) {
            if (!linha.startsWith("AVISO")) {
                return linha;
            }
        }
        throw new IOException("conexão fechada");
    }

    static String consultarSaldo(String conta) throws IOException {
        try (Socket s = new Socket("localhost", 5004)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println("LOGIN " + conta);
            resposta(in);
            out.println("SALDO");
            String r = resposta(in);
            out.println("SAIR");
            return r.substring(r.indexOf("R$"));
        }
    }
}

package Exercicio02;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Servidor que atende UM cliente de cada vez (sem threads).
 *
 * O cliente envia, em texto, uma sequência de inteiros seguida da operação, por exemplo:
 *   10
 *   20
 *   5
 *   SOMA
 * e então fecha o lado de escrita do socket (shutdownOutput). Quando o servidor detecta o
 * end of file na leitura, calcula a operação e devolve o resultado.
 *
 * Operações: SOMA (+), SUBTRACAO (-), MULTIPLICACAO (*), DIVISAO (/), MEDIA, MAIOR, MENOR
 */
public class Servidor {

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5002;

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Servidor de operações ouvindo na porta " + porta + " (um cliente por vez)");
            while (true) {
                try (Socket socket = servidor.accept()) {
                    atender(socket);
                } catch (IOException e) {
                    System.out.println("Erro com o cliente: " + e.getMessage());
                }
            }
        }
    }

    static void atender(Socket socket) throws IOException {
        String quem = socket.getRemoteSocketAddress().toString();
        System.out.println("\n[+] Atendendo " + quem);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        // lê tudo até o end of file (cliente chamou shutdownOutput)
        List<String> tokens = new ArrayList<>();
        String linha;
        while ((linha = in.readLine()) != null) {
            for (String t : linha.trim().split("\\s+")) {
                if (!t.isEmpty()) {
                    tokens.add(t);
                }
            }
        }
        System.out.println("    EOF detectado. Recebido: " + tokens);

        String resposta = calcular(tokens);
        System.out.println("    Resposta: " + resposta);
        out.println(resposta);
        System.out.println("[-] Fim do atendimento de " + quem);
    }

    static String calcular(List<String> tokens) {
        if (tokens.size() < 2) {
            return "ERRO envie pelo menos um inteiro e a operação";
        }
        String op = tokens.get(tokens.size() - 1).toUpperCase();
        List<Long> numeros = new ArrayList<>();
        for (String t : tokens.subList(0, tokens.size() - 1)) {
            try {
                numeros.add(Long.parseLong(t));
            } catch (NumberFormatException e) {
                return "ERRO valor inválido: " + t;
            }
        }

        try {
            switch (op) {
                case "SOMA", "+" -> {
                    long r = 0;
                    for (long n : numeros) r = Math.addExact(r, n);
                    return "OK " + r;
                }
                case "SUBTRACAO", "-" -> {
                    long r = numeros.get(0);
                    for (int i = 1; i < numeros.size(); i++) r = Math.subtractExact(r, numeros.get(i));
                    return "OK " + r;
                }
                case "MULTIPLICACAO", "*", "X" -> {
                    long r = 1;
                    for (long n : numeros) r = Math.multiplyExact(r, n);
                    return "OK " + r;
                }
                case "DIVISAO", "/" -> {
                    double r = numeros.get(0);
                    for (int i = 1; i < numeros.size(); i++) {
                        if (numeros.get(i) == 0) {
                            return "ERRO divisão por zero";
                        }
                        r /= numeros.get(i);
                    }
                    return "OK " + formatar(r);
                }
                case "MEDIA" -> {
                    double soma = 0;
                    for (long n : numeros) soma += n;
                    return "OK " + formatar(soma / numeros.size());
                }
                case "MAIOR", "MAX" -> {
                    return "OK " + numeros.stream().mapToLong(Long::longValue).max().getAsLong();
                }
                case "MENOR", "MIN" -> {
                    return "OK " + numeros.stream().mapToLong(Long::longValue).min().getAsLong();
                }
                default -> {
                    return "ERRO operação desconhecida: " + op
                            + " (use SOMA, SUBTRACAO, MULTIPLICACAO, DIVISAO, MEDIA, MAIOR ou MENOR)";
                }
            }
        } catch (ArithmeticException e) {
            return "ERRO resultado grande demais (overflow)";
        }
    }

    static String formatar(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.4f", v).replace(',', '.');
    }
}

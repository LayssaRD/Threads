package Exercicio03;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cliente do jogo da forca. O cliente não conhece a palavra: ele só recebe do servidor
 * o tamanho, as letras acertadas (com as posições) e as partes do boneco.
 */
public class Cliente {

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5003;

        try (Socket socket = new Socket(host, porta);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charsetConsole()))) {

            String inicio = in.readLine(); // TAMANHO n
            int tamanho = Integer.parseInt(inicio.split(" ")[1]);
            char[] palavra = new char[tamanho];
            Arrays.fill(palavra, '_');
            List<String> partes = new ArrayList<>();
            Set<Character> tentadas = new LinkedHashSet<>();
            int reveladas = 0;

            System.out.println("=== JOGO DA FORCA ===");
            System.out.println("O servidor escolheu uma palavra com " + tamanho + " letras.");

            while (true) {
                desenhar(partes, palavra, tentadas);
                System.out.print("Digite uma letra: ");
                String entrada = teclado.readLine();
                if (entrada == null) {
                    System.out.println("\nVocê desistiu.");
                    return;
                }
                entrada = entrada.trim();
                if (entrada.length() != 1 || !Character.isLetter(entrada.charAt(0))) {
                    System.out.println("Digite apenas UMA letra.");
                    continue;
                }

                out.println("LETRA " + entrada);
                String resposta = in.readLine();
                if (resposta == null) {
                    System.out.println("Servidor encerrou a conexão.");
                    return;
                }
                String[] r = resposta.split(" ", 3);
                switch (r[0]) {
                    case "ACERTO" -> {
                        char letra = r[1].charAt(0);
                        tentadas.add(letra);
                        for (String pos : r[2].split(",")) {
                            palavra[Integer.parseInt(pos) - 1] = letra;
                            reveladas++;
                        }
                        System.out.println(">> Acertou! Servidor revelou a letra " + letra + " na(s) posição(ões) " + r[2]);
                    }
                    case "ERRO" -> {
                        String parte = resposta.substring(5);
                        partes.add(parte);
                        tentadas.add(Character.toUpperCase(entrada.charAt(0)));
                        System.out.println(">> Errou! Servidor enviou a parte do boneco: " + parte);
                    }
                    case "REPETIDA" -> System.out.println(">> Você já tentou a letra " + r[1]);
                    default -> System.out.println(">> Jogada inválida");
                }

                if (reveladas == tamanho || partes.size() == 6) {
                    String fim = in.readLine(); // FIM VITORIA|DERROTA palavra
                    String[] f = fim.split(" ");
                    desenhar(partes, f[2].toCharArray(), tentadas);
                    if (f[1].equals("VITORIA")) {
                        System.out.println("PARABÉNS! Você acertou a palavra " + f[2] + "!");
                    } else {
                        System.out.println("ENFORCADO! A palavra era " + f[2] + ".");
                    }
                    return;
                }
            }
        }
    }

    static void desenhar(List<String> partes, char[] palavra, Set<Character> tentadas) {
        String cabeca = partes.contains("cabeça") ? "O" : " ";
        String tronco = partes.contains("tronco") ? "|" : " ";
        String bracoE = partes.contains("braço esquerdo") ? "/" : " ";
        String bracoD = partes.contains("braço direito") ? "\\" : " ";
        String pernaE = partes.contains("perna esquerda") ? "/" : " ";
        String pernaD = partes.contains("perna direita") ? "\\" : " ";

        StringBuilder sb = new StringBuilder();
        sb.append("\n  +---+\n");
        sb.append("  |   ").append(cabeca).append('\n');
        sb.append("  |  ").append(bracoE).append(tronco).append(bracoD).append('\n');
        sb.append("  |  ").append(pernaE).append(' ').append(pernaD).append('\n');
        sb.append(" ===\n\n");
        sb.append("  Palavra: ");
        for (char c : palavra) {
            sb.append(c).append(' ');
        }
        sb.append("\n  Letras tentadas: ").append(tentadas.isEmpty() ? "-" : tentadas);
        sb.append("   |   Erros: ").append(partes.size()).append("/6\n");
        System.out.println(sb);
    }

    static Charset charsetConsole() {
        Console c = System.console();
        return c != null ? c.charset() : Charset.defaultCharset();
    }
}
